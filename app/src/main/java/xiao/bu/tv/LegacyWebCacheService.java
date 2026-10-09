package xiao.bu.tv;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.webkit.WebView;

/** An explicit cache clear must not initialize legacy WebKit inside the player. */
public final class LegacyWebCacheService extends Service {
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        WebView cleaner = null;
        try {
            cleaner = WebViewAvailability.create(() -> new WebView(this));
            cleaner.clearCache(true); cleaner.clearFormData();
            android.webkit.WebStorage.getInstance().deleteAllData();
        } catch (RuntimeException error) {
            android.util.Log.w("LegacyWebGuard", "Unable to clear browser cache", error);
        } finally { if (cleaner != null) cleaner.destroy(); stopSelf(id); }
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
