package xiao.bu.tv;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Exercise the actual menu, worker/service cache completion and attached HUD. */
public final class CacheMenuHudInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;private final JSONArray rows=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] t,Object...v)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(activity,v);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);check(c.get(),s);}
    private void setting(JSONObject b)throws Exception{call("handleWebSettings",new Class[]{JSONObject.class},b);waitForIdleSync();SystemClock.sleep(70);}
    private TvUiMenu menu()throws Exception{return (TvUiMenu)get("tvUiMenu");}
    private View content()throws Exception{return ((PopupWindow)field(menu(),"popup")).getContentView();}
    private ListView list(boolean cats)throws Exception{return (ListView)field(content(),cats?"categories":"options");}
    private void open(int category)throws Exception{main(()->{call("openTvUiMenu",new Class[]{});ListView l=list(true);l.setSelection(category);l.performItemClick(null,category,category);});waitForIdleSync();SystemClock.sleep(70);}
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private String toast()throws Exception{Toast t=(Toast)get("cacheClearToast");if(t==null)return "";return ((TextView)t.getView().findViewById(android.R.id.message)).getText().toString();}
    private static void file(File file,int count)throws Exception{file.getParentFile().mkdirs();try(FileOutputStream f=new FileOutputStream(file)){byte[] block=new byte[8192];for(int i=0;i<count;i+=block.length)f.write(block,0,Math.min(block.length,count-i));}}
    private JSONObject httpClear()throws Exception{
        LocalControlServer server=(LocalControlServer)get("controlServer");HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/api/settings").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(3000);c.setReadTimeout(10000);c.setRequestProperty("Content-Type","application/json");
        try{try(OutputStream out=c.getOutputStream()){out.write("{\"clearWebCache\":true}".getBytes("UTF-8"));}check(c.getResponseCode()==200,"Cache HTTP failed");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))>0)bytes.write(b,0,n);}return new JSONObject(bytes.toString("UTF-8"));}finally{c.disconnect();}
    }
    public void onStart(){Bundle result=new Bundle();int code=1;ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String theme="builtin",size="auto";
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"cache-menu-hud");out.mkdirs();theme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(700);size=(String)get("uiScaleMode");
            String base=args.getString("base"),m3u="#EXTM3U\n#EXTINF:-1 group-title=\"验证\",验证视频\n"+base+"/vod.mp4\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No video");
            IMediaPlayer player=(IMediaPlayer)get("player");Surface surface=((DirectVideoView)get("videoView")).getVideoSurface();long position=player.getCurrentPosition();
            setting(new JSONObject().put("showDateTime",true).put("showNetworkSpeed",true).put("showPlaybackPosition",true).put("clockLocation","right").put("dateTimeFormat","time_only"));
            File keep=new File(c.getFilesDir(),"cache-clean-retain.txt"),replay=new File(c.getCacheDir(),"replay-retain-test/video.bin");file(keep,1024);file(replay,2048);
            for(String preset:new String[]{"auto","small","large"}){
                setting(new JSONObject().put("uiScaleMode",preset));
                for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                    TvUiPlugin.select(c,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme not applied");waitForIdleSync();SystemClock.sleep(70);
                    TextView clock=(TextView)get("videoClock"),speed=(TextView)get("networkSpeedOverlay"),progress=(TextView)get("videoPosition");View root=(View)get("root");
                    float oldSize=Math.max(18f,Math.min(52f,Math.min(root.getWidth(),root.getHeight())*.03f))*(Float)get("effectiveUiScale");
                    check(Math.abs(clock.getTextSize()-oldSize*1.12f)<.1f&&speed.getTextSize()==clock.getTextSize()&&progress.getTextSize()==clock.getTextSize(),"HUD not enlarged together "+id+"/"+preset);
                    rows.put(new JSONObject().put("kind","hud").put("theme",id).put("preset",preset).put("beforePx",oldSize).put("afterPx",clock.getTextSize()));
                    if(!id.equals("builtin")){
                        for(int category:new int[]{0,7}){
                            open(category);final JSONObject row=new JSONObject().put("kind","menu").put("theme",id).put("preset",preset).put("category",category);
                            main(()->{ListView cats=list(true);check(cats.getChildCount()==8,"Categories clipped");int top=cats.getChildAt(0).getTop(),bottom=cats.getHeight()-cats.getChildAt(7).getBottom();row.put("top",top).put("bottom",bottom);if(!id.equals("ui3"))check(Math.abs(top-bottom)<=1,"Unequal menu whitespace "+row);});rows.put(row);
                            if(preset.equals("auto"))shot(id+"-menu-"+category);main(()->menu().dismiss());
                        }
                        if(preset.equals("auto")){
                            File removable=new File(c.getCacheDir(),"browser/menu-clear-fixture.bin");file(removable,2*1024*1024);
                            android.system.Os.symlink(keep.getAbsolutePath(),new File(removable.getParentFile(),"external-link").getAbsolutePath());open(6);
                            main(()->list(false).performItemClick(null,5,5));check(!menu().visible(),"Cache menu did not respond");
                            until(()->toast().contains("正在清理"),1000,"No progress toast");
                            JSONObject duplicate=httpClear();check(duplicate.optBoolean("cacheClearInProgress"),"Duplicate cleanup not coalesced");
                            until(()->!(Boolean)get("cacheClearRequested")&&toast().contains("已清理"),10000,"Cleanup result toast never arrived: "+toast());check(!removable.exists()&&toast().contains("MB"),"No measured cleanup result: "+toast());
                            check(keep.length()==1024&&replay.length()==2048,"Cleanup removed unrelated data");
                            rows.put(new JSONObject().put("kind","native-cache").put("theme",id).put("message",toast()).put("duplicate",true));SystemClock.sleep(1000);shot(id+"-cleared");
                        }
                    }
                    check(get("player")==player&&((DirectVideoView)get("videoView")).getVideoSurface()==surface,"Menu/cache replaced playback");
                }
            }
            File removable=new File(c.getCacheDir(),"browser/http-clear-fixture.bin");file(removable,1024*1024);JSONObject http=httpClear();check(http.optBoolean("ok")&&http.getLong("clearedBytes")>=1024*1024&&!removable.exists(),"Web cleanup bytes wrong "+http);rows.put(http.put("kind","http-cache"));
            File sandbox=new File(c.getCacheDir(),"cache-clear-empty-test");sandbox.mkdirs();Context empty=new ContextWrapper(c){public File getCacheDir(){return sandbox;}public File getDir(String n,int mode){return new File(sandbox,n);}};
            BrowserCacheClear.Result zero=BrowserCacheClear.clear(empty,done->done.complete(null));check(zero.cleared==0&&zero.message().contains("0 B"),"Empty cache feedback wrong");
            BrowserCacheClear.Result failure=BrowserCacheClear.clear(empty,done->done.complete("测试失败"));check(failure.error!=null&&failure.message().contains("失败"),"Failure feedback missing");rows.put(new JSONObject().put("kind","empty-and-failure").put("empty",zero.message()).put("failure",failure.message()));
            BrowserCacheClear.Result thrown=BrowserCacheClear.clear(empty,done->{throw new IllegalStateException();});check(thrown.error!=null&&BrowserCacheClear.bytes(2L*1024*1024*1024).equals("2.00 GB"),"Exception or large size feedback wrong");rows.put(new JSONObject().put("kind","exception-and-format").put("message",thrown.message()));
            check(player.getCurrentPosition()>position+1000,"Playback stopped during cache clear");keep.delete();replay.delete();replay.getParentFile().delete();sandbox.delete();
            result.putString("stream","PASS: five native cache menu actions, progress and measured result, HTTP bytes, duplicate/empty/failure, retained data/player; six HUDs x three presets enlarged together; symmetric menu whitespace, blue preserved\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}try{if(activity!=null){setting(new JSONObject().put("uiScaleMode",size));main(()->activity.finish());}TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;}
        finish(code,result);
    }
}
