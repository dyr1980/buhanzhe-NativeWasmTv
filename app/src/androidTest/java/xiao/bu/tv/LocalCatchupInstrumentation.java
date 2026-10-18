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
import tv.danmaku.ijk.media.player.*;

/** Actual LAN playlist + real XMLTV history, using the application's EPG and playback actions. */
public final class LocalCatchupInstrumentation extends Instrumentation {
    private MainActivity activity;private Bundle args;private File out;private final JSONArray results=new JSONArray();
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,values);}
    private interface Work{void run()throws Exception;}
    private void main(Work task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private byte[] read(String url)throws Exception{java.net.URLConnection connection=NetworkClient.open(new java.net.URL(url));connection.setConnectTimeout(15000);connection.setReadTimeout(25000);try(InputStream in=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("Test response too large");bytes.write(block,0,n);}return bytes.toByteArray();}}
    private void save()throws Exception{try(FileOutputStream stream=new FileOutputStream(new File(out,"results.json"))){stream.write(results.toString(2).getBytes("UTF-8"));}}
    private void screenshot(String name)throws Exception{SystemClock.sleep(150);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream stream=new FileOutputStream(new File(out,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}finally{b.recycle();}}
    private long await(String url,boolean archive,long started)throws Exception{
        long end=SystemClock.elapsedRealtime()+25000;
        while(SystemClock.elapsedRealtime()<end){if((Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted")&&url.equals(get("activePlayerStreamUrl"))&&((get("catchupProgram")!=null)==archive))return SystemClock.elapsedRealtime()-started;SystemClock.sleep(50);}
        throw new AssertionError("No first frame: "+url+" active="+get("activePlayerStreamUrl")+" status="+((TextView)get("statusText")).getText());
    }
    public void onStart(){Bundle status=new Bundle();int code=1;Context context=getTargetContext();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String selected="builtin";
        try{
            out=new File(context.getExternalFilesDir(null),"local-catchup");out.mkdirs();
            String playlist=args.getString("playlist"),guide=args.getString("guide");check(playlist!=null&&guide!=null,"Missing LAN URLs");
            selected=new JSONObject(TvUiPlugin.status(context)).getString("selected");
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(activity!=null,"Activity not resumed");SystemClock.sleep(1500);
            byte[] bytes=read(playlist);try(FileOutputStream stream=new FileOutputStream(new File(out,"playlist.m3u"))){stream.write(bytes);}
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,bytes,playlist);
            String[] slugs={"cctv1","cctv5","cctv13"},themes={"builtin","ui3","ui2"};Channel[] channels=new Channel[slugs.length];int count=0;
            for(ChannelCatalog.Group g:groups)for(Channel c:g.channels){count++;for(int i=0;i<slugs.length;i++)if(c.sourceUrl(0).contains("/"+slugs[i]+".m3u8"))channels[i]=c;}
            main(()->{set("epgUrls",new String[]{guide});set("epgUrl",guide);set("epgEnabled",true);set("autoSwitchSource",false);set("autoSwitchSourceSeconds",0);set("showDebugInfo",true);set("showDateTime",true);set("showLoadTiming",true);});
            EpgManager epg=(EpgManager)get("epgManager");epg.refresh(new String[]{guide},null);end=SystemClock.elapsedRealtime()+25000;while(epg.isLoading()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);check(!epg.isLoading(),"EPG load timed out");
            final ChannelCatalog.Group[] testGroups={new ChannelCatalog.Group("本地源回看实测",ChannelCatalog.SOURCE_CUSTOM,channels)};
            main(()->ChannelCatalog.GROUPS=testGroups);
            for(int n=0;n<channels.length;n++){
                Channel c=channels[n];final int index=n;JSONObject row=new JSONObject().put("channel",c==null?slugs[n]:c.name).put("theme",themes[n]).put("playlist",playlist).put("parsedChannels",count);results.put(row);
                try{
                    check(c!=null,"Missing channel "+slugs[n]);check(c.catchupFor(0)!=null,"Missing catchup metadata");
                    TvUiPlugin.select(context,themes[n]);waitForIdleSync();
                    long now=System.currentTimeMillis();EpgManager.Program programme=null;
                    for(EpgManager.Program p:epg.programsFor(c))if(p.stopMillis<now-15*60000L&&p.startMillis>now-8*3600000L&&p.stopMillis-p.startMillis>=5*60000L&&(programme==null||p.stopMillis>programme.stopMillis))programme=p;
                    check(programme!=null,"No real completed programme: "+epg.getLastError());final EpgManager.Program history=programme;
                    String archive=c.catchupFor(0).url(c.sourceUrl(0),history.startMillis,history.stopMillis,now,history.catchupId);
                    row.put("programme",history.title).put("start",history.startMillis).put("end",history.stopMillis).put("archiveUrl",archive);
                    long started=SystemClock.elapsedRealtime();main(()->{set("currentGroupIndex",0);set("currentChannelIndex",index);set("currentSourceIndex",0);invoke("startChannel",new Class[]{int.class},index);});
                    row.put("liveFirstFrameMs",await(c.sourceUrl(0),false,started));
                    main(()->{invoke("openChannelList",new Class[]{});set("epgExpanded",true);invoke("showEpgForBrowsingChannel",new Class[]{int.class},index);});
                    EpgListAdapter adapter=(EpgListAdapter)get("epgAdapter");int chosen=-1;for(int i=0;i<adapter.getCount();i++)if(adapter.getItem(i).startMillis==history.startMillis){chosen=i;break;}check(chosen>=0,"Real history absent from EPG UI");final int position=chosen;
                    if(n==0){screenshot(slugs[n]+"-guide");started=SystemClock.elapsedRealtime();main(()->((ListView)get("epgList")).performItemClick(null,position,position));}
                    else{
                        main(()->{ListView list=(ListView)get("epgList");list.requestFocusFromTouch();list.requestFocus();list.setSelection(position);});SystemClock.sleep(200);waitForIdleSync();
                        screenshot(slugs[n]+"-guide");started=SystemClock.elapsedRealtime();sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
                    }
                    row.put("archiveFirstFrameMs",await(archive,true,started));
                    IMediaPlayer player=(IMediaPlayer)get("player");long begin=player.getCurrentPosition(),wall=SystemClock.elapsedRealtime();SystemClock.sleep(10000);check(get("player")==player,"Archive player replaced");long advanced=player.getCurrentPosition()-begin;
                    check(advanced>7000,"Archive stalled: "+advanced);row.put("sampleWallMs",SystemClock.elapsedRealtime()-wall).put("advancedMs",advanced).put("outputFps",player instanceof IjkMediaPlayer?IjkStreamMetrics.outputFrameRate((IjkMediaPlayer)player):0);
                    long duration=(Long)invoke("seekableDuration",new Class[]{});check(duration>40000,"Archive not seekable: "+duration);row.put("durationMs",duration);
                    main(()->invoke("showVideoControls",new Class[]{}));screenshot(slugs[n]+"-archive");
                    long target=Math.min(duration-15000,player.getCurrentPosition()+30000);main(()->invoke("seekPlayback",new Class[]{long.class},target));
                    end=SystemClock.elapsedRealtime()+15000;while(Math.abs(player.getCurrentPosition()-target)>7000&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);
                    check(Math.abs(player.getCurrentPosition()-target)<7000,"Archive seek missed target: "+player.getCurrentPosition()+" vs "+target);
                    long seekPos=player.getCurrentPosition();SystemClock.sleep(2500);check(player.getCurrentPosition()>seekPos+1000,"Seek did not resume video");row.put("seekTargetMs",target).put("seekPositionMs",seekPos);
                    String text=new String(read(archive),"UTF-8");check(text.contains("#EXT-X-ENDLIST"),"Archive response is still live");
                    java.util.regex.Matcher marker=java.util.regex.Pattern.compile("#EXT-X-PROGRAM-DATE-TIME:([^\\r\\n]+)").matcher(text);if(marker.find())row.put("firstSegmentDateTime",marker.group(1));row.put("vodEndlist",true);
                    started=SystemClock.elapsedRealtime();if(n==0)main(()->{VideoControlsOverlay controls=(VideoControlsOverlay)get("videoControlsOverlay");Field f=VideoControlsOverlay.class.getDeclaredField("returnLive");f.setAccessible(true);((View)f.get(controls)).performClick();});
                    else{main(()->{if(get("videoControlsOverlay")!=null)((VideoControlsOverlay)get("videoControlsOverlay")).hide();});sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);}
                    row.put("returnLiveMs",await(c.sourceUrl(0),false,started)).put("status","passed");screenshot(slugs[n]+"-live");
                }catch(Throwable error){row.put("status","failed").put("error",android.util.Log.getStackTraceString(error));main(()->{if(get("catchupProgram")!=null)invoke("returnToLive",new Class[]{});});}
                save();Bundle progress=new Bundle();progress.putString("stream",row.optString("channel")+" "+row.optString("status")+"\n");sendStatus(0,progress);
            }
            int failures=0;for(int i=0;i<results.length();i++)if(!"passed".equals(results.getJSONObject(i).optString("status")))failures++;
            status.putString("stream","Channels="+results.length()+" failures="+failures+" report="+new File(out,"results.json")+"\n");code=failures==0?-1:1;
        }catch(Throwable error){status.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{try{if(activity!=null)main(()->activity.finish());TvUiPlugin.select(context,selected);}catch(Throwable ignored){}ChannelCatalog.GROUPS=saved;}finish(code,status);
    }
}
