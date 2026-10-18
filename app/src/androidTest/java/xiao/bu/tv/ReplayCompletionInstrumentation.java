package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Real EOS, controller replay, origin byte accounting and channel cache retirement. */
public final class ReplayCompletionInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private Bundle args; private File out;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    interface Work { void run() throws Exception; }
    interface Condition { boolean get() throws Exception; }
    private void main(Work work)throws Exception {Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void until(Condition condition,long ms,String message)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!condition.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);check(condition.get(),message);}
    private static byte[] read(String url,String range)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(10000);if(range!=null)c.setRequestProperty("Range",range);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}finally{c.disconnect();}
    }
    private long originBytes(String base)throws Exception{return new JSONObject(new String(read(base+"/stats",null),"UTF-8")).getLong("bytes");}
    private void shot(String name)throws Exception{android.graphics.Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private void completedBackgroundRoundTrip(String base,String name)throws Exception{
        Object proxy=get(activity,"proxy");long bytes=originBytes(base);
        long now=SystemClock.uptimeMillis();
        check(getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME,0),true),"HOME down injection failed");
        check(getUiAutomation().injectInputEvent(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_HOME,0),true),"HOME up injection failed");
        until(()->(Boolean)get(activity,"backgroundPlaybackSuspended")&&get(activity,"player")==null,5000,"Completed player kept running in background");
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -W -n "+getTargetContext().getPackageName()+"/xiao.bu.tv.MainActivity");InputStream input=new FileInputStream(fd.getFileDescriptor())){byte[] b=new byte[1024];while(input.read(b)>=0){}}
        until(()->activity.hasWindowFocus()&&(Boolean)get(activity,"prepared"),15000,"Completed video did not prepare after returning");
        until(()->(Boolean)get(activity,"playbackCompleted")&&get(activity,"videoControlsOverlay")!=null&&((VideoControlsOverlay)get(activity,"videoControlsOverlay")).visible(),5000,"Returning from background lost completed state / pinned replay controller");
        check(!((IMediaPlayer)get(activity,"player")).isPlaying(),"Completed video restarted automatically");
        check(get(activity,"proxy")==proxy,"Returning from background discarded replay cache");
        check(originBytes(base)==bytes,"Completed background return downloaded the same video again");
        shot("completed-background-"+name);
    }
    public void onStart(){Bundle result=new Bundle();int code=1;JSONArray rows=new JSONArray();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String style="builtin";
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"replay-completion");out.mkdirs();String base=args.getString("base");
            style=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            until(()->activity!=null,15000,"Activity not resumed");SystemClock.sleep(2000);
            String playlist="#EXTM3U\n#EXTINF:-1 group-title=\"Replay\",MP4\n"+base+"/clip.mp4\n#EXTINF:-1 group-title=\"Replay\",HLS\n"+base+"/clip.m3u8\n#EXTINF:-1 group-title=\"Replay\",AES HLS\n"+base+"/aes.m3u8\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,playlist.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);set("showDebugInfo",true);});
            for(int index=0;index<3;index++){
                final int selected=index;
                long before=originBytes(base);
                main(()->{set("currentGroupIndex",0);invoke("startChannel",new Class[]{int.class},selected);});
                until(()->(Boolean)get(activity,"prepared")&&(Boolean)get(activity,"videoRenderingStarted"),15000,"No first video frame "+index);
                IMediaPlayer player=(IMediaPlayer)get(activity,"player");
                until(()->(Boolean)get(activity,"playbackCompleted"),15000,"No normal completion "+index+" pos="+player.getCurrentPosition()+" duration="+player.getDuration());
                until(()->get(activity,"videoControlsOverlay")!=null&&((VideoControlsOverlay)get(activity,"videoControlsOverlay")).visible(),3000,"EOS controls missing");
                main(()->{});
                VideoControlsOverlay controls=(VideoControlsOverlay)get(activity,"videoControlsOverlay");
                SystemClock.sleep(5500);check(controls.visible(),"EOS controls auto-hid");
                check(((TextView)get(controls,"feedback")).getText().toString().contains("播放完成"),"EOS message missing");
                shot("complete-"+index);
                HlsProxyServer proxy=(HlsProxyServer)get(activity,"proxy");
                Object cache=get(proxy,"replayCache");File directory=(File)get(cache,"directory");check(directory.isDirectory(),"No disk cache");
                long first=originBytes(base);check(first>before,"No initial upstream download");
                // Exercise a real cache read independently of the decoder's own packet buffer.
                if(index==0){check(read(proxy.mediaUrl(base+"/clip.mp4"),"bytes=0-1023").length==1024,"Range replay");}
                else {
                    String leaf=index==1?"clip.m3u8":"aes.m3u8";
                    String manifest=new String(read(proxy.playerHlsUrl(base+"/"+leaf),null),"UTF-8");
                    for(String line:manifest.split("\\r?\\n"))if(!line.isEmpty()&&!line.startsWith("#"))check(read(line,null).length>0,"Segment replay");
                }
                check(originBytes(base)==first,"Cache verification downloaded again "+index);
                if(index==1)sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                else main(()->((View)get(controls,"toggle")).performClick());
                until(()->player.isPlaying()&&player.getCurrentPosition()>300&&player.getCurrentPosition()<3500,10000,"Replay did not advance "+index);
                check(get(activity,"player")==player,"Replay replaced decoder/session");
                until(()->(Boolean)get(activity,"playbackCompleted"),15000,"Replay did not complete "+index);
                long replay=originBytes(base)-first;check(replay==0,"Replay consumed upstream bytes "+replay);
                if(index==0)completedBackgroundRoundTrip(base,"ijk");
                rows.put(new JSONObject().put("source",index==0?"MP4":index==1?"HLS":"AES HLS").put("firstBytes",first-before).put("replayBytes",replay).put("controllerPinned",true).put("samePlayer",true));
                if(index<2){
                    main(()->{set("currentGroupIndex",0);invoke("startChannel",new Class[]{int.class},selected+1);});
                    until(()->!directory.exists(),5000,"Channel switch retained old cache");
                    check(!(Boolean)get(activity,"playbackCompleted"),"Next channel retained completed state");
                    rows.getJSONObject(rows.length()-1).put("switchClearsCache",true);
                }
                Bundle progress=new Bundle();progress.putString("stream","PASS "+rows.getJSONObject(rows.length()-1)+"\n");sendStatus(0,progress);
            }
            main(()->{set("currentGroupIndex",0);invoke("startChannel",new Class[]{int.class},0);});
            until(()->(Boolean)get(activity,"prepared")&&(Boolean)get(activity,"videoRenderingStarted"),15000,"System fixture not ready");
            main(()->invoke("startAndroidMediaPlayer",new Class[]{Channel.class,String.class,boolean.class,boolean.class},groups[0].channels[0],base+"/clip.mp4",false,true));
            until(()->get(activity,"player") instanceof tv.danmaku.ijk.media.player.AndroidMediaPlayer&&(Boolean)get(activity,"prepared"),15000,"System player not ready");
            until(()->(Boolean)get(activity,"playbackCompleted"),15000,"System EOS missing");main(()->{});
            long systemBefore=originBytes(base);IMediaPlayer system=(IMediaPlayer)get(activity,"player");
            main(()->invoke("togglePlayback",new Class[]{}));
            until(()->system.isPlaying()&&system.getCurrentPosition()>300&&system.getCurrentPosition()<3500,10000,"System replay stalled");
            until(()->(Boolean)get(activity,"playbackCompleted"),15000,"System replay EOS missing");
            check(originBytes(base)==systemBefore,"System replay downloaded again");
            rows.put(new JSONObject().put("source","System MP4").put("replayBytes",0).put("samePlayer",get(activity,"player")==system));
            completedBackgroundRoundTrip(base,"system");
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                TvUiPlugin.select(c,id); main(()->{invoke("rebuildTvUiChrome",new Class[]{});invoke("showVideoControls",new Class[]{});});shot("theme-"+id);
                check(((VideoControlsOverlay)get(activity,"videoControlsOverlay")).visible(),"Shared completion missing "+id);
            }
            result.putString("stream","PASS: MP4/HLS/AES EOS pinned, replay zero origin bytes, channel switch clears, six themes\n");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(activity!=null)main(()->activity.finish());TvUiPlugin.select(getTargetContext(),style);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
