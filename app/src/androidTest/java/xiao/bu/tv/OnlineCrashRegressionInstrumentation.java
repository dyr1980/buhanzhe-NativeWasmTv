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
            result.putString("stream", "PASS recycled EPG/channel rows and nonblocking TLS finalizer\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL " + android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }
}
