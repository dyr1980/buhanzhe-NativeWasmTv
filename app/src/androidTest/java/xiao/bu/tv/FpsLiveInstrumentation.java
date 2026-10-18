package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Observe the automatic HUD ticker against real CCTV streams, without forcing refresh. */
public final class FpsLiveInstrumentation extends Instrumentation {
    private MainActivity activity;
    private File directory;
    private final JSONArray results=new JSONArray();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object get(String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    private void set(String key,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(activity,value);}
    private void call(String key)throws Exception{Method m=MainActivity.class.getDeclaredMethod(key);m.setAccessible(true);m.invoke(activity);}
    private Object stat(Object stats,String key)throws Exception{Field f=stats.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(stats);}
    private interface Work{void run()throws Exception;}
    private void main(Work work)throws Exception{Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new Exception(failure[0]);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void save()throws Exception{try(FileOutputStream out=new FileOutputStream(new File(directory,"fps-live.json"))){out.write(results.toString(2).getBytes("UTF-8"));}}
    private void progress(String text){Bundle out=new Bundle();out.putString("stream",text+"\n");sendStatus(0,out);}
    @Override public void onStart(){
        Bundle out=new Bundle();int failed=0;ChannelCatalog.Group[] original=ChannelCatalog.GROUPS;String selected=null;
        try {
            check(getTargetContext().getPackageName().endsWith(".iptvdiag"),"Only isolated package permitted");
            directory=getTargetContext().getExternalFilesDir(null);
            long until=SystemClock.elapsedRealtime()+30000;
            while(LocalPlayerRegistry.localInputOwner()==null && SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            activity=LocalPlayerRegistry.localInputOwner();check(activity!=null,"Main activity not launched");
            SystemClock.sleep(1000);selected=new JSONObject(TvUiPlugin.status(activity)).getString("selected");
            main(()->{
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("CCTV FPS regression",ChannelCatalog.SOURCE_CCTV_WEB,ChannelCatalog.CCTV_CHANNELS)};
                set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);
                set("autoSwitchSource",false);set("showDebugInfo",true);set("showColorDebugInfo",true);set("decodeMode","hardware");
            });
            int[] indices={0,1,4,12};String[] themes={"builtin","ui2","ui4","ui6"};
            for(int n=0;n<indices.length;n++) {
                JSONObject row=new JSONObject().put("channel",ChannelCatalog.CCTV_CHANNELS[indices[n]].name).put("theme",themes[n]).put("status","running");results.put(row);save();
                try {
                    TvUiPlugin.select(activity,themes[n]);waitForIdleSync();int index=indices[n];
                    main(()->{set("currentSourceIndex",0);Method play=MainActivity.class.getDeclaredMethod("startChannel",int.class);play.setAccessible(true);play.invoke(activity,index);});
                    until=SystemClock.elapsedRealtime()+70000;boolean ready=false;
                    while(SystemClock.elapsedRealtime()<until){boolean[] value={false};main(()->value[0]=(Boolean)get("videoRenderingStarted"));if(value[0]){ready=true;break;}SystemClock.sleep(100);}
                    check(ready,"No first frame");
                    long began=SystemClock.elapsedRealtime();Object previous=null;int updates=0;JSONArray samples=new JSONArray();
                    while(SystemClock.elapsedRealtime()-began<12000) {
                        main(()->{
                            Object stats=get("latestPlaybackDebugStats");
                            if(stats!=null && get("player") instanceof IjkMediaPlayer) {
                                IjkMediaPlayer player=(IjkMediaPlayer)get("player");
                                samples.put(new JSONObject().put("at",SystemClock.elapsedRealtime()).put("fps",stat(stats,"frameRate"))
                                    .put("sourceFps",stat(stats,"nominalFrameRate")).put("bitrate",stat(stats,"videoBitrate"))
                                    .put("cpu",stat(stats,"cpuUsage")).put("cpuAt",get("lastCpuSampleAt"))
                                    .put("nativeFps",IjkStreamMetrics.outputFrameRate(player)));
                            }
                        });
                        Object current=get("latestPlaybackDebugStats");if(current!=previous){updates++;previous=current;}
                        SystemClock.sleep(250);
                    }
                    row.put("automaticUpdates",updates).put("samples",samples);
                    check(updates>=10,"HUD ticker stopped or too slow");check(samples.length()>10,"No samples");
                    JSONObject last=samples.getJSONObject(samples.length()-1);
                    check(last.getDouble("fps")>0,"Live output FPS stuck at zero");check(last.getDouble("sourceFps")>0,"Source FPS missing");
                    Object stats=get("latestPlaybackDebugStats");Method format=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",stats.getClass());format.setAccessible(true);
                    row.put("label",format.invoke(null,stats)).put("status","passed");
                    android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
                    if(bitmap!=null){try(FileOutputStream image=new FileOutputStream(new File(directory,"fps-live-"+n+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}finally{bitmap.recycle();}}
                }catch(Throwable error){failed++;row.put("status","failed").put("error",android.util.Log.getStackTraceString(error));}
                save();progress(row.optString("channel")+" "+row.optString("status")+" "+row.optString("label"));
            }
            out.putString("stream","COMPLETE failures="+failed+"\n");
        }catch(Throwable error){failed++;out.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            if(activity!=null)try{main(()->{set("playRequestId",(Integer)get("playRequestId")+1);call("releasePlayer");activity.finish();});if(selected!=null)TvUiPlugin.select(activity,selected);}catch(Exception ignored){}
            ChannelCatalog.GROUPS=original;
        }
        finish(failed==0?-1:0,out);
    }
}
