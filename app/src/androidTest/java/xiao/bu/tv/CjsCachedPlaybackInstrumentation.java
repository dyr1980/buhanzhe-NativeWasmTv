package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Real disk-cache checks with a local update server; no user's plugins are changed. */
public final class CjsCachedPlaybackInstrumentation extends Instrumentation {
    private ServerSocket server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger catalogRequests = new AtomicInteger();
    private volatile boolean failRequests;
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void write(File file, byte[] data) throws Exception {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
    }
    private static void write(File file, String data) throws Exception { write(file, data.getBytes("UTF-8")); }
    private JSONObject entry(String id, String base) throws Exception {
        return new JSONObject().put("id", id).put("module", "libfixture")
                .put("config", base + "/branch/" + id + ".cjs")
                .put("qualities", new JSONObject().put("high", "hd"))
                .put("hosts", new JSONArray().put("127.0.0.1"))
                .put("playback", new JSONObject().put("page", base + "/tv/{id}")
                        .put("parameters", new JSONObject().put("id", "[a-z0-9]+")));
    }
    private void coldRuntime() throws Exception {
        for (String name : new String[]{"catalog", "catalogReadAttempted"}) {
            java.lang.reflect.Field f = CjsPluginRuntime.class.getDeclaredField(name); f.setAccessible(true);
            f.set(null, "catalog".equals(name) ? null : false);
        }
        java.lang.reflect.Field f = CjsPluginRuntime.class.getDeclaredField("states"); f.setAccessible(true);
        ((java.util.Map<?, ?>) f.get(null)).clear();
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int status = -1;
        try {
            final String prefix = "cached-playback-" + System.nanoTime();
            final File directory = new File(getTargetContext().getFilesDir(), prefix);
            Context isolated = new ContextWrapper(getTargetContext()) {
                @Override public Context getApplicationContext() { return this; }
                @Override public File getFilesDir() { return directory; }
                @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences(prefix + name, mode);
                }
            };
            CjsPluginRuntime.initialize(isolated); NetworkClient.initialize(isolated);
            server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            final String base = "http://127.0.0.1:" + server.getLocalPort();
            Thread peer = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String request = in.readLine(); String path = request.split(" ")[1].split("\\?")[0];
                    while (true) { String line = in.readLine(); if (line == null || line.isEmpty()) break; }
                    requests.incrementAndGet();
                    boolean descriptor = path.endsWith(".cjs");
                    String id = path.substring(path.lastIndexOf('/') + 1).replace(".cjs", "");
                    boolean catalog = "/catalog.json".equals(path);
                    if (catalog) catalogRequests.incrementAndGet();
                    byte[] body = catalog ? new JSONObject().put("protocol", 5).put("sites",
                            new JSONArray().put(entry("cached", base).put("config", base + "/cached.cjs"))
                                    .put(entry("missing", base))).toString().getBytes("UTF-8")
                            : descriptor ? new JSONObject().put("id", id).put("v", 7)
                            .put("manifest", base + "/manifest.json").toString().getBytes("UTF-8") : new byte[0];
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 " + (!failRequests && (descriptor || catalog) ? "200 OK" : "503 Unavailable")
                            + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    out.write(body); out.flush();
                } catch (Exception e) { if (!server.isClosed()) android.util.Log.e("CachedCjsTest", "peer", e); }
            }, "cached-cjs-update-peer");
            peer.start();
            File root = new File(directory, "cjs-sites-v5");
            write(new File(root, "catalog.json"), new JSONObject().put("protocol", 5)
                    .put("sites", new JSONArray().put(entry("cached", base).put("config", base + "/cached.cjs"))
                            .put(entry("missing", base))).toString());
            String profile = CjsPluginRuntime.currentProfile();
            File version = new File(root, "cached/" + profile + "/7");
            byte[] elf = new byte[20]; elf[0] = 0x7f; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[5] = 1;
            elf[4] = (byte) ("arm64-v8a".equals(BuildConfig.CJS_PLUGIN_ABI) ? 2 : 1);
            elf[18] = (byte) ("arm64-v8a".equals(BuildConfig.CJS_PLUGIN_ABI) ? 183
                    : "x86".equals(BuildConfig.CJS_PLUGIN_ABI) ? 3 : 40);
            write(new File(version, "libfixture.so"), elf);
            write(new File(version, "abi.txt"), BuildConfig.CJS_PLUGIN_ABI);
            write(new File(version, "profile.txt"), profile);
            write(new File(version, "runtime.json"), new JSONObject().put("protocol", 5).put("id", "cached")
                    .put("version", 7).put("scripts", new JSONObject().put("play", "local script")).toString());
            isolated.getSharedPreferences("cjs_sites_v5", 0).edit().putInt("cached:" + profile + ":active", 7).commit();
            CjsPluginRuntime.setManifestUrl(base + "/catalog.json");
            CjsPluginRuntime.prepareSource(base + "/branch/cached.cjs?id=cctv1");
            check(requests.get() == 1, "Branch import must discover its own descriptor once");
            requests.set(0);
            for (int cold = 0; cold < 3; cold++) {
                coldRuntime();
                for (int i = 0; i < 10; i++) {
                    check("7".equals(CjsPluginRuntime.ensureInstalled("cached")), "Cached version changed");
                    CjsPluginRuntime.prepareSource(base + "/branch/cached.cjs?id=cctv1");
                    check("local script".equals(CjsPluginRuntime.script("cached", "play", null)), "Local script unavailable");
                }
            }
            check(requests.get() == 0, "Cached playback contacted update server");
            File incomplete = new File(root, "cached/" + profile + "/8");
            write(new File(incomplete, "libfixture.so"), elf);
            write(new File(incomplete, "abi.txt"), BuildConfig.CJS_PLUGIN_ABI);
            write(new File(incomplete, "profile.txt"), profile);
            isolated.getSharedPreferences("cjs_sites_v5", 0).edit().putInt("cached:" + profile + ":pending", 8).commit();
            for (boolean missingVersion : new boolean[]{true, false}) {
                JSONObject broken = new JSONObject().put("protocol", 5).put("id", "cached");
                if (missingVersion) broken.put("scripts", new JSONObject().put("play", "broken script"));
                else broken.put("version", 8);
                write(new File(incomplete, "runtime.json"), broken.toString());
                coldRuntime();
                check("7".equals(CjsPluginRuntime.ensureInstalled("cached")), "Incomplete pending runtime replaced active cache");
                check("local script".equals(CjsPluginRuntime.script("cached", "play", null)), "Incomplete metadata broke playback");
            }
            isolated.getSharedPreferences("cjs_sites_v5", 0).edit().remove("cached:" + profile + ":pending").commit();
            CjsPluginRuntime.installOrUpdate("cached");
            check(requests.get() == 1, "Manual update stopped probing descriptor");
            CjsPluginRuntime.checkInstalledOnce();
            check(requests.get() == 2, "Startup did not check only the installed site's configured descriptor");
            check(catalogRequests.get() == 0, "Startup replaced imported branch routing with global catalog");
            for (int i = 0; i < 30; i++) CjsPluginRuntime.checkInstalledOnce();
            check(requests.get() == 2, "Startup check repeated within the process");
            java.lang.reflect.Field attempted = CjsPluginRuntime.class.getDeclaredField("startupUpdateAttempted");
            attempted.setAccessible(true); attempted.setBoolean(null, false);
            failRequests = true;
            CjsPluginRuntime.checkInstalledOnce();
            check(requests.get() == 3, "Failed startup check did not probe the installed site once");
            int failedCount = requests.get();
            CjsPluginRuntime.checkInstalledOnce();
            check(requests.get() == failedCount, "Failed startup check retried in the same process");
            failRequests = false;
            try { CjsPluginRuntime.ensureInstalled("missing"); throw new AssertionError("Missing plugin succeeded"); }
            catch (IOException expected) { }
            check(requests.get() >= failedCount + 2, "Missing plugin did not attempt install");
            int before = requests.get();
            check("7".equals(CjsPluginRuntime.ensureInstalled("cached")), "Other install failure broke cached plugin");
            check(requests.get() == before, "Cached plugin downloaded after another install failed");
            server.close(); peer.join(1000);
            check("7".equals(CjsPluginRuntime.ensureInstalled("cached")), "Cached playback needs reachable update server");
            result.putString("stream", "PASS 3 cold loads / 30 playback preparations with zero update requests; "
                    + "one startup batch, no repeated failed checks, manual updates, missing-plugin failure isolation and offline cache\n");
        } catch (Throwable e) { status = 0; result.putString("stream", android.util.Log.getStackTraceString(e)); }
        finally { try { if (server != null) server.close(); } catch (IOException ignored) { } }
        finish(status, result);
    }
}
