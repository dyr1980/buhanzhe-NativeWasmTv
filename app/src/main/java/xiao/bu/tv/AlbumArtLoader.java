package xiao.bu.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Cancellable, bounded logo/ID3 reads; slow streams never block cached station artwork. */
final class AlbumArtLoader {
    interface Callback { void loaded(Bitmap art); }
    interface NeighborCallback { void loaded(String url, Bitmap art); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private static ThreadPoolExecutor executor(String name) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10L,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), task -> {
                Thread thread = new Thread(task, name);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
    private final ThreadPoolExecutor worker = executor("album-art");
    // A slow old live-stream read must not queue in front of the next cached logo.
    private final ThreadPoolExecutor tags = executor("album-id3");
    private final ThreadPoolExecutor neighbors = executor("album-neighbors");
    private Future<?> pendingNeighbors;
    private volatile int neighborGeneration;
    private volatile boolean closed;
    private static volatile int memoryGeneration;
    private Future<?> pending;
    private Future<?> pendingTag;
    private static AlbumArtCache coverCache;
    // Decoded covers are small (<=512px). Keep a bounded hot set; never recycle a
    // bitmap here because the record/controller may still be drawing it.
    private static final android.util.LruCache<String, Bitmap> decoded =
            new android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };

    private static synchronized AlbumArtCache cache(Context app) throws IOException {
        if (coverCache == null) coverCache = new AlbumArtCache(
                new java.io.File(app.getCacheDir(), "album-art-v1"), AlbumArtCache.MAX_BYTES);
        return coverCache;
    }
    private volatile int generation;

    static void trimMemory() {
        synchronized (decoded) {
            memoryGeneration++;
            decoded.evictAll(); // Displayed covers remain valid; never recycle shared pixels.
        }
    }

    private static void cacheDecoded(String key, Bitmap bitmap, int expectedGeneration) {
        synchronized (decoded) {
            if (expectedGeneration == memoryGeneration && !Thread.currentThread().isInterrupted())
                decoded.put(key, bitmap);
        }
    }

    static Bitmap cachedLogo(String url) {
        if (url == null || url.isEmpty()) return null;
        try { return decoded.get(AlbumArtCache.key("image\n" + url + "\n")); }
        catch (IOException ignored) { return null; }
    }

    void prefetchNeighbors(Context context, String previous, String next, NeighborCallback callback) {
        cancelNeighbors();
        if (closed) return;
        final int request = neighborGeneration;
        final Context app = context.getApplicationContext();
        pendingNeighbors = neighbors.submit(() -> {
            String last = "";
            for (String url : new String[] {previous, next}) {
                if (Thread.currentThread().isInterrupted()) return;
                if (url == null || url.equals(last) || !(url.startsWith("http://") || url.startsWith("https://"))) continue;
                last = url;
                Bitmap art = readArtwork(app, url, null, true);
                if (art != null && !closed && request == neighborGeneration)
                    main.post(() -> { if (!closed && request == neighborGeneration) callback.loaded(url, art); });
            }
        });
    }

    void cancelNeighbors() {
        neighborGeneration++;
        if (pendingNeighbors != null) pendingNeighbors.cancel(true);
        neighbors.getQueue().clear();
        pendingNeighbors = null;
    }

    void clear() {
        generation++;
        if (pending != null) pending.cancel(true);
        worker.getQueue().clear();
        pending = null;
        if (pendingTag != null) pendingTag.cancel(true);
        tags.getQueue().clear();
        pendingTag = null;
    }

    void load(Context context, String address, String headers, String logoUrl, Callback callback) {
        clear();
        if (closed) return;
        final int request = generation;
        final Context app = context.getApplicationContext();
        pending = worker.submit(() -> {
            // Show the station cover before probing the live stream for ID3. Previously
            // even a cached logo waited for a fresh network request on every switch.
            Bitmap logo = null;
            if (logoUrl != null && (logoUrl.startsWith("https://") || logoUrl.startsWith("http://"))) {
                logo = readArtwork(app, logoUrl, null, true);
                if (logo != null) publish(request, callback, logo);
            }
            if (address == null || Thread.currentThread().isInterrupted()) return;
            final boolean hasLogo = logo != null;
            main.post(() -> {
                if (closed || request != generation) return;
                pendingTag = tags.submit(() -> {
                    Bitmap bitmap = readArtwork(app, address, headers, false);
                    // A real embedded cover supersedes the station logo when available.
                    if (bitmap != null || !hasLogo) publish(request, callback, bitmap);
                });
            });
        });
    }

    private void publish(int request, Callback callback, Bitmap art) {
        if (closed || request != generation) return;
        main.post(() -> { if (!closed && request == generation) callback.loaded(art); });
    }

    private static Bitmap readArtwork(Context app, String address, String headers, boolean image) {
        final int expectedMemory = memoryGeneration;
        if (Thread.currentThread().isInterrupted()) return null;
        HttpURLConnection connection = null;
        AlbumArtCache disk = null;
        String cacheKey = null;
        try {
            Uri uri = Uri.parse(address);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                try {
                    disk = cache(app);
                    // Distinguish an embedded APIC from an image URL and authenticated variants.
                    cacheKey = AlbumArtCache.key((image ? "image\n" : "apic\n") + address + "\n" + (headers == null ? "" : headers));
                    Bitmap memoryHit = decoded.get(cacheKey);
                    if (memoryHit != null) return memoryHit;
                    byte[] cached = disk.get(cacheKey);
                    if (cached != null) {
                        Bitmap hit = decodeArtwork(cached);
                        if (hit != null) { cacheDecoded(cacheKey, hit, expectedMemory); return hit; }
                        disk.remove(cacheKey);
                    }
                } catch (IOException ignored) { disk = null; } // Disk failure must not block playback.
            }
            InputStream input;
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                connection = NetworkClient.open(new URL(address));
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", "nTv/" + BuildConfig.VERSION_NAME);
                if (!image) {
                    connection.setRequestProperty("Range", "bytes=0-" + (Id3Artwork.MAX_TAG_BYTES + 9));
                    connection.setRequestProperty("Icy-MetaData", "0");
                }
                if (headers != null) for (String line : headers.split("\r?\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) connection.setRequestProperty(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
                input = connection.getInputStream();
            } else {
                if (uri.getScheme() == null) uri = Uri.fromFile(new java.io.File(address));
                input = app.getContentResolver().openInputStream(uri);
            }
            try (InputStream stream = input) {
                byte[] art = stream == null ? null : image ? readImage(stream) : Id3Artwork.read(stream);
                if (art != null && !Thread.currentThread().isInterrupted()) {
                    Bitmap decoded = decodeArtwork(art);
                    if (decoded != null && cacheKey != null) cacheDecoded(cacheKey, decoded, expectedMemory);
                    if (decoded != null && disk != null && !Thread.currentThread().isInterrupted()) {
                        try { disk.put(cacheKey, art); } catch (IOException ignored) { }
                    }
                    return decoded;
                }
            }
        } catch (Exception ignored) {
            // Live MP3 usually has no APIC tag. Artwork must never delay or fail playback.
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private static Bitmap decodeArtwork(byte[] art) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(art, 0, art.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(art, 0, art.length, options);
    }

    private static byte[] readImage(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long deadline = System.nanoTime() + 6000000000L;
        while (bytes.size() <= Id3Artwork.MAX_TAG_BYTES) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) return null;
            int count = input.read(buffer, 0, Math.min(buffer.length, Id3Artwork.MAX_TAG_BYTES - bytes.size() + 1));
            if (count < 0) return bytes.toByteArray();
            if (count > Id3Artwork.MAX_TAG_BYTES - bytes.size()) return null;
            bytes.write(buffer, 0, count);
        }
        return null;
    }

    void close() {
        closed = true;
        clear(); cancelNeighbors();
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow(); tags.shutdownNow(); neighbors.shutdownNow();
    }
}
