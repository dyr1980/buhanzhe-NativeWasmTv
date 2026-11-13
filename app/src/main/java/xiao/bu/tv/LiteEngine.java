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

    /** 站点文档脚本钩子（上游传 LitePageScripts::forDocument）。 */
    interface DocumentScriptHook {
        java.util.List<String> forDocument(Context context, String page);
    }

    /** 占位：不安装文档脚本钩子。 */
    static void installDocumentScripts(DocumentScriptHook hook) {
    }

    /** 占位：不安装站点兼容策略。 */
    static void installHostPolicy(LiteHostPolicy policy) {
    }

    /** 健康探测专用实例（ChannelHealth 对 webview:// 源使用）。 */
    static LiteEngine forHealthProbe(Context context, Listener listener, Dns resolver) {
        return new LiteEngine(context, listener, resolver, 1);
    }

    /** 预解析专用实例（占位与普通实例一致）。 */
    static LiteEngine forPreparse(Context context, Listener listener, Dns resolver, int multiplier) {
        return new LiteEngine(context, listener, resolver, multiplier);
    }

    /** 共享 HTTP 缓存（上游"清理缓存"时会调用 evictAll）。 */
    static okhttp3.Cache httpCache(Context context) {
        java.io.File dir = new java.io.File(context.getApplicationContext().getCacheDir(),
                "lite-http-cache");
        return new okhttp3.Cache(dir, 16L * 1024 * 1024);
    }

    /** 启动状态快照（占位返回 null，上游所有读取方都做了 null 判断）。 */
    LiteStartupState startupState() {
        return null;
    }

    /** 内核当前使用的 UA（占位返回默认 UA）。 */
    String userAgent() {
        return defaultUserAgent(null);
    }

    /** 占位：预解析页晋升为前台页（不生效）。 */
    void promotePreparse() {
    }

    /** 占位：暂存预解析页（不生效）。 */
    void parkPreparse() {
    }

    /** 占位：网页图片加载开关（不生效）。 */
    void loadImages(boolean value) {
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
