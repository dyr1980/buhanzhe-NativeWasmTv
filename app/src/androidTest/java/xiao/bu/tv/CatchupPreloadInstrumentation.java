package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Real decoder regression. Fixture base serves current, missing, mismatch,
 * broken, slow and match.m3u8; start is the matching programme's UTC millis. */
public final class CatchupPreloadInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private String base;
    private EpgManager.Program programme;
    private int count;

    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private Object get(String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }
    private void set(String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);
    }
    private Object call(String name,Class<?>[] types,Object... values)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);
    }
    private interface Work {void run()throws Exception;}
    private void main(Work work)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private void begin(String path)throws Exception{
        main(()->call("beginCatchupPlayback",new Class[]{int.class,int.class,int.class,EpgManager.Program.class,String.class},
                0,0,0,programme,base+path+".m3u8"));
    }
    private void awaitLoaded(boolean archive)throws Exception{
        long end=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<end){
            if((Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted")
                    &&(get("catchupProgram")!=null)==archive)return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("No first frame, archive="+archive+" status="+get("catchupValidationError"));
    }
    private void preserved(String path,boolean cancel)throws Exception{
        IMediaPlayer old=(IMediaPlayer)get("player");int request=(Integer)get("playRequestId");
        long before=old.getCurrentPosition();begin(path);
        if(cancel){SystemClock.sleep(250);main(()->call("cancelPendingCatchup",new Class[]{}));}
        long end=SystemClock.elapsedRealtime()+30000;
        while(get("catchupPreloader")!=null&&SystemClock.elapsedRealtime()<end){
            check(get("player")==old,"Player released during "+path);SystemClock.sleep(50);
        }
        check(get("catchupPreloader")==null,"Preloader stuck: "+path);
        SystemClock.sleep(1200);
        check(get("player")==old&&old.isPlaying(),"Playback stopped after "+path);
        check((Integer)get("playRequestId")==request&&get("catchupProgram")==null,"Selection changed after "+path);
        check(old.getCurrentPosition()>before+500,"Current playback clock stalled after "+path);
        count++;Bundle progress=new Bundle();progress.putString("stream","PASS preserved current playback: "+path+(cancel?" cancelled":"")+"\n");sendStatus(0,progress);
    }
    public void onStart(){
        Bundle result=new Bundle();ChannelCatalog.Group[] saved=null;
        try{
            base=args.getString("base");check(base!=null,"Missing fixture base URL");
            long start=Long.parseLong(args.getString("start"));
            programme=new EpgManager.Program(start,start+1800000,"Preload regression","");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2000);saved=ChannelCatalog.GROUPS;
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            String text="#EXTM3U\n#EXTINF:-1,Preload fixture\n"+base+"current.m3u8\n";
            ChannelCatalog.Group[] fixture=(ChannelCatalog.Group[])parse.invoke(null,text.getBytes("UTF-8"),base+"playlist.m3u");
            main(()->{
                ChannelCatalog.GROUPS=fixture;set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);
                set("autoSwitchSource",false);set("autoSwitchSourceSeconds",0);set("catchupProgram",null);
                call("startChannel",new Class[]{int.class},0);
            });
            awaitLoaded(false);
            preserved("missing",false);
            preserved("mismatch",false);
            preserved("broken",false);
            preserved("slow",true);
            preserved("slow",false);
            IMediaPlayer old=(IMediaPlayer)get("player");begin("match");
            long end=SystemClock.elapsedRealtime()+30000;
            while(get("catchupProgram")==null&&SystemClock.elapsedRealtime()<end){
                check(get("player")==old,"Current player released before readiness");SystemClock.sleep(30);
            }
            awaitLoaded(true);check(get("player")!=old,"Successful archive did not switch player");
            check((Boolean)get("catchupTimeVerified"),"Successful archive time not verified");
            count++;result.putString("stream","PASS "+count+" Android 11 cases: HTTP failure, wrong time, decoder failure, cancellation, timeout preserve the playing programme; matching first frame switches successfully.");
            finish(-1,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}
        finally{
            try{if(activity!=null)main(()->{call("cancelPendingCatchup",new Class[]{});activity.finish();});}catch(Throwable ignored){}
            if(saved!=null)ChannelCatalog.GROUPS=saved;
        }
    }
}
