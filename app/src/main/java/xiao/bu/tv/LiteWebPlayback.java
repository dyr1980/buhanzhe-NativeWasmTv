package xiao.bu.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.Closeable;
import java.util.Map;
import org.json.JSONObject;

/** Owns one lightweight page and its live media transport until channel close. */
final class LiteWebPlayback implements Closeable {
    interface Host {
        void stream(String url, boolean temporary);
        void status(String value);
        void failure(String value);
        default void render(android.view.View page) { }
        default int networkThreadMultiplier() { return 0; }
        boolean playing();
        long position();
        long duration();
        int width();
        int height();
        void setPaused(boolean paused);
    }
    private final Context context;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;
    private boolean closed, temporary, streamActive, finalStream;
    private int finalHandoffs;

    LiteWebPlayback(Context context, Host host) { this.context=context; this.host=host; }
    
    static boolean supported() {
        return BuildConfig.BUNDLED_LITE_BROWSER;
    }
    
    JSONObject startupTimings(){return new JSONObject();}
    int finalHandoffs(){return finalHandoffs;}
    long upstreamDownloadedBytes(){return -1L;}
    void enableAudit(){}
    JSONObject auditSnapshot(){return new JSONObject();}
    
    void open(String url, String script) {
        // 轻量内核已被移除，直接报错提示
        if (closed || finalStream) return;
        fail("当前版本不支持轻量内核网页嗅探");
    }
    
    private void fail(String message) {
        if (closed) return;
        close();
        host.failure(message);
    }

    boolean finishTemporary(boolean error) {
        return false;
    }
    
    public void close() { 
        if(closed)return;
        closed=true;
        ++generation;
        handler.removeCallbacksAndMessages(null);
    }
}
