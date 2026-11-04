package xiao.bu.tv;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.Dns;

/**
 * 私有 lite 内核的 Java 层入口。
 *
 * <p>这是一个“空实现”占位类：所有方法返回默认值、不做任何事。
 * 因为 app/build.gradle 里 BUNDLED_LITE_BROWSER 恒为 false，
 * LiteWebPlayback.supported() 永远返回 false，本类不会被真正调用，
 * 只为让工程通过编译。
 *
 * <p>方法签名与上游 LiteWebPlayback / MainActivity 的调用严格一致。
 */
final class LiteEngine {

    /** 网页渲染回调。 */
    interface Listener {
        void frame(JSONObject packet);

        void textInput(JSONObject state);

        void message(String value);

        void navigate(String next);

        void media(String source, Map<String, String> headers);

        void mediaControl(String source, boolean virtualMse, boolean paused);

        void temporaryMedia(String source, Map<String, String> headers);
    }

    /** 播放传输通道。 */
    interface PlaybackTransport {
        String url();

        void close();
    }

    private LiteEngine() {
    }

    static LiteEngine withNetworkThreadMultiplier(Context context, Listener listener,
                                                 Dns resolver, int multiplier) {
        return new LiteEngine();
    }

    static int networkThreadCount(int configured) {
        return Math.max(1, configured);
    }

    static boolean keepsOriginalMediaPipeline(String url) {
        return false;
    }

    static boolean sameHlsSessionSource(String first, String second) {
        return first != null && first.equals(second);
    }

    void setRendering(boolean value) {
    }

    void setHeadless(boolean value) {
    }

    void traceRequests(boolean value) {
    }

    long documentReadyAt() {
        return 0L;
    }

    void viewport(int width, int height, int density) {
    }

    int viewportWidth() {
        return 1280;
    }

    int viewportHeight() {
        return 720;
    }

    void renderPage() {
    }

    JSONObject startupTimings() {
        return new JSONObject();
    }

    long upstreamDownloadedBytes() {
        return 0L;
    }

    String runtimeBusySnapshot() {
        return "";
    }

    void evaluate(String script) {
    }

    boolean keepsMediaSession(String source) {
        return false;
    }

    PlaybackTransport playbackTransport(String source, Map<String, String> headers) {
        return null;
    }

    void load(String url, String script) {
    }

    void temporaryPlayback(double position, double duration, boolean ready, boolean error) {
    }

    void directPlaybackTime(String source, double position, int width, int height) {
    }

    void playbackTime(double position, int width, int height) {
    }

    void close() {
    }
}
