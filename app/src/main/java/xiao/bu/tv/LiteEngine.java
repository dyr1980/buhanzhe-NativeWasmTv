package xiao.bu.tv;

import java.util.Map;

import okhttp3.Dns;

/**
 * 私有轻量内核在公开检出中的"不可用"实现。
 *
 * 上游把浏览器内核（litehtml / LiteCanvas / LitePageWarmup / 专用 QuickJS）放在单独
 * 授权的私有工程里，公开仓库只有 `prebuilt/litebrowser/<ABI>/libntvLitebro.so` 的
 * 二进制快照，不含 Java 接口与 JS 资源，所以公开检出无法真正编译或启用轻量内核。
 *
 * 本文件按业务代码实际调用面提供同签名实现：{@link #isAvailable()} 恒为 false，
 * {@link #withNetworkThreadMultiplier} 不会被调用，所有 lite 路径都会回退到系统 WebView。
 *
 * 已合法取得内核授权时：把授权工程放进 `browser-core/`，删除本文件及
 * LiteCanvas / LitePageWarmup / LiteMp4Probe 三个占位类，构建脚本会自动恢复 ntvBro 变体。
 */
final class LiteEngine {
    private LiteEngine() { }

    interface Listener {
        void frame(org.json.JSONObject packet);
        void textInput(org.json.JSONObject state);
        void message(String value);
        void navigate(String next);
        void media(String source, Map<String, String> headers);
        void temporaryMedia(String source, Map<String, String> headers);
        void mediaControl(String source, boolean virtualMse, boolean paused);
    }

    static final class PlaybackTransport {
        private final String url;

        PlaybackTransport(String url) {
            this.url = url;
        }

        String url() {
            return url;
        }

        void close() throws Exception { }
    }

    static boolean isAvailable() {
        return false;
    }

    static boolean keepsOriginalMediaPipeline(String url) {
        return false;
    }

    static boolean sameHlsSessionSource(String left, String right) {
        return left != null && left.equals(right);
    }

    static int networkThreadCount(int multiplier) {
        return 0;
    }

    static LiteEngine withNetworkThreadMultiplier(android.content.Context context, Listener listener,
                                                 Dns dns, int multiplier) {
        throw new IllegalStateException("轻量内核未授权，公开检出不含该组件");
    }

    void traceRequests(boolean enabled) { }

    void setHeadless(boolean headless) { }

    void viewport(int width, int height, int scale) { }

    int viewportWidth() { return 0; }

    int viewportHeight() { return 0; }

    void load(String url, String script) { }

    void close() { }

    void setRendering(boolean rendering) { }

    void renderPage() { }

    long documentReadyAt() { return 0L; }

    String evaluate(String script) { return null; }

    org.json.JSONObject startupTimings() { return new org.json.JSONObject(); }

    org.json.JSONObject runtimeBusySnapshot() { return new org.json.JSONObject(); }

    long upstreamDownloadedBytes() { return -1L; }

    boolean keepsMediaSession(String source) { return false; }

    PlaybackTransport playbackTransport(String source, Map<String, String> headers) {
        return new PlaybackTransport(source);
    }

    void playbackTime(double position, int width, int height) { }

    void directPlaybackTime(String source, double position, int width, int height) { }

    void temporaryPlayback(double position, double duration, boolean completed, boolean error) { }
}
