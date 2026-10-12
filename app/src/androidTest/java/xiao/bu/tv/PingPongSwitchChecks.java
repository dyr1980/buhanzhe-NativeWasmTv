package xiao.bu.tv;

import android.app.Instrumentation;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Experimental harness only: production keeps its single-decoder strategy. */
final class PingPongSwitchChecks {
    private final Instrumentation test;
    private final MainActivity activity;
    private final JSONArray results;
    private final List<HlsProxyServer> proxies=new ArrayList<>();
    private final IMediaPlayer[] slots=new IMediaPlayer[2];
    private final CountDownLatch[] recycled=new CountDownLatch[]{new CountDownLatch(0),new CountDownLatch(0)};
    private final AtomicReference<Throwable> asyncFailure=new AtomicReference<>();
    private SurfaceView secondSurface;
    private final boolean separateSurface;
    private PingPongSwitchChecks(Instrumentation test,MainActivity activity,JSONArray results,boolean separateSurface) {
        this.test=test;this.activity=activity;this.results=results;this.separateSurface=separateSurface;
    }
    private Field field(String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private Object get(String name)throws Exception{return field(name).get(activity);}
    private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
    private interface Action{void run()throws Exception;}
    private void main(Action action)throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        test.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
        if(error.get()!=null)throw new Exception(error.get());
    }
    private static void listenersOff(IMediaPlayer player) {
        AndroidMediaPlayer adapter=(AndroidMediaPlayer)player;
        adapter.setOnPreparedListener(null);adapter.setOnInfoListener(null);adapter.setOnErrorListener(null);
        adapter.setOnCompletionListener(null);adapter.setOnVideoSizeChangedListener(null);
    }
    private long position(IMediaPlayer player)throws Exception {
        final long[] value={0};main(()->value[0]=player.getCurrentPosition());return value[0];
    }
    private void progress(IMediaPlayer player,long minimumMs,long timeoutMs)throws Exception {
        long first=position(player),deadline=SystemClock.elapsedRealtime()+timeoutMs;
        while(SystemClock.elapsedRealtime()<deadline) {
            if(asyncFailure.get()!=null)throw new Exception(asyncFailure.get());
            final boolean[] playing={false};final int[] width={0};
            main(()->{playing[0]=player.isPlaying();width[0]=player.getVideoWidth();});
            if(playing[0]&&width[0]>0&&position(player)-first>=minimumMs)return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Incoming player clock/video did not progress");
    }
    private String resolve(String source,int request)throws Exception {
        if(!Ku9ScriptResolver.isKu9Source(source))return source;
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        Ku9ScriptResolver resolver=new Ku9ScriptResolver(activity,(FrameLayout)get("root"));
        try {
            main(()->resolver.resolve(request,"PingPong",source,new Ku9ScriptResolver.Callback(){
                public void onResolved(int id,Ku9ScriptResolver.Result result){value.set(result.url);done.countDown();}
                public void onFailed(int id,String reason){error.set(reason);done.countDown();}
            }));
            if(!done.await(25,TimeUnit.SECONDS)||value.get()==null)
                throw new AssertionError("Ku9 resolution failed: "+error.get());
            return value.get();
        }finally{main(resolver::cancel);}
    }
    private void attach(IMediaPlayer player,int slot)throws Exception {
        DirectVideoView video=(DirectVideoView)get("videoView");
        player.setDisplay(separateSurface&&slot==1?secondSurface.getHolder():video.getVideoSurfaceHolder());
    }
    private void run(Bundle args,String origin)throws Exception {
        main(()->{
            slots[0]=(IMediaPlayer)get("player");listenersOff(slots[0]);
            slots[1]=new AndroidMediaPlayer();
            if(separateSurface) {
                secondSurface=new SurfaceView(activity);secondSurface.setZOrderMediaOverlay(true);
                secondSurface.getHolder().setType(android.view.SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                ((FrameLayout)get("root")).addView(secondSurface,new FrameLayout.LayoutParams(2,2));
            }
        });
        if(separateSurface) {
            long until=SystemClock.elapsedRealtime()+5000;
            while(!secondSurface.getHolder().getSurface().isValid()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);
            if(!secondSurface.getHolder().getSurface().isValid())throw new AssertionError("Second surface not created");
        }
        int active=0;
        String sourceList=args.getString("pingPongSources",origin+"b/index.m3u8|"+origin+"a/index.m3u8|"+origin+"c/index.m3u8|"+origin+"b/index.m3u8|"+origin+"a/index.m3u8|"+origin+"c/index.m3u8");
        if(Boolean.parseBoolean(args.getString("pingPongFailureCheck","false")))sourceList+="|"+origin+"failed/index.m3u8";
        String[] sources=sourceList.split("\\|");
        for(int n=0;n<sources.length;n++) {
            final int incoming=1-active,outgoing=active,round=n+1;
            JSONObject row=new JSONObject().put("phase","ping-pong-"+round).put("source",sources[n])
                    .put("surface",separateSurface?"separate":"shared");results.put(row);
            long started=SystemClock.elapsedRealtime();
            try {
                if(asyncFailure.get()!=null)throw new Exception(asyncFailure.get());
                long resolveAt=SystemClock.elapsedRealtime();String url=resolve(sources[n],10000+n);
                row.put("resolveMs",SystemClock.elapsedRealtime()-resolveAt);
                HlsProxyServer proxy=new HlsProxyServer(activity,false,true,true,2,true,"high",2,2,2);
                proxies.add(proxy);proxy.start();long preloadAt=SystemClock.elapsedRealtime();
                HlsSwitchSession.Prepared ready=proxy.prepareChannel(url);
                proxy.enableChannelSession(ready);
                row.put("preloadMs",SystemClock.elapsedRealtime()-preloadAt);
                long idleWaitAt=SystemClock.elapsedRealtime();
                if(!recycled[incoming].await(15,TimeUnit.SECONDS))throw new AssertionError("Idle slot reset timed out");
                row.put("idleSlotWaitMs",SystemClock.elapsedRealtime()-idleWaitAt);
                CountDownLatch prepared=new CountDownLatch(1);AtomicReference<String> prepareError=new AtomicReference<>();
                AndroidMediaPlayer next=(AndroidMediaPlayer)slots[incoming];
                IMediaPlayer old=slots[outgoing];long oldPosition=position(old);
                long prepareAt=SystemClock.elapsedRealtime();
                row.put("standbyStarted",true);
                main(()->{
                    next.setOnPreparedListener(mp->prepared.countDown());
                    next.setOnErrorListener((mp,what,extra)->{prepareError.set(what+"/"+extra);prepared.countDown();return true;});
                    next.getInternalMediaPlayer().setAudioStreamType(AudioManager.STREAM_MUSIC);
                    next.setVolume(0f,0f);
                    if(separateSurface)attach(next,incoming);
                    next.getInternalMediaPlayer().setDataSource(proxy.playerHlsUrl(url));next.prepareAsync();
                });
                if(!prepared.await(20,TimeUnit.SECONDS)||prepareError.get()!=null)
                    throw new AssertionError("Standby preparation failed: "+prepareError.get());
                row.put("prepareMs",SystemClock.elapsedRealtime()-prepareAt);
                // Measure outgoing progress after at least 600 ms, even if prepare was instant.
                long observation=Math.max(0,600-(SystemClock.elapsedRealtime()-prepareAt));SystemClock.sleep(observation);
                row.put("oldProgressWhilePrepareMs",position(old)-oldPosition);
                if(position(old)-oldPosition<200)throw new AssertionError("Outgoing stopped while standby prepared");
                long handoffAt=SystemClock.elapsedRealtime();
                long oldBeforeHandoff=position(old);
                final long[] detachMs={0},attachMs={0};
                main(()->{
                    long at=SystemClock.elapsedRealtime();
                    if(!separateSurface)old.setDisplay(null);
                    detachMs[0]=SystemClock.elapsedRealtime()-at;at=SystemClock.elapsedRealtime();
                    if(!separateSurface)attach(next,incoming);
                    else secondSurface.setLayoutParams(new FrameLayout.LayoutParams(incoming==1?-1:2,incoming==1?-1:2));
                    attachMs[0]=SystemClock.elapsedRealtime()-at;
                    old.setVolume(0f,0f);next.setVolume(1f,1f);next.start();
                    set("player",next);set("prepared",true);set("buffering",false);set("videoRenderingStarted",true);
                });
                row.put("detachMs",detachMs[0]).put("attachMs",attachMs[0]);
                CountDownLatch resetDone=new CountDownLatch(1);recycled[outgoing]=resetDone;
                final long resetDelay=Long.parseLong(args.getString("pingPongResetDelayMs","0"));
                new Thread(()->{
                    SystemClock.sleep(resetDelay);
                    long at=SystemClock.elapsedRealtime();
                    try{((AndroidMediaPlayer)old).getInternalMediaPlayer().reset();synchronized(results){row.put("backgroundResetMs",SystemClock.elapsedRealtime()-at);}}
                    catch(Throwable failure){asyncFailure.compareAndSet(null,failure);}
                    finally{resetDone.countDown();}
                },"ping-pong-reset").start();
                progress(next,600,20000);
                row.put("handoffToProgressMs",SystemClock.elapsedRealtime()-handoffAt)
                        .put("totalToProgressMs",SystemClock.elapsedRealtime()-started)
                        .put("width",next.getVideoWidth()).put("height",next.getVideoHeight());
                if(resetDelay>=5000) {
                    long oldAdvanced=position(old)-oldBeforeHandoff;
                    row.put("oldProgressAfterHandoffMs",oldAdvanced);
                    if(oldAdvanced<600)throw new AssertionError("Outgoing clock stalled during dual playback");
                }
                if(prepareError.get()!=null)throw new AssertionError("Incoming error after prepare: "+prepareError.get());
                active=incoming;
                progress(next,2000,10000);
                if(prepareError.get()!=null)throw new AssertionError("Incoming error during stability check: "+prepareError.get());
                row.put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize()).put("javaHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
                row.put("status","passed");
                Bundle update=new Bundle();update.putString("stream",row.toString()+"\n");test.sendStatus(0,update);
                SystemClock.sleep(Integer.parseInt(args.getString("pingPongSettleMs","5000")));
            }catch(Throwable failure){
                row.put("status","failed").put("error",android.util.Log.getStackTraceString(failure));
                if(Boolean.parseBoolean(args.getString("pingPongContinueSources","false"))&&!row.optBoolean("standbyStarted")) {
                    if(get("player")!=slots[active])throw new AssertionError("Failed warmup replaced outgoing player");
                    progress(slots[active],600,6000);
                    row.put("status","source-failed").put("outgoingPreserved",true);
                    Bundle update=new Bundle();update.putString("stream",row.toString()+"\n");test.sendStatus(0,update);
                }else throw failure;
            }
        }
    }
    private void close()throws Exception {
        main(()->{set("player",null);set("prepared",false);});
        for(int slot=0;slot<slots.length;slot++) {
            recycled[slot].await(20,TimeUnit.SECONDS);
            IMediaPlayer player=slots[slot];if(player!=null){
                CountDownLatch done=new CountDownLatch(1);
                new Thread(()->{try{player.release();}finally{done.countDown();}},"ping-pong-cleanup").start();
                done.await(20,TimeUnit.SECONDS);
            }
        }
        for(HlsProxyServer proxy:proxies)proxy.close();
        if(secondSurface!=null)main(()->((FrameLayout)get("root")).removeView(secondSurface));
    }
    static void run(Instrumentation test,MainActivity activity,Bundle args,JSONArray results,String origin)throws Exception {
        PingPongSwitchChecks experiment=new PingPongSwitchChecks(test,activity,results,
                "separate".equals(args.getString("pingPongSurface","shared")));
        try{experiment.run(args,origin);}finally{experiment.close();}
    }
}
