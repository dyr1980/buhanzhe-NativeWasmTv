package xiao.bu.channel.bl;

import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** At most three protobuf segments, fetched off the UI thread and invalidated on video switches. */
final class BlDanmakuFeed {
    private final BlApi api;private final BlDanmaku view;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LinkedHashMap<Integer,List<BlDanmakuData.Line>> cache=new LinkedHashMap<>(4,.75f,true);
    private final Set<Integer> pending=new HashSet<>();private int generation;private long cid,aid;private BlDanmakuData.Meta meta=new BlDanmakuData.Meta();
    private boolean closed,legacy,ready;String status="正在加载弹幕…";
    BlDanmakuFeed(BlApi api,BlDanmaku view){this.api=api;this.view=view;}
    void reset(long cid,long aid){generation++;this.cid=cid;this.aid=aid;ready=false;legacy=false;pending.clear();cache.clear();worker.getQueue().clear();view.setLines(Collections.<BlDanmakuData.Line>emptyList());status="正在加载弹幕…";final int token=generation;final long id=cid,video=aid;
        worker.execute(new Runnable(){public void run(){BlDanmakuData.Meta value;try{value=BlDanmakuData.meta(api.binary("/x/v2/dm/web/view",BlApi.map("type","1","oid",""+id,"pid",""+video),256*1024));}catch(Exception e){if(!closed&&token==generation)api.diagnostics.record("弹幕元信息回退",e,"cid="+id);value=new BlDanmakuData.Meta();}final BlDanmakuData.Meta found=value;main.post(new Runnable(){public void run(){if(closed||token!=generation)return;meta=found;ready=true;if(meta.disabled)status="此视频已关闭弹幕";else update(0);}});}});
    }
    void update(long at){if(closed||!ready||meta.disabled||legacy||!view.enabled())return;int page=(int)(at/meta.pageMs)+1;request(page);if(meta.total==0||page<meta.total)request(page+1);}
    private void request(final int page){if(page<1||meta.total>0&&page>meta.total||cache.containsKey(page)||pending.contains(page))return;pending.add(page);final int token=generation;final long id=cid,video=aid;
        worker.execute(new Runnable(){public void run(){if(closed||token!=generation)return;List<BlDanmakuData.Line> loaded;boolean fallback=false;String error="";
            try{loaded=BlDanmakuData.protobuf(api.binary("/x/v2/dm/wbi/web/seg.so",BlApi.map("type","1","oid",""+id,"pid",""+video,"segment_index",""+page),4*1024*1024));}
            catch(Exception failure){if(!closed&&token==generation)api.diagnostics.record("分段弹幕回退 XML",failure,"cid="+id+"; 分段="+page);try{loaded=BlDanmakuData.xml(api.bytes(api.endpoint(BlApi.API,"/x/v1/dm/list.so?oid="+id),4*1024*1024));fallback=true;}catch(Exception e){loaded=Collections.emptyList();if(!closed&&token==generation){BlDiagnostics.Entry entry=api.diagnostics.record("兼容弹幕加载",e,"cid="+id);error="弹幕暂不可用："+entry.summary;}else error="弹幕请求已结束";}}
            final List<BlDanmakuData.Line> result=loaded;final boolean xml=fallback;final String problem=error;
            main.post(new Runnable(){public void run(){if(closed||token!=generation)return;pending.remove(page);if(legacy)return;if(xml){legacy=true;cache.clear();}cache.put(page,result);while(cache.size()>3)cache.remove(cache.keySet().iterator().next());ArrayList<BlDanmakuData.Line> all=new ArrayList<>();for(List<BlDanmakuData.Line> segment:cache.values())all.addAll(segment);BlDanmakuData.sort(all);view.setLines(all);status=!problem.isEmpty()?problem:all.size()+" 条弹幕 · "+(legacy?"兼容模式":"分段加载");}});
        }});
    }
    void close(){closed=true;generation++;worker.shutdownNow();main.removeCallbacksAndMessages(null);cache.clear();pending.clear();}
}
