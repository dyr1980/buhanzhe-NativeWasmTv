package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Compare the actual attached clock / speed labels after host configuration. */
public final class HudTypographyInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;private boolean baseline;
    private final JSONArray results=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object call(String name,Class<?>[] types,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(c.get(),s);}
    private void setting(JSONObject b)throws Exception{call("handleWebSettings",new Class[]{JSONObject.class},b);waitForIdleSync();SystemClock.sleep(100);}
    private void labels(boolean equal)throws Exception{main(()->{View root=(View)get("root");root.removeCallbacks((Runnable)get("updateClock"));set("showNetworkSpeed",false);TextView clock=(TextView)get("videoClock"),speed=(TextView)get("networkSpeedOverlay");clock.setText("08:59:57");speed.setText(equal?"08:59:57":"390 KB/s");clock.setVisibility(View.VISIBLE);speed.setVisibility(View.VISIBLE);clock.bringToFront();speed.bringToFront();});waitForIdleSync();}
    private static Rect glyph(Bitmap bitmap,TextView label){int[] pos=new int[2];label.getLocationOnScreen(pos);Rect r=new Rect(bitmap.getWidth(),bitmap.getHeight(),-1,-1);
        for(int y=Math.max(0,pos[1]);y<Math.min(bitmap.getHeight(),pos[1]+label.getHeight());y++)for(int x=Math.max(0,pos[0]);x<Math.min(bitmap.getWidth(),pos[0]+label.getWidth());x++){
            int c=bitmap.getPixel(x,y);if(Color.red(c)>210&&Color.green(c)>210&&Color.blue(c)>210){r.left=Math.min(r.left,x);r.top=Math.min(r.top,y);r.right=Math.max(r.right,x+1);r.bottom=Math.max(r.bottom,y+1);}
        }return r;
    }
    private void audit(String id,String preset,String phase)throws Exception{
        labels(false);SystemClock.sleep(70);Bitmap screen=getUiAutomation().takeScreenshot();check(screen!=null,"Screenshot missing");
        if(preset.equals("auto"))try(FileOutputStream f=new FileOutputStream(new File(out,id+"-"+phase+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,f);}screen.recycle();
        labels(true);SystemClock.sleep(70);screen=getUiAutomation().takeScreenshot();
        try{
            TextView clock=(TextView)get("videoClock"),speed=(TextView)get("networkSpeedOverlay"),position=(TextView)get("videoPosition");
            Rect c=glyph(screen,clock),s=glyph(screen,speed);check(c.width()>30&&s.width()>30,"HUD glyphs missing");
            boolean same=clock.getTextSize()==speed.getTextSize()&&clock.getTypeface()==speed.getTypeface()&&clock.getTextScaleX()==speed.getTextScaleX()
                    &&Math.abs(c.height()-s.height())<=1&&Math.abs(c.width()-s.width())<=1;
            JSONObject row=new JSONObject().put("theme",id).put("preset",preset).put("phase",phase).put("sameFont",same).put("clockPx",clock.getTextSize()).put("speedPx",speed.getTextSize()).put("clockGlyph",c.width()+"x"+c.height()).put("speedGlyph",s.width()+"x"+s.height());results.put(row);
            if(!baseline)check(same,"HUD font mismatch: "+row);check(position.getTextSize()==clock.getTextSize(),"Progress font differs");
        }finally{screen.recycle();}
    }
    public void onStart(){Bundle result=new Bundle();int code=1;ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String savedTheme="builtin",savedScale="auto";
        try{
            baseline="true".equals(args.getString("baseline"));Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),baseline?"hud-font-before":"hud-font-after");out.mkdirs();savedTheme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(800);savedScale=(String)get("uiScaleMode");
            String base=args.getString("base"),m3u="#EXTM3U\n#EXTINF:-1 group-title=\"字体测试\",字体测试\n"+base+"/vod.mp4\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No video");
            setting(new JSONObject().put("showDateTime",true).put("showNetworkSpeed",true).put("dateTimeFormat","time_only").put("clockLocation","right").put("showPlaybackPosition",false));
            IMediaPlayer player=(IMediaPlayer)get("player");Surface surface=((DirectVideoView)get("videoView")).getVideoSurface();long start=player.getCurrentPosition();
            final View[] backdrop={null};main(()->{FrameLayout root=(FrameLayout)get("root");backdrop[0]=new View(activity);backdrop[0].setBackgroundColor(Color.BLACK);root.addView(backdrop[0],new FrameLayout.LayoutParams(-1,-1));});
            for(String preset:new String[]{"auto","small","large"}){
                setting(new JSONObject().put("uiScaleMode",preset));
                for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                    TvUiPlugin.select(c,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme not applied");waitForIdleSync();SystemClock.sleep(70);audit(id,preset,"switch");
                    main(()->{View root=(View)get("root");call("refreshUiScaleForViewport",new Class[]{int.class,int.class,boolean.class},root.getWidth(),root.getHeight(),true);});waitForIdleSync();audit(id,preset,"resize");
                    check(get("player")==player,"HUD restarted player");check(((DirectVideoView)get("videoView")).getVideoSurface()==surface&&surface.isValid(),"HUD recreated Surface");
                }
            }
            check(player.getCurrentPosition()>start+1000,"Playback stopped");
            main(()->((FrameLayout)get("root")).removeView(backdrop[0]));
            result.putString("stream",(baseline?"BASELINE":"PASS")+": 6 themes x 3 presets x theme-switch/resize; actual clock/speed glyph dimensions; same player and Surface\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(results.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(activity!=null){setting(new JSONObject().put("uiScaleMode",savedScale));main(()->activity.finish());}TvUiPlugin.select(getTargetContext(),savedTheme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
