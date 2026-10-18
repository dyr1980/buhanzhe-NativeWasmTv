package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.Map;
import org.json.*;

/** Cold plugin installation through MainActivity, real HTTP and first rendered frame. */
public final class PluginFirstPlaybackInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private String base,mode;
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String name)throws Exception{Class<?> type=o instanceof Class?(Class<?>)o:o.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
    private static void set(Object o,String name,Object value)throws Exception{Class<?> type=o instanceof Class?(Class<?>)o:o.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(o instanceof Class?null:o,value);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void call(String n,Class<?>[] types,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(activity,values);}
    private void main(Work w)throws Exception{Throwable[] failure={null};runOnMainSync(()->{try{w.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void until(Condition c,long ms,String message)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);check(c.get(),message);}
    private JSONObject stats()throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(base+"/stats").openConnection();try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}}
    public void onStart(){Bundle result=new Bundle();int code=1;JSONObject row=new JSONObject();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;
        try{
            Context c=getTargetContext();base=args.getString("base");mode=args.getString("mode");String prefix="plugin-first-"+mode+"-"+SystemClock.elapsedRealtime();
            Context isolated=new ContextWrapper(c){public Context getApplicationContext(){return this;}public File getFilesDir(){return new File(c.getCacheDir(),prefix);}public SharedPreferences getSharedPreferences(String n,int flags){return c.getSharedPreferences(prefix+n,flags);}};
            set(CjsPluginRuntime.class,"context",null);set(CjsPluginRuntime.class,"catalog",null);set(CjsPluginRuntime.class,"catalogReadAttempted",false);((Map)field(CjsPluginRuntime.class,"states")).clear();CjsPluginRuntime.initialize(isolated);CjsPluginRuntime.setManifestUrl(base+"/catalog.json");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No activity");SystemClock.sleep(1000);
            String m3u="#EXTM3U\n#EXTINF:-1 group-title=\"安装验证\",首次频道\nwebview://"+base+"/page\n#EXTINF:-1 group-title=\"安装验证\",普通频道\n"+base+"/vod.mp4?direct=1\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] fixture=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{call("releasePlayer",new Class[]{});set(activity,"videoRenderingStarted",false);set(activity,"prepared",false);ChannelCatalog.GROUPS=fixture;set(activity,"currentGroupIndex",0);set(activity,"currentSourceIndex",0);set(activity,"autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});
            HttpURLConnection begin=(HttpURLConnection)new URL(base+"/begin").openConnection();try{check(begin.getResponseCode()==200,"Fixture did not start");}finally{begin.disconnect();}
            if(mode.equals("cancel")||mode.equals("destroy")){
                until(()->get("pendingCjsInstallRetry")!=null,15000,"Retry was not scheduled");
                if(mode.equals("cancel")){main(()->call("startChannel",new Class[]{int.class},1));until(()->(Boolean)get("videoRenderingStarted"),15000,"Direct channel has no frame");}
                else main(()->activity.finish());
                JSONObject before=stats();SystemClock.sleep(3000);JSONObject after=stats();check(before.getInt("catalog") == after.getInt("catalog"),"Cancelled retry downloaded again");
                check(get("pendingCjsInstallRetry")==null,"Retry callback retained");if(mode.equals("cancel"))check((Integer)get("currentChannelIndex")==1,"Old installation switched channel back");row.put("cancelled",true);
            }else if(mode.equals("forbidden")||mode.equals("badsha")||mode.equals("exhausted")){
                until(()->!(Boolean)get("cjsPluginInstallInProgress")&&get("pendingCjsInstallRetry")==null&&(Integer)get("pendingCjsChannelIndex")<0,20000,"Install did not stop");
                check(!(Boolean)get("videoRenderingStarted"),"Invalid plugin played");JSONObject before=stats();SystemClock.sleep(1800);check(before.toString().equals(stats().toString()),"Failure kept retrying");
                int retries=(Integer)get("cjsPluginInstallRetryCount");check(retries==(mode.equals("exhausted")?2:0),"Unbounded/inappropriate retry count "+retries);row.put("retries",retries);
                check(!CjsPluginRuntime.isInstalled("fixture"),"Broken plugin activated");
            }else{
                until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),25000,"First channel never rendered after installation");
                check((Integer)get("currentChannelIndex")==0,"Playback required changing channel");check(CjsPluginRuntime.isInstalled("fixture"),"No active plugin");
                check(get("pendingCjsInstallRetry")==null&&!(Boolean)get("cjsPluginInstallInProgress"),"Install remained pending");
                tv.danmaku.ijk.media.player.IMediaPlayer player=(tv.danmaku.ijk.media.player.IMediaPlayer)get("player");long start=player.getCurrentPosition();SystemClock.sleep(1200);check(player.getCurrentPosition()>start+400,"First frame did not keep playing");
                row.put("firstFrameWithoutChannelSwitch",true).put("retries",get("cjsPluginInstallRetryCount"));
            }
            row.put("mode",mode).put("requests",stats()).put("passed",true);File folder=new File(c.getExternalFilesDir(null),"plugin-first-play");folder.mkdirs();try(FileOutputStream f=new FileOutputStream(new File(folder,mode+".json"))){f.write(row.toString(2).getBytes("UTF-8"));}
            result.putString("stream","PASS "+row+"\n");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{try{if(activity!=null)main(()->activity.finish());}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;}
        finish(code,result);
    }
}
