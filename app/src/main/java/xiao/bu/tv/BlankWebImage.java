package xiao.bu.tv;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * 网页图片占位图（1×1 透明 PNG，占位实现）。
 *
 * <p>上游 WebSourceView 用它把某些网页图片请求替换成 1×1 透明图。
 * 真实实现按规则判断哪些 URL 需要占位；占位实现 matches() 恒为
 * false——不做任何替换，所有图片照常加载，与作者引入该功能
 * 之前的行为一致。open()/length()/header() 保持可用，
 * 以防其它代码路径调用。</p>
 */
final class BlankWebImage {

    private static final byte[] IMAGE = base64Decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private BlankWebImage() {
    }

    /** 占位：不做任何替换。 */
    static boolean matches(String url, String method, boolean document, Map<String, String> headers) {
        return false;
    }

    /** 占位图内容（1×1 透明 PNG）。 */
    static InputStream open() {
        return new ByteArrayInputStream(IMAGE);
    }

    /** 占位图字节数。 */
    static long length() {
        return IMAGE.length;
    }

    /** 从请求头里取值（缺失返回 null）。 */
    static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static byte[] base64Decode(String value) {
        return android.util.Base64.decode(value, android.util.Base64.NO_WRAP);
    }
}
