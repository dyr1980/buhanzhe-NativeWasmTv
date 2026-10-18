package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Parse a real M3U/XMLTV fixture, persist it, then play archived HLS through the normal UI. */
public final class CatchupInstrumentation extends Instrumentation {
    private MainActivity activity;private Bundle args;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void callActivityOnResume(Activity value){super.callActivityOnResume(value);if(value instanceof MainActivity)activity=(MainActivity)value;}
    private static Field field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(String name)throws Exception{return field(activity,name).get(activity);}
    private void set(String name,Object value)throws Exception{field(activity,name).set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private void main(Runnable task){runOnMainSync(task);waitForIdleSync();}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private byte[] read(String url)throws Exception{try(InputStream in=NetworkClient.open(new java.net.URL(url)).getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);return out.toByteArray();}}
    private void waitPlayback(String marker)throws Exception{
        long until=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<until){String active=String.valueOf(get("activePlayerStreamUrl"));boolean live=marker.matches("/live\\.m3u8\\?line=[12]");if((Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted")&&active.contains(marker)&&(!live||!active.contains("playseek=")&&!active.contains("&start=")))return;SystemClock.sleep(40);}
        throw new AssertionError("No rendered playback: "+marker+" active="+get("activePlayerStreamUrl")+" prepared="+get("prepared")+" rendered="+get("videoRenderingStarted")+" request="+get("playRequestId")+" status="+((TextView)get("statusText")).getText()+" player="+get("player"));
    }
    private void screenshot(String name)throws Exception{
        android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image==null)return;
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    @Override public void onStart(){Bundle result=new Bundle();int code=1;Context c=getTargetContext();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;
        SharedPreferences prefs=c.getSharedPreferences(MainActivity.PREFERENCES,0);String epgUrl=prefs.getString("epg_url",null),epgUrls=prefs.getString("epg_urls_v2",null);
        try{
            String base=args.getString("base");check(base!=null,"Missing base fixture URL");
            boolean automatic="true".equals(args.getString("automatic"));
            String archiveMarker=automatic?"/live.m3u8?line=1&playseek=":"/archive.m3u8?start=";
            String secondArchive=automatic?"line=2&playseek=":"line=2&start=";
            prefs.edit().putString("epg_url",base+"/guide.xml").putString("epg_urls_v2",new JSONArray().put(base+"/guide.xml").toString()).commit();
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);check(activity!=null,"Activity not resumed");SystemClock.sleep(2000);
            if(args.getString("theme")!=null){TvUiPlugin.manage(c,new JSONObject().put("id",args.getString("theme")).toString().getBytes("UTF-8"));waitForIdleSync();}
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,read(base+"/channels.m3u"),base+"/channels.m3u");
            check(groups.length==1&&groups[0].channels.length==1,"M3U merge failed");
            Channel channel=groups[0].channels[0];check(channel.sourceCount()==2&&channel.catchupFor(0)!=null&&(automatic?channel.catchupFor(0).inferred()&&channel.catchupFor(1).inferred():"append".equals(channel.catchupFor(1).mode)),"Per-source catchup lost");
            ChannelCatalogStore db=new ChannelCatalogStore(c);check(db.replace(groups,"catchup-regression"),"SQLite write failed");ChannelCatalog.Group[] loaded=db.load();db.close();check(loaded!=null&&loaded[0].channels[0].catchupText().equals(channel.catchupText()),"SQLite catchup round trip failed");
            check(!automatic||loaded[0].channels[0].catchupFor(0).inferred(),"Automatic rule lost on reload");
            Channel off=channel.withCatchup(channel.sourceUrl(0),new IptvCatchup("none","",7,0)).withCatchup(channel.sourceUrl(1),new IptvCatchup("none","",7,0));
            ChannelCatalog.Group[] disabled={new ChannelCatalog.Group("禁用回看",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{off})};
            db=new ChannelCatalogStore(c);check(db.replace(disabled,"catchup-disabled"),"Disabled SQLite write failed");
            ChannelCatalog.Group[] disabledLoaded=db.load();check(disabledLoaded!=null&&disabledLoaded[0].channels[0].catchupDays()==0,"SQLite re-enabled disabled catchup");
            db.replace(groups,"catchup-regression");db.close();
            EpgManager epg=(EpgManager)get("epgManager");epg.refresh(new String[]{base+"/guide.xml"},null);
            until=SystemClock.elapsedRealtime()+10000;while(epg.isLoading()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);
            List<EpgManager.Program> programs=epg.programsFor(channel);check(programs.size()==5,"Historical XMLTV entries lost: "+programs.size());
            main(()->{try{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);set("epgEnabled",true);invoke("startChannel",new Class[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}});
            waitPlayback("/live.m3u8?line=1");
            main(()->{try{invoke("openChannelList",new Class[]{});set("epgExpanded",true);invoke("showEpgForBrowsingChannel",new Class[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}});
            EpgListAdapter adapter=(EpgListAdapter)get("epgAdapter");int previous=-1,expired=-1,future=-1;
            for(int i=0;i<adapter.getCount();i++){String title=adapter.getItem(i).title;if(title.equals("昨天节目"))previous=i;if(title.equals("过期节目"))expired=i;if(title.equals("未来节目"))future=i;}
            check(previous>=0&&expired<0&&future>=0,"EPG archive window filtering failed");
            final int next=future;Object livePlayer=get("player");
            main(()->{try{invoke("playEpgProgram",new Class[]{int.class},next);}catch(Exception e){throw new RuntimeException(e);}});check(get("player")==livePlayer&&get("catchupProgram")==null,"Future programme interrupted live playback");
            final int history=previous;
            main(()->{try{ListView list=(ListView)get("epgList");list.performItemClick(null,history,history);}catch(Exception e){throw new RuntimeException(e);}});
            waitPlayback(archiveMarker);check(get("catchupProgram")!=null,"Archive state lost");
            check(((String)invoke("currentDebugSourcePath",new Class[]{})).contains(archiveMarker),"Debug URL still shows live stream");
            IMediaPlayer archive=(IMediaPlayer)get("player");long position=archive.getCurrentPosition();SystemClock.sleep(1300);check(archive.getCurrentPosition()>position,"Archive clock not advancing");
            main(()->{try{invoke("showVideoControls",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});
            check((Long)invoke("seekableDuration",new Class[]{})>40000,"Archive not seekable");screenshot("catchup-controls");
            main(()->{try{invoke("seekPlayback",new Class[]{long.class},12000L);}catch(Exception e){throw new RuntimeException(e);}});
            until=SystemClock.elapsedRealtime()+8000;while(archive.getCurrentPosition()<10000&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);check(archive.getCurrentPosition()>=10000,"Archive seek failed");
            main(()->{try{invoke("startChannel",new Class[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}});
            waitPlayback(archiveMarker);check(get("catchupProgram")!=null,"Retry returned to live");
            main(()->{try{invoke("switchCustomSource",new Class[]{int.class,boolean.class,String.class},1,false,"");}catch(Exception e){throw new RuntimeException(e);}});
            waitPlayback(secondArchive);check(get("catchupProgram")!=null,"Source switch returned to live");
            main(()->{try{invoke("showVideoControls",new Class[]{});VideoControlsOverlay controls=(VideoControlsOverlay)get("videoControlsOverlay");((View)field(controls,"returnLive").get(controls)).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            waitPlayback("/live.m3u8?line=2");check(get("catchupProgram")==null&&!String.valueOf(get("activePlayerStreamUrl")).contains("playseek="),"Live button did not restore live");
            main(()->{try{invoke("openChannelList",new Class[]{});set("epgExpanded",true);invoke("showEpgForBrowsingChannel",new Class[]{int.class},0);ListView list=(ListView)get("epgList");list.requestFocus();list.setSelection(history);}catch(Exception e){throw new RuntimeException(e);}});
            until=SystemClock.elapsedRealtime()+3000;while(!((View)get("epgList")).isShown()||((View)get("epgList")).getWidth()==0){check(SystemClock.elapsedRealtime()<until,"EPG column did not complete layout");SystemClock.sleep(40);}
            SystemClock.sleep(150);
            main(()->{try{ListView list=(ListView)get("epgList");list.requestFocusFromTouch();list.requestFocus();list.setSelection(history);}catch(Exception e){throw new RuntimeException(e);}});
            ListView focused=(ListView)get("epgList");screenshot("catchup-focus");check(focused.hasFocus()&&focused.getSelectedItemPosition()==history,"EPG focus not ready focus="+focused.hasFocus()+" selection="+focused.getSelectedItemPosition()+" expected="+history+" expanded="+get("epgExpanded")+" size="+focused.getWidth()+"x"+focused.getHeight()+" focusedView="+activity.getCurrentFocus()+" focusable="+focused.isFocusable()+" touch="+focused.isInTouchMode());
            screenshot("catchup-guide");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitPlayback(secondArchive);
            main(()->{try{if(get("videoControlsOverlay")!=null)((VideoControlsOverlay)get("videoControlsOverlay")).hide();}catch(Exception e){throw new RuntimeException(e);}});
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitPlayback("/live.m3u8?line=2");check(get("catchupProgram")==null,"BACK did not restore live");
            // A failed archive open must not silently issue a live URL.
            Channel broken=channel.withCatchup(channel.sourceUrl(1),new IptvCatchup("default",base+"/missing.m3u8?start={utc}",7,0));
            main(()->{try{groups[0].channels[0]=broken;set("autoSwitchSource",false);invoke("openChannelList",new Class[]{});set("epgExpanded",true);invoke("showEpgForBrowsingChannel",new Class[]{int.class},0);}catch(Exception e){throw new RuntimeException(e);}});
            main(()->{try{invoke("playEpgProgram",new Class[]{int.class},history);}catch(Exception e){throw new RuntimeException(e);}});
            until=SystemClock.elapsedRealtime()+12000;while(get("player")!=null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
            waitForIdleSync();
            check(get("player")==null&&get("catchupProgram")!=null&&((TextView)get("statusText")).getText().toString().contains("回看加载失败"),"Failed archive did not remain in catchup: "+get("activePlayerStreamUrl")+" program="+get("catchupProgram")+" status="+((TextView)get("statusText")).getText());
            main(()->{try{invoke("returnToLive",new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});waitPlayback("/live.m3u8?line=2");
            result.putString("stream",(automatic?"PASS automatic: ":"PASS: ")+"M3U inherited/per-source catchup; merged sources; SQLite; past XMLTV; archive window; future programme ignored; EPG click -> real HLS first frame; playback clock; seek; source switch and retry retain programme; live button; remote OK/BACK; failed archive stays in catchup\n");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)main(()->activity.finish());ChannelCatalog.GROUPS=saved;
            SharedPreferences.Editor editor=prefs.edit();if(epgUrl==null)editor.remove("epg_url");else editor.putString("epg_url",epgUrl);if(epgUrls==null)editor.remove("epg_urls_v2");else editor.putString("epg_urls_v2",epgUrls);editor.commit();}finish(code,result);
    }
}
