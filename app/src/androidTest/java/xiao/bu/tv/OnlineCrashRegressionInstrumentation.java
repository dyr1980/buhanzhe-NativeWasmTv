package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.View;
import java.net.Socket;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;

/** Real row recycling and finalizer contention regressions for the online reports. */
public final class OnlineCrashRegressionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class FailingPage extends View implements xiao.bu.tv.jar.ChannelPlugin,
            xiao.bu.tv.jar.ChannelPlugin.NativePage {
        int disposed;
        FailingPage(android.content.Context context) { super(context); }
        @Override public boolean isShown() { return true; }
        public View create(android.content.Context context, ClassLoader loader, org.json.JSONObject config,
                xiao.bu.tv.jar.ChannelPlugin.Host host) { return this; }
        public void update(long position, boolean playing) { }
        public void dispose() { disposed++; }
        public boolean onBack() { throw new android.os.NetworkOnMainThreadException(); }
        public void setActive(boolean active) { throw new android.os.NetworkOnMainThreadException(); }
        @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            throw new NoSuchMethodError("old plugin ABI");
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            throw new IllegalStateException("plugin pointer callback");
        }
    }
    private void pluginCallbacks() throws Exception {
        FrameLayout parent = new FrameLayout(getTargetContext());
        for (int operation = 0; operation < 4; operation++) {
            JarChannelRuntime runtime = new JarChannelRuntime(getTargetContext(), parent);
            FailingPage page = new FailingPage(getTargetContext());parent.addView(page);
            final int[] messages = {0};
            JarChannelRuntime.Callback owner = new JarChannelRuntime.Callback() {
                public boolean current() { return true; }
                public void play(String url) { throw new AssertionError("Unexpected playback"); }
                public void status(String message) { messages[0]++; }
                public void configureBrowser(android.webkit.WebView view) { }
            };
            for (String name : new String[]{"plugin", "view", "callback"}) {
                Field field = JarChannelRuntime.class.getDeclaredField(name);field.setAccessible(true);
                field.set(runtime, "callback".equals(name) ? owner : page);
            }
            try {
                if (operation == 0) check(runtime.goBackIfPossible(), "Failed back not consumed");
                if (operation == 1) runtime.setActive(false);
                if (operation == 2) check(runtime.dispatchMedia(new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)), "Failed key not consumed");
                if (operation == 3) {
                    android.view.MotionEvent event = android.view.MotionEvent.obtain(1, 1,
                            android.view.MotionEvent.ACTION_DOWN, 0, 0, 0);
                    try { check(runtime.dispatchPointer(event, true), "Failed touch not consumed"); }
                    finally { event.recycle(); }
                }
                check(!runtime.visible() && page.disposed == 1 && messages[0] == 1,
                        "Plugin failure did not detach and dispose exactly once: " + operation);
            } finally { runtime.destroy(); }
        }
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            final Throwable[] uiFailure = {null};
            runOnMainSync(() -> {
                try {
                FrameLayout parent = new FrameLayout(getTargetContext());
                UiScaleHelper scale = new UiScaleHelper();
                EpgListAdapter epg = new EpgListAdapter(getTargetContext(), scale);
                long now = System.currentTimeMillis();
                epg.showPrograms(Collections.singletonList(new EpgManager.Program(now - 1000, now + 60000, "节目")));
                View row = epg.getView(0, new TextView(getTargetContext()), parent);
                row.setTag(null);
                row = epg.getView(0, row, parent);
                check("节目".contentEquals(((TextView) row.findViewById(R.id.epg_program_title)).getText()), "EPG row lost title");
                epg.showPrograms(Collections.emptyList());
                check(epg.getItem(0) == null && epg.getItem(-1) == null, "Stale EPG position was not rejected");
                row = epg.getView(0, row, parent);
                check(((TextView)row.findViewById(R.id.epg_program_title)).getText().length() == 0
                        && ((TextView)row.findViewById(R.id.epg_program_time)).getText().length() == 0
                        && !row.isActivated(), "Empty EPG retained recycled content");
                epg.getView(-1, row, parent);
                epg.showPrograms(Collections.singletonList(new EpgManager.Program(now - 1000, now + 60000, "恢复节目")));
                row = epg.getView(0, row, parent);
                check("恢复节目".contentEquals(((TextView)row.findViewById(R.id.epg_program_title)).getText()), "EPG failed to recover after empty refresh");
                epg.getView(1, row, parent);
                ChannelListAdapter channels = new ChannelListAdapter(getTargetContext(), scale);
                View group = channels.getView(0, null, parent);
                channels.showChannels(0, ChannelCatalog.CCTV_CHANNELS, 0, 0, 0);
                View channel = channels.getView(0, group, parent);
                check(channel != group && channel.findViewById(R.id.channel_item_program) != null, "Group reused as channel");
                channels.showGroups(ChannelCatalog.GROUPS, 0);
                View replacement = channels.getView(0, channel, parent);
                check(replacement != channel, "Channel reused as group");
                replacement.setTag("foreign holder");
                channels.getView(0, replacement, parent);
                String selected = getTargetContext().getSharedPreferences("tv_ui", 0)
                        .getString("selected", "builtin");
                try {
                    for (String theme : TvUiPlugin.IDS) {
                        TvUiPlugin.select(getTargetContext(), theme);
                        ChannelListAdapter adapter = new ChannelListAdapter(getTargetContext(), scale);
                        adapter.showChannels(0, ChannelCatalog.CCTV_CHANNELS, 0, 0, 0);
                        View valid = adapter.getView(0, null, parent);
                        for (int position : new int[]{-1, adapter.getCount(), Integer.MAX_VALUE}) {
                            check(adapter.getItem(position) == null, "Stale item survived: " + theme);
                            check(!adapter.getView(position, valid, parent).isEnabled(),
                                    "Stale channel retained an active row: " + theme);
                        }
                        adapter.showGroups(new ChannelCatalog.Group[0], 0);
                        check(!adapter.getView(0, valid, parent).isEnabled(),
                                "Cleared group list accepted a queued bind: " + theme);
                        check(theme.equals(new org.json.JSONObject(TvUiPlugin.status(getTargetContext()))
                                .getString("selected")), "Stale bind reset the selected theme: " + theme);
                        adapter.showChannels(0, ChannelCatalog.CCTV_CHANNELS, 0, 0, 0);
                        check(adapter.getView(0, valid, parent).isEnabled(),
                                "Valid channel did not recover: " + theme);
                    }
                } finally { TvUiPlugin.select(getTargetContext(), selected); }
                pluginCallbacks();
                } catch (Throwable error) { uiFailure[0] = error; }
            });
            if (uiFailure[0] != null) throw uiFailure[0];
            Constructor<LegacyTlsSocket> constructor = LegacyTlsSocket.class.getDeclaredConstructor(Socket.class, String.class, boolean.class, X509TrustManager.class);
            constructor.setAccessible(true);
            final int[] closes = {0};
            Socket transport = new Socket() {
                @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(new byte[0]); }
                @Override public java.io.OutputStream getOutputStream() { return new java.io.ByteArrayOutputStream(); }
                @Override public synchronized void close() { closes[0]++; }
            };
            LegacyTlsSocket socket = constructor.newInstance(transport, "localhost", true, null);
            Field engineField = LegacyTlsSocket.class.getDeclaredField("engine");
            engineField.setAccessible(true);
            Object engine = engineField.get(socket);
            CountDownLatch finished = new CountDownLatch(1);
            final Throwable[] failure = {null};
            synchronized (engine) {
                Thread finalizer = new Thread(() -> {
                    try { socket.finalize(); } catch (Throwable error) { failure[0] = error; }
                    finally { finished.countDown(); }
                }, "test-finalizer");
                finalizer.start();
                check(finished.await(1, TimeUnit.SECONDS), "Finalizer waited on TLS engine monitor");
            }
            check(failure[0] == null && closes[0] == 0, "Finalizer performed transport I/O");
            socket.close();
            check(closes[0] == 1, "Explicit close stopped closing transport");
            CountDownLatch registration = new CountDownLatch(1), releaseRegistration = new CountDownLatch(1);
            final boolean[] registeredOnMain = {false};
            android.content.Context context = new android.content.ContextWrapper(getTargetContext()) {
                @Override public android.content.Context getApplicationContext() { return this; }
                @Override public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
                    registeredOnMain[0] = android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
                    registration.countDown();
                    try { releaseRegistration.await(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    return null;
                }
            };
            try {
                runOnMainSync(() -> ReceivedApkCleanup.initialize(context));
                check(registration.await(1, TimeUnit.SECONDS) && !registeredOnMain[0], "Install receiver registration blocked main thread");
            } finally { releaseRegistration.countDown(); }
            HlsProxyServer proxy = new HlsProxyServer();
            try {
                java.lang.reflect.Method decrypt = HlsProxyServer.class.getDeclaredMethod("decryptCctvSegment", byte[].class, String.class, boolean.class);
                decrypt.setAccessible(true);
                try {
                    decrypt.invoke(proxy, new byte[0], "https://localhost/test.ts", false);
                    throw new AssertionError("Missing plugin was not rejected");
                } catch (java.lang.reflect.InvocationTargetException expected) {
                    check(expected.getCause() instanceof java.io.IOException, "Missing plugin escaped as fatal Error: " + expected.getCause());
                }
            } finally { proxy.close(); }
            result.putString("stream", "PASS recycled EPG/channel rows, six-theme stale/empty channel binds, plugin back/lifecycle/key/touch exception containment, refresh recovery and nonblocking TLS finalizer\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL " + android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }
}
