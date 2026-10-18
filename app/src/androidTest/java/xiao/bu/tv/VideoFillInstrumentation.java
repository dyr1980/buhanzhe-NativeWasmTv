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
import tv.danmaku.ijk.media.player.*;

/** Verify decoded Surface pixels, including low-RAM software output, not only View bounds. */
public final class VideoFillInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;
    private final JSONArray rows=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] t,Object...v)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(activity,v);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(c.get(),s);}
    private void setting(JSONObject b)throws Exception{call("handleWebSettings",new Class[]{JSONObject.class},b);waitForIdleSync();SystemClock.sleep(200);}
    private void mode(String mode)throws Exception{setting(new JSONObject().put("videoScaleMode",mode));check(mode.equals(get("videoScaleMode")),"Mode not applied");check(mode.equals(activity.getSharedPreferences(MainActivity.PREFERENCES,0).getString("video_scale_mode","")),"Mode not persisted");}
    private void ready()throws Exception{until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No decoded frame");SystemClock.sleep(650);waitForIdleSync();}
    private void channel(int i)throws Exception{main(()->{set("currentGroupIndex",0);call("startChannel",new Class[]{int.class},i);});ready();}
    private TvUiMenu menu()throws Exception{return (TvUiMenu)get("tvUiMenu");}
    private ListView list(boolean cats)throws Exception{Object c=((PopupWindow)field(menu(),"popup")).getContentView();return (ListView)field(c,cats?"categories":"options");}
    private void clean()throws Exception{main(()->{if(menu().visible())menu().dismiss();ViewGroup root=(ViewGroup)get("root");for(int i=0;i<root.getChildCount();i++)if(!(root.getChildAt(i) instanceof DirectVideoView))root.getChildAt(i).setVisibility(View.GONE);});waitForIdleSync();SystemClock.sleep(150);}
    private static int pattern(double x,double y,double aspect){
        if(x<0||x>1||y<0||y>1)return Color.BLACK;
        if(y<.1)return Color.RED;if(y>.9)return Color.BLUE;
        if(x<.1)return Color.YELLOW;if(x>.9)return Color.CYAN;
        if(Math.abs(x-.5)<.05&&Math.abs(y-.5)<.05*aspect)return Color.WHITE;
        return Color.GREEN;
    }
    private static boolean boundary(double x,double y,double aspect){
        for(double b:new double[]{0,.1,.9,1,.45,.55})if(Math.abs(x-b)<.018)return true;
        for(double b:new double[]{0,.1,.9,1,.5-.05*aspect,.5+.05*aspect})if(Math.abs(y-b)<.018)return true;
        return false;
    }
    private JSONArray nativeBuffers(String name,int width,int height)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("dumpsys SurfaceFlinger"))){byte[] b=new byte[8192];int n;while((n=input.read(b))>0)bytes.write(b,0,n);}
        String dump=bytes.toString("UTF-8");try(FileOutputStream f=new FileOutputStream(new File(out,name+"-buffers.txt"))){f.write(bytes.toByteArray());}
        JSONArray sizes=new JSONArray();
        for(String layer:dump.split("\\+ Layer ")){
            int end=layer.indexOf('\n');if(end<0||!layer.substring(0,end).contains("SurfaceView - "+getTargetContext().getPackageName()+"/"))continue;
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("activeBuffer=\\[\\s*(\\d+)x\\s*(\\d+):").matcher(layer);
            if(match.find()){int w=Integer.parseInt(match.group(1)),h=Integer.parseInt(match.group(2));if(w>0&&h>0){sizes.put(w+"x"+h);check((long)w*h<=(long)width*height&&Math.max(w,h)<=Math.max(width,height),"Native software buffer exceeds viewport: "+w+"x"+h);}}
        }
        check(sizes.length()>0,"Native output layer missing");return sizes;
    }
    private void pixels(String name,String mode,double aspect,boolean bounded)throws Exception{
        clean();View root=(View)get("root");DirectVideoView video=(DirectVideoView)get("videoView");
        int w=root.getWidth(),h=root.getHeight();double dw=w,dh=h;
        if(!mode.equals("stretch")){
            if((w/(double)h>aspect)==mode.equals("fill"))dh=w/aspect;else dw=h*aspect;
        }
        double left=(w-dw)/2,top=(h-dh)/2;
        SurfaceView child=video.getSurfaceView();
        check(Math.abs(child.getWidth()-dw)<2&&Math.abs(child.getHeight()-dh)<2,"Wrong size "+name+": "+child.getWidth()+"x"+child.getHeight()+" expected "+dw+"x"+dh);
        check(Math.abs(video.getLeft()+child.getLeft()-left)<2&&Math.abs(video.getTop()+child.getTop()-top)<2,"Not centered "+name);
        if(mode.equals("fill"))check(video.getWidth()==w&&video.getHeight()==h,"Crop changed swipe viewport");
        Rect buffer=video.getSurfaceView().getHolder().getSurfaceFrame();
        int[] location=new int[2];root.getLocationOnScreen(location);Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"No screenshot");
        int tested=0,failed=0;
        try{
            for(int iy=0;iy<11;iy++)for(int ix=0;ix<15;ix++){
                int x=(int)((ix+.5)*w/15),y=(int)((iy+.5)*h/11);
                double sx=(x-left)/dw,sy=(y-top)/dh;if(boundary(sx,sy,aspect))continue;
                int expected=pattern(sx,sy,aspect),actual=bitmap.getPixel(x+location[0],y+location[1]);tested++;
                int delta=Math.max(Math.abs(Color.red(actual)-Color.red(expected)),Math.max(Math.abs(Color.green(actual)-Color.green(expected)),Math.abs(Color.blue(actual)-Color.blue(expected))));
                if(delta>70)failed++;
            }
            try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,f);}
        }finally{bitmap.recycle();}
        // SurfaceHolder.getSurfaceFrame() describes the window frame on API 25,
        // not the producer buffer. Read SurfaceFlinger to verify actual allocation.
        JSONObject row=new JSONObject().put("name",name).put("mode",mode).put("view",video.getWidth()+"x"+video.getHeight()).put("surface",child.getWidth()+"x"+child.getHeight()).put("surfaceFrame",buffer.width()+"x"+buffer.height()).put("buffer",field(child,"mWindowSpaceWidth")+"x"+field(child,"mWindowSpaceHeight")).put("pixelSamples",tested).put("failedPixels",failed).put("bounded",bounded);rows.put(row);
        if(bounded)row.put("nativeBuffers",nativeBuffers(name,w,h));
        check(tested>70&&failed==0,"Decoded crop pixels differ "+row);
    }
    public void onStart(){Bundle result=new Bundle();int code=1;ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String theme="builtin",savedMode="fit",savedDecode="hardware";
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"video-fill");out.mkdirs();theme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(800);savedMode=(String)get("videoScaleMode");savedDecode=(String)get("decodeMode");
            setting(new JSONObject().put("showDebugInfo",false).put("showDateTime",false).put("showNetworkSpeed",false).put("showLoadTiming",false).put("showPlaybackPosition",false).put("decodeMode","hardware"));
            String base=args.getString("base"),m3u="#EXTM3U\n";for(String name:new String[]{"fourthree","wide","portrait","sar"})m3u+="#EXTINF:-1 group-title=\"填充测试\","+name+"\n"+base+"/"+name+".mp4\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("autoSwitchSource",false);});channel(0);
            IMediaPlayer player=(IMediaPlayer)get("player");Surface surface=((DirectVideoView)get("videoView")).getVideoSurface();long position=player.getCurrentPosition();
            for(String mode:new String[]{"fit","fill","stretch","fit"}){mode(mode);pixels("fourthree-"+mode,mode,4d/3,false);check(get("player")==player,"Ratio restarted decoder");check(((DirectVideoView)get("videoView")).getVideoSurface()==surface&&surface.isValid(),"Ratio recreated Surface");}
            check(player.getCurrentPosition()>position+500,"Ratio switch stopped playback");
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                mode("fit");TvUiPlugin.select(c,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme not applied");waitForIdleSync();
                if(!id.equals("builtin")){
                    main(()->{call("openTvUiMenu",new Class[]{});ListView cats=list(true);cats.setSelection(1);cats.performItemClick(null,1,1);});waitForIdleSync();SystemClock.sleep(100);
                    main(()->{check(list(false).getCount()==5,"Missing fill choice");check(list(false).getAdapter().getView(4,null,list(false)).getContentDescription().toString().startsWith("填充，"),"Missing fill label");list(false).performItemClick(null,4,4);});
                    until(()->"fill".equals(get("videoScaleMode")),5000,"Menu did not apply fill");waitForIdleSync();SystemClock.sleep(200);
                    Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(out,id+"-menu.png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}
                }else mode("fill");
                pixels(id+"-fill","fill",4d/3,false);check(get("player")==player,"Theme restarted decoder");check(((DirectVideoView)get("videoView")).getVideoSurface()==surface,"Theme replaced Surface");
            }
            mode("fill");DirectVideoView first=(DirectVideoView)get("videoView");
            for(int i=1;i<=2;i++){
                main(()->call("animateRelativeChannelSwitch",new Class[]{int.class,float.class},1,-1f));ready();until(()->!(Boolean)get("channelSwitchAnimating"),5000,"Swipe did not finish");
                check((Integer)get("currentChannelIndex")==i,"Swipe selected wrong channel");check(((DirectVideoView)get("videoView")!=first)==(i==1),"Phone did not alternate Surfaces");
                pixels("swipe-"+i+"-fill","fill",i==1?7d/3:9d/16,false);
            }
            for(int i=1;i<4;i++){
                channel(i);double aspect=i==1?7d/3:i==2?9d/16:4d/3;
                if(i==3){IMediaPlayer p=(IMediaPlayer)get("player");check(p.getVideoSarNum()*15==p.getVideoSarDen()*16,"SAR metadata missing");}
                for(String mode:new String[]{"fill","fit"}){mode(mode);pixels((i==1?"wide":i==2?"portrait":"sar")+"-"+mode,mode,aspect,false);}
            }
            // Exercise the actual software output policy used on Android 4.4 / low-RAM TVs.
            main(()->set("lowResourceDevice",true));setting(new JSONObject().put("decodeMode","software"));ready();channel(2);
            check(((IjkMediaPlayer)get("player")).getVideoDecoder()==IjkMediaPlayer.FFP_PROPV_DECODER_AVCODEC,"Not software decoded");
            for(String mode:new String[]{"fill","fit","stretch","fill"}){mode(mode);pixels("lowram-portrait-"+mode,mode,9d/16,true);}
            mode("fill");MainActivity old=activity;main(()->activity.recreate());until(()->activity!=old,15000,"No recreated Activity");waitForIdleSync();check("fill".equals(get("videoScaleMode")),"Fill not restored on recreation");rows.put(new JSONObject().put("name","preference-recreation").put("restored",true));
            result.putString("stream","PASS: decoded center crop / fit / stretch, all six themes, menu actions, wide / portrait / SAR, bounded low-RAM software output, same player/Surface, preference recreation\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).edit().putString("video_scale_mode",savedMode).putString("decode_mode",savedDecode).apply();if(activity!=null)main(()->activity.finish());TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
