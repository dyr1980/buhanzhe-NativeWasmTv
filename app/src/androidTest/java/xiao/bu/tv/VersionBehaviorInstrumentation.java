package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.HashSet;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Shared black-box playback checks, compiled against each version's production sources. */
public final class VersionBehaviorInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle arguments;
    private final StringBuilder report=new StringBuilder();
    private ChannelCatalog.Group[] original;
    private interface Work { void run() throws Exception; }
    private void ui(Work work) throws Exception {
        Throwable[] failure={null};
        runOnMainSync(() -> {try {work.run();} catch(Throwable t) {failure[0]=t;}});
        if(failure[0]!=null) throw new Exception(failure[0]);
    }
    private Object get(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private void set(String name,Object value) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);
    }
    private Object call(Object owner,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(owner,args);
    }
    private void check(boolean valid,String reason) {if(!valid) throw new AssertionError(reason);}
    private IMediaPlayer player() throws Exception {return (IMediaPlayer)get(activity,"player");}
    private void play(int index,boolean audio) throws Exception {
        String expected=new String[]{"MP4","HLS VOD","MP3"}[index];
        report.append("SWITCH index=").append(index).append(" group=").append(get(activity,"currentGroupIndex"))
            .append(" suspended=").append(get(activity,"multimediaSuspended")).append('\n');
        long started=SystemClock.elapsedRealtime();
        ui(() -> call(activity,"switchChannel",new Class<?>[]{int.class,int.class},index,0));
        boolean[] ready={false};
        while(SystemClock.elapsedRealtime()-started<20000) {
            ui(() -> ready[0]=(Boolean)get(activity,"prepared")
                && get(activity,"activePlayerChannel") instanceof Channel
                && ((Channel)get(activity,"activePlayerChannel")).name.equals(expected)
                && (Boolean)get(activity,"audioOnlyPlayback")==audio
                && (audio || (Boolean)get(activity,"videoRenderingStarted")));
            if(ready[0]) break;
            SystemClock.sleep(100);
        }
        check(ready[0],"No prepared "+(audio?"audio":"video frame")+" index="+index
            +" prepared="+get(activity,"prepared")+" frame="+get(activity,"videoRenderingStarted")
            +" active="+(get(activity,"activePlayerChannel")==null?"null":((Channel)get(activity,"activePlayerChannel")).name)
            +" selected="+get(activity,"currentChannelIndex")+" suspended="+get(activity,"multimediaSuspended")
            +" status="+((android.widget.TextView)get(activity,"statusText")).getText());
        IMediaPlayer media=player();long from=media.getCurrentPosition();
        SystemClock.sleep(1000);
        check(media.isPlaying() && media.getCurrentPosition()>from+350,"Playback stalled index="+index
            +" from="+from+" to="+media.getCurrentPosition()+" playing="+media.isPlaying()+" same="+(player()==media)
            +" buffering="+get(activity,"buffering"));
        check(media.getDuration()>10000,"VOD duration missing index="+index);
        if(!audio) check(media.getVideoWidth()==320 && media.getVideoHeight()==180,"Wrong video dimensions");
        report.append("PASS index=").append(index).append(" audio=").append(audio).append(" prepared/first-frame-ms=")
            .append(SystemClock.elapsedRealtime()-started-1000).append(" decoder=").append(media.getClass().getSimpleName())
            .append(" clock=").append(media.getCurrentPosition()).append('\n');
        ui(() -> call(activity,"togglePlayback",new Class<?>[0]));
        long paused=media.getCurrentPosition();SystemClock.sleep(650);
        check(!media.isPlaying() && Math.abs(media.getCurrentPosition()-paused)<250,"Pause failed");
        ui(() -> media.seekTo(10000));
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(Math.abs(media.getCurrentPosition()-10000)>1500 && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100);
        check(Math.abs(media.getCurrentPosition()-10000)<1500,"Seek failed: "+media.getCurrentPosition());
        ui(() -> call(activity,"togglePlayback",new Class<?>[0]));
        from=media.getCurrentPosition();deadline=SystemClock.elapsedRealtime()+5000;
        while((!media.isPlaying() || media.getCurrentPosition()<=from+250) && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100);
        check(media.isPlaying() && media.getCurrentPosition()>from+250,"Resume failed position="+media.getCurrentPosition()+" from="+from+" buffering="+get(activity,"buffering"));
        if(!audio) frame(index);
        report.append("PASS pause/seek-10s/resume index=").append(index).append('\n');
    }
    private void frame(int index) throws Exception {
        ui(() -> {call(activity,"hideLoading",new Class<?>[0]);call(activity,"closeChannelList",new Class<?>[0]);});
        SystemClock.sleep(150);
        Bitmap bitmap=null;HashSet<Integer> colors=new HashSet<Integer>();
        long deadline=SystemClock.elapsedRealtime()+5000;
        do {
            if(bitmap!=null)bitmap.recycle();
            bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot unavailable");colors.clear();
            for(int y=bitmap.getHeight()/3;y<bitmap.getHeight()*2/3;y+=8)
                for(int x=bitmap.getWidth()/3;x<bitmap.getWidth()*2/3;x+=8) colors.add(bitmap.getPixel(x,y));
            if(colors.size()>20)break;
            SystemClock.sleep(150);
        }while(SystemClock.elapsedRealtime()<deadline);
        try {
            File folder=new File(getTargetContext().getExternalFilesDir(null),"regression");folder.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(folder,"frame-"+index+".png"))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            check(colors.size()>20,"Video surface stayed blank: colors="+colors.size()+" pixel="+bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2)
                +" view="+((View)get(activity,"videoView")).getVisibility()+" blackout="+((View)get(activity,"channelSwitchBlackout")).getVisibility());
            report.append("PASS rendered screenshot center-colors=").append(colors.size()).append('\n');
        } finally {bitmap.recycle();}
    }
    private void swipe(View overlay,float x1,float y1,float x2,float y2,boolean cancel) {
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(now,now+50,action,action==0?x1:x2,action==0?y1:y2,0);
            overlay.onTouchEvent(event);event.recycle();
        }
    }
    private void overlay() throws Exception {
        Class<?> type;
        try {type=Class.forName("xiao.bu.tv.VideoControlsOverlay");}
        catch(ClassNotFoundException baseline) {report.append("N/A baseline has no video gesture overlay\n");return;}
        Class<?> contract=Class.forName("xiao.bu.tv.VideoControlsOverlay$Playback");
        final long[] position={4000},duration={20000};final int[] seeks={0};
        Object source=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args) -> {
            switch(method.getName()) {
                case "playing":return true;case "duration":return duration[0];case "position":return position[0];
                case "title":return "regression";case "speedSupported":return true;case "speed":return args==null?1f:null;
                case "seek":position[0]=((Number)args[0]).longValue();seeks[0]++;return null;
                default:return null;
            }
        });
        Constructor<?> constructor=type.getDeclaredConstructor(android.app.Activity.class,contract,boolean.class);constructor.setAccessible(true);
        View[] view={null};FrameLayout root=(FrameLayout)activity.findViewById(android.R.id.content);
        AudioManager audio=(AudioManager)activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        int oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        float oldBrightness=activity.getWindow().getAttributes().screenBrightness;
        try {
            ui(() -> {view[0]=(View)constructor.newInstance(activity,source,false);root.addView(view[0],new FrameLayout.LayoutParams(-1,-1));
                android.view.WindowManager.LayoutParams params=activity.getWindow().getAttributes();params.screenBrightness=.5f;activity.getWindow().setAttributes(params);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2,0);
                call(view[0],"show",new Class<?>[0]);});
            waitForIdleSync();SystemClock.sleep(200);
            ui(() -> {
                float w=view[0].getWidth(),h=view[0].getHeight();check(w>0 && h>0,"Overlay unmeasured");
                int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                swipe(view[0],w*.2f,h*.6f,w*.2f,h*.45f,false);
                check(activity.getWindow().getAttributes().screenBrightness>.6f,"Left swipe did not brighten");
                check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==before,"Left swipe changed volume");
                float brightness=activity.getWindow().getAttributes().screenBrightness;
                swipe(view[0],w*.8f,h*.6f,w*.8f,h*.45f,false);
                check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>before,"Right swipe did not raise volume");
                check(activity.getWindow().getAttributes().screenBrightness==brightness,"Right swipe changed brightness");
                swipe(view[0],w*.3f,h*.5f,w*.6f,h*.5f,false);
                check(seeks[0]==1 && Math.abs(position[0]-10000)<50,"Horizontal seek target wrong");
                swipe(view[0],w*.3f,h*.5f,w*.6f,h*.5f,true);check(seeks[0]==1,"Canceled swipe committed seek");
                duration[0]=0;swipe(view[0],w*.3f,h*.5f,w*.6f,h*.5f,false);check(seeks[0]==1,"Live stream was seeked");
            });
            report.append("PASS overlay left brightness/right volume, horizontal seek, cancel and live guards\n");
        } finally {ui(() -> {
            if(view[0]!=null){call(view[0],"hide",new Class<?>[0]);root.removeView(view[0]);}
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume,0);
            android.view.WindowManager.LayoutParams params=activity.getWindow().getAttributes();params.screenBrightness=oldBrightness;activity.getWindow().setAttributes(params);
        });}
    }
    @Override public void onCreate(Bundle args) {super.onCreate(args);arguments=args;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int status=-1;LegacyFullInstrumentation.Fixture local=null;
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);
            local=new LegacyFullInstrumentation.Fixture(new java.io.File(android.os.Environment.getExternalStorageDirectory(),"ntv-legacy-audit"));final String fixtureBase=local.base();
            ui(() -> {
                ((java.util.concurrent.atomic.AtomicInteger)get(activity,"catalogLoadGeneration")).incrementAndGet();
                set("pendingCjsChannelIndex",-1);set("playRequestId",((Integer)get(activity,"playRequestId"))+1);
                call(activity,"cancelCustomSourceTimeout",new Class<?>[0]);call(activity,"clearPendingPlayer",new Class<?>[0]);
                call(activity,"cancelRemoteResolve",new Class<?>[0]);
                try {call(activity,"cancelHttpResolve",new Class<?>[0]);} catch(NoSuchMethodException baseline) { }
                call(activity,"releasePlayer",new Class<?>[0]);call(activity,"closeWebSource",new Class<?>[0]);
                original=ChannelCatalog.GROUPS;
                String base=fixtureBase;
                Channel[] channels=new Channel[]{new Channel("1","MP4","",base+"video.mp4",null,null),
                    new Channel("2","HLS VOD","",base+"vod.m3u8",null,null),new Channel("3","MP3","",base+"audio.mp3",null,null)};
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("Regression",ChannelCatalog.SOURCE_CUSTOM,channels)};
                set("currentGroupIndex",0);set("decodeMode",arguments.getString("decodeMode","auto"));
            });
            play(0,false);play(1,false);play(2,true);play(0,false);overlay();
        } catch(Throwable error) {status=0;report.append(android.util.Log.getStackTraceString(error));}
        finally {if(activity!=null) try {ui(() -> {call(activity,"releasePlayer",new Class<?>[0]);if(original!=null) ChannelCatalog.GROUPS=original;activity.finish();});}catch(Exception error){status=0;report.append(error);}}
        if(local!=null)local.close();
        result.putString("stream",report.toString());finish(status,result);
    }
}
