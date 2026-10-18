package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.lang.reflect.Method;

/** HTTP regressions exercise the real updater without launching playback or installation. */
public final class UpdateManifestInstrumentation extends Instrumentation {
    private ServerSocket server;
    private volatile int primaryStatus = 200, legacyStatus = 200, legacyReads;
    private volatile String primaryBody = "{\"versionCode\":7}";
    private volatile String lastRequest;
    private boolean live;
    private static final String LEGACY = "{\"versionCode\":6}";

    @Override public void onCreate(Bundle args) {
        live=args!=null && "true".equals(args.getString("live"));
        super.onCreate(args); start();
    }
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private void pass(String message) {
        Bundle out = new Bundle(); out.putString("stream", "PASS " + message + "\n"); sendStatus(0, out);
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int status = -1;
        String originalBase=GithubProxy.baseUrl();
        boolean originalEnabled=GithubProxy.isEnabled();
        try {
            for (String invalid : new String[]{null,"","  ","?_=1789530265847","/version-lite.json",
                    "ftp://example.com/version.json","https:///version.json","https://example.com/bad path"}) {
                try { AutoUpdater.manifestRequestUrl(invalid,42L); throw new AssertionError("Accepted invalid update URL"); }
                catch (IOException expected) {
                    check(!(expected instanceof MalformedURLException),"Leaked raw URL exception");
                    check(expected.getMessage().contains("更新地址"),"Missing readable URL error");
                }
            }
            check(AutoUpdater.manifestRequestUrl(" https://github.com/a/version.json?key=v#section ",42L)
                    .equals("https://github.com/a/version.json?key=v&_=42"),"Query or fragment corrupted");
            check(AutoUpdater.manifestRequestUrl("https://github.com/a/version.json",42L)
                    .equals("https://github.com/a/version.json?_=42"),"Missing canonical URL");
            pass("blank/relative update URLs rejected before timestamp; query and fragment handled");
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            Thread fixture = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(2000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                        String request = in.readLine(), line;
                        if (request == null) continue;
                        lastRequest=request;
                        while ((line = in.readLine()) != null && !line.isEmpty()) { }
                        boolean legacy = request.contains("/version.json?");
                        if (legacy) legacyReads++;
                        int code = legacy ? legacyStatus : primaryStatus;
                        byte[] body = (legacy ? LEGACY : primaryBody).getBytes("UTF-8");
                        OutputStream out = socket.getOutputStream();
                        out.write(("HTTP/1.1 " + code + " Test\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "
                                + body.length + "\r\n\r\n").getBytes("UTF-8"));
                        out.write(body); out.flush();
                    } catch (IOException ignored) { }
                }
            }, "update-fixture");
            fixture.start();
            String base = "http://127.0.0.1:" + server.getLocalPort();
            AutoUpdater updater = new AutoUpdater(null);
            check(updater.readManifest(base + "/version-lite.json").equals(primaryBody), "Lite result");
            check(legacyReads == 0, "Lite must not request legacy");
            check(lastRequest.contains("/version-lite.json?_="),"Local request lost path or timestamp");
            GithubProxy.setBaseUrl(base+"/proxy/");
            GithubProxy.setEnabled(true);
            check(updater.readManifest("https://github.com/a/releases/latest/download/version-lite.json")
                    .equals(primaryBody),"Accelerated metadata failed");
            check(lastRequest.contains("/proxy/https://github.com/a/releases/latest/download/version-lite.json?_="),
                    "Accelerator lost or duplicated canonical URL");
            GithubProxy.setBaseUrl(originalBase);
            GithubProxy.setEnabled(originalEnabled);
            pass("real HTTP requests preserve canonical URL through shared accelerator routing");
            for (int code : new int[]{404, 403, 500}) {
                primaryStatus = code;
                try { updater.readManifest(base + "/version-lite.json"); throw new AssertionError("Expected HTTP error"); }
                catch (IOException expected) { check(expected.getMessage().contains(String.valueOf(code)), "Preserve HTTP error"); }
            }
            check(legacyReads == 0, "Lightweight checks must never fetch full metadata");
            pass("404/403/500 remain readable errors; no request falls back to version.json");
            if (live) {
            Method load = AutoUpdater.class.getDeclaredMethod("loadUpdateInfo", String.class, boolean.class);
            load.setAccessible(true);
            Object info = load.invoke(updater, "https://github.com/buhanzhe/NativeWasmTv/releases/latest/download/version-lite.json", true);
            check(info != null, "Live metadata parsed");
            pass("live GitHub accelerated release check and architecture-specific metadata parsing");
            }
            result.putString("stream", "ALL UPDATE MANIFEST CHECKS PASSED\n");
        } catch (Throwable error) { status = 0; result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finally {
            GithubProxy.setBaseUrl(originalBase); GithubProxy.setEnabled(originalEnabled);
            try { if (server != null) server.close(); } catch (IOException ignored) { }
        }
        finish(status, result);
    }
}
