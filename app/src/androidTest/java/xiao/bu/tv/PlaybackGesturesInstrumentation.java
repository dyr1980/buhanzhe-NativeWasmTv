package xiao.bu.tv;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Inject real touchscreen sequences into the Activity, including hidden controls. */
public final class PlaybackGesturesInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;private final JSONArray rows=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private Object get(String n)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(activity);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] t,Object...v)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(activity,v);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(35);check(c.get(),s);}
    private IMediaPlayer player()throws Exception{return (IMediaPlayer)get("player");}
    private void ready()throws Exception{until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No decoded frame");waitForIdleSync();SystemClock.sleep(120);}
    private void channel(int i)throws Exception{main(()->{set("currentGroupIndex",0);call("startChannel",new Class[]{int.class},i);});ready();}
    private void hide()throws Exception{main(()->{call("closeChannelList",new Class[]{});((TvUiMenu)get("tvUiMenu")).dismiss();VideoControlsOverlay c=(VideoControlsOverlay)get("videoControlsOverlay");if(c!=null)c.hide();});waitForIdleSync();}
    private void touch(int action,long down,float x,float y)throws Exception{View root=(View)get("root");int[] at=new int[2];main(()->root.getLocationOnScreen(at));MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,at[0]+x*root.getWidth(),at[1]+y*root.getHeight(),0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();}
    private void drag(float x,float y,float dx,float dy,boolean cancel,boolean finish)throws Exception{long down=SystemClock.uptimeMillis();touch(0,down,x,y);for(int i=1;i<=6;i++){touch(2,down,x+dx*i/6,y+dy*i/6);SystemClock.sleep(18);}if(finish)touch(cancel?3:1,down,x+dx,y+dy);}
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private void row(String theme,String mode,String gesture)throws Exception{rows.put(new JSONObject().put("theme",theme).put("mode",mode).put("gesture",gesture).put("pass",true));}
    public void onStart(){Bundle result=new Bundle();int code=1;ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String theme="builtin";AudioManager audio=null;int oldVolume=0;float oldBrightness=-1;
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"playback-gestures");out.mkdirs();theme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(800);
            oldBrightness=activity.getWindow().getAttributes().screenBrightness;audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);int volume=Math.max(2,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2);
            int systemBrightness=android.provider.Settings.System.getInt(c.getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,-1);
            String base=args.getString("base"),m3u="#EXTM3U\n";
            for(int i=0;i<3;i++)m3u+="#EXTINF:-1 group-title=\"手势\",直播"+i+"\n"+base+"/live.m3u8?channel="+i+"\n";
            m3u+="#EXTINF:-1 group-title=\"手势\",点播\n"+base+"/vod.mp4\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");main(()->{ChannelCatalog.GROUPS=groups;set("autoSwitchSource",false);});
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}){
                TvUiPlugin.select(c,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme not applied");channel(0);hide();check((Long)call("seekableDuration",new Class[]{})==0,"Fixture not live");
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);main(()->PlaybackBrightness.apply(activity,35));IMediaPlayer p=player();drag(.2f,.65f,0,-.25f,false,true);
                check(PlaybackBrightness.percent(activity)>60,"Live left swipe did not brighten");check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==volume&&player()==p&&(Integer)get("currentChannelIndex")==0,"Live brightness changed volume/channel");row(id,"live","left brightness");if(id.equals("ui2"))shot("live-brightness");
                drag(.42f,.55f,-.34f,0,false,true);until(()->!(Boolean)get("channelSwitchAnimating"),4000,"Horizontal switch stuck");ready();check((Integer)get("currentChannelIndex")==1&&(Integer)get("currentSourceIndex")==0,"Left-half swipe did not change channel");row(id,"live","left-half horizontal next");
                drag(.58f,.55f,.34f,0,false,true);until(()->!(Boolean)get("channelSwitchAnimating"),4000,"Horizontal return stuck");ready();check((Integer)get("currentChannelIndex")==0,"Right-half swipe did not return");row(id,"live","right-half horizontal previous");
                drag(.42f,.55f,-.34f,0,true,true);SystemClock.sleep(320);check((Integer)get("currentChannelIndex")==0,"Cancelled live swipe changed channel");row(id,"live","cancel horizontal");
                drag(.8f,.65f,0,-.28f,false,true);until(()->!(Boolean)get("channelSwitchAnimating"),4000,"Vertical live switch stuck");ready();check((Integer)get("currentChannelIndex")==1,"Live right vertical swipe no longer changes channel");row(id,"live","right vertical next");
                channel(3);hide();check((Long)call("seekableDuration",new Class[]{})>30000,"Fixture not VOD");p=player();
                main(()->PlaybackBrightness.apply(activity,35));audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0);drag(.2f,.65f,0,-.25f,false,true);
                check(PlaybackBrightness.percent(activity)>60&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)==volume,"Hidden VOD brightness wrong");check(player()==p&&(Integer)get("currentChannelIndex")==3,"VOD brightness changed player/channel");row(id,"vod","hidden left brightness");if(id.equals("ui2"))shot("vod-brightness");hide();
                drag(.8f,.4f,0,.25f,false,true);check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)<volume,"Hidden VOD right swipe did not reduce volume");check(player()==p&&(Integer)get("currentChannelIndex")==3,"VOD volume changed player/channel");row(id,"vod","hidden right volume");if(id.equals("ui2"))shot("vod-volume");hide();
                main(()->call("seekPlayback",new Class[]{long.class},15000L));until(()->Math.abs(player().getCurrentPosition()-15000)<2500,8000,"Initial seek failed");
                long before=p.getCurrentPosition(),down=SystemClock.uptimeMillis();touch(0,down,.15f,.4f);for(int i=1;i<=6;i++){touch(2,down,.15f+.3f*i/6,.4f);SystemClock.sleep(20);}
                check(Math.abs(p.getCurrentPosition()-before)<2000,"Seek committed before UP");if(id.equals("ui2"))shot("vod-seek-preview");touch(1,down,.45f,.4f);
                long expected=before+Math.round(.3*(Long)call("seekableDuration",new Class[]{}));until(()->Math.abs(player().getCurrentPosition()-expected)<3000,10000,"VOD swipe did not seek forward");
                check(player()==p&&(Integer)get("currentChannelIndex")==3,"VOD swipe changed channel/player");DirectVideoView view=(DirectVideoView)get("videoView");check(Math.abs(view.getTranslationX())<1&&Math.abs(view.getTranslationY())<1,"VOD moved Surface");row(id,"vod","hidden seek preview and commit");
                hide();long rewind=p.getCurrentPosition()-Math.round(.25*(Long)call("seekableDuration",new Class[]{}));drag(.7f,.4f,-.25f,0,false,true);until(()->Math.abs(player().getCurrentPosition()-rewind)<3000,10000,"VOD left swipe did not rewind");check(player()==p&&(Integer)get("currentChannelIndex")==3,"VOD rewind changed channel/player");row(id,"vod","hidden seek backward");
                hide();before=p.getCurrentPosition();drag(.7f,.4f,-.3f,0,true,true);SystemClock.sleep(220);check(Math.abs(p.getCurrentPosition()-before)<2000,"Cancelled VOD swipe sought");row(id,"vod","cancel seek");
                // Same controller also handles gestures while already visible.
                main(()->PlaybackBrightness.apply(activity,60));drag(.2f,.4f,0,.2f,false,true);check(PlaybackBrightness.percent(activity)<45,"Visible controller brightness failed");row(id,"vod","visible controller brightness");
                hide();
            }
            check(android.provider.Settings.System.getInt(c.getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS,-1)==systemBrightness,"System brightness changed");
            result.putString("stream","PASS: six themes; live full-width channel swipes and brightness; hidden/visible VOD brightness, volume, seek preview/commit/cancel; player unchanged; global brightness unchanged\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(audio!=null)audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume,0);if(activity!=null){final float b=oldBrightness;main(()->{android.view.WindowManager.LayoutParams p=activity.getWindow().getAttributes();p.screenBrightness=b;activity.getWindow().setAttributes(p);activity.finish();});}TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
