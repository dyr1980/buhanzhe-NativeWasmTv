package xiao.bu.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Receiver side of /api/multimedia/control protocol 1. Never encodes or sends media. */
final class MultimediaReceiver implements Closeable {
    private final MainActivity host;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile String receiverToken = "";
    private CastCursorChannel.Receiver cursor;
    boolean acceptsCursor(String token) { return !receiverToken.isEmpty() && receiverToken.equals(token); }
    private long receiverBeat;
    private boolean destroyed;
    MultimediaReceiver(MainActivity host) { this.host = host; }
    boolean active() { return !receiverToken.isEmpty(); }
    private <T> T onUi(Callable<T> action) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return action.call();
        FutureTask<T> task = new FutureTask<T>(action); ui.post(task);
        return task.get(8, TimeUnit.SECONDS);
    }

    String control(JSONObject request) throws Exception {
        // Control requests arrive on the HTTP worker; resolve before taking over the UI.
        final URI stream;
        final java.net.InetAddress peer;
        if ("start".equals(request.optString("action"))) {
            stream = new URI(request.optString("url"));
            if (!"rtsp".equals(stream.getScheme()) || stream.getHost() == null || !"/cast".equals(stream.getPath()))
                throw new IOException("无效的 RTSP 地址");
            peer = java.net.InetAddress.getByName(stream.getHost());
        } else {
            stream = null;
            peer = null;
        }
        return onUi(() -> {
            if (destroyed) throw new IOException("接收器已关闭");
            String action = request.optString("action");
            JSONObject result = new JSONObject().put("ok", true).put("protocol", 1);
            if ("hello".equals(action)) return result.put("takeoverMedia", true).toString();
            if ("state".equals(action)) return result.put("active", active()).toString();
            String token = request.optString("token");
            if (token.isEmpty()) throw new IOException("缺少会话标识");
            if ("start".equals(action)) {
                host.checkMultimediaReceiver(request.optString("takeoverSession"));
                if (active()) throw new IOException("当前已有接收会话");
                receiverToken = token; receiverBeat = SystemClock.elapsedRealtime();
                host.suspendForMultimedia();
                try {
                    cursor=new CastCursorChannel.Receiver(peer,
                            token,host::receiveCastCursor);
                    host.startMultimediaReceiver(stream.toString(), request.optString("transport", "tcp"), request.optString("title", "多媒体投屏"));
                    ui.post(watchdog);
                } catch (Exception error) { endReceiver(); throw error; }
            } else if (token.equals(receiverToken)) {
                if ("heartbeat".equals(action)) receiverBeat = SystemClock.elapsedRealtime();
                else if ("stop".equals(action)) endReceiver();
                else throw new IOException("未知接收指令");
            }
            return result.put("active", token.equals(receiverToken))
                    .put("cursorPort",token.equals(receiverToken) && cursor!=null ? cursor.port() : 0).toString();
        });
    }
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!active()) return;
            if (SystemClock.elapsedRealtime() - receiverBeat >= 3000) endReceiver();
            else ui.postDelayed(this, 250);
        }
    };
    private void endReceiver() {
        if (!active()) return;
        receiverToken = ""; ui.removeCallbacks(watchdog);
        if(cursor!=null){cursor.close();cursor=null;}
        if (!destroyed) host.restoreAfterMultimedia();
    }
    void returnToPrevious() { endReceiver(); }
    void stop() { endReceiver(); }
    @Override public void close() { destroyed = true; endReceiver(); }
}
