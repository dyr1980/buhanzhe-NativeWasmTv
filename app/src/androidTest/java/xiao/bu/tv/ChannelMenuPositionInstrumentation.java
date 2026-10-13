package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Menu cursor regression; synthetic channels never replace a saved playlist. */
public final class ChannelMenuPositionInstrumentation extends Instrumentation {
    private MainActivity activity;
    private ListView groups, channels;
    private ChannelCatalog.Group[] original;
    private Map<String, ?> preferences;
    private int originalGroup, originalChannel, originalSource;
    private interface Work { void run() throws Exception; }
    private void ui(Work work) {
        final Throwable[] error = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new RuntimeException(error[0]);
    }
    private Field field(String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private Object get(String name) throws Exception { return field(name).get(activity); }
    private void call(String name) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity);
    }
    private void key(int code) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
    private void checkCursor(int group, int channel) throws Exception {
        check(field("currentGroupIndex").getInt(activity) == group, "Playback group changed");
        check(field("currentChannelIndex").getInt(activity) == channel, "Playback channel changed");
        check(field("browsingGroupIndex").getInt(activity) == group,
                "Menu returned to another group: " + field("browsingGroupIndex").getInt(activity));
        check(channels.getCheckedItemPosition() == channel, "Checked channel lost position");
        check(channels.getSelectedItemPosition() == channel, "Remote selection lost position");
        check(channels.getFirstVisiblePosition() <= channel && channels.getLastVisiblePosition() >= channel,
                "Playing channel is outside viewport");
    }
    private void reopen(int group, int channel, boolean staleCallback) throws Exception {
        ui(() -> {
            call("closeChannelList");
            field("currentGroupIndex").setInt(activity, group);
            field("currentChannelIndex").setInt(activity, channel);
            field("currentSourceIndex").setInt(activity, 0);
            // The hidden list still remembers the previously browsed CCTV group.
            groups.setSelection(1);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            if (staleCallback) {
                AdapterView.OnItemSelectedListener listener = groups.getOnItemSelectedListener();
                if (listener != null) {
                    // Deliver the hidden list's notification before posted focus/scroll restoration.
                    listener.onItemSelected(groups, null, 1, 1);
                }
            }
        });
        SystemClock.sleep(220);
        ui(() -> checkCursor(group, channel));
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); StringBuilder report = new StringBuilder(); int status = -1;
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1600);
            setInTouchMode(false);
            ui(() -> {
                call("cancelHttpResolve"); call("releasePlayer"); call("closeWebSource"); call("hideLoading");
                call("ensureChannelPanelInitialized");
                original = ChannelCatalog.GROUPS;
                preferences = activity.getSharedPreferences(MainActivity.PREFERENCES, 0).getAll();
                originalGroup = field("currentGroupIndex").getInt(activity);
                originalChannel = field("currentChannelIndex").getInt(activity);
                originalSource = field("currentSourceIndex").getInt(activity);
                Channel[] rows = new Channel[180];
                for (int i = 0; i < rows.length; i++) rows[i] = new Channel(String.valueOf(i + 70),
                        "Position " + i, "position-" + i, "http://127.0.0.1:1/" + i, null, null);
                ChannelCatalog.Group[] fixture = new ChannelCatalog.Group[14];
                fixture[0] = new ChannelCatalog.Group("Favorites", ChannelCatalog.SOURCE_FAVORITES, new Channel[0]);
                for (int g = 1; g < fixture.length; g++) fixture[g] = new ChannelCatalog.Group(
                        g == 1 ? "CCTV" : "Group " + g, ChannelCatalog.SOURCE_CUSTOM, rows);
                ChannelCatalog.GROUPS = fixture;
                groups = (ListView) get("groupList"); channels = (ListView) get("channelList");
            });
            for (int group : new int[]{3, 12}) {
                for (int index : new int[]{126, 151, 152, 153, 179, 0}) reopen(group, index, false);
            }
            report.append("PASS repeated OK restores group, channel, focus and viewport at varied indices\n");
            reopen(3, 152, true);
            report.append("PASS stale group notification cannot reset menu to CCTV\n");
            ui(() -> { key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_DOWN); });
            SystemClock.sleep(150);
            ui(() -> {
                check(field("browsingGroupIndex").getInt(activity) == 4, "Group down key failed");
                key(KeyEvent.KEYCODE_DPAD_UP);
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
            });
            SystemClock.sleep(150);
            ui(() -> {
                checkCursor(3, 152);
                check(channels.hasFocus(), "Posted group restoration stole channel focus");
                key(KeyEvent.KEYCODE_DPAD_DOWN);
            });
            SystemClock.sleep(150);
            ui(() -> {
                check(channels.getSelectedItemPosition() == 153, "Channel down key failed");
                key(KeyEvent.KEYCODE_DPAD_UP);
            });
            SystemClock.sleep(150);
            ui(() -> {
                checkCursor(3, 152);
                groups.performItemClick(null, 5, 5);
                check(field("browsingGroupIndex").getInt(activity) == 5, "Group click failed");
            });
            report.append("PASS group/channel up-down, right focus and group click\n");
            ui(() -> call("updatePlayingChannelSelection"));
            SystemClock.sleep(150);
            ui(() -> checkCursor(3, 152));
            report.append("PASS playback updates explicitly restore matching adapters\n");
            // Exercise the production catalog replacement that removes preceding entries.
            ChannelCatalog.Group[] reduced = new ChannelCatalog.Group[1];
            ui(() -> {
                call("closeChannelList");
                Channel[] before = ChannelCatalog.GROUPS[3].channels;
                Channel[] after = new Channel[before.length - 3];
                System.arraycopy(before, 3, after, 0, after.length);
                reduced[0] = new ChannelCatalog.Group("Group 3", ChannelCatalog.SOURCE_CUSTOM, after);
            });
            Method apply = MainActivity.class.getDeclaredMethod("applyPlaylistGroupVisibility", ChannelCatalog.Group[].class);
            apply.setAccessible(true); apply.invoke(activity, (Object) reduced);
            int[] location = new int[2];
            ui(() -> {
                location[0] = field("currentGroupIndex").getInt(activity);
                location[1] = field("currentChannelIndex").getInt(activity);
                check(location[1] == 149, "Deletion did not preserve channel identity");
            });
            reopen(location[0], location[1], true);
            report.append("PASS deleting preceding channels preserves identity and OK menu location\n");
        } catch (Throwable e) {
            status = 0; report.append(android.util.Log.getStackTraceString(e));
        } finally {
            if (activity != null) ui(() -> {
                call("closeChannelList");
                if (original != null) {
                    ChannelCatalog.setCustomGroups(java.util.Arrays.copyOfRange(original, 1, original.length));
                    ChannelCatalog.GROUPS = original;
                    field("currentGroupIndex").setInt(activity, originalGroup);
                    field("currentChannelIndex").setInt(activity, originalChannel);
                    field("currentSourceIndex").setInt(activity, originalSource);
                }
                if (preferences != null) {
                    SharedPreferences.Editor edit = activity.getSharedPreferences(MainActivity.PREFERENCES, 0).edit().clear();
                    for (Map.Entry<String, ?> entry : preferences.entrySet()) {
                        String k = entry.getKey(); Object v = entry.getValue();
                        if (v instanceof String) edit.putString(k, (String) v);
                        else if (v instanceof Boolean) edit.putBoolean(k, (Boolean) v);
                        else if (v instanceof Integer) edit.putInt(k, (Integer) v);
                        else if (v instanceof Long) edit.putLong(k, (Long) v);
                        else if (v instanceof Float) edit.putFloat(k, (Float) v);
                        else if (v instanceof java.util.Set) edit.putStringSet(k, (java.util.Set<String>) v);
                    }
                    edit.commit();
                }
                activity.finish();
            });
        }
        result.putString("stream", report.toString()); finish(status, result);
    }
}
