package xiao.bu.tv;
import android.app.*;import android.os.*;import android.content.*;import java.lang.reflect.*;import java.util.*;
public final class EpgDisplayDiagnosticInstrumentation extends Instrumentation {
 MainActivity activity;public void onCreate(Bundle b){super.onCreate(b);start();}
 public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
 public void onStart(){Bundle b=new Bundle();try{
 getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 for(int i=0;i<200&&activity==null;i++)SystemClock.sleep(50);SystemClock.sleep(2000);
 Field f=MainActivity.class.getDeclaredField("epgManager");f.setAccessible(true);EpgManager manager=(EpgManager)f.get(activity);
 Method refresh=MainActivity.class.getDeclaredMethod("refreshEpgNow");refresh.setAccessible(true);runOnMainSync(()->{try{refresh.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
 for(int i=0;i<500&&manager.isLoading();i++)SystemClock.sleep(50);
 Method open=MainActivity.class.getDeclaredMethod("openChannelList");open.setAccessible(true);runOnMainSync(()->{try{open.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(500);
 Field af=MainActivity.class.getDeclaredField("channelAdapter");af.setAccessible(true);Object adapter=af.get(activity);Field am=ChannelListAdapter.class.getDeclaredField("epgManager");am.setAccessible(true);
 StringBuilder s=new StringBuilder("adapterBound="+(am.get(adapter)==manager)+" now="+System.currentTimeMillis()+" loaded="+"loaded"+" error="+manager.getLastError()+"\n");
 int count=0;for(ChannelCatalog.Group group:ChannelCatalog.GROUPS)for(Channel channel:group.channels){
 if(count++>12)break;List<EpgManager.Program> programmes=manager.programsFor(channel);String title=new EpgManager.CurrentProgram().titleFor(manager,channel,System.currentTimeMillis());
 s.append(channel.name).append(" id=").append(channel.epgId).append(" rows=").append(programmes.size()).append(" title=").append(title);
 if(!programmes.isEmpty())s.append(" first=").append(programmes.get(0).startMillis).append(" last=").append(programmes.get(programmes.size()-1).stopMillis);s.append("\n");
 }b.putString("stream",s.toString());finish(-1,b);
 }catch(Throwable e){b.putString("stream",android.util.Log.getStackTraceString(e));finish(1,b);}}
}
