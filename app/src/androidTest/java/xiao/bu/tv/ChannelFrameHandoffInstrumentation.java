package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Fixture base provides live0/1/2.m3u8 with red/green/blue video;
 * channel 2's manifest is delayed so the menu's closing frame is observable. */
public final class ChannelFrameHandoffInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private String base;
    private int checks;
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private Object get(String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private void set(String name,Object value)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);
    }
    private Object call(String name,Class<?>[] types,Object... values)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,values);
    }
    private interface Work {void run()throws Exception;}
    private interface Condition {boolean get()throws Exception;}
    private void main(Work work)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private void until(Condition condition,String message)throws Exception{
        long end=SystemClock.elapsedRealtime()+20000;
        while(!condition.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);
        check(condition.get(),message);
    }
    private void ready(int channel)throws Exception{
        until(()->(Integer)get("currentChannelIndex")==channel&&(Boolean)get("prepared")
                &&(Boolean)get("videoRenderingStarted")&&get("catchupProgram")==null
                &&(base+"live"+channel+".m3u8").equals(get("activePlayerStreamUrl")),"Target first frame missing: "+channel);
        SystemClock.sleep(180);
        Bundle progress=new Bundle();progress.putString("stream","First frame: channel "+channel+"\n");sendStatus(0,progress);
    }
    private void touch(int action,long down,float x,float y)throws Exception{
        View root=(View)get("root");int[] at=new int[2];main(()->root.getLocationOnScreen(at));
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,at[0]+x*root.getWidth(),at[1]+y*root.getHeight(),0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();
    }
    private void swipe(boolean up)throws Exception{
        long down=SystemClock.uptimeMillis();float begin=up?.7f:.3f,delta=up?-.35f:.35f;
        touch(MotionEvent.ACTION_DOWN,down,.8f,begin);
        for(int i=1;i<=6;i++){touch(MotionEvent.ACTION_MOVE,down,.8f,begin+delta*i/6);SystemClock.sleep(18);}
        touch(MotionEvent.ACTION_UP,down,.8f,begin+delta);
    }
    private int videoPixel()throws Exception{
        DirectVideoView video=(DirectVideoView)get("videoView");int[] point=new int[2];main(()->video.getLocationOnScreen(point));
        Bitmap screen=getUiAutomation().takeScreenshot();check(screen!=null,"Screenshot unavailable");
        try{return screen.getPixel(Math.min(screen.getWidth()-1,Math.max(0,point[0]+video.getWidth()*2/5)),
                Math.min(screen.getHeight()-1,Math.max(0,point[1]+video.getHeight()*3/10)));}
        finally{screen.recycle();}
    }
    private void choose(int channel)throws Exception{
        main(()->{
            call("openChannelList",new Class[]{boolean.class},false);
            check(!(Boolean)get("channelSwitchAnimating")&&get("retiringVideoView")==null,"Menu inherited running swipe");
            DirectVideoView spare=(DirectVideoView)get("standbyVideoView");
            if(spare!=null)check(spare.isFrameCovered(),"Retired Surface still shows an older channel");
            call("switchBrowsingChannel",new Class[]{int.class},channel);
            check(((DirectVideoView)get("videoView")).isFrameCovered(),"Menu closed before stale frame was concealed");
            check((Integer)get("concealedVideoRequestId")==getInt("playRequestId"),"Closing draw has wrong cover owner");
        });
        if(channel==2){
            SystemClock.sleep(120);int pixel=videoPixel();
            check(Color.red(pixel)<35&&Color.green(pixel)<35&&Color.blue(pixel)<35,"Old frame visible during closing draw: "+Integer.toHexString(pixel));
        }
        ready(channel);check(!((DirectVideoView)get("videoView")).isFrameCovered(),"Target frame remained concealed");
        int color=videoPixel();
        if(channel==0)check(Color.red(color)>Color.green(color)+70&&Color.red(color)>Color.blue(color)+70,"Wrong final red frame");
        if(channel==2)check(Color.blue(color)>Color.green(color)+70&&Color.blue(color)>Color.red(color)+70,"Wrong final blue frame");
    }
    private int getInt(String name)throws Exception{return (Integer)get(name);}
    public void onStart(){
        Bundle result=new Bundle();ChannelCatalog.Group[] saved=null;int code=0;
        SharedPreferences preferences=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        String[] keys={"last_channel_snapshot_v2","last_channel_favorite_key","last_group_index_v2","last_channel_index_v2"};
        java.util.Map<String,?> previous=preferences.getAll();
        boolean priorFixture=preferences.getString(keys[0],"").contains("Frame regression");
        try{
            base=args.getString("base");check(base!=null,"Missing fixture base");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);saved=ChannelCatalog.GROUPS;
            StringBuilder m3u=new StringBuilder("#EXTM3U\n");
            for(int i=0;i<3;i++)m3u.append("#EXTINF:-1 group-title=\"Frame regression\",Channel ").append(i).append('\n').append(base).append("live").append(i).append(".m3u8\n");
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.toString().getBytes("UTF-8"),base+"list.m3u");
            main(()->{
                ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);
                set("autoSwitchSource",false);set("autoSwitchSourceSeconds",0);set("catchupProgram",null);
                call("switchChannel",new Class[]{int.class,int.class},0,0);
            });
            ready(0);check((Long)call("seekableDuration",new Class[]{})==0,"Fixture is not live");
            for(int i=0;i<2;i++){
                swipe(true);ready(1);until(()->!(Boolean)get("channelSwitchAnimating"),"Up swipe stuck");choose(2);
                swipe(false);ready(1);until(()->!(Boolean)get("channelSwitchAnimating"),"Down swipe stuck");choose(0);
            }
            // Open/select while the surface entrance animation is still active.
            swipe(true);choose(2);swipe(false);choose(0);
            result.putString("stream","PASS "+checks+" real frame checks: up/down gestures, list switching, closing-frame blackout, target colour and interrupted swipe cleanup");code=-1;
        }catch(Throwable error){
            String state="";
            try{
                tv.danmaku.ijk.media.player.IMediaPlayer player=(tv.danmaku.ijk.media.player.IMediaPlayer)get("player");
                state=" channel="+get("currentChannelIndex")+" prepared="+get("prepared")+" rendered="+get("videoRenderingStarted")
                        +" covered="+((DirectVideoView)get("videoView")).isFrameCovered()+" playing="+(player!=null&&player.isPlaying())
                        +" position="+(player==null?-1:player.getCurrentPosition());
            }catch(Throwable ignored){}
            result.putString("stream",android.util.Log.getStackTraceString(error)+state);
        }
        finally{
            try{if(activity!=null)main(()->activity.finish());}catch(Throwable ignored){}
            waitForIdleSync();
            if(saved!=null)ChannelCatalog.GROUPS=saved;
            SharedPreferences.Editor restore=preferences.edit();
            for(String key:keys){
                Object value=priorFixture?null:previous.get(key);
                if(value instanceof String)restore.putString(key,(String)value);
                else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else restore.remove(key);
            }
            restore.commit();
        }
        finish(code,result);
    }
}
