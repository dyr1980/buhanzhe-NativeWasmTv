package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Run in a fresh, separate applicationId; never clear the user's installed app. */
public final class StartupCatalogInstrumentation extends Instrumentation {
    private MainActivity activity;
    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        Throwable[] error = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(owner, args);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void startActivity(int expectedSource) throws Exception {
        activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        checkSelection(expectedSource);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while ((Integer) get(activity, "catalogGeneration") == 0 && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(20);
        check((Integer) get(activity, "catalogGeneration") > 0, "Full catalog did not load");
        checkSelection(expectedSource);
    }
    private void checkSelection(int expectedSource) throws Exception {
        main(() -> {
            Channel channel = (Channel) call(activity, "currentChannel", new Class<?>[0]);
            check("CCTV-1 综合".equals(channel.name), "Wrong first channel: " + channel.name);
            check((Integer) get(activity, "currentSourceIndex") == expectedSource,
                    "Source changed during startup: expected=" + expectedSource + " actual=" + get(activity, "currentSourceIndex"));
            check(channel.sourceCount() == 2, "Startup source list differs from full catalog");
            check(channel.sourceUrl(0).equals("webview://https://yangshipin.cn/tv/home?pid=600001859"),
                    "Wrong preferred source");
        });
    }
    private void closeActivity() throws Exception {
        if (activity == null) return;
        main(() -> activity.finish());
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!activity.isDestroyed() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20);
        check(activity.isDestroyed(), "Activity did not finish");
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try {
            startActivity(0);
            main(() -> {
                call(activity, "switchChannel", new Class<?>[]{int.class, int.class},
                        (Integer) get(activity, "currentChannelIndex"), 1);
                // Fresh installs may still be downloading resolver plugins and
                // return before playback persists its snapshot. Seed an existing
                // user's saved choice without depending on external downloads.
                call(activity, "saveLastChannelSnapshot", new Class<?>[]{ChannelCatalog.Group.class, Channel.class},
                        call(activity, "currentGroup", new Class<?>[0]),
                        call(activity, "currentChannel", new Class<?>[0]));
            });
            closeActivity();
            startActivity(1);
            result.putString("stream", "PASS clean first launch selects CCTV1 source 1 before/after catalog load; saved source 2 survives Activity restart and full catalog replacement\n");
        } catch (Throwable error) { code = 0; result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finally { try { closeActivity(); } catch (Exception ignored) { } }
        finish(code, result);
    }
}
