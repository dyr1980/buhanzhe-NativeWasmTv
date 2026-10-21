package xiao.bu.channel.bl;

import android.graphics.*;
import android.view.View;
import android.content.SharedPreferences;
import java.util.*;

/** Bounded, clock-driven scrolling/top/bottom overlay with collision-aware lanes. */
final class BlDanmaku extends View {
    interface Clock{long position();boolean running();}
    private static final class Drawn {BlDanmakuData.Line line;float width,size,y,speed;long life;int lane;}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),outline=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Clock clock;private final BlUi ui;private final SharedPreferences prefs;
    private final List<Drawn> visible=new ArrayList<>();private List<BlDanmakuData.Line> lines=Collections.emptyList();
    private int next,maximum=24,alpha=230,weight,bottomInset;private long previous=-1,life=8000;
    private float scale=1,area=.5f;private boolean enabled=true,active=true,attached,scheduled,top=true,bottom=true,live;private String[] blocked=new String[0];
    long drawnCount,animationFrames;
    // A decoder may briefly report !isPlaying while buffering. Keep a cheap wake-up pending
    // until it recovers; only explicit disable/background/detach cancels the animation loop.
    private final Runnable frame=new Runnable(){public void run(){scheduled=false;if(!enabled||!active||!attached)return;boolean playing=clock.running();if(playing){animationFrames++;invalidate();}schedule(playing?33:100);}};
    BlDanmaku(BlUi ui,Clock clock,SharedPreferences prefs){super(ui.context);this.ui=ui;this.clock=clock;this.prefs=prefs;setFocusable(false);outline.setStyle(Paint.Style.STROKE);outline.setColor(0xff000000);configure();}
    void configure(){enabled=prefs.getBoolean("dm_enabled",true);top=prefs.getBoolean("dm_top",true);bottom=prefs.getBoolean("dm_bottom",true);scale=Math.max(.6f,Math.min(1.8f,prefs.getFloat("dm_size",1)));area=Math.max(.25f,Math.min(1,prefs.getFloat("dm_area",.5f)));alpha=Math.round(Math.max(.2f,Math.min(1,prefs.getFloat("dm_alpha",.9f)))*255);life=(long)(8000/Math.max(.5f,Math.min(2,prefs.getFloat("dm_speed",1))));maximum=Math.max(8,Math.min(48,prefs.getInt("dm_density",24)));weight=prefs.getInt("dm_weight",0);blocked=prefs.getString("dm_block","").split("[,，\n]");visible.clear();previous=-1;setVisibility(enabled?VISIBLE:GONE);start();}
    void setLines(List<BlDanmakuData.Line> value){live=false;lines=value;visible.clear();previous=-1;start();}
    // Overlay visibility changes placement, not the playback timeline or scrolling text identities.
    void inset(int pixels){pixels=Math.max(ui.px(62),pixels);if(bottomInset!=pixels){int change=pixels-bottomInset;bottomInset=pixels;for(Drawn d:visible)if(d.line.mode==4)d.y-=change;invalidate();}}
    void addLive(List<BlDanmakuData.Line> batch){if(!live){live=true;lines=new ArrayList<>();visible.clear();next=0;previous=-1;}long now=android.os.SystemClock.elapsedRealtime();for(BlDanmakuData.Line line:batch){line.at=now;lines.add(line);}if(lines.size()>400){int remove=lines.size()-400;lines.subList(0,remove).clear();next=Math.max(0,next-remove);}start();}
    void enabled(boolean value){prefs.edit().putBoolean("dm_enabled",value).apply();configure();}
    boolean enabled(){return enabled;}int loaded(){return lines.size();}int visibleCount(){return visible.size();}
    private void schedule(int delay){if(!scheduled&&enabled&&active&&attached){scheduled=true;postDelayed(frame,delay);}}
    void active(boolean value){active=value;start();}
    void start(){if(active&&enabled&&attached){if(!scheduled){invalidate();schedule(0);}}else{removeCallbacks(frame);scheduled=false;invalidate();}}
    protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;start();}
    protected void onDetachedFromWindow(){attached=false;removeCallbacks(frame);scheduled=false;super.onDetachedFromWindow();}
    protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);visible.clear();previous=-1;}
    private boolean allowed(BlDanmakuData.Line l){if(l.weight<weight||l.mode==4&&!bottom||l.mode==5&&!top)return false;for(String word:blocked)if(!word.trim().isEmpty()&&l.text.contains(word.trim()))return false;return true;}
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!enabled)return;long now=live?android.os.SystemClock.elapsedRealtime():clock.position();
        if(previous<0||now<previous||now-previous>3000){visible.clear();int low=0,high=lines.size();long from=Math.max(0,now-life);while(low<high){int mid=(low+high)/2;if(lines.get(mid).at<from)low=mid+1;else high=mid;}next=low;}
        previous=now;for(int i=visible.size()-1;i>=0;i--){Drawn d=visible.get(i);if(now-d.line.at>=life||d.y<d.size||d.y>getHeight()-bottomInset)visible.remove(i);}
        float row=ui.px(40)*scale;int lanes=Math.max(1,(int)((Math.min(getHeight()*area,getHeight()-bottomInset)-ui.px(32))/row));
        while(next<lines.size()&&lines.get(next).at<=now){BlDanmakuData.Line l=lines.get(next++);if(now-l.at>=life||visible.size()>=maximum||!allowed(l))continue;
            Drawn d=new Drawn();d.line=l;d.life=life;d.size=ui.px(Math.max(16,Math.min(36,l.size)))*scale;paint.setTextSize(d.size);d.width=paint.measureText(l.text);d.speed=(getWidth()+d.width)/life;
            int lane=-1;for(int candidate=0;candidate<lanes;candidate++){float y=l.mode==4?getHeight()-bottomInset-candidate*row:(candidate+1)*row;boolean free=true;for(Drawn old:visible){if(Math.abs(old.y-y)>=row)continue;boolean fixed=l.mode==4||l.mode==5,oldFixed=old.line.mode==4||old.line.mode==5;if(fixed||oldFixed||l.mode==6||old.line.mode==6){free=false;break;}float oldRight=getWidth()-old.speed*(now-old.line.at)+old.width;float gap=getWidth()-d.speed*(now-l.at)-oldRight-ui.px(24);long remaining=old.life-(now-old.line.at);if(gap<0||d.speed>old.speed&&gap/(d.speed-old.speed)<remaining){free=false;break;}}if(free){lane=candidate;d.y=y;break;}}
            if(lane<0)continue;d.lane=lane;visible.add(d);
        }
        paint.setAlpha(alpha);outline.setAlpha(alpha);outline.setStrokeWidth(Math.max(1,ui.px(2)));
        for(Drawn d:visible){long elapsed=now-d.line.at;float x=d.line.mode==4||d.line.mode==5?(getWidth()-d.width)/2:d.line.mode==6?-d.width+d.speed*elapsed:getWidth()-d.speed*elapsed;paint.setTextSize(d.size);outline.setTextSize(d.size);paint.setColor(d.line.color);paint.setAlpha(alpha);canvas.drawText(d.line.text,x,d.y,outline);canvas.drawText(d.line.text,x,d.y,paint);drawnCount++;}
    }
    void close(){active=false;removeCallbacks(frame);scheduled=false;visible.clear();lines=Collections.emptyList();}
}
