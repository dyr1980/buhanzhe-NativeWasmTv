package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Run only in an isolated applicationId, against an authorized local sample. */
public final class HardwareStreamDiagnostic extends Instrumentation {
    private MainActivity activity;
    private String liveUrl;
    private boolean deniedTail;
    private interface Work { void run() throws Exception; }
    private Object get(String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    private void set(String name, Object value) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value);
    }
    private void main(Work work) throws Exception {
        CountDownLatch done = new CountDownLatch(1); Throwable[] failure = {null};
        new Handler(Looper.getMainLooper()).post(() -> {
            try {work.run();} catch (Throwable e) {failure[0] = e;} finally {done.countDown();}
        });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("UI timeout");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private void log(String value) { Bundle b = new Bundle(); b.putString("stream", value + "\n"); sendStatus(1, b); }
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); liveUrl = args == null ? null : args.getString("streamUrl");
        deniedTail = args != null && "true".equals(args.getString("deniedTail")); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try {
            if (!getTargetContext().getPackageName().endsWith(".hwdiagnostic")) throw new AssertionError("Use isolated applicationId");
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);
            main(() -> ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet());
            String[][] cases = {{"hardware", "auto", "sample.m3u8"}, {"hardware", "auto", "remux.mp4"},
                    {"software", "auto", "sample.m3u8"}, {"hardware", "OMX.google.h264.decoder", "sample.m3u8"}};
            if (liveUrl != null) cases = new String[][]{{"hardware", "auto", liveUrl}, {"software", "auto", liveUrl}};
            if (deniedTail) cases = new String[][]{{"hardware", "auto", "late-denied.m3u8"}};
            for (String[] test : cases) {
                final boolean[] rendered = {false};
                log("CASE " + test[0] + " " + test[1] + " " + (liveUrl == null ? test[2] : "authorized live stream"));
                main(() -> {
                    set("decodeMode", test[0]); set("hardwareDecoder", test[1]);
                    Channel channel = new Channel("1", "CCTV sample", null, "http://127.0.0.1:19983/" + (liveUrl == null ? test[2] : "sample.m3u8"), null, null);
                    ChannelCatalog.GROUPS = new ChannelCatalog.Group[]{new ChannelCatalog.Group("Diagnostic", ChannelCatalog.SOURCE_CUSTOM, new Channel[]{channel})};
                    set("currentGroupIndex", 0); set("currentChannelIndex", 0);
                    Method m = MainActivity.class.getDeclaredMethod("switchChannel", int.class, int.class); m.setAccessible(true); m.invoke(activity, 0, 0);
                    if (liveUrl != null) {
                        // Invalidate the fixture's queued resolver before starting the live URL.
                        set("playRequestId", (Integer)get("playRequestId") + 1);
                        String page = "https://ysxw.cctv.cn/", agent = "Mozilla/5.0";
                        ((HlsProxyServer)get("proxy")).setWebRequestHeaders(page, agent, null);
                        Method h = MainActivity.class.getDeclaredMethod("buildWebStreamHeaders", String.class, String.class, String.class);
                        h.setAccessible(true); set("webStreamHeaders", h.invoke(activity, page, agent, null));
                        Method play = MainActivity.class.getDeclaredMethod("startResolvedPlayer", Channel.class, String.class);
                        play.setAccessible(true); play.invoke(activity, channel, liveUrl);
                    }
                });
                for (int i = 0; i < (liveUrl == null && !deniedTail ? 4 : 12); i++) {
                    SystemClock.sleep(2000);
                    main(() -> {
                        IjkMediaPlayer p = (IjkMediaPlayer)get("player");
                        if (p != null && (Boolean)get("videoRenderingStarted") && p.getCurrentPosition() > 500)
                            rendered[0] = true;
                        if (liveUrl != null && p != null && !liveUrl.equals(get("activePlayerStreamUrl")))
                            throw new AssertionError("Live diagnostic was replaced by another source");
                        log("SAMPLE prepared=" + get("prepared") + " rendering=" + get("videoRenderingStarted")
                            + (p == null ? " player=null" : " decoder=" + p.getVideoDecoder() + " clock=" + p.getCurrentPosition()
                            + " duration=" + p.getDuration() + " playing=" + p.isPlaying()
                            + " buffering=" + get("buffering") + " forbidden=" + ((HlsProxyServer)get("proxy")).wasPlaybackForbidden()
                            + " codec=" + p.getMediaInfo().mVideoDecoderImpl
                            + " decodeFps=" + p.getVideoDecodeFramesPerSecond() + " outputFps=" + p.getVideoOutputFramesPerSecond())
                            + " status=" + ((android.widget.TextView)get("statusText")).getText());
                    });
                }
                if (deniedTail) main(() -> {
                    if (!rendered[0] || !((android.widget.TextView)get("statusText")).getText().toString().contains("HTTP 403")
                            || get("player") != null) throw new AssertionError("Late segment 403 left the final video frame pretending to play");
                    log("PASS late segment 403 is reported after cached playback drains");
                });
            }
            result.putString("stream", "DONE diagnostic; MediaCodec API does not establish physical hardware decoding on an emulator\n");
        } catch (Throwable e) { code = 0; result.putString("stream", android.util.Log.getStackTraceString(e)); }
        finally { if (activity != null) try {main(() -> activity.finish());} catch (Exception ignored) {} }
        finish(code, result);
    }
}
