package xiao.bu.tv.jar;

import android.content.Context;
import android.view.View;
import org.json.JSONObject;

/** API 1. create/update/dispose and Host callbacks run on the Android main thread. */
public interface ChannelPlugin {
    interface Host {
        void play(String url);
        void status(String message);
        void configureBrowser(android.webkit.WebView webView);
    }
    interface BrowserHost extends Host {
        void chooseFiles(android.webkit.ValueCallback<android.net.Uri[]> callback,String accept,boolean multiple);
    }
    View create(Context context, ClassLoader resources, JSONObject config, Host host) throws Exception;
    void update(long positionMs, boolean playing);
    void dispose();
}
