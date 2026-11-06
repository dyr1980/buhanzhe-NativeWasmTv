package xiao.bu.tv;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.Dns;

/**
 * 私有 lite 内核的 Java 层入口（占位实现）。
 *
 * <p>所有方法返回默认值、不做任何事。app/build.gradle 里 BUNDLED_LITE_BROWSER
 * 恒为 false，LiteWebPlayback.supported() 永远返回 false，本类不会被真正调用，
 * 只为让工程通过编译。方法签名与上游调用严格一致，作者更新后如新增调用，
 * 只需在下面补上同签名空方法即可。
 */
final class LiteEngine {

    /** 网页渲染回调（方法带默认空实现，调用方可以只覆写关心的几个）。 */
    interface Listener {
        default void frame(JSONObject packet) {
        }

        default void textInput(JSONObject state) {
        }

        default void message(String value) {
        }

        default void navigate(String next) {
        }

        default void media(String source, Map<String, String> headers) {
        }

        default void mediaControl(String source, boolean virtualMse, boolean paused) {
        }

        default void temporaryMedia(String source, Map<String, String> headers) {
        }
    }

    /** 播放传输通道。 */
    interface PlaybackTransport {
        String url();

        void close();
    }

    /**
     * 与上游调用一致的构造器（ChannelHealth 直接 new）。
     * 占位实现不启动任何东西。
     */
    LiteEngine(Context context, Listener listener, Dns resolver, int multiplier) {
    }

    static LiteEngine withNetworkThreadMultiplier(Context context, Listener listener,
                                                 Dns resolver, int multiplier) {
        return new LiteEngine(context, listener, resolver, multiplier);
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

    /** 上游用于桌面模式 UA 计算的默认 UA；占位返回通用 Android Chrome UA。 */
    static String defaultUserAgent(String url) {
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    }

    void setRendering(boolean value) {
    }

    void setHeadless(boolean value) {
    }

    void traceRequests(boolean value) {
    }

    /** 限制内核内存用量（占位，不生效）。 */
    void setAuditMemoryMiB(int mib) {
    }

    /** 覆盖请求 UA（占位，不生效）。 */
    void userAgentOverride(String userAgent) {
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

    JSONObject runtimeBusySnapshot() {
        return new JSONObject();
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
