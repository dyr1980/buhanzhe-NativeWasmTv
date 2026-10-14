package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Identical first-frame measurement fixture for 1.6.0 and the working copy. */
public final class ProviderLoadComparisonInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle arguments;
    private Field field(String name) throws Exception {
        Field value=MainActivity.class.getDeclaredField(name); value.setAccessible(true); return value;
    }
    private void optional(String name, Object value) throws Exception {
        try { field(name).set(activity,value); } catch(NoSuchFieldException ignored) { }
    }
    private void invoke(String name) throws Exception {
        Method value=MainActivity.class.getDeclaredMethod(name); value.setAccessible(true); value.invoke(activity);
    }
    private void emit(JSONObject row) {
        Bundle status=new Bundle(); status.putString("sample",row.toString()); sendStatus(0,status);
    }
    private void clearUrlCaches() throws Exception {
        for(String file:new String[]{"cjs-result-cache","live_url_resolver","yangshipin_resolver"}) {
            android.content.SharedPreferences prefs=activity.getSharedPreferences(file,0);
            android.content.SharedPreferences.Editor edit=prefs.edit();
            for(String key:prefs.getAll().keySet()) {
                if(file.equals("cjs-result-cache") || key.startsWith("url_")
                    || key.startsWith("cached_url_") || key.startsWith("cached_at_")) edit.remove(key);
            }
            edit.commit();
        }
        for(String[] names:new String[][]{{"liveUrlResolver","memoryCache"},{"yangshipinResolver","apiCache"}}) {
            try {
                Object resolver=field(names[0]).get(activity);
                Field cache=resolver.getClass().getDeclaredField(names[1]); cache.setAccessible(true);
                java.util.Map<?,?> values=(java.util.Map<?,?>)cache.get(resolver);
                synchronized(values) { values.clear(); }
            }catch(NoSuchFieldException ignored) { }
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            String transfer=arguments.getString("cacheTransfer","");
            if(transfer.length()>0) {
                JSONObject caches=new JSONObject();
                if("import".equals(transfer)) {
                    byte[] payload=android.util.Base64.decode(arguments.getString("cachePayload"),android.util.Base64.NO_WRAP);
                    java.io.InputStream input=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(payload));
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                    try { byte[] buffer=new byte[4096]; int n;
                        while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
                    }finally { input.close(); }
                    caches=new JSONObject(new String(bytes.toByteArray(),"UTF-8"));
                }
                for(String name:new String[]{"yangshipin_resolver","cjs-result-cache"}) {
                    android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences(name,0);
                    if("export".equals(transfer)) {
                        org.json.JSONArray entries=new org.json.JSONArray();
                        for(java.util.Map.Entry<String,?> e:prefs.getAll().entrySet()) entries.put(
                            new JSONObject().put("key",e.getKey()).put("value",e.getValue())
                                .put("kind",e.getValue().getClass().getSimpleName()));
                        caches.put(name,entries);
                    }else {
                        android.content.SharedPreferences.Editor edit=prefs.edit().clear();
                        org.json.JSONArray entries=caches.getJSONArray(name);
                        for(int i=0;i<entries.length();i++) {
                            JSONObject e=entries.getJSONObject(i); String key=e.getString("key"),kind=e.getString("kind");
                            if("Long".equals(kind))edit.putLong(key,e.getLong("value"));
                            else if("Integer".equals(kind))edit.putInt(key,e.getInt("value"));
                            else if("Boolean".equals(kind))edit.putBoolean(key,e.getBoolean("value"));
                            else if("Float".equals(kind))edit.putFloat(key,(float)e.getDouble("value"));
                            else if("String".equals(kind))edit.putString(key,e.getString("value"));
                            else throw new AssertionError("Unsupported cache preference: "+kind);
                        }
                        if(!edit.commit())throw new AssertionError("Cache import failed");
                    }
                }
                if("export".equals(transfer))emit(new JSONObject().put("type","cache").put("payload",caches));
                result.putString("stream","PASS cache "+transfer); finish(-1,result); return;
            }
            // Only isolated benchmark packages are launched by the host script.
            getTargetContext().getSharedPreferences("tv_player",0).edit()
                .putBoolean("auto_switch_source",false).putString("resolution_mode","high")
                .putString("decode_mode","auto").putString("live_delay_mode",
                    arguments.getString("delayMode","stable")).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(4000);
            runOnMainSync(()->{try {
                invoke("closeWebSource"); invoke("releasePlayer");
                optional("pendingCjsChannelIndex",-1);
                try { ((java.util.concurrent.atomic.AtomicInteger)field("catalogLoadGeneration").get(activity)).incrementAndGet(); }
                catch(NoSuchFieldException ignored) { }
            }catch(Exception e){throw new RuntimeException(e);}});
            // Installation is reported separately and never charged to channel loading.
            if("true".equals(arguments.getString("setup"))) {
                try {
                    Class<?> runtime=Class.forName("xiao.bu.tv.CjsPluginRuntime");
                    for(String site:new String[]{"tv.cctv.com","yangshipin.cn"}) {
                        long start=SystemClock.elapsedRealtime();
                        Object installed=runtime.getMethod("isInstalled",String.class).invoke(null,site);
                        if(!Boolean.TRUE.equals(installed)) runtime.getMethod("installOrUpdate",String.class).invoke(null,site);
                        emit(new JSONObject().put("type","setup").put("site",site)
                            .put("elapsedMs",SystemClock.elapsedRealtime()-start)
                            .put("plugin",runtime.getMethod("statusJson").invoke(null)));
                    }
                } catch(ClassNotFoundException ignored) { }
                result.putString("stream","PASS setup"); finish(-1,result); return;
            }
            int batch=Integer.parseInt(arguments.getString("batch","0"));
            String[] ids={"cctv1","cctv5","cctv13"};
            String[] pids={"600001859","600001818","600001811"};
            int count=Integer.parseInt(arguments.getString("count","6"));
            for(int turn=0;turn<count;turn++) {
                boolean fixed="true".equals(arguments.getString("fixedCctv"));
                boolean uncached="true".equals(arguments.getString("uncached"));
                int provider=fixed?1:(turn+batch)%2;
                int channelIndex=((fixed?turn:turn/2)+batch)%3;
                String url=provider==0?"webview://https://yangshipin.cn/tv/home?pid="+pids[channelIndex]
                    :"webview://https://tv.cctv.com/live/"+ids[channelIndex]+"/";
                if(fixed) {
                    String[] sources={
                        "https://ldocctvwbcdbyte.volcfcdn.com/ldocctvwbcd/cdrmldcctv1_1/index.m3u8?b=200-4000",
                        "https://ldcctvwbcdtxy.liveplay.myqcloud.com/ldcctvwbcd/cdrmldcctv5_1/index.m3u8?b=200-4000",
                        "https://ldncctvwbcdbd.a.bdydns.com/ldncctvwbcd/cdrmldcctv13_1/index.m3u8?b=200-4000"};
                    url=arguments.getString("fixedUrl"+channelIndex,sources[channelIndex]);
                }
                Channel channel=new Channel("1",ids[channelIndex],ids[channelIndex],url,null,null);
                final long[] start={0}; final Throwable[] error={null}; final int[] request={0};
                runOnMainSync(()->{try {
                    field("autoSwitchSource").setBoolean(activity,false);
                    field("liveDelayMode").set(activity,arguments.getString("delayMode","stable"));
                    if(uncached) clearUrlCaches();
                    ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group(
                        "PROVIDER_SPEED",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
                    field("currentGroupIndex").setInt(activity,0);
                    field("currentChannelIndex").setInt(activity,0);
                    field("currentSourceIndex").setInt(activity,0);
                    Method method=MainActivity.class.getDeclaredMethod("switchChannel",int.class);
                    method.setAccessible(true); start[0]=SystemClock.elapsedRealtime(); method.invoke(activity,0);
                    request[0]=field("playRequestId").getInt(activity);
                } catch(Throwable e){error[0]=e;}});
                long deadline=start[0]+Long.parseLong(arguments.getString("timeoutMs","60000"));
                final boolean[] ready={false}; final String[] status={""}; String outcome="TIMEOUT";
                while(error[0]==null && SystemClock.elapsedRealtime()<deadline) {
                    runOnMainSync(()->{try {
                        ready[0]=field("videoRenderingStarted").getBoolean(activity)
                            && field("playbackReadyRequestId").getInt(activity)==request[0]
                            && field("playRequestId").getInt(activity)==request[0];
                        status[0]=((TextView)field("statusText").get(activity)).getText().toString();
                    }catch(Throwable e){error[0]=e;}});
                    if(ready[0]) { outcome="FIRST_FRAME"; break; }
                    if(status[0].contains("解析失败") || status[0].contains("脚本执行失败")
                        || status[0].contains("安装失败") || status[0].contains("没有备用线路")) {
                        outcome="FAILED"; break;
                    }
                    SystemClock.sleep(25);
                }
                long elapsed=SystemClock.elapsedRealtime()-start[0];
                JSONObject row=new JSONObject().put("type","sample").put("batch",batch)
                    .put("turn",turn).put("coldProcessSample",turn==0)
                    .put("provider",provider==0?"央视频":"央视网").put("channel",ids[channelIndex])
                    .put("url",url).put("delayMode",arguments.getString("delayMode","stable"))
                    .put("uncached",uncached).put("fixedCctv",fixed)
                    .put("requestId",request[0]).put("status",error[0]==null?outcome:"TEST_ERROR")
                    .put("firstFrameMs",ready[0]?elapsed:JSONObject.NULL).put("elapsedMs",elapsed)
                    .put("statusText",status[0]);
                if(error[0]!=null) row.put("error",android.util.Log.getStackTraceString(error[0]));
                if(ready[0]) {
                    final tv.danmaku.ijk.media.player.IMediaPlayer[] media={null};
                    final long[] position={0};
                    runOnMainSync(()->{try {
                        media[0]=(tv.danmaku.ijk.media.player.IMediaPlayer)field("player").get(activity);
                        row.put("width",media[0].getVideoWidth()).put("height",media[0].getVideoHeight());
                        row.put("activeStreamUrl",field("activePlayerStreamUrl").get(activity));
                        boolean audio=false;
                        tv.danmaku.ijk.media.player.misc.ITrackInfo[] tracks=
                            ((tv.danmaku.ijk.media.player.IjkMediaPlayer)media[0]).getTrackInfo();
                        if(tracks!=null)for(tv.danmaku.ijk.media.player.misc.ITrackInfo track:tracks)
                            if(track.getTrackType()==tv.danmaku.ijk.media.player.misc.ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)audio=true;
                        row.put("audioTrackPresent",audio);
                        position[0]=media[0].getCurrentPosition();
                    }catch(Exception e){throw new RuntimeException(e);}});
                    int seconds=turn==count-1?Integer.parseInt(arguments.getString("observeSeconds","2")):2;
                    long until=SystemClock.elapsedRealtime()+seconds*1000L;
                    final long[] buffering={0};
                    org.json.JSONArray fpsSamples=new org.json.JSONArray();
                    while(SystemClock.elapsedRealtime()<until) {
                        SystemClock.sleep(100);
                        runOnMainSync(()->{try {
                            if(field("buffering").getBoolean(activity))buffering[0]+=100;
                            tv.danmaku.ijk.media.player.IjkMediaPlayer ijk=
                                (tv.danmaku.ijk.media.player.IjkMediaPlayer)media[0];
                            fpsSamples.put(new JSONObject().put("atMs",SystemClock.elapsedRealtime())
                                .put("fps",ijk.getVideoOutputFramesPerSecond()));
                        }
                            catch(Exception e){throw new RuntimeException(e);}});
                    }
                    row.put("observeSeconds",seconds).put("bufferingObservedMs",buffering[0]);
                    row.put("fpsSamples",fpsSamples);
                    runOnMainSync(()->{try {
                        row.put("advancedMs",media[0].getCurrentPosition()-position[0]);
                        tv.danmaku.ijk.media.player.IjkMediaPlayer ijk=
                            (tv.danmaku.ijk.media.player.IjkMediaPlayer)media[0];
                        row.put("nativeOutputFps",ijk.getVideoOutputFramesPerSecond());
                        row.put("decodeFps",ijk.getVideoDecodeFramesPerSecond());
                        try {
                            Class<?> metrics=Class.forName("xiao.bu.tv.IjkStreamMetrics");
                            for(String name:new String[]{"outputFrameRate","outputFrameCount","extendedMetricsAvailable"}) {
                                Method metric=metrics.getDeclaredMethod(name,ijk.getClass()); metric.setAccessible(true);
                                row.put(name,metric.invoke(null,ijk));
                            }
                            Method state=MainActivity.class.getDeclaredMethod("buildLocalMediaState");
                            state.setAccessible(true); row.put("mediaState",state.invoke(activity));
                            Method refresh=MainActivity.class.getDeclaredMethod("refreshVideoInfo");
                            refresh.setAccessible(true); refresh.invoke(activity);
                            Object stats=field("latestPlaybackDebugStats").get(activity);
                            Method format=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",stats.getClass());
                            format.setAccessible(true); row.put("debugFpsLabel",format.invoke(null,stats));
                        }catch(ClassNotFoundException | NoSuchMethodException ignored) { }
                    }
                        catch(Exception e){throw new RuntimeException(e);}});
                    if("true".equals(arguments.getString("expectFps"))) {
                        if(row.optDouble("outputFrameRate")<=0 || row.optDouble("nativeOutputFps")<=0)
                            throw new AssertionError("Rendered frames must produce nonzero live FPS: "+row);
                        runOnMainSync(()->media[0].pause());
                        SystemClock.sleep(200);
                        runOnMainSync(()->{try {
                            tv.danmaku.ijk.media.player.IjkMediaPlayer ijk=
                                (tv.danmaku.ijk.media.player.IjkMediaPlayer)media[0];
                            Class<?> metrics=Class.forName("xiao.bu.tv.IjkStreamMetrics");
                            Method output=metrics.getDeclaredMethod("outputFrameRate",ijk.getClass());
                            output.setAccessible(true);
                            float paused=((Number)output.invoke(null,ijk)).floatValue();
                            row.put("pausedOutputFps",paused);
                            if(paused!=0f || ijk.getVideoOutputFramesPerSecond()!=0f)
                                throw new AssertionError("Paused output must be zero");
                            media[0].start();
                        }catch(Exception e){throw new RuntimeException(e);}});
                    }
                }
                emit(row);
                SystemClock.sleep(500);
            }
            result.putString("stream","PASS measurement completed");
        } catch(Throwable error) { result.putString("stream",android.util.Log.getStackTraceString(error)); }
        finally {
            if(activity!=null) runOnMainSync(()->{try {
                invoke("closeWebSource"); invoke("releasePlayer"); activity.finish();
            }catch(Exception ignored){}});
        }
        finish(-1,result);
    }
}
