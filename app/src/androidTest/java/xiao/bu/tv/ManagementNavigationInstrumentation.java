package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import java.io.*;
import java.net.*;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual bundled multi-page HTML + Activity Back, isolated from any TV playback. */
public final class ManagementNavigationInstrumentation extends Instrumentation {
    private ManagementActivity activity;
    private WebView web;
    private volatile int playbackCommands;
    private volatile int cancels;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private String js(String expression) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); String[] result = {null};
        runOnMainSync(() -> web.evaluateJavascript(expression, value -> { result[0] = value; latch.countDown(); }));
        check(latch.await(5, TimeUnit.SECONDS), "JavaScript timeout"); return result[0];
    }
    private void await(String expression) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (!"true".equals(js(expression))) {
            check(SystemClock.elapsedRealtime() < deadline, "Not ready: " + expression);
            SystemClock.sleep(50);
        }
    }
    private void page(String name) throws Exception {
        await("location.pathname==='/pages/" + name + ".html'&&document.readyState==='complete'&&!!window.NtvNavigation");
    }
    private void start(String url) throws Exception {
        activity = (ManagementActivity)startActivitySync(new Intent(getTargetContext(), ManagementActivity.class)
                .putExtra(ManagementActivity.EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Field field = ManagementActivity.class.getDeclaredField("webView"); field.setAccessible(true);
        web = (WebView)field.get(activity);
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1; ServerSocket server = null;
        try {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            final ServerSocket fixture = server;
            new Thread(() -> {
                while (!fixture.isClosed()) try (Socket socket = fixture.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String request = reader.readLine(), line; int length = 0;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                    }
                    char[] posted = new char[length]; int offset = 0;
                    while (offset < length) { int n = reader.read(posted, offset, length-offset); if (n < 0) break; offset += n; }
                    if (request.contains("/api/pointer") && new String(posted).contains("cancel")) cancels++;
                    if (request.startsWith("POST") && (request.contains("/api/media/control") || request.contains("/api/playback"))) playbackCommands++;
                    String path = request.split(" ")[1].split("\\?")[0];
                    byte[] body; String type = "application/json";
                    if (path.startsWith("/api/")) body = "{\"ok\":true,\"settings\":{\"flyMouseEnabled\":true},\"groups\":[],\"current\":{}}".getBytes("UTF-8");
                    else {
                        type = ControlSite.contentType(path);
                        try (InputStream input = getTargetContext().getAssets().open(ControlSite.assetPath(path));
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192]; int n;
                            while ((n = input.read(buffer)) >= 0) bytes.write(buffer, 0, n);
                            body = bytes.toByteArray();
                        }
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: "
                            + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); out.write(body); out.flush();
                } catch (Exception ignored) { }
            }, "management-navigation-fixture").start();
            String base = "http://127.0.0.1:" + server.getLocalPort();
            start(base + "/pages/flymouse.html"); page("flymouse");
            js("switchControlMode('keyboard');document.getElementById('remoteText').value='navigation draft';"
                    + "pointerQueue.push({action:'down'});navigateTo('/pages/media.html')");
            page("media"); check(cancels > 0, "Page changed without ordered pointer cancellation");
            js("mediaOpenSettings()");
            await("document.getElementById('mediaSettingsBackdrop').getAttribute('aria-hidden')==='false'");
            runOnMainSync(() -> activity.onBackPressed());
            await("document.getElementById('mediaSettingsBackdrop').getAttribute('aria-hidden')==='true'&&!history.state.ntvOverlay");
            page("media");
            js("mediaOpenSettings();history.back()");
            await("document.getElementById('mediaSettingsBackdrop').getAttribute('aria-hidden')==='true'&&!history.state.ntvOverlay");
            js("goBack()"); page("flymouse");
            check("true".equals(js("flyControlMode==='keyboard'&&document.getElementById('remoteText').value==='navigation draft'&&!gyroRunning")),
                    "Flymouse state not restored or sensors restarted");
            js("navigateTo('/pages/media.html')"); page("media");
            runOnMainSync(() -> activity.onBackPressed()); page("flymouse");
            js("navigateTo('/pages/media.html')"); page("media");
            js("delete window.NtvNavigation");
            runOnMainSync(() -> activity.onBackPressed()); page("flymouse");
            runOnMainSync(() -> activity.onBackPressed());
            await("location.pathname==='/index.html'&&document.readyState==='complete'");
            js("document.querySelector('a[href=\"/pages/browser.html\"]').click()"); page("browser");
            js("navigateTo('/pages/script.html')"); page("script");
            js("createUserScript()"); await("!document.getElementById('scriptEditor').hidden");
            runOnMainSync(() -> activity.onBackPressed());
            await("document.getElementById('scriptEditor').hidden&&!history.state.ntvOverlay");
            js("goBack()"); page("browser"); js("goBack()");
            await("location.pathname==='/index.html'&&document.readyState==='complete'");
            runOnMainSync(() -> activity.onBackPressed());
            long deadline = SystemClock.elapsedRealtime() + 3000;
            while (!activity.isFinishing() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            check(activity.isFinishing(), "Home Back did not close management Activity");
            check(playbackCommands == 0, "Management navigation sent playback commands");
            result.putString("stream", "PASS Android multi-document origin, pointer release, flymouse state, browser/APP/button overlay Back, script editor and root-only exit; no playback commands\n");
        } catch (Throwable error) {
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            result.putString("stream", trace.toString()); code = 0;
        } finally {
            if (activity != null) runOnMainSync(() -> activity.finish());
            if (server != null) try { server.close(); } catch (IOException ignored) {}
        }
        finish(code, result);
    }
}
