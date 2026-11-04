package xiao.bu.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import java.io.Closeable;
import org.json.JSONObject;

/** 仅用于编译通过的空壳类 */
final class LiteWebPlayback implements Closeable {
    interface Host {
        void stream(String url, boolean temporary);
        void status(String value);
        void failure(String value);
        default void render(View page) { }
        default int networkThreadMultiplier() { return 0; }
        boolean playing();
        long position();
        long duration();
        int width();
        int height();
        void setPaused(boolean paused);
    }

    LiteWebPlayback(Context context, Host host) { }
    static boolean supported() { return false; }
    JSONObject startupTimings() { return new JSONObject(); }
    int finalHandoffs() { return 0; }
    long upstreamDownloadedBytes() { return -1L; }
    void enableAudit() {}
    JSONObject auditSnapshot() { return new JSONObject(); }
    void open(String url, String script) {}
    boolean finishTemporary(boolean error) { return false; }
    boolean isRendering() { return false; }
    void applyPageCover(boolean enabled, String style) {}
    boolean handleKey(KeyEvent event) { return false; }
    boolean revealPage() { return false; }
    void setActive(boolean active) {}
    public void close() { }
}
