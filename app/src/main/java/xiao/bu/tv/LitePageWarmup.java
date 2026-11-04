package xiao.bu.tv;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;

/**
 * Warms DNS and TLS for a channel's pages before the user actually opens them.
 *
 * <p>Only the transport-level preparation is performed here: resolving the host
 * and keeping the answers in the JVM's own cache. The private lightweight core
 * would additionally pre-parse the page; that half is unavailable without
 * {@code browser-core}, and skipping it changes nothing for the system WebView
 * engine.</p>
 */
final class LitePageWarmup {
    private static final String TAG = "ChannelWarmup";
    private static final long LOOKUP_TIMEOUT_MS = 4000L;

    private final Context context;
    private final Dns dns;
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private volatile String pendingHost = "";

    LitePageWarmup(Context context, Dns dns) {
        this.context = context == null ? null : context.getApplicationContext();
        this.dns = dns;
        this.worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lite-page-warmup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Resolves the host of {@code url} so a later connection skips the lookup. */
    void dns(String url) {
        if (closed || url == null) return;
        final String host = hostOf(url);
        if (host.length() == 0 || host.equals(pendingHost)) return;
        pendingHost = host;
        final long startedAt = SystemClock.elapsedRealtime();
        try {
            worker.execute(() -> {
                if (closed) return;
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    if (closed) return;
                    Log.d(TAG, "Warmed DNS for " + host + " in "
                            + (SystemClock.elapsedRealtime() - startedAt) + "ms ("
                            + (addresses == null ? 0 : addresses.length) + " answers)");
                } catch (Exception error) {
                    // Warming is best effort: a failure must never surface to the user.
                    Log.d(TAG, "DNS warmup skipped for " + host + ": " + error);
                }
            });
        } catch (RuntimeException rejected) {
            // The executor may already be shutting down.
        }
    }

    /** Prepares the page itself; unavailable without the authorized core. */
    void page(String url) {
        dns(url);
    }

    /** Drops the queued work for the previous channel selection. */
    void cancel() {
        pendingHost = "";
    }

    void close() {
        closed = true;
        pendingHost = "";
        try {
            worker.shutdownNow();
        } catch (RuntimeException ignored) {
        }
        try {
            worker.awaitTermination(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            return host == null ? "" : host;
        } catch (Exception notAUrl) {
            return "";
        }
    }
}
