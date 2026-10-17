package xiao.bu.tv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Test-only raw USB tunnels for API 19, whose adbd lacks reverse forwarding. */
final class UsbLoopbackBridge implements Closeable {
    private final Set<Socket> sockets = new HashSet<>();
    private final ArrayList<ServerSocket> listeners = new ArrayList<>();
    private volatile boolean closed;

    UsbLoopbackBridge() throws IOException {
        try { listen(62020, 62022); listen(62021, 62023); }
        catch (IOException error) { close(); throw error; }
    }
    private static void daemon(Runnable job) {
        Thread thread = new Thread(job, "test-usb-loopback");
        thread.setDaemon(true); thread.start();
    }
    private boolean track(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        synchronized (sockets) {
            if (!closed) { sockets.add(socket); return true; }
        }
        socket.close(); return false;
    }
    private void listen(int frontPort, int tunnelPort) throws IOException {
        InetAddress local = InetAddress.getByName("127.0.0.1");
        ServerSocket front = new ServerSocket(); front.setReuseAddress(true);
        front.bind(new java.net.InetSocketAddress(local, frontPort), 8);
        listeners.add(front);
        ServerSocket back = new ServerSocket(); back.setReuseAddress(true);
        back.bind(new java.net.InetSocketAddress(local, tunnelPort), 16);
        listeners.add(back);
        LinkedBlockingQueue<Socket> tunnels = new LinkedBlockingQueue<>(32);
        daemon(() -> {
            while (!closed) try {
                Socket socket = back.accept();
                if (track(socket) && !tunnels.offer(socket)) drop(socket);
            } catch (IOException error) { if (!closed) android.util.Log.w("UsbLoopback", "tunnel accept", error); }
        });
        daemon(() -> {
            while (!closed) try {
                Socket client = front.accept();
                if (track(client)) daemon(() -> {
                    Socket tunnel = null;
                    try {
                        tunnel = tunnels.poll(10, TimeUnit.SECONDS);
                        if (tunnel == null) throw new IOException("USB peer unavailable");
                        final Socket peer = tunnel;
                        daemon(() -> copy(client, peer));
                        copy(peer, client);
                    } catch (IOException | InterruptedException error) {
                        if (!closed) android.util.Log.w("UsbLoopback", "pair", error);
                    } finally { drop(client); if (tunnel != null) drop(tunnel); }
                });
            } catch (IOException error) { if (!closed) android.util.Log.w("UsbLoopback", "client accept", error); }
        });
    }
    private void copy(Socket from, Socket to) {
        try {
            InputStream input = from.getInputStream(); OutputStream output = to.getOutputStream();
            byte[] bytes = new byte[32768]; int count;
            while (!closed && (count = input.read(bytes)) >= 0) output.write(bytes, 0, count);
        } catch (IOException ignored) { }
        finally { drop(from); drop(to); }
    }
    private void drop(Socket socket) {
        synchronized (sockets) { sockets.remove(socket); }
        try { socket.close(); } catch (IOException ignored) { }
    }
    @Override public void close() {
        closed = true;
        for (ServerSocket listener : listeners) try { listener.close(); } catch (IOException ignored) { }
        ArrayList<Socket> snapshot;
        synchronized (sockets) { snapshot = new ArrayList<>(sockets); }
        for (Socket socket : snapshot) drop(socket);
    }
}
