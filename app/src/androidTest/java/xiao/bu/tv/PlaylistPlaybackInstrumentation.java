package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaMeta;

/** Replays all captured playlist entries through the real Activity/player. */
public final class PlaylistPlaybackInstrumentation extends Instrumentation {
    private static final class CapabilityLimit extends Exception {}
    private Bundle args;
    private MainActivity activity;
    private final JSONArray results=new JSONArray();

    private void verifyLiveDelayPreferences(File directory) throws Exception {
        SharedPreferences prefs=getTargetContext().getSharedPreferences("regression-live-delay",0);
        Method read=MainActivity.class.getDeclaredMethod("readLiveDelayMode",SharedPreferences.class);
        read.setAccessible(true);
        JSONArray checks=new JSONArray();
        Object[][] cases={
                {"fresh",null,null,"stable"},
                {"1.7 stable","stable",null,"stable"},
                {"1.7 low","low",null,"low"},
                {"1.7 balanced","balanced",null,"balanced"},
                {"v2 only","auto",null,"auto"},
                {"old wins","low","auto","low"},
                {"invalid old","invalid",null,"stable"},
                {"invalid type",Integer.valueOf(2),"low","stable"},
                {"invalid v2",null,"invalid","stable"}
        };
        try {
            for(Object[] test:cases) {
                SharedPreferences.Editor editor=prefs.edit().clear();
                if("v2 only".equals(test[0]))editor.putString("live_delay_mode_v2",(String)test[1]);
                else if(test[1] instanceof String)editor.putString("live_delay_mode",(String)test[1]);
                else if(test[1] instanceof Integer)editor.putInt("live_delay_mode",(Integer)test[1]);
                if(test[2]!=null)editor.putString("live_delay_mode_v2",(String)test[2]);
                if(!editor.commit())throw new AssertionError("Cannot prepare preference fixture");
                String actual=(String)read.invoke(null,prefs);
                if(!test[3].equals(actual) || prefs.contains("live_delay_mode_v2")
                        || !actual.equals(read.invoke(null,prefs)))throw new AssertionError("Live delay migration: "+test[0]);
                if(!"fresh".equals(test[0]) && !actual.equals(prefs.getString("live_delay_mode",null)))
                    throw new AssertionError("Choice not saved under the published key");
                checks.put(new JSONObject().put("name",test[0]).put("mode",actual));
            }
            try(FileOutputStream output=new FileOutputStream(new File(directory,"live-delay-preferences-checks.json"))) {
                output.write(new JSONObject().put("status","passed").put("key","live_delay_mode")
                        .put("default","stable").put("cases",checks).toString(2).getBytes("UTF-8"));
            }
        } finally {prefs.edit().clear().commit();}
    }

    private void verifyProbeManifestReuse(File directory) throws Exception {
        java.net.ServerSocket origin=new java.net.ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        Thread server=new Thread(()->{
            try {
                while(!origin.isClosed())try(java.net.Socket socket=origin.accept()) {
                    java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),"UTF-8"));
                    String request=reader.readLine();
                    String line;while((line=reader.readLine())!=null && !line.isEmpty()) {}
                    requests.incrementAndGet();
                    byte[] body=("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsegment.ts\n#EXT-X-ENDLIST\n").getBytes("UTF-8");
                    if(request!=null && request.contains("/master.m3u8"))body=("#EXTM3U\n"
                            +"#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"description\",DEFAULT=NO,AUTOSELECT=YES,URI=\"description.m3u8\"\n"
                            +"#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"main\",DEFAULT=YES,URI=\"main-audio.m3u8\"\n"
                            +"#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"s\",NAME=\"captions\",URI=\"captions.m3u8\"\n"
                            +"#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\",AUDIO=\"a\",SUBTITLES=\"s\"\nvideo.m3u8\n").getBytes("UTF-8");
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    socket.getOutputStream().write(body);socket.getOutputStream().flush();
                }
            } catch(java.io.IOException closed) { }
        },"test-probe-manifest-origin");server.start();
        Method fetch=HlsProxyServer.class.getDeclaredMethod("fetch",String.class);fetch.setAccessible(true);
        HlsProxyServer probe=new HlsProxyServer(null,false,true,true,0,true,"high",0,0);
        HlsProxyServer normal=new HlsProxyServer(null,false,true,true,0,true,"high",0,0);
        try {
            probe.setMetadataProbeMode();probe.start();normal.start();
            String url="http://127.0.0.1:"+origin.getLocalPort()+"/list.m3u8";
            HlsProxyServer.ProxyResponse first=(HlsProxyServer.ProxyResponse)fetch.invoke(probe,url);
            HlsProxyServer.ProxyResponse second=(HlsProxyServer.ProxyResponse)fetch.invoke(probe,url);
            if(requests.get()!=1 || !java.util.Arrays.equals(first.body,second.body))
                throw new AssertionError("Probe repeated the manifest request");
            fetch.invoke(normal,url);fetch.invoke(normal,url);
            if(requests.get()!=3)throw new AssertionError("Ordinary playback unexpectedly reused a probe manifest");
            String master="http://127.0.0.1:"+origin.getLocalPort()+"/master.m3u8";
            String probeMaster=new String(((HlsProxyServer.ProxyResponse)fetch.invoke(probe,master)).body,"UTF-8");
            String normalMaster=new String(((HlsProxyServer.ProxyResponse)fetch.invoke(normal,master)).body,"UTF-8");
            if(!probeMaster.contains("NAME=\"main\"") || probeMaster.contains("NAME=\"description\"")
                    || probeMaster.contains("TYPE=SUBTITLES") || !normalMaster.contains("NAME=\"description\"")
                    || !normalMaster.contains("TYPE=SUBTITLES"))throw new AssertionError("Probe rendition filtering changed playback tracks");
            try(FileOutputStream output=new FileOutputStream(new File(directory,"probe-manifest-reuse-checks.json"))) {
                output.write(new JSONObject().put("status","passed").put("probeFetches",2)
                        .put("probeOriginRequests",1).put("normalFetches",2).put("normalOriginRequests",2)
                        .put("probeDefaultAudioOnly",true).put("normalTracksPreserved",true)
                        .put("externalRequests",0).toString(2).getBytes("UTF-8"));
            }
        } finally {probe.close();normal.close();origin.close();server.join(2000);}
    }

    /** Exercise protocol selection without depending on a CDN producing a timeout. */
    private void verifyHttp2Fallback(File directory) throws Exception {
        HlsProxyServer proxy=new HlsProxyServer(null,false,true,true,0,true,"high",1,0);
        Method note=HlsProxyServer.class.getDeclaredMethod("noteHttp2Failure",String.class,java.io.IOException.class);
        Method open=HlsProxyServer.class.getDeclaredMethod("openUpstreamConnection",String.class);
        Field hosts=HlsProxyServer.class.getDeclaredField("http1FallbackHosts");
        Field carrier=HlsProxyServer.class.getDeclaredField("carrierIptvSession");
        note.setAccessible(true);open.setAccessible(true);hosts.setAccessible(true);carrier.setAccessible(true);
        Map<?,?> selected=(Map<?,?>)hosts.get(proxy);
        JSONObject checks=new JSONObject();
        try {
            note.invoke(proxy,"https://plain.example.test/a",new java.net.SocketTimeoutException("ordinary timeout"));
            if(!selected.isEmpty())throw new AssertionError("Ordinary timeout changed protocol");
            java.net.SocketTimeoutException timeout=new java.net.SocketTimeoutException("HTTP/2 headers timeout");
            timeout.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                    "okhttp3.internal.http2.Http2Stream","takeHeaders","Http2Stream.java",1)});
            note.invoke(proxy,"https://failed.example.test/a",new java.io.IOException("wrapped",timeout));
            if(!selected.containsKey("failed.example.test") || selected.size()!=1)
                throw new AssertionError("HTTP/2 cause did not select only the failing host");
            HttpURLConnection fallback=(HttpURLConnection)open.invoke(proxy,"https://failed.example.test/b");
            HttpURLConnection normal=(HttpURLConnection)open.invoke(proxy,"https://other.example.test/b");
            try {
                Field delegate=fallback.getClass().getDeclaredField("delegate");delegate.setAccessible(true);
                Object failedTransport=delegate.get(fallback),normalTransport=delegate.get(normal);
                if(!failedTransport.getClass().getSimpleName().equals("GithubConnection")
                        || failedTransport.getClass().equals(normalTransport.getClass()))
                    throw new AssertionError("HTTP/1.1 fallback connection was not selected");
                Field transport=NetworkClient.class.getDeclaredField("http1Factory");transport.setAccessible(true);
                java.util.List<okhttp3.Protocol> protocols=((okhttp3.OkUrlFactory)transport.get(null)).client().protocols();
                if(protocols.size()!=1 || protocols.get(0)!=okhttp3.Protocol.HTTP_1_1)
                    throw new AssertionError("Fallback transport still negotiates HTTP/2");
                checks.put("fallbackConnection",failedTransport.getClass().getName())
                        .put("unaffectedConnection",normalTransport.getClass().getName()).put("fallbackProtocols",protocols.toString());
            } finally {fallback.disconnect();normal.disconnect();}
            carrier.setBoolean(proxy,true);
            note.invoke(proxy,"https://carrier.example.test/a",timeout);
            if(selected.size()!=1)throw new AssertionError("Carrier route changed protocol");
            checks.put("status","passed").put("ordinaryTimeoutUnaffected",true)
                    .put("wrappedHttp2TimeoutSelectedHost",true).put("otherHostUnaffected",true)
                    .put("carrierRouteUnaffected",true).put("networkRequests",0);
            try(FileOutputStream output=new FileOutputStream(new File(directory,"http2-fallback-checks.json"))) {
                output.write(checks.toString(2).getBytes("UTF-8"));
            }
        } finally {proxy.close();}
    }

    private void saveCodecCapabilities(File directory) throws Exception {
        JSONArray codecs=new JSONArray();
        for(int i=0;android.os.Build.VERSION.SDK_INT>=16 && i<android.media.MediaCodecList.getCodecCount();i++) {
            android.media.MediaCodecInfo info=android.media.MediaCodecList.getCodecInfoAt(i);
            if(info.isEncoder())continue;
            JSONObject codec=new JSONObject().put("name",info.getName());
            JSONArray types=new JSONArray();
            for(String type:info.getSupportedTypes()) {
                JSONObject entry=new JSONObject().put("mime",type);
                try {
                    JSONArray profiles=new JSONArray();
                    for(android.media.MediaCodecInfo.CodecProfileLevel profile:info.getCapabilitiesForType(type).profileLevels)
                        profiles.put(new JSONObject().put("profile",profile.profile).put("level",profile.level));
                    entry.put("profiles",profiles);
                } catch(RuntimeException unsupported) {entry.put("error",unsupported.toString());}
                types.put(entry);
            }
            codecs.put(codec.put("types",types));
        }
        try(FileOutputStream output=new FileOutputStream(new File(directory,"codec-capabilities.json"))) {
            output.write(codecs.toString(2).getBytes("UTF-8"));
        }
    }

    private Field field(String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private Object stat(Object stats,String name) throws Exception {
        Field field=stats.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(stats);
    }
    private String readUrl(String url) throws Exception {
        HttpURLConnection connection=null;
        if(!url.startsWith("/")) {
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        }
        try(InputStream input=connection==null ? new java.io.FileInputStream(url) : connection.getInputStream()) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while((n=input.read(buffer))>0)bytes.write(buffer,0,n);
            return bytes.toString("UTF-8");
        } finally {if(connection!=null)connection.disconnect();}
    }
    private void save(File target) throws Exception {
        try(FileOutputStream output=new FileOutputStream(target)) {
            output.write(results.toString(2).getBytes("UTF-8"));
        }
    }
    private void savePending(File target,JSONObject row) throws Exception {
        JSONArray checkpoint=new JSONArray(results.toString());
        checkpoint.put(new JSONObject(row.toString()).put("status","running"));
        try(FileOutputStream output=new FileOutputStream(target)) {
            output.write(checkpoint.toString(2).getBytes("UTF-8"));
        }
    }
    private void captureVideo(JSONObject row,File report,int index)throws Exception {
        DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
        boolean pixelCopy=android.os.Build.VERSION.SDK_INT>=26;
        java.util.ArrayList<android.view.View> hidden=new java.util.ArrayList<>();
        if(!pixelCopy)runOnMainSync(() -> {
            android.view.ViewGroup root=(android.view.ViewGroup)view.getParent();
            for(int i=0;i<root.getChildCount();i++) {
                android.view.View child=root.getChildAt(i);
                if(child!=view && child.getVisibility()==android.view.View.VISIBLE) {
                    hidden.add(child);child.setVisibility(android.view.View.INVISIBLE);
                }
            }
        });
        Bitmap image;
        try {
            if(!pixelCopy)SystemClock.sleep(200);
            if(pixelCopy) image=SurfaceCapture.copy(view);
            else if(android.os.Build.VERSION.SDK_INT>=18) image=getUiAutomation().takeScreenshot();
            else {
                String endpoint=args.getString("captureEndpoint");
                if(endpoint==null)throw new AssertionError("API 14-17 requires the ADB screenshot bridge");
                HttpURLConnection connection=(HttpURLConnection)new URL(endpoint+"?index="+index).openConnection();
                connection.setConnectTimeout(10000);connection.setReadTimeout(30000);
                try(InputStream input=connection.getInputStream()) {image=android.graphics.BitmapFactory.decodeStream(input);}
                finally {connection.disconnect();}
            }
        } finally {
            if(!pixelCopy)runOnMainSync(() -> {
                for(android.view.View child:hidden)if(child.getVisibility()==android.view.View.INVISIBLE)
                    child.setVisibility(android.view.View.VISIBLE);
            });
        }
        if(image==null)throw new AssertionError("No screenshot");
        int visible=0;
        // Scan the whole video: real intros may be black except for a caption
        // near the bottom. UI text has been excluded from the old-API capture.
        for(int y=image.getHeight()/20;y<image.getHeight()*19/20;y+=12)
            for(int x=image.getWidth()/20;x<image.getWidth()*19/20;x+=12)
                if((image.getPixel(x,y)&0xffffff)!=0)visible++;
        row.put("captureVisible",visible>5).put("captureMethod",pixelCopy?"PixelCopy Surface"
                :android.os.Build.VERSION.SDK_INT>=18?"UiAutomation with UI hidden":"ADB screencap with UI hidden");
        File capture=new File(report.getParentFile(),String.format(java.util.Locale.US,"playlist-%04d.png",index));
        try(FileOutputStream output=new FileOutputStream(capture)){image.compress(Bitmap.CompressFormat.PNG,100,output);}
        image.recycle();
        if(visible<=5)throw new AssertionError("Video-only capture is black");
    }
    private static int processPssKb() {
        android.os.Debug.MemoryInfo memory=new android.os.Debug.MemoryInfo();
        android.os.Debug.getMemoryInfo(memory);
        return memory.getTotalPss();
    }
    private void checkSystemPlayer(JSONObject row,JSONObject item,File report,int index)throws Exception {
        tv.danmaku.ijk.media.player.IMediaPlayer player=(tv.danmaku.ijk.media.player.IMediaPlayer)field("player").get(activity);
        long began=SystemClock.elapsedRealtime(),lastPosition=player.getCurrentPosition(),advanced=0;
        while(SystemClock.elapsedRealtime()-began<item.optLong("sampleMs",5500)) {
            if(field("player").get(activity)!=player || !player.isPlaying())throw new AssertionError("System playback stopped or replaced");
            if(!activity.hasWindowFocus())throw new AssertionError("Lost foreground");
            long position=player.getCurrentPosition();advanced+=Math.max(0,position-lastPosition);lastPosition=position;
            SystemClock.sleep(100);
        }
        if(advanced<1000)throw new AssertionError("System playback clock did not advance");
        captureVideo(row,report,index);row.put("playbackStatus","played");
        if(item.optBoolean("availabilityOnly")) {
            row.put("positionAdvancedMs",advanced).put("status","played")
                    .put("metadataStatus","notChecked").put("resolvedUrl",field("activePlayerStreamUrl").get(activity))
                    .put("width",field("videoWidth").getInt(activity)).put("height",field("videoHeight").getInt(activity))
                    .put("decoderType",player instanceof tv.danmaku.ijk.media.player.AndroidMediaPlayer ? "system" : "ijk");
            return;
        }
        Method collect=MainActivity.class.getDeclaredMethod("collectPlaybackStreamStats",float.class);collect.setAccessible(true);
        Object stats=collect.invoke(activity,0f);
        Method format=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",stats.getClass());format.setAccessible(true);
        VideoStreamDescription description=(VideoStreamDescription)stat(stats,"videoDescription");
        float source=((Number)stat(stats,"nominalFrameRate")).floatValue();
        int width=((Number)stat(stats,"width")).intValue(),height=((Number)stat(stats,"height")).intValue();
        row.put("decoderType","system").put("decoder","Android MediaPlayer (implementation unavailable)")
                .put("width",width).put("height",height).put("positionAdvancedMs",advanced)
                .put("sourceFps",source).put("outputFps",JSONObject.NULL).put("fpsLabel",format.invoke(null,stats))
                .put("scanType",description.scanType).put("bitDepth",description.bitDepth).put("transfer",description.colorTransfer)
                .put("videoCodec",stat(stats,"videoCodec")).put("audioCodec",stat(stats,"audioCodec"))
                .put("processPssKb",processPssKb()).put("outputMetricsAvailable",false);
        if(item.has("width") && (width!=item.getInt("width") || height!=item.getInt("height")))throw new AssertionError("System source dimensions differ");
        if(item.has("expectedVideoCodec") && !item.getString("expectedVideoCodec").equals(stat(stats,"videoCodec")))throw new AssertionError("System source video codec differs");
        if(item.has("expectedAudioCodec") && !item.getString("expectedAudioCodec").equals(stat(stats,"audioCodec")))throw new AssertionError("System source audio codec differs");
        if(item.has("expectedSourceFps") && Math.abs(source-item.getDouble("expectedSourceFps"))>.5)throw new AssertionError("System source frame cadence differs");
        if(item.has("expectedScanType") && !item.getString("expectedScanType").equals(description.scanType))throw new AssertionError("System source scan type differs");
        row.put("metadataStatus","passed").put("status","played");
    }
    @android.annotation.TargetApi(26)
    private static final class SurfaceCapture {
        static Bitmap copy(DirectVideoView view)throws Exception {
            Bitmap image=Bitmap.createBitmap(Math.max(1,view.getWidth()),
                    Math.max(1,view.getHeight()),Bitmap.Config.ARGB_8888);
            java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);
            int[] status={-1};
            android.view.PixelCopy.request(view.getSurfaceView(),image,value->{status[0]=value;ready.countDown();},
                    new android.os.Handler(android.os.Looper.getMainLooper()));
            if(!ready.await(5,java.util.concurrent.TimeUnit.SECONDS) || status[0]!=android.view.PixelCopy.SUCCESS) {
                image.recycle();throw new AssertionError("Video Surface capture failed: "+status[0]);
            }
            return image;
        }
    }
    @Override public void onCreate(Bundle args) {super.onCreate(args);this.args=args;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int failures=0,probeFailures=0,unavailable=0;
        SharedPreferences prefs=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=prefs.getAll();ChannelCatalog.Group[] savedGroups=ChannelCatalog.GROUPS;
        File report=new File(getTargetContext().getExternalFilesDir(null),args.getString("reportName","playlist-playback.json"));
        try {
            saveCodecCapabilities(report.getParentFile());
            verifyLiveDelayPreferences(report.getParentFile());
            verifyHttp2Fallback(report.getParentFile());
            verifyProbeManifestReuse(report.getParentFile());
            JSONArray manifest=new JSONArray(readUrl(args.getString("manifest")));
            if(args.containsKey("playlist")) {
                Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);
                String controls="#EXTM3U\n"
                        +"#EXTINF:-1 tvg-id=\"family\",CDTV1\nhttp://example.test/1\n"
                        +"#EXTINF:-1 tvg-id=\"family\",CDTV2\nhttp://example.test/2\n"
                        +"#EXTINF:-1 tvg-id=\"family\",CDTV1 HD\nhttp://example.test/1-backup\n"
                        +"#EXTINF:-1 tvg-id=\"family\",CDTV1\nhttp://example.test/1\n"
                        +"#EXTINF:-1 tvg-id=\"cctv1\",CCTV-1\nhttp://example.test/cctv1\n"
                        +"#EXTINF:-1 tvg-id=\"cctv1\",CCTV-1 4K\nhttp://example.test/cctv1-4k\n";
                ChannelCatalog.Group[] control=(ChannelCatalog.Group[])parse.invoke(null,(Object)controls.getBytes("UTF-8"));
                if(control.length!=1 || control[0].channels.length!=4 || control[0].channels[0].urls.length!=2
                        || control[0].channels[1].urls.length!=1)
                    throw new AssertionError("EPG identity, variant or backup-source merge regression");
                ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,
                        (Object)readUrl(args.getString("playlist")).getBytes("UTF-8"));
                java.util.Set<String> parsed=new java.util.HashSet<>();int count=0;
                for(ChannelCatalog.Group group:groups) for(Channel channel:group.channels) {
                    count++;parsed.add(channel.name+"\n"+channel.url);
                }
                if(count!=manifest.length())throw new AssertionError("Playlist channel count: "+count);
                for(int i=0;i<manifest.length();i++) {
                    JSONObject row=manifest.getJSONObject(i);
                    if(!parsed.contains(row.getString("name")+"\n"+row.getString("sourceUrl")))
                        throw new AssertionError("Playlist name/URL mismatch: "+row.getString("name"));
                }
                Bundle progress=new Bundle();progress.putString("stream","playlistParsed="+count+" names/URLs matched\n");
                sendStatus(0,progress);
            }
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2000);
            if(!saved.containsKey("live_delay_mode") && !saved.containsKey("live_delay_mode_v2")
                    && !"stable".equals(field("liveDelayMode").get(activity)))
                throw new AssertionError("Fresh player did not use stable live delay");
            DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
            long deadline=SystemClock.elapsedRealtime()+10000;
            while(!view.isSurfaceReady()) {
                if(SystemClock.elapsedRealtime()>deadline)throw new AssertionError("No Surface");
                SystemClock.sleep(100);
            }
            Method play=MainActivity.class.getDeclaredMethod("startIjkPlayer",Channel.class,String.class,
                    boolean.class,boolean.class,int[].class,long.class);play.setAccessible(true);
            Method startChannel=MainActivity.class.getDeclaredMethod("startChannel",int.class);startChannel.setAccessible(true);
            final boolean resolve="true".equals(args.getString("routeThroughResolver"));
            int first=Integer.parseInt(args.getString("first","1"));
            int last=Integer.parseInt(args.getString("last",String.valueOf(Integer.MAX_VALUE)));
            String only=","+args.getString("onlyIndices","")+",";
            for(int i=0;i<manifest.length();i++) {
                JSONObject item=manifest.getJSONObject(i);int index=item.getInt("index");
                if(index<first || index>last)continue;
                if(!",,".equals(only) && !only.contains(","+index+","))continue;
                JSONObject row=new JSONObject().put("index",index).put("name",item.getString("name"));
                row.put("liveDelayMode",field("liveDelayMode").get(activity));
                boolean foregroundInterrupted=false;
                long start=SystemClock.elapsedRealtime();
                if(!"decoded".equals(item.optString("sourceStatus","decoded"))) {
                    unavailable++;
                    row.put("status","sourceUnavailable").put("error",item.optString("sourceError"));
                    results.put(row);save(report);
                    Bundle progress=new Bundle();progress.putString("stream",index+" "+item.getString("name")+" sourceUnavailable\n");
                    sendStatus(0,progress);continue;
                }
                try {
                    android.app.ActivityManager manager=(android.app.ActivityManager)activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
                    android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
                    manager.getMemoryInfo(memory);
                    row.put("systemAvailableBeforeProbeKb",memory.availMem/1024)
                            .put("systemLowMemoryBeforeProbe",memory.lowMemory);
                    if(item.optBoolean("verbosePlayer")) {
                        IjkMediaPlayer.loadLibrariesOnce(null);
                        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_VERBOSE);
                    }
                    String url=item.getString("fixture");
                    if(item.optBoolean("checkResourceProbe")) {
                        long probeStarted=SystemClock.elapsedRealtime();
                        SniffedMediaProbe resourceProbe=new SniffedMediaProbe();
                        final SniffedMediaProbe.Result[] found=new SniffedMediaProbe.Result[1];
                        final java.util.concurrent.CountDownLatch completed=new java.util.concurrent.CountDownLatch(1);
                        try {
                            resourceProbe.submit(url,"","nTv", "", value->{found[0]=value;completed.countDown();});
                            if(!completed.await(15,java.util.concurrent.TimeUnit.SECONDS))
                                row.put("probeStatus","failed").put("probeError","Probe callback timeout");
                            else {
                                SniffedMediaProbe.Result value=found[0];
                                row.put("probeResult",value.json());
                                boolean correct="ready".equals(value.status)
                                        && (!item.has("width") || value.width==item.getInt("width") && value.height==item.getInt("height"))
                                        && (!item.has("expectedMinProbeBitrate") || value.bitrate>=item.getLong("expectedMinProbeBitrate"));
                                row.put("probeStatus",correct?"passed":"failed");
                                if(!correct)row.put("probeError",value.error.length()>0?value.error:"Probe dimensions/status differ");
                            }
                        } finally {resourceProbe.close();}
                        row.put("probeMs",SystemClock.elapsedRealtime()-probeStarted);
                        if(!"passed".equals(row.optString("probeStatus")))probeFailures++;
                        Bundle probeProgress=new Bundle();
                        probeProgress.putString("stream",index+" "+item.getString("name")+" probe="+row.getString("probeStatus")+"\n");
                        sendStatus(0,probeProgress);
                        start=SystemClock.elapsedRealtime();
                    }
                    savePending(report,row);
                    Channel channel=new Channel(String.valueOf(index),item.getString("name"),"playlist-test",url,null,null);
                    final java.util.concurrent.CountDownLatch lockHeld=new java.util.concurrent.CountDownLatch(1);
                    final java.util.concurrent.CountDownLatch unlock=new java.util.concurrent.CountDownLatch(1);
                    if(item.optBoolean("checkUiResponsiveness")) {
                        Field lock=HlsProxyServer.class.getDeclaredField("CMG_DECRYPT_LOCK");lock.setAccessible(true);
                        final Object decryptLock=lock.get(null);
                        new Thread(()->{synchronized(decryptLock) {
                            lockHeld.countDown();
                            try {unlock.await(2500,java.util.concurrent.TimeUnit.MILLISECONDS);}
                            catch(InterruptedException e){Thread.currentThread().interrupt();}
                        }},"test-busy-old-decrypt").start();
                        if(!lockHeld.await(2,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("No held decrypt lock");
                    }
                    long selectionStarted=SystemClock.elapsedRealtime();
                    runOnMainSync(() -> {try {
                        ((java.util.concurrent.atomic.AtomicInteger)field("catalogLoadGeneration").get(activity)).incrementAndGet();
                        // Invalidate the Activity's asynchronous startup resolver before
                        // replacing its channel; it must not replace a test fixture.
                        field("playRequestId").setInt(activity,field("playRequestId").getInt(activity)+1);
                        field("autoSwitchSource").setBoolean(activity,false);
                        field("hdrMode").set(activity,item.optString("hdrMode","hardware"));
                        field("decodeMode").set(activity,item.optString("decodeMode","hardware"));
                        ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("PLAYLIST_TEST",
                                ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
                        field("currentGroupIndex").setInt(activity,0);field("currentChannelIndex").setInt(activity,0);
                        field("currentSourceIndex").setInt(activity,0);
                        if(item.optBoolean("checkUiResponsiveness")) {
                            field("proxyStatefulCmgSource").setBoolean(activity,true);
                            field("browsingGroupIndex").setInt(activity,0);
                            ((android.view.View)field("channelListPanel").get(activity)).setVisibility(android.view.View.VISIBLE);
                            Method select=MainActivity.class.getDeclaredMethod("switchBrowsingChannel",int.class);select.setAccessible(true);
                            select.invoke(activity,0);
                            if(((android.view.View)field("channelListPanel").get(activity)).getVisibility()!=android.view.View.GONE)
                                throw new AssertionError("Channel list still visible after selection");
                        } else if(resolve)startChannel.invoke(activity,0);
                        else play.invoke(activity,channel,url,"software".equals(item.optString("decodeMode")),true,null,0L);
                    }catch(Exception error){throw new RuntimeException(error);}});
                    unlock.countDown();
                    if(item.optBoolean("checkUiResponsiveness")) {
                        long latency=SystemClock.elapsedRealtime()-selectionStarted;row.put("uiSwitchMs",latency);
                        if(latency>500)throw new AssertionError("UI selection blocked behind decrypt lock: "+latency+"ms");
                    }
                    deadline=SystemClock.elapsedRealtime()+(android.os.Build.VERSION.SDK_INT<=15?50000:20000);
                    while(!field("videoRenderingStarted").getBoolean(activity)
                            || field("preparingChannelSwitch").getBoolean(activity)
                            || (resolve ? field("activePlayerChannel").get(activity)!=channel
                                    : !url.equals(field("activePlayerStreamUrl").get(activity)))) {
                        final String[] playbackMessage = {""};
                        runOnMainSync(() -> {try {
                            playbackMessage[0] = ((android.widget.TextView)field("statusText").get(activity)).getText().toString();
                        } catch(Exception error) {throw new RuntimeException(error);}});
                        if(playbackMessage[0].startsWith("设备内存不足以软解此分辨率")) {
                            int limit = field("maxSoftwareVideoPixels").getInt(activity);
                            if(limit <= 0 || item.has("width") && (long)item.getInt("width")*item.getInt("height")<=limit)
                                throw new AssertionError("Unexpected software memory rejection");
                            if(field("player").get(activity)!=null)throw new AssertionError("Rejected decoder was not released");
                            row.put("capabilityMessage",playbackMessage[0]).put("softwarePixelLimit",limit)
                                    .put("rejectionMs",SystemClock.elapsedRealtime()-start);
                            throw new CapabilityLimit();
                        }
                        if(field("player").get(activity)==null && playbackMessage[0].startsWith("系统播放器出错"))
                            throw new AssertionError(playbackMessage[0]);
                        if(!activity.hasWindowFocus())throw new AssertionError("Lost foreground before first frame");
                        if(SystemClock.elapsedRealtime()>deadline)throw new AssertionError("First frame timeout");
                        SystemClock.sleep(100);
                    }
                    row.put("firstFrameMs",SystemClock.elapsedRealtime()-start);
                    savePending(report,row);
                    if(resolve ? field("activePlayerChannel").get(activity)!=channel
                            : !url.equals(field("activePlayerStreamUrl").get(activity)))
                        throw new AssertionError("Startup request replaced the fixture URL");
                    if(item.optBoolean("availabilityOnly") || field("player").get(activity) instanceof tv.danmaku.ijk.media.player.AndroidMediaPlayer) {
                        checkSystemPlayer(row,item,report,index);
                        row.put("elapsedMs",SystemClock.elapsedRealtime()-start);results.put(row);save(report);
                        Bundle progress=new Bundle();progress.putString("stream",index+" "+item.getString("name")+" played ("
                                +(item.optBoolean("availabilityOnly") ? "availability" : "system")+")\n");sendStatus(0,progress);
                        continue;
                    }
                    IjkMediaPlayer player=(IjkMediaPlayer)field("player").get(activity);
                    long beginPosition=player.getCurrentPosition();
                    deadline=SystemClock.elapsedRealtime()+Math.max(10000,item.optLong("sampleMs",2000)+5000);
                    // TS audio can advance before enough video has arrived to
                    // publish the first cadence sample. Require both signals.
                    long samplingStart=SystemClock.elapsedRealtime();
                    long lastSample=samplingStart,lastPosition=beginPosition;
                    long lastProgressAt=samplingStart,progressPosition=beginPosition,maxProgressGap=0;
                    org.json.JSONArray progressSamples=new org.json.JSONArray();
                    row.put("progressSamples",progressSamples);
                    while(player.getCurrentPosition()-beginPosition<1000
                            || SystemClock.elapsedRealtime()-samplingStart<item.optLong("sampleMs",2000)
                            || !(IjkStreamMetrics.outputFrameRate(player)>0)) {
                        if(field("player").get(activity)!=player)throw new AssertionError("Player replaced");
                        if(!activity.hasWindowFocus())throw new AssertionError("Lost foreground");
                        if(SystemClock.elapsedRealtime()>deadline)throw new AssertionError("Playback stalled");
                        long sampleTime=SystemClock.elapsedRealtime();
                        long currentPosition=player.getCurrentPosition();
                        if(currentPosition>progressPosition) {
                            maxProgressGap=Math.max(maxProgressGap,sampleTime-lastProgressAt);
                            lastProgressAt=sampleTime;progressPosition=currentPosition;
                        }
                        if(item.has("maxNoProgressMs") && sampleTime-lastProgressAt>item.getLong("maxNoProgressMs"))
                            throw new AssertionError("Playback clock stopped for "+(sampleTime-lastProgressAt)+"ms");
                        if(sampleTime-lastSample>=1000) {
                            long position=player.getCurrentPosition();
                            progressSamples.put(new JSONObject().put("elapsedMs",sampleTime-samplingStart)
                                    .put("advancedMs",position-lastPosition)
                                    .put("outputFps",IjkStreamMetrics.outputFrameRate(player)));
                            lastPosition=position;lastSample=sampleTime;
                        }
                        SystemClock.sleep(100);
                    }
                    row.put("maxProgressGapMs",Math.max(maxProgressGap,SystemClock.elapsedRealtime()-lastProgressAt));
                    float fps=IjkStreamMetrics.outputFrameRate(player);
                    if(!(fps>0))throw new AssertionError("No advancing output frames");
                    row.put("outputFps",fps).put("positionAdvancedMs",player.getCurrentPosition()-beginPosition)
                            .put("width",player.getVideoWidth()).put("height",player.getVideoHeight())
                            .put("decoder",IjkStreamMetrics.videoDecoderName(player))
                            .put("decoderType",player.getVideoDecoder()==IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC?"hardware":"software");
                    savePending(report,row);
                    // Verify actual video before metadata assertions, so missing
                    // header fields are not reported as a failure to play the video.
                    captureVideo(row,report,index);
                    row.put("playbackStatus","played");
                    VideoStreamDescription description=IjkStreamMetrics.videoDescription(player);
                    row.put("scanType",description.scanType).put("bitDepth",description.bitDepth)
                            .put("transfer",description.colorTransfer).put("primaries",description.colorPrimaries)
                            .put("sourceFps",IjkStreamMetrics.sourceFrameRate(player));
                    String deinterlace=IjkStreamMetrics.deinterlaceStatus(player,description);
                    float sourceFps=IjkStreamMetrics.sourceFrameRate(player);
                    Method collect=MainActivity.class.getDeclaredMethod("collectPlaybackStreamStats",float.class);
                    collect.setAccessible(true);Object debugStats=collect.invoke(activity,fps);
                    Method format=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",debugStats.getClass());
                    format.setAccessible(true);String fpsLabel=(String)format.invoke(null,debugStats);
                    if(item.optBoolean("checkDebugInfo")) {
                        final Object[] snapshot=new Object[1];final String[] overlay=new String[1];
                        runOnMainSync(()->{try {
                            field("showDebugInfo").setBoolean(activity,true);
                            field("showColorDebugInfo").setBoolean(activity,true);
                            Method visibility=MainActivity.class.getDeclaredMethod("applyDebugInfoVisibility");
                            visibility.setAccessible(true);visibility.invoke(activity);
                            Method refresh=MainActivity.class.getDeclaredMethod("refreshVideoInfo");
                            refresh.setAccessible(true);refresh.invoke(activity);
                            snapshot[0]=field("latestPlaybackDebugStats").get(activity);
                            overlay[0]=((android.widget.TextView)field("debugInfoOverlay").get(activity)).getText().toString();
                        }catch(Exception e){throw new RuntimeException(e);}});
                        Object hud=snapshot[0];
                        String videoCodec=(String)stat(hud,"videoCodec");
                        String audioCodec=(String)stat(hud,"audioCodec");
                        long videoBitrate=(Long)stat(hud,"videoBitrate"),audioBitrate=(Long)stat(hud,"audioBitrate");
                        row.put("hudText",overlay[0]).put("videoCodec",videoCodec).put("audioCodec",audioCodec)
                                .put("videoBitrate",videoBitrate).put("audioBitrate",audioBitrate);
                        if(item.has("expectedVideoCodec") && !item.getString("expectedVideoCodec").equals(videoCodec))
                            throw new AssertionError("Debug video codec: "+videoCodec);
                        if(item.has("expectedAudioCodec") && !item.getString("expectedAudioCodec").equals(audioCodec))
                            throw new AssertionError("Debug audio codec: "+audioCodec);
                        if(!overlay[0].contains(player.getVideoWidth()+"x"+player.getVideoHeight())
                                || !overlay[0].contains(" fps") || !overlay[0].contains(audioCodec))
                            throw new AssertionError("Actual HUD missing stream fields: "+overlay[0]);
                        for(String type:new String[]{"Video","Audio"}) {
                            String key="expected"+type+"Bitrate";
                            if(!item.has(key))continue;
                            long actual="Video".equals(type)?videoBitrate:audioBitrate;
                            double expected=item.getDouble(key);
                            double tolerance=item.optDouble("bitrateTolerance",0.4);
                            if(actual<=0 || Math.abs(actual-expected)>expected*tolerance)
                                throw new AssertionError("Debug "+type+" bitrate "+actual+" vs reference "+expected);
                        }
                        IjkMediaMeta meta=IjkMediaMeta.parse(player.getMediaMeta());
                        if(meta!=null && meta.mAudioStream!=null) {
                            row.put("audioSampleRate",meta.mAudioStream.mSampleRate)
                                    .put("audioChannelLayout",meta.mAudioStream.mChannelLayout)
                                    .put("audioChannels",Long.bitCount(meta.mAudioStream.mChannelLayout));
                            if(item.has("expectedSampleRate") && item.getInt("expectedSampleRate")!=meta.mAudioStream.mSampleRate)
                                throw new AssertionError("Audio sample rate differs");
                            if(item.has("expectedAudioChannels") && item.getInt("expectedAudioChannels")!=Long.bitCount(meta.mAudioStream.mChannelLayout))
                                throw new AssertionError("Audio channel count differs");
                        }
                    }
                    String expectedFpsLabel=Math.round(fps)+"/"
                            +(sourceFps>0.01f ? String.valueOf(Math.round(sourceFps
                                    *("interlaced".equals(description.scanType)?2f:1f))) : "--")+" fps";
                    if(!expectedFpsLabel.equals(fpsLabel))
                        throw new AssertionError("Output/source cadence label differs: "+fpsLabel
                                +" expected "+expectedFpsLabel);
                    if(item.has("expectedFpsLabel") && !item.getString("expectedFpsLabel").equals(fpsLabel))
                        throw new AssertionError("Fixture FPS label differs: "+fpsLabel);
                    if(item.has("expectedTransfer") && !item.getString("expectedTransfer").equals(description.colorTransfer))
                        throw new AssertionError("Transfer signalling differs: "+description.colorTransfer);
                    if(item.has("expectedBitDepth") && item.getInt("expectedBitDepth")!=description.bitDepth)
                        throw new AssertionError("Source bit depth differs: "+description.bitDepth);
                    if(item.has("expectedInferred") && item.getBoolean("expectedInferred")!=description.colorInferred)
                        throw new AssertionError("Colour provenance differs");
                    if(item.has("expectedPrimaries") && !item.getString("expectedPrimaries").equals(description.colorPrimaries))
                        throw new AssertionError("Primaries differ: "+description.colorPrimaries);
                    if(item.has("expectedGpuTransfer")) {
                        Field active=NativeHdrOutput.class.getDeclaredField("active");active.setAccessible(true);
                        Object bridge=active.get(null);
                        Field transfer=NativeHdrOutput.class.getDeclaredField("transfer");transfer.setAccessible(true);
                        if(bridge==null || transfer.getInt(bridge)!=item.getInt("expectedGpuTransfer"))
                            throw new AssertionError("GPU was not configured with the source HDR transfer");
                        row.put("gpuTransfer",transfer.getInt(bridge));
                    }
                    if(item.has("width") && (item.getInt("width")!=player.getVideoWidth()
                            || item.getInt("height")!=player.getVideoHeight()))
                        throw new AssertionError("Decoded dimensions differ from the captured source");
                    if(item.optBoolean("hasAudio") && player.getMediaInfo().mAudioDecoderImpl==null)
                        throw new AssertionError("Source audio has no decoder");
                    row.put("decoder",IjkStreamMetrics.videoDecoderName(player))
                            .put("cpuPercent",field("cachedCpuUsage").getFloat(activity))
                            .put("processPssKb",processPssKb())
                            .put("nativeHeapKb",android.os.Debug.getNativeHeapAllocatedSize()/1024)
                            .put("resolvedUrl",String.valueOf(field("activePlayerStreamUrl").get(activity)))
                            .put("decoderType",player.getVideoDecoder()==IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC ? "hardware" : "software")
                            .put("audioDecoder",String.valueOf(player.getMediaInfo().mAudioDecoderImpl))
                            .put("width",player.getVideoWidth()).put("height",player.getVideoHeight())
                            .put("dynamicRange",description.dynamicRange).put("bitDepth",description.bitDepth)
                            .put("primaries",description.colorPrimaries).put("transfer",description.colorTransfer)
                            .put("colorInferred",description.colorInferred).put("debugLabel",description.debugLabel())
                            .put("outputFps",fps).put("positionAdvancedMs",player.getCurrentPosition()-beginPosition)
                            .put("sourceFps",sourceFps).put("scanType",description.scanType).put("fpsLabel",fpsLabel)
                            .put("deinterlace",deinterlace)
                            .put("gpuFrames",NativeHdrOutput.renderedFrameCount());
                    String route=NativeHdrOutput.outputRoute(player.getVideoDecoder()==IjkMediaPlayer.FFP_PROPV_DECODER_MEDIACODEC);
                    row.put("colorOutput",route).put("decoderColor",NativeHdrOutput.decoderColor());
                    if(item.has("expectedColorOutput") && !item.getString("expectedColorOutput").equals(route))
                        throw new AssertionError("Colour output route differs: "+route);
                    // The video-only capture already passed above. Do not ask
                    // legacy SurfaceFlinger to allocate a second screenshot;
                    // HUD text/layout assertions are checked independently.
                    if(item.has("expectedDeinterlace") && !item.getString("expectedDeinterlace").equals(deinterlace))
                        throw new AssertionError("Deinterlace route differs: "+deinterlace);
                    if(item.has("expectedSourceFps") && Math.abs(sourceFps-item.getDouble("expectedSourceFps"))>0.5)
                        throw new AssertionError("Source frame cadence differs: "+sourceFps);
                    if(item.has("minOutputFps") && fps<item.getDouble("minOutputFps"))
                        throw new AssertionError("Output cadence too low: "+fps);
                    if(item.has("expectedScanType") && !item.getString("expectedScanType").equals(description.scanType))
                        throw new AssertionError("Source scan type differs: "+description.scanType);
                    row.put("metadataStatus","passed");
                    row.put("status","played");
                } catch(Throwable error) {
                    if(error instanceof CapabilityLimit) {
                        row.put("status","capabilityLimited").put("playbackStatus","capabilityLimited");
                    } else {
                    foregroundInterrupted=error.getMessage()!=null&&error.getMessage().startsWith("Lost foreground");
                    if("played".equals(row.optString("playbackStatus")))row.put("metadataStatus","failed");
                    else row.put("playbackStatus",foregroundInterrupted?"interrupted":"failed");
                    failures++;row.put("status",foregroundInterrupted?"interrupted":"failed")
                            .put("error",android.util.Log.getStackTraceString(error));
                    // Persist the actual assertion before collecting diagnostics.
                    // Old Dalvik can block while suspending a native/EGL thread.
                    JSONArray checkpoint=new JSONArray(results.toString());checkpoint.put(row);
                    try(FileOutputStream output=new FileOutputStream(report)) {output.write(checkpoint.toString(2).getBytes("UTF-8"));}
                    // A timeout before prepare needs Java/proxy state too; native
                    // logs alone cannot distinguish socket waits from cache waits.
                    File dump=new File(report.getParentFile(),String.format(java.util.Locale.US,"failure-%04d-threads.txt",index));
                    if(android.os.Build.VERSION.SDK_INT>=18)try(java.io.FileOutputStream output=new java.io.FileOutputStream(dump)) {
                        for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()) {
                            StringBuilder text=new StringBuilder(entry.getKey().getName()).append(" ").append(entry.getKey().getState()).append('\n');
                            for(StackTraceElement frame:entry.getValue())text.append("  ").append(frame).append('\n');
                            output.write(text.append('\n').toString().getBytes("UTF-8"));
                        }
                        row.put("failureThreads",dump.getName());
                    } catch(Exception ignored) { }
                    }
                }
                row.put("elapsedMs",SystemClock.elapsedRealtime()-start);results.put(row);save(report);
                android.util.Log.i("nTvPlaylistTest",row.toString());
                Bundle progress=new Bundle();progress.putString("stream",index+" "+item.getString("name")+" "+row.getString("status")+"\n");
                sendStatus(0,progress);
                if(foregroundInterrupted)break;
            }
            result.putString("stream","channels="+results.length()+" appFailures="+failures
                    +" probeFailures="+probeFailures
                    +" sourceUnavailable="+unavailable+" report="+report+"\n");
            finish(failures==0 && probeFailures==0 && unavailable==0 ? -1 : 0,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}
        finally {
            ChannelCatalog.GROUPS=savedGroups;
            SharedPreferences.Editor editor=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()) {
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
            }
            editor.commit();
            if(activity!=null)runOnMainSync(activity::finish);
        }
    }
}
