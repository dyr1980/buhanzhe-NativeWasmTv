package xiao.bu.tv;

/** UI-thread feedback only; never submits a seek or changes a stream/decoder. */
final class PlaybackSeekFeedback {
    private long target=-1,origin,requestedAt,nearPosition=-1,bufferedAt=-1;
    private boolean completed;
    private String failure="";
    void reset(){target=nearPosition=bufferedAt=-1;completed=false;failure="";}
    void request(long position,long actual,long now){origin=actual;target=position;requestedAt=now;nearPosition=-1;completed=false;failure="";}
    void complete(){completed=true;}
    boolean pending(){return target>=0;}
    void failed(String message){if(target>=0){failure=message;}}
    void observe(long position,boolean buffering,boolean playing,long now){
        if(buffering){if(bufferedAt<0)bufferedAt=now;}else bufferedAt=-1;
        // A completion callback can precede the first new clock: do not accept the old position.
        boolean moved=target<origin-500?position<origin-250:target>origin+500?position>=Math.min(target,origin+500):true;
        if(target>=0&&completed&&!buffering&&moved&&Math.abs(position-target)<=10000){
            if(!playing){target=-1;failure="";return;}
            if(nearPosition<0||position<nearPosition)nearPosition=position;
            else if(position-nearPosition>=200){target=-1;failure="";}
        }
    }
    String status(long now,boolean buffering){
        if(!failure.isEmpty())return failure;
        if(target>=0){long age=Math.max(0,now-requestedAt);
            if(age>=25000)return "跳转仍未恢复，请重试或切换线路；可能网速不足或源站限制跳转";
            if(age>=8000)return "正在加载，等待较久；可能网速不足或源站限制跳转";
            return "正在加载跳转位置…";
        }
        if(buffering)return bufferedAt>=0&&now-bufferedAt>=8000?"缓冲较慢，请检查网络或切换线路":"正在缓冲…";
        return "";
    }
    boolean loading(long now,boolean buffering){return failure.isEmpty()&&(target>=0?now-requestedAt<25000:buffering);}
}
