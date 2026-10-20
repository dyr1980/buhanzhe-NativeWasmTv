package xiao.bu.tv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;
public final class GlassThemeInstrumentation extends Instrumentation {
 volatile MainActivity activity;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
 Object field(String n)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(activity);}
 public void onStart(){Bundle result=new Bundle();try{
 getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 long until=SystemClock.elapsedRealtime()+20000;while(activity==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);if(activity==null)throw new AssertionError("No activity");SystemClock.sleep(2000);
 TvUiPlugin.select(activity,"ui6");waitForIdleSync();SystemClock.sleep(1000);
 runOnMainSync(()->{try{Method m=MainActivity.class.getDeclaredMethod("openChannelList");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
 SystemClock.sleep(1500);
 runOnMainSync(()->{try{
 ListView groups=(ListView)field("groupList"),channels=(ListView)field("channelList");
 if(groups.getChildCount()==0||channels.getChildCount()==0)throw new AssertionError("Empty lists");
 if(groups.getChildAt(0).getHeight()!=channels.getChildAt(0).getHeight())throw new AssertionError("Mismatched rows");
 channels.requestFocus();channels.setSelection(0);
 }catch(Exception e){throw new RuntimeException(e);}});
 SystemClock.sleep(1000);
 android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
 try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"glass-theme.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
 screenshot.recycle();
 int original=(Integer)field("browsingGroupIndex");EpgManager manager=(EpgManager)field("epgManager");int empty=-1;
 for(int i=0;i<ChannelCatalog.GROUPS.length;i++){Channel[] entries=ChannelCatalog.GROUPS[i].channels;if(entries.length>0&&manager.programsFor(entries[0]).isEmpty()){empty=i;break;}}
 if(empty<0)throw new AssertionError("No empty-guide fixture");
 final int emptyGroup=empty;
 runOnMainSync(()->{try{
 Method show=MainActivity.class.getDeclaredMethod("showChannelMenu",int.class);show.setAccessible(true);show.invoke(activity,emptyGroup);
 Method refresh=MainActivity.class.getDeclaredMethod("showEpgForBrowsingChannel",int.class);refresh.setAccessible(true);refresh.invoke(activity,0);
 View toggle=(View)field("epgToggle");if(toggle.isEnabled())throw new AssertionError("Empty guide entry enabled");toggle.performClick();
 if((Boolean)field("epgExpanded")||((View)field("epgColumn")).getVisibility()!=View.GONE)throw new AssertionError("Empty guide opened");
 show.invoke(activity,original);
 }catch(Exception e){throw new RuntimeException(e);}});
 result.putString("stream","PASS ui6 selection, equal row pitches, focus, screenshot and empty guide cannot open");finish(-1,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
