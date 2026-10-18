package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Real playback + persisted web settings, all themes and a narrow HUD layout. */
public final class PlaybackHudInstrumentation extends Instrumentation {
    private volatile MainActivity activity; private Bundle args; private File out;
    interface Work {void run()throws Exception;} interface Condition {boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private Object get(String name)throws Exception{return get(activity,name);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String message)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);check(c.get(),message);}
    private TextView text(String field)throws Exception{return (TextView)get(field);}
    private void setting(JSONObject json)throws Exception{
        LocalControlServer server=(LocalControlServer)get("controlServer");
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/api/settings").openConnection();
        c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        try{try(OutputStream out=c.getOutputStream()){out.write(json.toString().getBytes("UTF-8"));}check(c.getResponseCode()==200,"Settings HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream()){while(in.read()!=-1){}}}finally{c.disconnect();}
        main(()->{});SystemClock.sleep(100);
    }
    private void shot(String name)throws Exception{SystemClock.sleep(100);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private void geometry(String id,boolean clock)throws Exception{
        main(()->{
            TextView p=text("videoPosition"),c=text("videoClock"),traffic=text("networkSpeedOverlay"),debug=text("debugInfoOverlay");View root=(View)get("root");
            sameBackground(c.getBackground(),p.getBackground(),id);
            check(p.getPaddingLeft()==c.getPaddingLeft()&&p.getPaddingRight()==c.getPaddingRight()
                    &&p.getPaddingTop()==c.getPaddingTop()&&p.getPaddingBottom()==c.getPaddingBottom(),"HUD padding differs "+id);
            check(p.isShown(),"Position absent "+id);
            check(p.getTextSize()==c.getTextSize(),"Clock/position font differs "+id);
            if(clock){check(p.getRight()<c.getLeft(),"Clock overlap "+id+" "+p.getRight()+"/"+c.getLeft());check(Math.abs(p.getTop()+p.getBaseline()-c.getTop()-c.getBaseline())<=1,"Clock baseline "+id);}
            else check(Math.abs(root.getWidth()-p.getRight()-((FrameLayout.LayoutParams)p.getLayoutParams()).rightMargin)<=1,"Position not right aligned "+id);
            check(debug.getRight()<traffic.getLeft(),"Traffic overlaps debug "+id+" "+debug.getRight()+"/"+traffic.getLeft());
            check(root.getHeight()-traffic.getBottom()<root.getHeight()*.05f,"Traffic floated above bottom "+id);
            check(Math.abs(debug.getTextSize()-(Float)get("debugInfoTextSizePx"))<.1f,"Debug shrunk "+id);
            String data=debug.getText().toString();check(data.contains("CPU")&&data.contains("fps")&&!data.contains("secret"),"Critical data/URL compacting "+data);
        });
    }
    private static void sameBackground(android.graphics.drawable.Drawable clock,android.graphics.drawable.Drawable progress,String id){
        if(clock==null){check(progress==null,"Unexpected progress background "+id);return;}
        check(progress!=null&&progress!=clock,"HUD background not independently cloned "+id);
        android.graphics.Rect a=new android.graphics.Rect(clock.getBounds()),b=new android.graphics.Rect(progress.getBounds());
        // Taller bounds expose incorrect radius scaling even when small pills clamp it.
        android.graphics.Bitmap left=android.graphics.Bitmap.createBitmap(320,160,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Bitmap right=android.graphics.Bitmap.createBitmap(320,160,android.graphics.Bitmap.Config.ARGB_8888);
        try{
            clock.setBounds(0,0,320,160);progress.setBounds(0,0,320,160);
            clock.draw(new android.graphics.Canvas(left));progress.draw(new android.graphics.Canvas(right));
            check(left.sameAs(right),"Clock/progress radius or background differs "+id);
        }finally{clock.setBounds(a);progress.setBounds(b);left.recycle();right.recycle();}
    }
    private void narrow()throws Exception{
        final FrameLayout[] host={null};final TextView[] labels=new TextView[2];
        main(()->{
            host[0]=new FrameLayout(activity);host[0].setBackgroundColor(0xff000000);
            TextView c=new TextView(activity),p=new TextView(activity);labels[0]=c;labels[1]=p;
            c.setText("07:16:49");c.setTextColor(-1);c.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22);c.setIncludeFontPadding(false);c.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,32,Gravity.TOP|Gravity.RIGHT);cp.rightMargin=10;cp.topMargin=8;host[0].addView(c,cp);host[0].addView(p,new FrameLayout.LayoutParams(-2,-2));
            PlaybackPositionHud.style(p,c);PlaybackPositionHud.update(p,c,"00:53 | 1:59:54",true,true,640,10,8,10);
            ((FrameLayout)get("root")).addView(host[0],new FrameLayout.LayoutParams(640,360,Gravity.TOP|Gravity.LEFT));
        });
        SystemClock.sleep(300);waitForIdleSync();
        final android.graphics.Rect box=new android.graphics.Rect(),label=new android.graphics.Rect();
        main(()->{
            TextView c=labels[0],p=labels[1];
            check(p.getWidth()>100&&p.getLeft()>=0&&p.getRight()<c.getLeft(),"Narrow overlap");
            check(p.getTop()+p.getBaseline()==c.getTop()+c.getBaseline(),"Narrow baseline");
            host[0].getGlobalVisibleRect(box);p.getGlobalVisibleRect(label);
        });
        android.graphics.Bitmap full=getUiAutomation().takeScreenshot();check(full!=null,"Narrow screenshot absent");
        int pixels=0;for(int y=label.top;y<label.bottom;y++)for(int x=label.left;x<label.right;x++)if((full.getPixel(x,y)&0xffffff)!=0)pixels++;
        check(pixels>100,"Narrow text absent on device");
        android.graphics.Bitmap cropped=android.graphics.Bitmap.createBitmap(full,box.left,box.top,box.width(),box.height());
        try(FileOutputStream stream=new FileOutputStream(new File(out,"narrow.png"))){cropped.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}cropped.recycle();full.recycle();
        main(()->((FrameLayout)get("root")).removeView(host[0]));
    }
    public void onStart(){Bundle result=new Bundle();int code=1;JSONArray rows=new JSONArray();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String theme="builtin";
        try{
            Context context=getTargetContext();out=new File(context.getExternalFilesDir(null),"playback-hud");out.mkdirs();theme=new JSONObject(TvUiPlugin.status(context)).getString("selected");
            context.getSharedPreferences(MainActivity.PREFERENCES,0).edit().remove("show_playback_position").apply();
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"Activity unavailable");SystemClock.sleep(2000);
            check(!(Boolean)get("showPlaybackPosition"),"Position not default off");String base=args.getString("base");
            String list="#EXTM3U\n#EXTINF:-1 group-title=\"HUD\",HUD Video\n"+base+"/vod.mp4?secret=long-unimportant-query\n#EXTINF:-1 group-title=\"HUD\",HUD Live\n"+base+"/live.m3u8\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,list.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);invoke("startChannel",new Class[]{int.class},0);});
            until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No VOD frame");
            IMediaPlayer player=(IMediaPlayer)get("player");
            setting(new JSONObject().put("showPlaybackPosition",true).put("showDateTime",true).put("clockLocation","right").put("dateTimeFormat","time_only").put("showDebugInfo",true).put("showColorDebugInfo",true).put("showLoadTiming",true).put("showNetworkSpeed",true));
            check(context.getSharedPreferences(MainActivity.PREFERENCES,0).getBoolean("show_playback_position",false),"Progress choice not persisted");
            until(()->text("videoPosition").isShown(),3000,"Progress not shown");String before=text("videoPosition").getText().toString();SystemClock.sleep(2200);check(!before.equals(text("videoPosition").getText().toString()),"Progress did not tick");
            main(()->{player.pause();if(get("videoControlsOverlay")!=null)((VideoControlsOverlay)get("videoControlsOverlay")).hide();});
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                TvUiPlugin.select(context,id);main(()->{invoke("rebuildTvUiChrome",new Class[]{});invoke("applyClockLocation",new Class[]{});invoke("refreshVideoInfo",new Class[]{});});SystemClock.sleep(180);geometry(id,true);shot(id+"-clock");
                setting(new JSONObject().put("showDateTime",false));geometry(id,false);shot(id+"-progress");
                setting(new JSONObject().put("showDateTime",true));
                check(get("player")==player,"HUD restarted player");rows.put(new JSONObject().put("theme",id).put("clockLeftOfTime",true).put("rightAlignedWithoutTime",true).put("trafficCorner",true).put("debugFontPreserved",true));
            }
            setting(new JSONObject().put("clockLocation","left"));main(()->{});SystemClock.sleep(100);check(text("videoPosition").getLeft()>text("videoClock").getRight(),"Left clock overlaps right position");
            setting(new JSONObject().put("showNetworkSpeed",false));main(()->{});SystemClock.sleep(100);
            check(((FrameLayout.LayoutParams)text("debugInfoOverlay").getLayoutParams()).rightMargin<50,"Traffic reserve not released");
            setting(new JSONObject().put("showPlaybackPosition",false));check(!text("videoPosition").isShown(),"Progress did not hide");
            setting(new JSONObject().put("showPlaybackPosition",true));
            main(()->{set("currentGroupIndex",0);invoke("startChannel",new Class[]{int.class},1);});until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),15000,"No live frame");SystemClock.sleep(1200);check(!text("videoPosition").isShown(),"Live shows finite progress");
            narrow();
            result.putString("stream","PASS: persisted HTTP switch, ticking position, six themes clock/standalone alignment, fixed traffic corner, readable wrapping, live hidden, 640px layout\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(activity!=null)main(()->activity.finish());TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
