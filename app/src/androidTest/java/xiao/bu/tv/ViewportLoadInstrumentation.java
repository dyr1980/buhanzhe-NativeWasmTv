package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Verifies real CSS viewport sizing even while a third-party resource holds load open. */
public final class ViewportLoadInstrumentation extends Instrumentation {
    private Bundle args;
    private WebView web;
    private WebSourceView source;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle b) { super.onCreate(b); args = b; start(); }
    @Override public void onStart() {
        int code = -1;
        try {
            MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(),
                    MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(2500);
            main(() -> {
                Field request = MainActivity.class.getDeclaredField("playRequestId");
                request.setAccessible(true); request.setInt(activity, request.getInt(activity) + 1);
                Method release = MainActivity.class.getDeclaredMethod("releasePlayer");
                release.setAccessible(true); release.invoke(activity);
                source = (WebSourceView) field(activity, "webSourceView"); source.setListener(null);
                source.applyConfiguration("1080p", true, "windows", 1f);
                source.open(910001, "http://127.0.0.1:18891/warmup");
            });
            SystemClock.sleep(1800); // Settle container size before switching documents.
            main(() -> {
                source.open(910002, args.getString("url", "http://127.0.0.1:18891/slow"));
                web = (WebView) field(source, "webView");
            });
            JSONObject snapshot = null;
            for (int i = 0; i < 8; i++) {
                SystemClock.sleep(1000);
                snapshot = json("({url:location.href,ready:document.readyState,width:innerWidth,"
                        + "clientWidth:document.documentElement.clientWidth,screenWidth:screen.width,"
                        + "viewport:(document.querySelector('meta[name=viewport]')||{}).content,"
                        + "allViewports:Array.prototype.map.call(document.querySelectorAll('meta[name=viewport]'),function(m){return m.content;}),"
                        + "injected:!!window.__ntvViewportTarget})");
                report.append(snapshot).append('\n');
            }
            if (!args.containsKey("probe")) {
                if (snapshot == null || Math.abs(snapshot.getDouble("width") - 1920) > 2
                        || !snapshot.optBoolean("injected")) throw new AssertionError("Viewport not applied before load: " + snapshot);
                if (!args.containsKey("url") && !"interactive".equals(snapshot.getString("ready")))
                    throw new AssertionError("Slow resource fixture completed too early");
            }
            report.append("PASS\n");
        } catch (Throwable e) { code = 1; report.append(android.util.Log.getStackTraceString(e)); }
        Bundle result = new Bundle(); result.putString("stream", report.toString()); finish(code, result);
    }
    private JSONObject json(String expression) throws Exception {
        String[] value = {null}; CountDownLatch latch = new CountDownLatch(1);
        main(() -> web.evaluateJavascript("JSON.stringify(" + expression + ")",
                v -> { value[0] = v; latch.countDown(); }));
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("JS timeout");
        return new JSONObject((String) new JSONTokener(value[0]).nextValue());
    }
    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        Exception[] error = {null}; runOnMainSync(() -> { try { work.run(); } catch (Exception e) { error[0] = e; } });
        if (error[0] != null) throw error[0];
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
}
