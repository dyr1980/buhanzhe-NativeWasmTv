package xiao.bu.tv;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;

/** Exercises the committed swipe path and decoder replacement on real HLS. */
public final class LegacyChannelSwitchInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private ServerSocket server;
    private ServerSocket bootstrap;
    private final CountDownLatch bootstrapHold=new CountDownLatch(1);
    private final ExecutorService http=Executors.newCachedThreadPool();
    private final JSONArray results=new JSONArray();
    private final CountDownLatch segmentRequested=new CountDownLatch(1);
    private final CountDownLatch allowSegments=new CountDownLatch(1);
    private boolean checkBuffering;
    private boolean checkHotSwitch;
    private volatile long liveStartedAt;
    private final CountDownLatch slowRequested=new CountDownLatch(1),allowSlow=new CountDownLatch(1);
    private Throwable mainFailure;
    private HeldReleasePlayer heldRelease;
    private static final class HeldReleasePlayer extends AndroidMediaPlayer {
        final IMediaPlayer delegate;
        final CountDownLatch entered=new CountDownLatch(1),allow=new CountDownLatch(1);
        HeldReleasePlayer(IMediaPlayer delegate){this.delegate=delegate;}
        @Override public long getCurrentPosition(){return 0;}
        @Override public void release(){
            entered.countDown();
            try{allow.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            finally{delegate.release();super.release();}
        }
    }
    @Override public void callActivityOnCreate(Activity target,Bundle state) {
        // A locked/sleeping test device must not steal the playback window focus.
        target.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                |WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                |WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                |WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        super.callActivityOnCreate(target,state);
        if(target instanceof MainActivity && bootstrap!=null)try {
            Field generation=field("catalogLoadGeneration");
            ((java.util.concurrent.atomic.AtomicInteger)generation.get(target)).incrementAndGet();
            field("remoteCatalogUrl").set(target,"http://127.0.0.1:"+bootstrap.getLocalPort());
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private Field field(String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private Object get(String name)throws Exception{return field(name).get(activity);}
    private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
    private void call(String name,Class<?>[] types,Object...values)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);
    }
    private interface Action{void run()throws Exception;}
    private void main(Action action)throws Exception {
        mainFailure=null;
        runOnMainSync(()->{try{action.run();}catch(Throwable error){mainFailure=error;}});
        if(mainFailure!=null)throw new Exception(mainFailure);
    }
    private void fixture(final File root)throws Exception {
        server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        http.execute(()->{
            while(!server.isClosed())try {
                final Socket socket=server.accept();socket.setSoTimeout(5000);
                http.execute(()->{try {
                    BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"ISO-8859-1"));
                    String first=input.readLine();if(first==null)return;
                    String path=new URI(first.split(" ")[1]).getPath();
                    String name=path.substring(path.lastIndexOf('/')+1);
                    if(checkHotSwitch&&path.startsWith("/failed/")) {
                        socket.getOutputStream().write("HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("ISO-8859-1"));return;
                    }
                    if(checkHotSwitch&&path.startsWith("/slow/")&&name.endsWith(".ts")) {
                        slowRequested.countDown();if(!allowSlow.await(20,TimeUnit.SECONDS))return;
                    }
                    if(checkBuffering && path.startsWith("/gated/") && name.endsWith(".ts")) {
                        segmentRequested.countDown();
                        if(!allowSegments.await(20,TimeUnit.SECONDS))throw new IOException("Segment gate timed out");
                    }
                    if(name.endsWith(".m3u8"))name="index.m3u8";
                    String range=null,line;
                    while((line=input.readLine())!=null&&line.length()>0)
                        if(line.toLowerCase(Locale.US).startsWith("range: bytes="))range=line.substring(13).trim();
                    String folder=path.contains("/b/")?"b":path.contains("/c/")?"c":"a";
                    File file=new File(checkHotSwitch?new File(root,folder):root,name);
                    byte[] playlist=null;
                    if(checkHotSwitch&&name.endsWith(".m3u8")) {
                        synchronized(this){if(liveStartedAt==0)liveStartedAt=SystemClock.elapsedRealtime();}
                        int firstPart=Math.max(0,(int)((SystemClock.elapsedRealtime()-liveStartedAt)/2000)-1);
                        StringBuilder text=new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:").append(firstPart).append('\n');
                        for(int n=firstPart;n<firstPart+3;n++)text.append("#EXTINF:2,\n").append(String.format(Locale.US,"clip%03d.ts",n%40)).append('\n');
                        playlist=text.toString().getBytes("UTF-8");
                    }
                    long length=playlist==null?file.length():playlist.length,begin=0,end=length-1;
                    if(range!=null){String[] parts=range.split("-",-1);begin=Long.parseLong(parts[0]);
                        if(parts.length>1&&parts[1].length()>0)end=Math.min(end,Long.parseLong(parts[1]));}
                    OutputStream output=socket.getOutputStream();
                    if(playlist==null&&!file.isFile()||begin>end){output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes("ISO-8859-1"));return;}
                    String headers="HTTP/1.1 "+(range==null?"200 OK":"206 Partial Content")+"\r\nContent-Type: "
                            +(name.endsWith(".m3u8")?"application/vnd.apple.mpegurl":"video/MP2T")
                            +"\r\nContent-Length: "+(end-begin+1)+"\r\nAccept-Ranges: bytes\r\n"
                            +(range==null?"":"Content-Range: bytes "+begin+"-"+end+"/"+length+"\r\n")+"Connection: close\r\n\r\n";
                    output.write(headers.getBytes("ISO-8859-1"));
                    if(playlist!=null){output.write(playlist);return;}
                    try(RandomAccessFile data=new RandomAccessFile(file,"r")) {
                        data.seek(begin);byte[] buffer=new byte[32768];long remaining=end-begin+1;
                        while(remaining>0){int n=data.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)break;
                            output.write(buffer,0,n);remaining-=n;}
                    }
                }catch(Exception ignored){}finally{try{socket.close();}catch(IOException ignored){}}});
            }catch(Exception error){if(!server.isClosed())android.util.Log.e("LegacySwitchTest","fixture failed",error);}
        });
    }
    private void waitPlaying(String phase,int index)throws Exception {
        long started=SystemClock.elapsedRealtime(),deadline=started+25000;
        IMediaPlayer active=null;long position=0;
        while(SystemClock.elapsedRealtime()<deadline) {
            IMediaPlayer current=(IMediaPlayer)get("player");
            if(current!=null && field("prepared").getBoolean(activity) && field("videoRenderingStarted").getBoolean(activity)
                    && field("currentChannelIndex").getInt(activity)==index
                    && !field("preparingChannelSwitch").getBoolean(activity)
                    && !field("channelSwitchAnimating").getBoolean(activity)) {
                if(active!=current){active=current;position=current.getCurrentPosition();}
                if(current.getCurrentPosition()-position>=600 && current.getVideoWidth()>0 && current.isPlaying()) {
                    if(Build.VERSION.SDK_INT<=15 && !(current instanceof AndroidMediaPlayer))
                        throw new AssertionError("Legacy system player was bypassed");
                    if(((View)get("channelSwitchBlackout")).getVisibility()!=View.GONE)
                        throw new AssertionError("Blackout covers incoming frame");
                    if(!activity.hasWindowFocus())throw new AssertionError("Lost foreground");
                    String status=((TextView)get("statusText")).getText().toString();
                    if(!status.contains("播放中"))throw new AssertionError("Playback status not restored: "+status);
                    if (Boolean.parseBoolean(args.getString("checkMetadata", "false"))) {
                        final VideoStreamDescription[] description = {VideoStreamDescription.UNKNOWN};
                        long metadataDeadline = SystemClock.elapsedRealtime() + 6000;
                        do {
                            main(() -> {
                                Method collect = MainActivity.class.getDeclaredMethod("collectPlaybackStreamStats", float.class);
                                collect.setAccessible(true);
                                Object stats = collect.invoke(activity, 0f);
                                Field source = stats.getClass().getDeclaredField("videoDescription");
                                source.setAccessible(true);
                                description[0] = (VideoStreamDescription) source.get(stats);
                            });
                            if (description[0].complete()) break;
                            SystemClock.sleep(50);
                        } while (SystemClock.elapsedRealtime() < metadataDeadline);
                        if (description[0].bitDepth != 8 || !"SDR".equals(description[0].dynamicRange)
                                || !description[0].complete())
                            throw new AssertionError("HUD metadata missing: " + phase + " " + description[0].debugLabel());
                        results.put(new JSONObject().put("phase", phase + "-HUD-metadata")
                                .put("label", description[0].debugLabel()).put("status", "passed"));
                    }
                    results.put(new JSONObject().put("phase",phase).put("index",index)
                            .put("elapsedMs",SystemClock.elapsedRealtime()-started)
                            .put("player",current.getClass().getSimpleName()).put("width",current.getVideoWidth())
                            .put("positionAdvancedMs",current.getCurrentPosition()-position).put("status","played")
                            .put("cardStatus",status));
                    Bundle progress=new Bundle();progress.putString("stream","PASS "+phase+" index="+index+"\n");sendStatus(0,progress);return;
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Playback did not resume: "+phase+" index="+index+" player="+get("player")
                +" prepared="+get("prepared")+" rendering="+get("videoRenderingStarted"));
    }
    private void info(int what)throws Exception {
        Method notify=Class.forName("tv.danmaku.ijk.media.player.AbstractMediaPlayer")
                .getDeclaredMethod("notifyOnInfo",int.class,int.class);notify.setAccessible(true);
        notify.invoke(get("player"),what,0);
    }
    private void assertStatus(String expected)throws Exception {
        String text=((TextView)get("statusText")).getText().toString();
        if(!text.contains(expected))throw new AssertionError("Expected "+expected+", got "+text);
    }
    private void bufferingScenario()throws Exception {
        main(()->call("startChannel",new Class<?>[]{int.class},1));
        if(!segmentRequested.await(12,TimeUnit.SECONDS))throw new AssertionError("No gated media request");
        long deadline=SystemClock.elapsedRealtime()+2500;
        while(get("player")==null && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
        for(int n=0;n<20;n++) {
            main(()->{
                assertStatus("缓冲中");
                if(((View)get("channelBar")).getVisibility()!=View.VISIBLE)
                    throw new AssertionError("Buffering card hidden while segments unavailable");
                if((Boolean)get("videoRenderingStarted")&&!(Boolean)get("preparingChannelSwitch"))throw new AssertionError("Target rendered without media");
            });
            SystemClock.sleep(100);
        }
        main(()->{
            // A prepared callback and BUFFERING_END can both precede the first frame.
            boolean wasPrepared=(Boolean)get("prepared"),wasBuffering=(Boolean)get("buffering");
            set("prepared",true);
            call("showChannelBar",new Class<?>[]{String.class,String.class},"Switch B","直播播放中");
            assertStatus("缓冲中");
            info(IMediaPlayer.MEDIA_INFO_BUFFERING_END);
            assertStatus("缓冲中");
            set("prepared",wasPrepared);
            set("buffering",wasBuffering);
        });
        results.put(new JSONObject().put("phase","withheld-fragments-and-prepared-without-frame")
                .put("status","buffering"));
        allowSegments.countDown();
        waitPlaying("fragments-released",1);
        main(()->{
            call("showChannelBar",new Class<?>[]{String.class,String.class},"Switch B","直播播放中");
            info(IMediaPlayer.MEDIA_INFO_BUFFERING_START);
            assertStatus("缓冲中");
        });
        SystemClock.sleep(700);
        main(()->{
            assertStatus("缓冲中");
            info(IMediaPlayer.MEDIA_INFO_BUFFERING_END);
            assertStatus("播放中");
            call("showChannelBar",new Class<?>[]{String.class,String.class},"Switch B","已暂停");
            call("refreshChannelPlaybackStatus",new Class<?>[]{});
            assertStatus("已暂停");
            call("showChannelBar",new Class<?>[]{String.class,String.class},"Switch B","播放错误: 测试提示");
            call("refreshChannelPlaybackStatus",new Class<?>[]{});
            assertStatus("播放错误");
        });
        results.put(new JSONObject().put("phase","buffering-events-and-notices").put("status","passed"));
        main(()->call("startChannel",new Class<?>[]{int.class},0));
        waitPlaying("back-to-baseline",0);
    }
    private void hotSwitchScenario()throws Exception {
        IMediaPlayer original=(IMediaPlayer)get("player");Object stableProxy=get("proxy");
        long oldPosition=original.getCurrentPosition();
        main(()->call("startChannel",new Class<?>[]{int.class},1));
        if(!segmentRequested.await(12,TimeUnit.SECONDS))throw new AssertionError("No gated warmup request");
        SystemClock.sleep(1400);
        main(()->{
            if(get("player")!=original||!original.isPlaying()||original.getCurrentPosition()-oldPosition<600)
                throw new AssertionError("Outgoing playback stopped during warmup");
            assertStatus("缓冲中");
            if(((View)get("channelSwitchBlackout")).getVisibility()!=View.GONE)throw new AssertionError("Old picture covered during warmup");
        });
        results.put(new JSONObject().put("phase","old-playback-continues-withheld-target").put("status","passed"));
        allowSegments.countDown();waitPlaying("matching-format-hot-switch",1);
        if(get("player")!=original||get("proxy")!=stableProxy)throw new AssertionError("Matching channel rebuilt player or proxy");
        main(()->call("animateRelativeChannelSwitch",new Class<?>[]{int.class,float.class},-1,1f));
        waitPlaying("matching-format-hot-back",0);
        results.put(new JSONObject().put("phase","return-channel-handoff")
                .put("mode",get("player")==original?"hot":"warmed-reopen"));
        main(()->call("startChannel",new Class<?>[]{int.class},2));waitPlaying("resolution-change-reopens",2);
        IMediaPlayer smaller=(IMediaPlayer)get("player");
        if(smaller==original||smaller.getVideoWidth()!=320)throw new AssertionError("Changed parameters reused decoder");
        main(()->call("startChannel",new Class<?>[]{int.class},3));
        long timeout=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<timeout && ((Boolean)get("preparingChannelSwitch")||(Integer)get("currentChannelIndex")!=2))SystemClock.sleep(100);
        if(get("player")!=smaller||(Integer)get("currentChannelIndex")!=2||(Boolean)get("preparingChannelSwitch"))
            throw new AssertionError("Failed target did not preserve outgoing playback");
        results.put(new JSONObject().put("phase","failed-target-preserves-old-player").put("status","passed"));
        main(()->call("startChannel",new Class<?>[]{int.class},4));
        if(!slowRequested.await(12,TimeUnit.SECONDS))throw new AssertionError("No slow warmup to cancel");
        main(()->call("startChannel",new Class<?>[]{int.class},0));waitPlaying("rapid-latest-wins",0);
        allowSlow.countDown();SystemClock.sleep(800);
        if((Integer)get("currentChannelIndex")!=0||(Boolean)get("preparingChannelSwitch"))throw new AssertionError("Cancelled warmup took over playback");
        results.put(new JSONObject().put("phase","cancelled-warmup-stays-cancelled").put("status","passed"));
    }
    private void holdOutgoingRelease()throws Exception {
        HlsProxyServer proxy=(HlsProxyServer)get("proxy");
        Field timeline=HlsProxyServer.class.getDeclaredField("switchSession");timeline.setAccessible(true);
        HlsSwitchSession session=(HlsSwitchSession)timeline.get(proxy);
        long deadline=SystemClock.elapsedRealtime()+6000;
        for(;;) {
            session.playlist();
            try{session.reserve(3);break;}catch(IOException missing) {
                if(SystemClock.elapsedRealtime()>deadline)throw missing;
                SystemClock.sleep(200);
            }
        } // An eight-second reserved queue requires a reopen at clock zero.
        heldRelease=new HeldReleasePlayer((IMediaPlayer)get("player"));
        main(()->set("player",heldRelease));
    }
    private void waitWarmupDuringRelease()throws Exception {
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<deadline
                && (get("channelWarmupThread")!=null||get("pendingChannelSetupAfterRelease")==null))SystemClock.sleep(50);
        if(get("player")!=null||!(Boolean)get("legacyPlayerReleasePending")
                ||get("channelWarmupThread")!=null||get("pendingChannelSetupAfterRelease")==null)
            throw new AssertionError("Preloading did not finish while release was held, or a decoder opened too early");
        main(()->assertStatus("缓冲中"));
    }
    private void parallelReleaseScenario()throws Exception {
        holdOutgoingRelease();main(()->call("startChannel",new Class<?>[]{int.class},1));
        if(!heldRelease.entered.await(3,TimeUnit.SECONDS)||!segmentRequested.await(10,TimeUnit.SECONDS))
            throw new AssertionError("Channel resolution/download waited for the previous release");
        allowSegments.countDown();waitWarmupDuringRelease();
        results.put(new JSONObject().put("phase","preload-completes-during-held-release").put("status","passed"));
        main(()->call("startChannel",new Class<?>[]{int.class},0));waitWarmupDuringRelease();
        heldRelease.allow.countDown();waitPlaying("parallel-release-latest-wins",0);
        holdOutgoingRelease();main(()->call("startChannel",new Class<?>[]{int.class},3));
        if(!heldRelease.entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Failure test did not release early");
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<deadline&&((Integer)get("currentChannelIndex")!=0
                ||get("pendingChannelSetupAfterRelease")==null))SystemClock.sleep(50);
        if((Integer)get("currentChannelIndex")!=0||get("player")!=null)
            throw new AssertionError("Failed target did not restore the original channel while release was pending");
        heldRelease.allow.countDown();waitPlaying("parallel-release-failure-rolls-back",0);
    }
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int code=-1;
        SharedPreferences prefs=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=prefs.getAll();ChannelCatalog.Group[] groups=ChannelCatalog.GROUPS;
        File report=new File(getTargetContext().getExternalFilesDir(null),"legacy-channel-switch.json");
        try {
            checkBuffering=Boolean.parseBoolean(args.getString("checkBuffering","false"));
            checkHotSwitch=Boolean.parseBoolean(args.getString("checkHotSwitch","false"));
            if(checkHotSwitch)checkBuffering=true;
            fixture(new File(args.getString("fixtureDir")));
            bootstrap=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
            http.execute(()->{try(Socket socket=bootstrap.accept()){bootstrapHold.await(15,TimeUnit.SECONDS);}catch(Exception ignored){}});
            prefs.edit().putString("last_channel_snapshot_v2",new JSONObject()
                    .put("groupTitle","SWITCH_BOOTSTRAP").put("catalogSource",ChannelCatalog.SOURCE_CUSTOM)
                    .put("number","1").put("name","SWITCH_BOOTSTRAP")
                    .put("urls",new JSONArray().put("http://A/ku9/js/__ku9_missing_bootstrap_fixture__.js"))
                    .put("sourceIndex",0).toString()).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2000);
            main(()->{
                ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet();
                set("autoSwitchSource",false);set("remoteCatalogUrl","");
                String url="http://127.0.0.1:"+server.getLocalPort()+"/";
                Channel a=new Channel("1","Switch A","",url+(checkHotSwitch?"a/":"")+"a.m3u8",null,null);
                Channel b=new Channel("2","Switch B","",url+(checkBuffering?"gated/":"")+(checkHotSwitch?"b/":"")+"b.m3u8",null,null);
                Channel c=new Channel("3","Switch C","",url+"c/c.m3u8",null,null);
                Channel bad=new Channel("4","Failed","",url+"failed/index.m3u8",null,null);
                Channel slow=new Channel("5","Slow B","",url+"slow/b/index.m3u8",null,null);
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("SWITCH_TEST",ChannelCatalog.SOURCE_CUSTOM,checkHotSwitch?new Channel[]{a,b,c,bad,slow}:new Channel[]{a,b})};
                set("currentGroupIndex",0);set("currentSourceIndex",0);
                ((View)get("root")).requestFocus();
                call("startChannel",new Class<?>[]{int.class},0);
            });
            bootstrapHold.countDown();bootstrap.close();
            waitPlaying("baseline",0);
            if(Boolean.parseBoolean(args.getString("checkPingPong","false"))) {
                PingPongSwitchChecks.run(this,activity,args,results,
                        "http://127.0.0.1:"+server.getLocalPort()+"/");
                result.putString("stream","PASS experimental dual-player handoff and background reset\n");
            }else if(Boolean.parseBoolean(args.getString("checkParallelRelease","false"))) {
                parallelReleaseScenario();result.putString("stream","PASS concurrent release/preload + release gate + latest-wins + failed-target rollback\n");
            }else if(checkHotSwitch) {
                hotSwitchScenario();result.putString("stream","PASS hot reuse + outgoing progress + format fallback + failure preservation + latest-wins cancellation\n");
            }else {
            if(checkBuffering)bufferingScenario();
            int count=Integer.parseInt(args.getString("rounds","6"));
            for(int n=0;n<count;++n) {
                final int index=(n+1)%2,step=index==1?1:-1;
                main(()->call("animateRelativeChannelSwitch",new Class<?>[]{int.class,float.class},step,step==1?-1f:1f));
                waitPlaying("swipe-"+(n+1),index);
            }
            main(()->{
                set("channelSwitchAnimating",true);set("channelSwitchDirectionY",-1f);
                call("startChannel",new Class<?>[]{int.class},1);
                call("startChannel",new Class<?>[]{int.class},0);
            });
            waitPlaying("rapid-latest-wins",0);
            result.putString("stream","PASS baseline + "+count+" committed swipes + rapid replacement\n");
            }
        }catch(Throwable error){code=0;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            if(heldRelease!=null)heldRelease.allow.countDown();
            bootstrapHold.countDown();try{if(bootstrap!=null)bootstrap.close();}catch(Exception ignored){}
            allowSegments.countDown();
            allowSlow.countDown();
            try(FileOutputStream out=new FileOutputStream(report)){out.write(results.toString(2).getBytes("UTF-8"));}catch(Exception ignored){}
            if(activity!=null)try{main(()->activity.finish());}catch(Exception ignored){}
            ChannelCatalog.GROUPS=groups;
            SharedPreferences.Editor editor=prefs.edit().clear();
            for(Map.Entry<String,?> item:saved.entrySet()) {
                String key=item.getKey();Object value=item.getValue();
                if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
            }
            editor.commit();try{if(server!=null)server.close();}catch(Exception ignored){}http.shutdownNow();
        }
        finish(code,result);
    }
}
