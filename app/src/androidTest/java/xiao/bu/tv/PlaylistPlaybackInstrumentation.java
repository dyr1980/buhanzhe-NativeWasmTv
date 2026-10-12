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

/** Replays all captured playlist entries through the real Activity/player. */
public final class PlaylistPlaybackInstrumentation extends Instrumentation {
    private Bundle args;
    private MainActivity activity;
    private final JSONArray results=new JSONArray();

    private Field field(String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field;
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
    @Override public void onCreate(Bundle args) {super.onCreate(args);this.args=args;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int failures=0,unavailable=0;
        SharedPreferences prefs=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=prefs.getAll();ChannelCatalog.Group[] savedGroups=ChannelCatalog.GROUPS;
        File report=new File(getTargetContext().getExternalFilesDir(null),args.getString("reportName","playlist-playback.json"));
        try {
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
                long start=SystemClock.elapsedRealtime();
                if(!"decoded".equals(item.optString("sourceStatus","decoded"))) {
                    unavailable++;
                    row.put("status","sourceUnavailable").put("error",item.optString("sourceError"));
                    results.put(row);save(report);
                    Bundle progress=new Bundle();progress.putString("stream",index+" "+item.getString("name")+" sourceUnavailable\n");
                    sendStatus(0,progress);continue;
                }
                try {
                    String url=item.getString("fixture");
                    Channel channel=new Channel(String.valueOf(index),item.getString("name"),"playlist-test",url,null,null);
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
                        if(resolve)startChannel.invoke(activity,0);
                        else play.invoke(activity,channel,url,"software".equals(item.optString("decodeMode")),true,null,0L);
                    }catch(Exception error){throw new RuntimeException(error);}});
                    deadline=SystemClock.elapsedRealtime()+20000;
                    while(!field("videoRenderingStarted").getBoolean(activity)) {
                        if(SystemClock.elapsedRealtime()>deadline)throw new AssertionError("First frame timeout");
                        SystemClock.sleep(100);
                    }
                    row.put("firstFrameMs",SystemClock.elapsedRealtime()-start);
                    if(resolve ? field("activePlayerChannel").get(activity)!=channel
                            : !url.equals(field("activePlayerStreamUrl").get(activity)))
                        throw new AssertionError("Startup request replaced the fixture URL");
                    IjkMediaPlayer player=(IjkMediaPlayer)field("player").get(activity);
                    long beginPosition=player.getCurrentPosition();deadline=SystemClock.elapsedRealtime()+10000;
                    // TS audio can advance before enough video has arrived to
                    // publish the first cadence sample. Require both signals.
                    long samplingStart=SystemClock.elapsedRealtime();
                    while(player.getCurrentPosition()-beginPosition<1000
                            || SystemClock.elapsedRealtime()-samplingStart<item.optLong("sampleMs",2000)
                            || !(IjkStreamMetrics.outputFrameRate(player)>0)) {
                        if(field("player").get(activity)!=player)throw new AssertionError("Player replaced");
                        if(!activity.hasWindowFocus())throw new AssertionError("Lost foreground");
                        if(SystemClock.elapsedRealtime()>deadline)throw new AssertionError("Playback stalled");
                        SystemClock.sleep(100);
                    }
                    float fps=IjkStreamMetrics.outputFrameRate(player);
                    if(!(fps>0))throw new AssertionError("No advancing output frames");
                    VideoStreamDescription description=IjkStreamMetrics.videoDescription(player);
                    String deinterlace=IjkStreamMetrics.deinterlaceStatus(player,description);
                    if(item.has("expectedDeinterlace") && !item.getString("expectedDeinterlace").equals(deinterlace))
                        throw new AssertionError("Deinterlace route differs: "+deinterlace);
                    float sourceFps=IjkStreamMetrics.sourceFrameRate(player);
                    if(item.has("expectedSourceFps") && Math.abs(sourceFps-item.getDouble("expectedSourceFps"))>0.5)
                        throw new AssertionError("Source frame cadence differs: "+sourceFps);
                    if(item.has("minOutputFps") && fps<item.getDouble("minOutputFps"))
                        throw new AssertionError("Output cadence too low: "+fps);
                    if(item.has("expectedScanType") && !item.getString("expectedScanType").equals(description.scanType))
                        throw new AssertionError("Source scan type differs: "+description.scanType);
                    Method collect=MainActivity.class.getDeclaredMethod("collectPlaybackStreamStats",float.class);
                    collect.setAccessible(true);Object debugStats=collect.invoke(activity,fps);
                    Method format=MainActivity.class.getDeclaredMethod("formatDebugFrameRate",debugStats.getClass());
                    format.setAccessible(true);String fpsLabel=(String)format.invoke(null,debugStats);
                    if(!fpsLabel.contains("输出") || !fpsLabel.contains("源"))
                        throw new AssertionError("FPS labels are ambiguous: "+fpsLabel);
                    if("interlaced".equals(description.scanType) && !fpsLabel.contains("场"))
                        throw new AssertionError("Interlaced source omits field cadence: "+fpsLabel);
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
                    Bitmap image=getUiAutomation().takeScreenshot();
                    if(image==null)throw new AssertionError("No screenshot");
                    int visible=0;
                    for(int y=image.getHeight()/5;y<image.getHeight()*2/5;y+=16)
                        for(int x=image.getWidth()/8;x<image.getWidth()*7/8;x+=16)
                            if((image.getPixel(x,y)&0xffffff)!=0)visible++;
                    row.put("captureVisible",visible>5);
                    File capture=new File(report.getParentFile(),String.format(java.util.Locale.US,"playlist-%04d.png",index));
                    try(FileOutputStream output=new FileOutputStream(capture)){image.compress(Bitmap.CompressFormat.PNG,100,output);}
                    image.recycle();
                    if(visible<=5)throw new AssertionError("Video-only capture is black");
                    row.put("status","played");
                } catch(Throwable error) {
                    failures++;row.put("status","failed").put("error",android.util.Log.getStackTraceString(error));
                }
                row.put("elapsedMs",SystemClock.elapsedRealtime()-start);results.put(row);save(report);
                android.util.Log.i("nTvPlaylistTest",row.toString());
                Bundle progress=new Bundle();progress.putString("stream",index+" "+item.getString("name")+" "+row.getString("status")+"\n");
                sendStatus(0,progress);
            }
            result.putString("stream","channels="+results.length()+" appFailures="+failures
                    +" sourceUnavailable="+unavailable+" report="+report+"\n");
            finish(failures==0 && unavailable==0 ? -1 : 0,result);
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
