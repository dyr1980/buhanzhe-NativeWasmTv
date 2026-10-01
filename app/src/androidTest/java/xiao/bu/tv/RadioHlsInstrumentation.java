package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Run in a separate applicationId with an audio/video HLS fixture on localhost:19982. */
public final class RadioHlsInstrumentation extends Instrumentation {
    private MainActivity activity;
    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = { null };
        new Handler(Looper.getMainLooper()).post(() -> {
            try { work.run(); } catch (Throwable e) { error[0] = e; } finally { latch.countDown(); }
        });
        check(latch.await(10, TimeUnit.SECONDS), "UI thread timeout");
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static Object get(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private void log(String value) { Bundle b = new Bundle(); b.putString("stream", value + "\n"); sendStatus(1, b); }
    private static ChannelCatalog.Group[] parse(String text) throws Exception {
        Method m = PlaylistManager.class.getDeclaredMethod("parse", byte[].class);
        m.setAccessible(true); return (ChannelCatalog.Group[]) m.invoke(null, (Object) text.getBytes("UTF-8"));
    }
    private void catalogTests(ChannelCatalog.Group[] groups) throws Exception {
        Channel radio = groups[0].channels[0];
        check(radio.radio && !groups[0].channels[1].radio && !groups[0].channels[2].radio, "radio leaks into following channel");
        ChannelCatalog.Group[] merged = parse("#EXTM3U\n#EXTINF:-1,Same\nhttps://example.com/a.m3u8\n"
                + "#EXTINF:-1 radio=\"TRUE\",Same\nhttps://example.com/b.m3u8\n");
        check(merged[0].channels.length == 1 && merged[0].channels[0].radio
                && merged[0].channels[0].sourceCount() == 2, "merged channel loses radio flag");
        ChannelCatalog.Group[] one = parse("#EXTM3U\n#EXTINF:-1 radio=\"1\" tvg-id=\"yyzs\" tvg-logo=\"https://example.com/logo.png\",音乐之声\n"
                + "https://ytcast2.radio.cn/110/radios/40641/index_40641.m3u8\n");
        check(one[0].channels[0].radio && "yyzs".equals(one[0].channels[0].epgId)
                && "https://example.com/logo.png".equals(one[0].channels[0].logoUrl), "radio metadata parse");
        final File db = new File(getTargetContext().getCacheDir(), "radio-test-" + System.nanoTime() + ".db");
        Context isolated = new ContextWrapper(getTargetContext()) {
            @Override public File getDatabasePath(String name) { return db; }
            @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f) {
                return SQLiteDatabase.openOrCreateDatabase(db, f);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler h) {
                return SQLiteDatabase.openOrCreateDatabase(db.getPath(), f, h);
            }
        };
        // Build the prior schema in an isolated scratch database; no user catalog is touched.
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(db, null);
        try {
            for (String key : new String[] {"CREATE_GROUPS", "CREATE_CHANNELS", "CREATE_URLS", "CREATE_META"}) {
                Field f = ChannelCatalogStore.class.getDeclaredField(key); f.setAccessible(true);
                String sql = ((String) f.get(null)).replace(", radio INTEGER NOT NULL DEFAULT 0", "");
                old.execSQL(sql);
            }
            old.execSQL("INSERT INTO catalog_meta(name,value) VALUES('complete','1')");
            old.execSQL("INSERT INTO catalog_meta(name,value) VALUES('fingerprint','old')");
            old.setVersion(3);
        } finally { old.close(); }
        ChannelCatalogStore store = new ChannelCatalogStore(isolated);
        try {
            check(store.load() == null && !store.hasFingerprint("old"), "old lossy cache not invalidated");
            check(store.replace(groups, "radio-v4"), "cannot save radio catalog");
        } finally { store.close(); }
        store = new ChannelCatalogStore(isolated);
        try {
            ChannelCatalog.Group[] restored = store.load();
            check(restored != null && restored[0].channels[0].radio && !restored[0].channels[1].radio
                    && store.hasFingerprint("radio-v4"), "radio database roundtrip");
        } finally { store.close(); }
        log("PASS radio parse/reset/merge, schema 3-to-4 migration and catalog roundtrip");
    }
    private void play(int index, boolean audio) throws Exception {
        main(() -> call(activity, "switchChannel", new Class<?>[] {int.class, int.class}, index, 0));
        long deadline = SystemClock.elapsedRealtime() + 20000;
        boolean[] ready = {false};
        while (!ready[0] && SystemClock.elapsedRealtime() < deadline) {
            main(() -> ready[0] = (Boolean) get(activity, "prepared")); SystemClock.sleep(100);
        }
        check(ready[0], "HLS not prepared index=" + index);
        long[] first = {0};
        main(() -> {
            check((Boolean) get(activity, "audioOnlyPlayback") == audio, "wrong HLS audio classification");
            first[0] = ((IjkMediaPlayer) get(activity, "player")).getCurrentPosition();
        });
        SystemClock.sleep(2500);
        main(() -> {
            IjkMediaPlayer p = (IjkMediaPlayer) get(activity, "player");
            check(p.isPlaying() && p.getCurrentPosition() > first[0] + 300, "HLS playback clock stalled");
            // The outgoing artwork may remain until the incoming video's first frame.
            check(((View) get(activity, "audioArtwork")).getVisibility() == (audio ? View.VISIBLE : View.GONE), "artwork visibility after playback started");
            JSONObject state = new JSONObject((String) call(activity, "buildMediaStateJson", new Class<?>[] {boolean.class}, false));
            check(state.getBoolean("audioOnly") == audio, "management media state mismatch");
            if (index == 3) check(p.getDuration() <= 0 && !state.optBoolean("seekable"), "live radio treated as seekable file");
            log("PASS HLS index=" + index + " audioOnly=" + audio + " clock=" + p.getCurrentPosition());
        });
    }
    private void denied(String path) throws Exception {
        ChannelCatalog.Group[] groups = parse("#EXTM3U\n#EXTINF:-1 radio=\"true\",HTTP denial test\nhttp://127.0.0.1:19982/" + path + "\n");
        main(() -> { ChannelCatalog.GROUPS = groups; set(activity, "currentGroupIndex", 0);
            call(activity, "switchChannel", new Class<?>[] {int.class, int.class}, 0, 0); });
        long deadline = SystemClock.elapsedRealtime() + 20000;
        String[] status = {""};
        do {
            SystemClock.sleep(100);
            main(() -> status[0] = ((android.widget.TextView) get(activity, "statusText")).getText().toString());
        } while (!status[0].contains("HTTP 403") && SystemClock.elapsedRealtime() < deadline);
        check(status[0].contains("HTTP 403") && status[0].contains("服务器拒绝访问"), "HTTP status lost for " + path + ": " + status[0]);
        main(() -> check(get(activity, "player") == null, "failed player still retrying"));
        log("PASS explicit 403 hint: " + path + " " + status[0]);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try {
            ChannelCatalog.Group[] groups = parse("#EXTM3U\n#EXTINF:-1 radio=\"true\" group-title=\"电台测试\",HLS Radio\nhttp://127.0.0.1:19982/audio.m3u8\n"
                    + "#EXTINF:-1 radio=\"false\" group-title=\"电台测试\",HLS TV\nhttp://127.0.0.1:19982/video.m3u8\n"
                    + "#EXTINF:-1 group-title=\"电台测试\",Unmarked audio HLS\nhttp://127.0.0.1:19982/audio.m3u8?unmarked=1\n"
                    + "#EXTINF:-1 radio=\"true\" group-title=\"电台测试\",Live HLS Radio\nhttp://127.0.0.1:19982/live.m3u8\n");
            catalogTests(groups);
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);
            main(() -> {
                ((java.util.concurrent.atomic.AtomicInteger) get(activity, "catalogLoadGeneration")).incrementAndGet();
                ChannelCatalog.GROUPS = groups;
                set(activity, "currentGroupIndex", 0); set(activity, "currentChannelIndex", 0);
            });
            play(0, true); play(1, false); play(2, true); play(3, true); play(0, true);
            denied("denied.m3u8"); denied("denied.mp3"); denied("resolve-denied"); denied("denied-segment.m3u8");
            main(() -> {ChannelCatalog.GROUPS = groups;set(activity, "currentGroupIndex", 0);});
            play(0, true);
            result.putString("stream", "PASS radio HLS, video switching and unmarked audio detection\n");
        } catch (Throwable error) { code = 0; result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finally { if (activity != null) try { main(() -> activity.finish()); } catch (Exception ignored) {} }
        finish(code, result);
    }
}
