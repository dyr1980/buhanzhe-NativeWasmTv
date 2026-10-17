package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/** Real built-in provider selection with remote keys and draw-before-load checks. */
public final class ProviderLiveInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private File directory;
    private final JSONArray results=new JSONArray();
    private Throwable mainError;
    static final class Observation {
        long position=-1, lastProgressAt, maxNoProgressMs, bufferingMs, advancedMs;
        long outputFrames=-1, lastOutputAt, maxNoVideoFramesMs, firstOutputFrames=-1, startedAt;
        Observation(long now) { reset(now); }
        void reset(long now) {
            position=outputFrames=firstOutputFrames=-1;
            lastProgressAt=lastOutputAt=startedAt=now;
            advancedMs=0;
        }
        void sampleClock(long now,long value) {
            if(value>=0) {
                if(position>=0&&value!=position) {
                    lastProgressAt=now;
                    // HLS can rebase to an earlier window. Count progress from
                    // the new baseline rather than waiting for the old peak.
                    advancedMs+=Math.max(0,value-position);
                }
                position=value;
            }
            maxNoProgressMs=Math.max(maxNoProgressMs,now-lastProgressAt);
        }
        void sampleOutput(long now,long frames) {
            if(frames<0)return;
            if(firstOutputFrames<0)firstOutputFrames=frames;
            if(frames>outputFrames) { outputFrames=frames;lastOutputAt=now; }
            maxNoVideoFramesMs=Math.max(maxNoVideoFramesMs,now-lastOutputAt);
        }
    }
    interface Action { void run() throws Exception; }
    private Field field(String name)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(String name)throws Exception{return field(name).get(activity);}
    private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
    private void call(String name)throws Exception {Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private void main(Action action)throws Exception {
        mainError=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){mainError=e;}});
        if(mainError!=null)throw new Exception(mainError);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void save(String name,Object value)throws Exception {
        try(FileOutputStream output=new FileOutputStream(new File(directory,name))) {output.write(value.toString().getBytes("UTF-8"));}
    }
    private void progress(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    private static Object property(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private void stop()throws Exception {
        main(()->{
            set("playRequestId",(Integer)get("playRequestId")+1);
            ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet();
            call("cancelPendingChannelListSelection");call("cancelHttpResolve");call("cancelChannelManifest");
            call("closeWebSource");call("releasePlayer");
        });
        SystemClock.sleep(250);
    }
    private void clearUrls()throws Exception {
        for(String name:new String[]{"cjs-result-cache","live_url_resolver","yangshipin_resolver"}) {
            android.content.SharedPreferences p=getTargetContext().getSharedPreferences(name,0);
            android.content.SharedPreferences.Editor edit=p.edit();
            for(String key:p.getAll().keySet())if(name.equals("cjs-result-cache")||key.startsWith("url_")||key.startsWith("cached_"))edit.remove(key);
            edit.commit();
        }
        for(String[] pair:new String[][]{{"yangshipinResolver","apiCache"}}) {
            Object resolver=get(pair[0]);Map<?,?> cache=(Map<?,?>)property(resolver,pair[1]);synchronized(cache){cache.clear();}
        }
    }
    private void menu(int provider,int channel)throws Exception {
        main(()->{
            set("currentGroupIndex",provider);call("openChannelList");
            Method show=MainActivity.class.getDeclaredMethod("showChannelMenu",int.class);show.setAccessible(true);show.invoke(activity,provider);
        });
        SystemClock.sleep(250);
        main(()->{
            ListView list=(ListView)get("channelList");list.setSelection(channel);list.setItemChecked(channel,true);
            Method favorite=MainActivity.class.getDeclaredMethod("setFavoriteActionFocused",boolean.class);favorite.setAccessible(true);favorite.invoke(activity,false);
            list.requestFocus();
        });
        SystemClock.sleep(150);
    }
    private long[] select(JSONObject row)throws Exception {
        long[] clock={0,0,0}; // OK down, closing draw, heavy setup seen before draw
        final ViewTreeObserver.OnDrawListener[] listener={null};
        main(()->{
            View root=(View)get("root");View panel=(View)get("channelListPanel");
            check(((ListView)get("channelList")).hasFocus(),"Remote OK would not select a channel");
            listener[0]=()->{
                if(clock[0]>0&&clock[1]==0&&panel.getVisibility()==View.GONE) {
                    clock[1]=SystemClock.elapsedRealtime();
                    try {if(get("pendingChannelListSelection")==null)clock[2]=1;}catch(Exception e){clock[2]=1;}
                }
            };
            root.getViewTreeObserver().addOnDrawListener(listener[0]);clock[0]=SystemClock.elapsedRealtime();
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
            row.put("okDispatchMs",SystemClock.elapsedRealtime()-clock[0]);
            check(panel.getVisibility()==View.GONE,"Channel list not hidden during OK callback");
        });
        try {
            long deadline=clock[0]+3000;
            while(clock[1]==0&&SystemClock.elapsedRealtime()<deadline){main(()->{});SystemClock.sleep(10);}
            row.put("panelCloseDrawMs",clock[1]>0?clock[1]-clock[0]:JSONObject.NULL);
            check(clock[1]>0,"Closing frame never drawn");
            check(clock[2]==0,"Channel setup ran before the closing frame");
            check(clock[1]-clock[0]<=250,"Channel list took over 250ms to paint closed");
        } finally {main(()->((View)get("root")).getViewTreeObserver().removeOnDrawListener(listener[0]));}
        return clock;
    }
    private JSONObject uiPreflight()throws Exception {
        JSONObject row=new JSONObject();
        Field f=HlsProxyServer.class.getDeclaredField("CMG_DECRYPT_LOCK");f.setAccessible(true);Object lock=f.get(null);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread peer=new Thread(()->{synchronized(lock){held.countDown();try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}},"provider-test-busy-decrypt");
        peer.start();
        try {
            check(held.await(15,TimeUnit.SECONDS),"Cannot hold decrypt fixture lock");
            menu(0,0);
            select(row);SystemClock.sleep(150);
            long ping=SystemClock.elapsedRealtime();main(()->{});row.put("uiPingWhileDecryptBlockedMs",SystemClock.elapsedRealtime()-ping);
            check(row.getLong("uiPingWhileDecryptBlockedMs")<=250,"UI blocked behind old decrypt worker");
            row.put("status","passed");
        }finally{release.countDown();peer.join(1000);stop();}
        return row;
    }
    private static String basename(String url){String path=URI.create(url).getPath();return path.substring(path.lastIndexOf('/')+1);}
    private JSONObject edgeEstimate(HlsProxyServer proxy,IjkMediaPlayer player) {
        JSONObject row=new JSONObject();HttpURLConnection c=null;
        try {
            String manifest=(String)property(proxy,"monitoredCctvPlaylistUrl");
            if(manifest==null){return row.put("status","unavailable").put("reason","No monitored live manifest");}
            Method open=HlsProxyServer.class.getDeclaredMethod("openUpstreamConnection",String.class);open.setAccessible(true);
            long began=SystemClock.elapsedRealtime();c=(HttpURLConnection)open.invoke(proxy,manifest);
            c.setConnectTimeout(2500);c.setReadTimeout(2500);
            Method headers=HlsProxyServer.class.getDeclaredMethod("applyRequestHeaders",HttpURLConnection.class,String.class);headers.setAccessible(true);headers.invoke(proxy,c,manifest);
            if(c.getResponseCode()!=200)throw new IOException("Edge HTTP "+c.getResponseCode());
            StringBuilder text=new StringBuilder();
            try(BufferedReader input=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))) {
                String line;while((line=input.readLine())!=null){text.append(line).append('\n');if(text.length()>256*1024)throw new IOException("Edge manifest limit");}
            }
            final String[] requested={null};final long[] queued={0};
            main(()->{
                check(get("proxy")==proxy&&get("player")==player,"Player changed during edge measurement");
                Method source=HlsProxyServer.class.getDeclaredMethod("isYangshipinUrl",String.class);source.setAccessible(true);
                Object taskLock=property(proxy,(Boolean)source.invoke(null,manifest)?"cmgSegmentTasks":"cctvSegmentTasks");
                synchronized(taskLock){requested[0]=(String)property(proxy,"lastCctvRequestedUrl");}
                queued[0]=Math.max(0,player.getVideoCachedDuration());
            });
            if(requested[0]==null)throw new IOException("No consumed segment");
            List<String> names=new ArrayList<>();List<Long> seconds=new ArrayList<>();long duration=0;
            for(String raw:text.toString().split("\n")) {
                String line=raw.trim();
                if(line.startsWith("#EXTINF:"))duration=Math.round(Double.parseDouble(line.substring(8).split(",",2)[0])*1000);
                else if(!line.isEmpty()&&!line.startsWith("#")){names.add(basename(URI.create(manifest).resolve(line).toString()));seconds.add(duration);}
            }
            int consumed=names.indexOf(basename(requested[0]));
            if(consumed<0)throw new IOException("Consumed segment outside current CDN window");
            long gap=0;for(int n=consumed+1;n<seconds.size();n++)gap+=seconds.get(n);
            long queryMs=SystemClock.elapsedRealtime()-began;
            row.put("status","estimated").put("cdnSegmentsAfterRead",names.size()-consumed-1)
                    .put("cdnEdgeGapMs",gap).put("videoPacketCacheMs",queued[0]).put("currentSegmentMs",seconds.get(consumed))
                    .put("estimateLowerMs",gap+queued[0]).put("estimateUpperMs",gap+queued[0]+seconds.get(consumed)+queryMs)
                    .put("queryMs",queryMs).put("readSegment",basename(requested[0])).put("edgeSegment",names.get(names.size()-1))
                    .put("meaning","Approximate additional latency behind the CDN complete-segment edge; includes one segment of uncertainty, excludes broadcast encoding/CDN and panel scanout");
        }catch(Throwable e){try{row.put("status","unavailable").put("reason",e.toString());}catch(Exception ignored){}}
        finally{if(c!=null)c.disconnect();}
        return row;
    }
    @android.annotation.TargetApi(26)
    private static class SurfaceCopy {
        static android.graphics.Bitmap copy(DirectVideoView view)throws Exception {
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,view.getWidth()),Math.max(1,view.getHeight()),android.graphics.Bitmap.Config.ARGB_8888);
            CountDownLatch done=new CountDownLatch(1);int[] status={-1};
            android.view.PixelCopy.request(view.getSurfaceView(),bitmap,s->{status[0]=s;done.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
            if(!done.await(5,TimeUnit.SECONDS)||status[0]!=android.view.PixelCopy.SUCCESS){bitmap.recycle();throw new IOException("Video surface capture failed");}
            return bitmap;
        }
    }
    private void capture(JSONObject row,int index)throws Exception {
        DirectVideoView view=(DirectVideoView)get("videoView");boolean pixel=android.os.Build.VERSION.SDK_INT>=26;
        List<View> hidden=new ArrayList<>();
        if(!pixel)main(()->{android.view.ViewGroup root=(android.view.ViewGroup)view.getParent();for(int n=0;n<root.getChildCount();n++){View child=root.getChildAt(n);if(child!=view&&child.getVisibility()==View.VISIBLE){hidden.add(child);child.setVisibility(View.INVISIBLE);}}});
        android.graphics.Bitmap bitmap;
        try{if(!pixel)SystemClock.sleep(200);bitmap=pixel?SurfaceCopy.copy(view):getUiAutomation().takeScreenshot();}
        finally{if(!pixel)main(()->{for(View child:hidden)if(child.getVisibility()==View.INVISIBLE)child.setVisibility(View.VISIBLE);});}
        check(bitmap!=null,"No video screenshot");int visible=0;
        for(int y=bitmap.getHeight()/20;y<bitmap.getHeight()*19/20;y+=12)for(int x=bitmap.getWidth()/20;x<bitmap.getWidth()*19/20;x+=12)if((bitmap.getPixel(x,y)&0xffffff)!=0)visible++;
        try(FileOutputStream output=new FileOutputStream(new File(directory,String.format(Locale.US,"provider-%02d.png",index)))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();row.put("captureVisible",visible>5);check(visible>5,"Video-only screen is black");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    @Override public void onStart(){Bundle output=new Bundle();int failed=0;ChannelCatalog.Group[] original=ChannelCatalog.GROUPS;
        try {
            check(getTargetContext().getPackageName().endsWith(".providerregression"),"Only isolated provider packages may run this fixture");
            directory=getTargetContext().getExternalFilesDir(null);directory.mkdirs();
            for(String name:new String[]{"provider-live.json","ui-preflight.json","setup.json"})new File(directory,name).delete();
            for(int n=1;n<=10;n++) {
                new File(directory,String.format(Locale.US,"provider-%02d.png",n)).delete();
                for(int part=0;part<2;part++)new File(directory,String.format(Locale.US,"frozen-%02d-%d.png",n,part)).delete();
            }
            getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).edit()
                    .putBoolean("auto_switch_source",false).putBoolean("auto_update_channel_list",false)
                    .putBoolean("show_debug_info",true).putBoolean("show_load_timing",true).putBoolean("show_color_debug_info",true)
                    .putString("decode_mode",args.getString("decode","auto")).putString("resolution_mode",args.getString("quality","high")).putString("live_delay_mode","stable").commit();
            CjsPluginRuntime.initialize(getTargetContext());long setup=SystemClock.elapsedRealtime();
            CjsPluginRuntime.ensureCatalog();for(String id:new String[]{"tv.cctv.com","yangshipin.cn"})if(!CjsPluginRuntime.isInstalled(id))CjsPluginRuntime.installOrUpdate(id);
            save("setup.json",new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-setup).put("plugins",CjsPluginRuntime.statusJson()));
            progress("PLUGINS_READY");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2500);setInTouchMode(false);stop();
            int[] positions={0,1,4,7,12};Channel[] ysp=new Channel[5],cctv=new Channel[5];
            for(int n=0;n<5;n++){ysp[n]=ChannelCatalog.YANGSHIPIN_CCTV_CHANNELS[positions[n]];cctv[n]=ChannelCatalog.CCTV_CHANNELS[positions[n]];}
            main(()->{
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("央视频回归",ChannelCatalog.SOURCE_YSP_CCTV,ysp),new ChannelCatalog.Group("央视网回归",ChannelCatalog.SOURCE_CCTV_WEB,cctv)};
                set("autoSwitchSource",false);set("showDebugInfo",true);set("showLoadTiming",true);set("showColorDebugInfo",true);set("liveDelayMode","stable");
                set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);
            });
            try{save("ui-preflight.json",uiPreflight());}
            catch(Throwable error){failed++;save("ui-preflight.json",new JSONObject().put("status","failed").put("error",android.util.Log.getStackTraceString(error)));stop();}
            boolean cold=Integer.parseInt(args.getString("round","1"))%2==1;if(cold)main(()->clearUrls());
            int seconds=Integer.parseInt(args.getString("observeSeconds","30"));
            String subset=args.getString("channels","");int only=Integer.parseInt(args.getString("providerOnly","-1"));
            boolean allowRecovery="true".equals(args.getString("allowRecovery","false"));
            long detachVideoAfterMs=Long.parseLong(args.getString("detachVideoAfterMs","0"));
            for(int turn=0;turn<10;turn++) {
                int provider=cold?turn/5:1-turn/5;int channel=cold?turn%5:4-turn%5;
                if(only>=0&&provider!=only || !subset.isEmpty()&&!Arrays.asList(subset.split(",")).contains(String.valueOf(channel)))continue;
                Channel wanted=ChannelCatalog.GROUPS[provider].channels[channel];
                JSONObject row=new JSONObject().put("index",turn+1).put("provider",provider==0?"央视频":"央视网").put("channel",wanted.name)
                        .put("round",Integer.parseInt(args.getString("round","1"))).put("delayMode","stable").put("appPid",android.os.Process.myPid()).put("status","running");
                results.put(row);save("provider-live.json",results);progress("BEGIN "+row.optString("provider")+" "+wanted.name);
                try {
                    main(()->{set("currentSourceIndex",0);});menu(provider,channel);long[] clock=select(row);
                    long deadline=clock[0]+60000;boolean ready=false;
                    while(SystemClock.elapsedRealtime()<deadline) {
                        final boolean[] valid={false};main(()->{valid[0]=(Boolean)get("videoRenderingStarted")&&get("activePlayerChannel")==wanted
                                &&((Integer)get("playbackReadyRequestId")).intValue()==((Integer)get("playRequestId")).intValue();});
                        if(valid[0]){ready=true;break;}check(activity.hasWindowFocus(),"Lost device foreground");SystemClock.sleep(25);
                    }
                    check(ready,"First frame timeout");row.put("firstFrameMs",SystemClock.elapsedRealtime()-clock[0]);
                    if("true".equals(args.getString("checkRecoveryExhaustion","false"))) {
                        main(()->{
                            int beforeGroup=(Integer)get("currentGroupIndex"),beforeChannel=(Integer)get("currentChannelIndex");
                            set("playbackRecoveryAttempts",field("PLAYBACK_RECOVERY_MAX_ATTEMPTS").getInt(null));
                            Method recover=MainActivity.class.getDeclaredMethod("recoverStalledPlayback",int.class,IMediaPlayer.class,String.class,boolean.class);
                            recover.setAccessible(true);recover.invoke(activity,(Integer)get("playRequestId"),get("player"),"test video output failure",true);
                            check(get("player")==null,"Exhausted decoder recovery did not stop");
                            check((Integer)get("currentGroupIndex")==beforeGroup&&(Integer)get("currentChannelIndex")==beforeChannel,"Decoder failure changed channel");
                            check(((android.widget.TextView)get("statusText")).getText().toString().contains("系统解码器多次恢复失败"),"Missing decoder failure prompt");
                        });
                        row.put("status","passed").put("policyCheck","decoder retries exhausted: same channel, stopped, manual retry prompt");
                        save("provider-live.json",results);progress("RESULT decoder recovery exhaustion passed");continue;
                    }
                    final IjkMediaPlayer[] player={null};main(()->{check(get("player") instanceof IjkMediaPlayer,"Unexpected playback backend");player[0]=(IjkMediaPlayer)get("player");
                        row.put("width",player[0].getVideoWidth()).put("height",player[0].getVideoHeight()).put("streamUrl",get("activePlayerStreamUrl"));
                        ChannelStartupTiming timing=(ChannelStartupTiming)get("channelStartupTiming");row.put("startupTiming",timing.format((Integer)get("playRequestId")));});
                    JSONArray samples=new JSONArray();long observationStarted=SystemClock.elapsedRealtime();
                    final long[] until={observationStarted+seconds*1000L};
                    final Observation observation=new Observation(observationStarted);
                    JSONArray recoveries=new JSONArray();
                    JSONArray pixelAudits=new JSONArray();
                    while(SystemClock.elapsedRealtime()<until[0]) {
                        main(()->{
                            if(allowRecovery && get("player")!=player[0] && get("activePlayerChannel")==wanted
                                    && get("player") instanceof IjkMediaPlayer) {
                                recoveries.put(new JSONObject().put("afterFirstFrameMs",SystemClock.elapsedRealtime()-clock[0])
                                        .put("software",get("activeSoftwareDecode")));
                                player[0]=(IjkMediaPlayer)get("player");
                                observation.reset(SystemClock.elapsedRealtime());
                                // A late failure must still leave a full healthy
                                // observation window after the new player starts.
                                until[0]=Math.max(until[0],observation.startedAt+30000);
                            }
                            check(get("player")==player[0]&&get("activePlayerChannel")==wanted,"Player replaced or wrong channel during observation");
                            long now=SystemClock.elapsedRealtime(),position=player[0].getCurrentPosition();
                            if(detachVideoAfterMs>0 && !row.has("faultInjectedAtMs") && now-clock[0]>=detachVideoAfterMs) {
                                player[0].setDisplay(null);
                                row.put("faultInjectedAtMs",now-clock[0]);
                            }
                            observation.sampleClock(now,position);
                            if((Boolean)get("buffering"))observation.bufferingMs+=250;
                            long frames=IjkStreamMetrics.outputFrameCount(player[0]);
                            observation.sampleOutput(now,frames);
                            samples.put(new JSONObject().put("atMs",now).put("positionMs",position).put("fps",IjkStreamMetrics.outputFrameRate(player[0]))
                                    .put("outputFrames",frames).put("videoCacheMs",player[0].getVideoCachedDuration()).put("audioCacheMs",player[0].getAudioCachedDuration()).put("buffering",get("buffering")));
                        });
                        if(allowRecovery && "true".equals(args.getString("auditFrozenPixels","false"))
                                && recoveries.length()==0 && pixelAudits.length()<2
                                && observation.maxNoVideoFramesMs>=(pixelAudits.length()==0?4000:7000)) {
                            if(pixelAudits.length()==0)progress("VIDEO_OUTPUT_STOPPED pid="+android.os.Process.myPid()+" index="+(turn+1));
                            capture(row,turn+1);
                            String name=String.format(Locale.US,"frozen-%02d-%d.png",turn+1,pixelAudits.length());
                            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                            try(InputStream input=new FileInputStream(new File(directory,String.format(Locale.US,"provider-%02d.png",turn+1)));
                                OutputStream image=new FileOutputStream(new File(directory,name))) {
                                byte[] bytes=new byte[8192];int length;while((length=input.read(bytes))!=-1){image.write(bytes,0,length);digest.update(bytes,0,length);}
                            }
                            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.US,"%02x",value&255));
                            pixelAudits.put(new JSONObject().put("image",name).put("sha256",hash.toString())
                                    .put("outputFrames",observation.outputFrames).put("afterFirstFrameMs",SystemClock.elapsedRealtime()-clock[0]));
                        }
                        SystemClock.sleep(250);
                    }
                    row.put("observeSeconds",seconds).put("actualObserveMs",SystemClock.elapsedRealtime()-observationStarted)
                            .put("samples",samples).put("maxNoProgressMs",observation.maxNoProgressMs).put("bufferingObservedMs",observation.bufferingMs)
                            .put("maxNoVideoFramesMs",observation.maxNoVideoFramesMs).put("outputFramesAdvanced",observation.outputFrames-observation.firstOutputFrames)
                            .put("recoveries",recoveries).put("frozenPixelAudits",pixelAudits);
                    main(()->{
                        row.put("positionAdvancedMs",observation.advancedMs);boolean audio=false;
                        ITrackInfo[] tracks=player[0].getTrackInfo();if(tracks!=null)for(ITrackInfo t:tracks)if(t.getTrackType()==ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)audio=true;
                        row.put("audioTrackPresent",audio);check(audio,"Audio track missing");
                        check(player[0].isPlaying(),"Playback stopped");call("refreshVideoInfo");Object stats=get("latestPlaybackDebugStats");
                        Method fps=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",stats.getClass());fps.setAccessible(true);
                        VideoStreamDescription description=IjkStreamMetrics.videoDescription(player[0]);
                        row.put("fpsLabel",fps.invoke(null,stats)).put("sourceFps",IjkStreamMetrics.sourceFrameRate(player[0]))
                            .put("outputFps",IjkStreamMetrics.outputFrameRate(player[0])).put("scanType",description.scanType)
                            .put("nativeDecodeFps",player[0].getVideoDecodeFramesPerSecond()).put("nativeOutputFps",player[0].getVideoOutputFramesPerSecond())
                            .put("gpuFps",Float.isNaN(NativeHdrOutput.renderedFrameRate())?JSONObject.NULL:NativeHdrOutput.renderedFrameRate())
                            .put("gpuFrames",NativeHdrOutput.renderedFrameCount())
                            .put("description",description.debugLabel()).put("decoder",IjkStreamMetrics.videoDecoderName(player[0]));
                        check(!"unknown".equals(description.scanType),"Unknown source scan type");check(row.getDouble("sourceFps")>0,"Missing source frame rate");
                    });
                    if(allowRecovery && recoveries.length()>0) {
                        long healthyObservationMs=SystemClock.elapsedRealtime()-observation.startedAt;
                        row.put("afterRecoveryObservedMs",healthyObservationMs);
                        check(recoveries.length()==1,"Repeated playback recovery");
                        check(healthyObservationMs>=20000,"Insufficient observation after recovery");
                        check(row.getDouble("outputFps")>=row.getDouble("sourceFps")*.8,"Recovered output did not become healthy");
                        check(observation.outputFrames-observation.firstOutputFrames>=healthyObservationMs/100,"Recovery did not sustain video output");
                        check(row.getLong("positionAdvancedMs")>=healthyObservationMs*.7,"Recovered playback clock advanced too slowly");
                    }else {
                        check(observation.maxNoProgressMs<3000,"Playback clock stalled for over 3 seconds");
                        check(observation.maxNoVideoFramesMs<3000,"Video output stopped for over 3 seconds");
                        check(row.getLong("positionAdvancedMs")>=seconds*700L,"Playback clock advanced too slowly");
                    }
                    row.put("edgeDelay",edgeEstimate((HlsProxyServer)get("proxy"),player[0]));capture(row,turn+1);row.put("status","passed");
                }catch(Throwable error){failed++;row.put("status","failed").put("error",android.util.Log.getStackTraceString(error));}
                if(row.has("firstFrameMs")) {
                    if(!row.has("edgeDelay"))try{row.put("edgeDelay",edgeEstimate((HlsProxyServer)get("proxy"),(IjkMediaPlayer)get("player")));}catch(Exception ignored){}
                    if(!row.has("captureVisible"))try{capture(row,turn+1);}catch(Throwable error){row.put("captureError",error.toString());}
                }
                save("provider-live.json",results);progress("RESULT "+row.optString("provider")+" "+wanted.name+" "+row.optString("status")+" first="+row.optLong("firstFrameMs",-1)+" ui="+row.optLong("panelCloseDrawMs",-1));
            }
            output.putString("stream","COMPLETE cases="+results.length()+" failures="+failed+"\n");
        }catch(Throwable error){failed++;output.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null)try{stop();main(()->activity.finish());}catch(Exception ignored){}ChannelCatalog.GROUPS=original;}
        finish(failed==0?-1:0,output);
    }
}
