package xiao.bu.tv;

import android.net.Uri;
import org.json.JSONObject;
import java.io.IOException;

/** https://host/plugin.jar?sha256=HEX&...plugin configuration... */
final class JarChannelSource {
    final String url, sha;
    final JSONObject config;
    static boolean matches(String value) {
        if(value==null)return false;
        Uri uri=Uri.parse(value);
        return "https".equals(uri.getScheme()) && uri.getPath()!=null && uri.getPath().endsWith(".jar");
    }
    JarChannelSource(String source) throws Exception {
        if (!matches(source)) throw new IOException("不是 JAR 频道");
        Uri uri=Uri.parse(source);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getFragment()!=null || !uri.getPath().endsWith(".jar"))
            throw new IOException("JAR 频道需要 HTTPS .jar 地址");
        sha=uri.getQueryParameter("sha256");
        if(sha==null || !sha.matches("[0-9a-f]{64}")) throw new IOException("JAR 频道缺少有效 SHA-256");
        url=uri.buildUpon().clearQuery().build().toString();
        config=new JSONObject();
        for(String key:uri.getQueryParameterNames()) {
            if(uri.getQueryParameters(key).size()!=1) throw new IOException("JAR 配置参数重复: "+key);
            if(!"sha256".equals(key)) config.put(key,uri.getQueryParameter(key));
        }
    }
}
