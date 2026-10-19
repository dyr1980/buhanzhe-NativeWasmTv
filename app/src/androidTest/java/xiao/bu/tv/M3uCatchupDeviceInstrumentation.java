package xiao.bu.tv;
import android.app.*;import android.content.*;import android.os.*;
import java.io.*;import java.net.*;import java.lang.reflect.*;import java.util.*;
import org.json.*;import tv.danmaku.ijk.media.player.IMediaPlayer;
/** Five real supplied M3U replay streams, through the Activity playback path. */
public final class M3uCatchupDeviceInstrumentation extends Instrumentation {
    MainActivity activity;Bundle args;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    interface Work{void run()throws Exception;}
    void main(Work work)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    Object get(String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    void set(String key,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(activity,value);}
    byte[] read(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}finally{c.disconnect();}}
    public void onStart(){Bundle result=new Bundle();JSONArray rows=new JSONArray();int code=1;ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;Map<String,?> preferences=null;int oldGroup=0,oldChannel=0,oldSource=0;
        try{
            Context c=getTargetContext();preferences=c.getSharedPreferences(MainActivity.PREFERENCES,0).getAll();
            JSONArray plan=new JSONArray(new String(read(args.getString("base")+"/plan.json"),"UTF-8"));
            byte[] m3u=read(args.getString("base")+"/playlist.m3u");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(30);if(activity==null)throw new AssertionError("No Activity");SystemClock.sleep(1200);
            oldGroup=(Integer)get("currentGroupIndex");oldChannel=(Integer)get("currentChannelIndex");oldSource=(Integer)get("currentSourceIndex");saved=ChannelCatalog.GROUPS;
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u,"http://10.1.1.1:1111/playlist.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("autoSwitchSource",false);});
            for(int i=0;i<plan.length();i++){
                JSONObject item=plan.getJSONObject(i);String id=item.getString("id");int group=-1,index=-1;
                for(int g=0;g<groups.length&&group<0;g++)for(int n=0;n<groups[g].channels.length;n++)if(id.equals(groups[g].channels[n].epgId)){group=g;index=n;break;}
                if(group<0)throw new AssertionError("M3U channel missing "+id);
                Channel channel=groups[group].channels[index];EpgManager.Program programme=new EpgManager.Program(item.getLong("start"),item.getLong("end"),item.getString("title"),"");
                String url=channel.catchupFor(0).url(channel.sourceUrl(0),programme.startMillis,programme.stopMillis,System.currentTimeMillis(),"");
                final int g=group,n=index;long began=SystemClock.elapsedRealtime();
                main(()->{set("currentGroupIndex",g);set("currentSourceIndex",0);Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class,EpgManager.Program.class,String.class);start.setAccessible(true);start.invoke(activity,n,programme,url);});
                deadline=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<deadline&&(!(Boolean)get("prepared")||!(Boolean)get("videoRenderingStarted")))SystemClock.sleep(60);
                boolean frame=(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted");
                JSONObject row=new JSONObject().put("channel",id).put("programme",programme.title).put("url",url).put("firstFrame",frame).put("startupMs",SystemClock.elapsedRealtime()-began);
                if(frame){IMediaPlayer player=(IMediaPlayer)get("player");long position=player.getCurrentPosition();SystemClock.sleep(3000);long advance=player.getCurrentPosition()-position;row.put("advanceMs",advance).put("playing",player.isPlaying()).put("width",player.getVideoWidth()).put("height",player.getVideoHeight());
                    row.put("pass",advance>1200&&player.isPlaying()&&player.getVideoWidth()>0&&get("catchupProgram")!=null&&url.equals(get("activePlayerStreamUrl")));
                }else row.put("pass",false);
                rows.put(row);sendStatus(0,new Bundle());
            }
            int passed=0;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optBoolean("pass"))passed++;
            code=passed==5?-1:1;result.putString("stream","RESULT "+passed+"/5 real Android replay streams\n"+rows.toString(2)+"\n");
        }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t)+"\n"+rows.toString());}
        finally{
            final ChannelCatalog.Group[] restore=saved;final int g=oldGroup,n=oldChannel,s=oldSource;
            try{if(activity!=null)main(()->{ChannelCatalog.GROUPS=restore;set("currentGroupIndex",g);set("currentChannelIndex",n);set("currentSourceIndex",s);activity.finish();});}catch(Exception ignored){}
            if(preferences!=null){SharedPreferences.Editor edit=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).edit().clear();for(Map.Entry<String,?> e:preferences.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else if(v instanceof Set)edit.putStringSet(e.getKey(),(Set<String>)v);}edit.commit();}
        }
        finish(code,result);
    }
}
