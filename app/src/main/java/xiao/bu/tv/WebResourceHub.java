package xiao.bu.tv;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * 私有轻量内核的"共享网页资源缓存中枢"（占位实现）。
 *
 * <p>上游公开代码引用了本类，但类本身属于作者未公开的部分。这里提供
 * 编译所需的全部签名，并把行为降级为"不做共享缓存拦截"：
 * eligible() 恒为 false，SharedWebResources.intercept() 因此返回 null，
 * 网页资源由系统 WebView 自己加载——与作者引入该功能之前的行为一致。</p>
 *
 * <p>ChannelHealth 对 webview:// 源的健康探测走 newCall()，这里直接
 * 退回普通的 OkHttp 请求，保证探测功能照常工作。</p>
 */
final class WebResourceHub {

    /** 请求用途标记：DOCUMENT=主文档，PUBLIC_STATIC=可共享的公开静态资源。 */
    enum Kind {
        DOCUMENT,
        PUBLIC_STATIC
    }

    /** 共享缓存装载器接口（SharedWebResources 实现了它）。 */
    interface Loader {
        Call newCall(OkHttpClient client, Request request);
    }

    private static volatile Loader loader;

    static void install(Loader value) {
        loader = value;
    }

    /** 占位：所有请求都不走共享缓存，由 WebView 正常自行加载。 */
    static boolean eligible(Request request) {
        return false;
    }

    /** 占位：直接返回实际请求。 */
    static Request responseRequest(Request original, Request actual) {
        return actual;
    }

    /** 健康探测等场景直接退回普通 OkHttp 调用，功能不缺失。 */
    static Call newCall(OkHttpClient client, Request request) {
        return client.newCall(request);
    }
}
