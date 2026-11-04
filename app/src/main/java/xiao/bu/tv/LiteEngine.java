package xiao.bu.tv;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.Dns;

/**
 * Stand-in for the private lightweight browser engine.
 *
 * <p>The real engine ships inside the separately authorized {@code browser-core}
 * checkout. When that core is absent the lightweight path is disabled at compile
 * time ({@code BuildConfig.BUNDLED_LITE_BROWSER == false}) and at run time
 * ({@code LiteWebPlayback.supported()}), so these methods only need to fail
 * loudly instead of performing page work.</p>
 */
class LiteEngine {
    /** A resolved media URL that the native player can open directly. */
    interface PlaybackTransport {
        String url();

        void close() throws Exception;
    }

    /** Callbacks delivered by the engine while a page is loading. */
    interface Listener {
        void frame(JSONObject packet);

        void textInput(JSONObject state);

        void message(String value);

        void navigate(String next);

        void media(String source, Map<String, String> headers);

        void temporaryMedia(String source, Map<String, String> headers);

        void mediaControl(String source, boolean virtualMse, boolean paused);
    }

    static LiteEngine withNetworkThreadMultiplier(Context context, Listener listener,
            Dns resolver, int multiplier) {
        throw new UnsupportedOperationException(
                "轻量内核未授权，请改用系统 WebView 内核");
    }

    static boolean keepsOriginalMediaPipeline(String url) {
        return false;
    }

    static boolean sameHlsSessionSource(String first, String second) {
        return first != null && first.equals(second);
    }

    static int networkThreadCount(int multiplier) {
        return 0;
    }

    void load(String url, String script) {
    }

    void close() {
    }

    void setHeadless(boolean headless) {
    }

    void setRendering(boolean rendering) {
    }

    void viewport(int width, int height, int mode) {
    }

    int viewportWidth() {
        return 0;
    }

    int viewportHeight() {
        return 0;
    }

    void traceRequests(boolean enabled) {
    }

    void renderPage() {
    }

    String evaluate(String script) {
        return "";
    }

    JSONObject startupTimings() {
        return new JSONObject();
    }

    JSONObject runtimeBusySnapshot() {
        return new JSONObject();
    }

    long documentReadyAt() {
        return 0L;
    }

    long upstreamDownloadedBytes() {
        return -1L;
    }

    boolean keepsMediaSession(String source) {
        return false;
    }

    PlaybackTransport playbackTransport(String source, Map<String, String> headers) {
        throw new UnsupportedOperationException("轻量内核未授权");
    }

    void playbackTime(double position, int width, int height) {
    }

    void directPlaybackTime(String source, double position, int width, int height) {
    }

    void temporaryPlayback(double position, double duration, boolean committed,
            boolean failed) {
    }
}
