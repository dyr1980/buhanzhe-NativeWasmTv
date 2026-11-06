package xiao.bu.tv;

import okhttp3.Dns;

/**
 * 频道切换前的网页预热（占位实现）。
 *
 * 上游用私有内核提前解析 DNS、预取页面，缩短换台等待。公开检出没有内核，
 * 这里保持同样的构造与方法签名，但所有预热动作都是空操作：
 * 换台时直接走正常加载流程，功能不受影响，只是少了这一层预取优化。
 */
final class LitePageWarmup implements java.io.Closeable {
    LitePageWarmup(android.content.Context context, Dns dns) { }

    void dns(String url) { }

    void page(String url) { }

    void cancel() { }

    @Override
    public void close() { }
}
