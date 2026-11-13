package xiao.bu.tv;

import android.content.Context;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 私有轻量内核的 HTTP 传输栈（占位实现）。
 *
 * <p>真实实现是作者为老安卓（4.4/5.0）手写的轻量 TLS/HTTP 客户端。
 * 占位实现直接返回一个普通的 OkHttpClient：编译签名一致，
 * 且该路径只在 LiteTlsSocket.Factory 被启用时才会走到
 * （占位实现永远不会触发），运行期实际不会用到本类。</p>
 */
final class LiteHttp {

    private LiteHttp() {
    }

    static OkHttpClient client(Context context, Dns dns) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (dns != null) {
            builder.dns(dns);
        }
        return builder.build();
    }
}
