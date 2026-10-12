package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Source metadata must be available at prepared, before starting playback. */
public final class VideoMetadataInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();JSONArray report=new JSONArray();int code=-1;
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=new FileInputStream(args.getString("manifest"))) {
                byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))>0)bytes.write(buffer,0,n);
            }
            JSONArray fixtures=new JSONArray(bytes.toString("UTF-8"));
            for(int i=0;i<fixtures.length();i++) {
                JSONObject fixture=fixtures.getJSONObject(i);
                if (Boolean.parseBoolean(args.getString("cacheProbe","false"))) {
                    CachedVideoMetadataProbe cached = new CachedVideoMetadataProbe(getTargetContext().getCacheDir());
                    try {
                        ByteArrayOutputStream sample = new ByteArrayOutputStream();
                        try (InputStream input = new FileInputStream(fixture.getString("fixture"))) {
                            byte[] block = new byte[8192]; int n;
                            while (sample.size() < 384 * 1024 && (n = input.read(block, 0,
                                    Math.min(block.length, 384 * 1024 - sample.size()))) > 0) sample.write(block, 0, n);
                        }
                        cached.submit(sample.toByteArray());
                        long deadline = android.os.SystemClock.elapsedRealtime() + 6000;
                        while (cached.description().bitDepth == 0 && android.os.SystemClock.elapsedRealtime() < deadline)
                            android.os.SystemClock.sleep(50);
                        VideoStreamDescription description = cached.description();
                        if (description.bitDepth != fixture.getInt("expectedDepth")
                                || !description.colorPrimaries.equals(fixture.getString("expectedPrimaries"))
                                || !description.colorTransfer.equals(fixture.getString("expectedTransfer"))
                                || description.colorInferred != fixture.getBoolean("expectedInferred"))
                            throw new AssertionError("cached " + fixture.getString("name") + ": " + description.debugLabel());
                        report.put(new JSONObject().put("name", fixture.getString("name"))
                                .put("label", description.debugLabel()).put("colorInferred", description.colorInferred));
                    } finally { cached.close(); }
                    continue;
                }
                IjkMediaPlayer player=new IjkMediaPlayer();
                try {
                    CountDownLatch prepared=new CountDownLatch(1);
                    final String[] error={null};
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,"start-on-prepared",0);
                    player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,"mediacodec",0);
                    player.setOnPreparedListener(p -> prepared.countDown());
                    player.setOnErrorListener((p,what,extra)->{error[0]=what+"/"+extra;prepared.countDown();return true;});
                    player.setDataSource(fixture.getString("fixture"));player.prepareAsync();
                    if(!prepared.await(20,TimeUnit.SECONDS) || error[0]!=null)
                        throw new AssertionError("preload failed: "+fixture.getString("name")+" "+error[0]);
                    VideoStreamDescription description=IjkStreamMetrics.videoDescription(player);
                    if(description.bitDepth!=fixture.getInt("expectedDepth")
                            || !description.colorPrimaries.equals(fixture.getString("expectedPrimaries"))
                            || !description.colorTransfer.equals(fixture.getString("expectedTransfer"))
                            || description.colorInferred!=fixture.getBoolean("expectedInferred"))
                        throw new AssertionError(fixture.getString("name")+": "+description.debugLabel());
                    if(player.getCurrentPosition()!=0 || player.isPlaying())
                        throw new AssertionError("preload started playback");
                    report.put(new JSONObject().put("name",fixture.getString("name"))
                            .put("label",description.debugLabel()).put("colorInferred",description.colorInferred)
                            .put("position",player.getCurrentPosition()));
                }finally {player.release();}
            }
        }catch(Throwable error){code=1;result.putString("stream",android.util.Log.getStackTraceString(error));}
        result.putString("metadata",report.toString());finish(code,result);
    }
}
