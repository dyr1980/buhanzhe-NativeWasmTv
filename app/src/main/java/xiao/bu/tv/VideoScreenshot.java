package xiao.bu.tv;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.PixelCopy;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot copies only: no recording permission, extra decoder or per-frame work. */
final class VideoScreenshot {
    private static final String TAG = "VideoScreenshot";
    static final String PATH = "/api/recording/screenshot";
    // Allow detailed full-resolution captures without silently downscaling them.
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 360;
    private final AtomicBoolean busy = new AtomicBoolean();

    interface Source {
        Target current() throws IOException;
    }

    static final class Target {
        final DirectVideoView view;
        final Object session;
        final int width;
        final int height;

        Target(DirectVideoView view, Object session, int width, int height) {
            this.view = view;
            this.session = session;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        Target forPreview() {
            float scale = Math.min(1f, Math.min((float) PREVIEW_WIDTH / width,
                    (float) PREVIEW_HEIGHT / height));
            return new Target(view, session, Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)));
        }
    }

    byte[] capture(final Source source) throws IOException {
        return capture(source, false);
    }

    byte[] capturePreview(final Source source) throws IOException {
        return capture(source, true);
    }

    private byte[] capture(final Source source, final boolean preview) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return captureTexture(source, preview);
        }
        acquire(preview);
        final Capture request = new Capture();
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            @Override public void run() {
                if (request.isAbandoned()) {
                    busy.set(false);
                    return;
                }
                Bitmap bitmap = null;
                try {
                    Target target = source.current();
                    if (preview) target = target.forPreview();
                    bitmap = Bitmap.createBitmap(target.width, target.height,
                            Bitmap.Config.ARGB_8888);
                    requestCopy(source, target, bitmap, request, main);
                } catch (Exception error) {
                    if (bitmap != null) bitmap.recycle();
                    request.complete(null, new IOException(error.getMessage(), error));
                    busy.set(false);
                } catch (OutOfMemoryError error) {
                    if (bitmap != null) bitmap.recycle();
                    request.complete(null, new IOException("内存不足，暂时无法截屏"));
                    busy.set(false);
                }
            }
        });
        Bitmap bitmap;
        try {
            bitmap = request.await();
        } catch (IOException error) {
            // A timeout can race the callback just after it publishes the image.
            // Pending copies release this flag themselves when they finally finish.
            if (request.ready.getCount() == 0) busy.set(false);
            throw error;
        }
        try {
            return encode(bitmap, preview);
        } catch (OutOfMemoryError error) {
            throw new IOException("内存不足，暂时无法保存截屏");
        } finally {
            bitmap.recycle();
            busy.set(false);
        }
    }

    private static <T> T onMain(java.util.concurrent.Callable<T> action) throws IOException {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<T>(action);
        new Handler(Looper.getMainLooper()).post(task);
        try {
            return task.get(8, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("截图读取画面失败", cause);
        } catch (java.util.concurrent.TimeoutException error) {
            task.cancel(false);
            throw new IOException("截图读取超时", error);
        } catch (InterruptedException error) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new IOException("截图已取消", error);
        }
    }

    private byte[] captureTexture(final Source source, boolean preview) throws IOException {
        acquire(preview);
        Bitmap bitmap = null;
        long started = SystemClock.elapsedRealtime();
        try {
            Target selected = onMain(() -> source.current());
            final Target target = preview ? selected.forPreview() : selected;
            if (!target.view.usesTextureOutput())
                throw new IOException("当前视频输出不支持纹理截图");
            final Target current = target;
            // TextureView has received the decoder output since playback began.
            // Read one source-size frame without changing MediaPlayer's Surface or codec state.
            bitmap = onMain(() -> {
                if (source.current().session != current.session) throw new IOException("频道已切换，请重试");
                return current.view.captureTextureFrame(current.width, current.height);
            });
            if (bitmap == null) throw new IOException("未收到截图画面，请在播放时重试");
            onMain(() -> {
                if (source.current().session != current.session) throw new IOException("频道已切换，请重试");
                return null;
            });
            long copied = SystemClock.elapsedRealtime();
            byte[] bytes = encode(bitmap, preview);
            Log.i(TAG, (preview ? "Preview" : "Screenshot") + " size="
                    + target.width + "x" + target.height + " readMs=" + (copied - started)
                    + " encodeMs=" + (SystemClock.elapsedRealtime() - copied)
                    + " bytes=" + bytes.length);
            return bytes;
        } catch (OutOfMemoryError error) {
            throw new IOException("内存不足，暂时无法截图");
        } finally {
            if (bitmap != null) bitmap.recycle();
            busy.set(false);
        }
    }

    private void acquire(boolean preview) throws IOException {
        if (busy.compareAndSet(false, true)) return;
        // The explicit screenshot may arrive just after the controller's tiny preview.
        // Give that one-shot preview a moment to finish rather than rejecting the tap.
        long deadline = SystemClock.elapsedRealtime() + (preview ? 0L : 1500L);
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                Thread.sleep(40L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("截图已取消", error);
            }
            if (busy.compareAndSet(false, true)) return;
        }
        throw new IOException("正在截屏，请稍后再试");
    }

    private static byte[] encode(Bitmap bitmap, boolean preview) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, preview ? 78 : 95, output)) {
            throw new IOException(preview ? "预览图片生成失败" : "截图图片生成失败");
        }
        return output.toByteArray();
    }

    @TargetApi(24)
    private void requestCopy(final Source source, final Target target, final Bitmap bitmap,
            final Capture request, Handler handler) {
        PixelCopy.request(target.view.getSurfaceView(), bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override public void onPixelCopyFinished(int result) {
                IOException failure = null;
                try {
                    if (result != PixelCopy.SUCCESS) {
                        throw new IOException("当前视频画面尚不可截取，请出画后重试（" + result + "）");
                    }
                    if (source.current().session != target.session) {
                        throw new IOException("截屏时频道已切换，请重试");
                    }
                } catch (Exception error) {
                    failure = new IOException(error.getMessage(), error);
                } finally {
                    if (failure != null) bitmap.recycle();
                    request.complete(failure == null ? bitmap : null, failure);
                    if (failure != null || request.isAbandoned()) busy.set(false);
                }
            }
        }, handler);
    }

    private static final class Capture {
        final CountDownLatch ready = new CountDownLatch(1);
        private Bitmap bitmap;
        private IOException error;
        private boolean abandoned;

        synchronized boolean isAbandoned() { return abandoned; }

        synchronized void complete(Bitmap image, IOException failure) {
            if (abandoned) {
                if (image != null) image.recycle();
            } else {
                bitmap = image;
                error = failure;
            }
            ready.countDown();
        }

        Bitmap await() throws IOException {
            boolean completed = false;
            try {
                completed = ready.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                if (!completed) {
                    abandoned = true;
                    if (bitmap != null) bitmap.recycle();
                    throw new IOException("截屏超时，请稍后重试");
                }
                if (error != null) throw error;
                if (bitmap == null) throw new IOException("未获取到视频画面");
                return bitmap;
            }
        }
    }

    static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(60000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            InputStream input = status == 200
                    ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (input != null) {
                try {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) {
                            throw new IOException("截屏响应过大");
                        }
                        output.write(buffer, 0, count);
                    }
                } finally { input.close(); }
            }
            byte[] bytes = output.toByteArray();
            if (status != 200) {
                String message = "截屏失败：HTTP " + status;
                try { message = new JSONObject(new String(bytes, "UTF-8"))
                        .optString("message", message); } catch (Exception ignored) { }
                throw new IOException(message);
            }
            boolean jpeg = bytes.length >= 4 && bytes[0] == (byte) 0xff
                    && bytes[1] == (byte) 0xd8
                    && bytes[bytes.length - 2] == (byte) 0xff
                    && bytes[bytes.length - 1] == (byte) 0xd9;
            boolean png = bytes.length >= 8 && bytes[0] == (byte) 137
                    && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26
                    && bytes[7] == 10;
            if (!jpeg && !png) {
                throw new IOException("设备未返回有效的 JPEG/PNG 截屏，请更新设备端 APP");
            }
            return bytes;
        } finally { connection.disconnect(); }
    }
}
