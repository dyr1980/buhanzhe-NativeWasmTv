package xiao.bu.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Fault injection is confined to this isolated instrumentation process. */
public final class CrashRecoveryInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Activity management;
    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { failure[0] = e; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static Object call(Object owner, String name) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name);
        m.setAccessible(true); return m.invoke(owner);
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void resetProviderFailure() throws Exception {
        Field f = WebViewAvailability.class.getDeclaredField("failure");
        f.setAccessible(true); f.set(null, null);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try {
            main(() -> {
                for (boolean linkage : new boolean[] {false, true}) {
                    resetProviderFailure();
                    int[] attempts = {0};
                    for (int i = 0; i < 2; i++) {
                        try {
                            WebViewAvailability.create(() -> {
                                attempts[0]++;
                                if (linkage) throw new NoClassDefFoundError("android.util.UMLog");
                                throw new NullPointerException("missing WebView provider");
                            });
                            throw new AssertionError("Provider failure escaped guard");
                        } catch (WebViewAvailability.UnavailableException expected) {
                            check(expected.getCause() != null, "Lost cause");
                        }
                    }
                    check(attempts[0] == 1, "Retried half-initialized provider");
                }
            });
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(() -> {
                ChannelCatalog.Group[] saved = ChannelCatalog.GROUPS;
                try {
                    ChannelCatalog.GROUPS = new ChannelCatalog.Group[0];
                    check(call(activity, "currentChannel") != null, "Empty catalog recovery failed");
                    ChannelCatalog.GROUPS = new ChannelCatalog.Group[] {
                            new ChannelCatalog.Group("empty", 3, null)};
                    check(call(activity, "currentChannel") != null, "Empty group recovery failed");
                } finally { ChannelCatalog.GROUPS = saved; }
                WebSourceView browser = new WebSourceView(activity, null);
                browser.open(77, "https://example.com/");
                browser.destroyPage();
            });
            management = startActivitySync(new Intent(getTargetContext(), ManagementActivity.class)
                    .putExtra(ManagementActivity.EXTRA_URL, "http://127.0.0.1:9966/")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(() -> {
                check(!management.isFinishing(), "Management error panel not shown");
                management.onBackPressed();
                check(management.isFinishing(), "Cannot return from WebView failure");
                resetProviderFailure();
                WebView healthy = WebViewAvailability.create(() -> new WebView(activity));
                healthy.destroy();
            });
            result.putString("stream", "PASS missing/broken WebView guarded; poisoned provider not retried; browser and management survive; back works; empty catalogs recover; healthy WebView works\n");
        } catch (Throwable error) {
            code = 0; result.putString("stream", android.util.Log.getStackTraceString(error));
        } finally {
            try { main(() -> {
                resetProviderFailure();
                if (management != null) management.finish();
                if (activity != null) activity.finish();
            }); } catch (Exception ignored) { }
        }
        finish(code, result);
    }
}
