package xiao.bu.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

/** Persistent web-only bookmark tree. Channel groups never enter this store. */
final class WebBookmarkStore {
    static final class Node {
        final boolean folder;
        String title;
        String url;
        final ArrayList<Node> children = new ArrayList<Node>();
        // The bounded hot cache/views own pixels; a large bookmark tree must not.
        java.lang.ref.WeakReference<Bitmap> icon;
        boolean iconLoaded;

        Node(boolean folder, String title, String url) {
            this.folder = folder;
            this.title = title == null ? "" : title.trim();
            this.url = url == null ? "" : url;
        }
    }

    private static final String TREE = "web_bookmarks_v2";
    private static final String LEGACY = "web_bookmarks_v1";
    // Serialize writes to the same .tmp file; repeated icon callbacks must not
    // start an unbounded number of threads or keep unbounded pending bitmaps.
    private static final ThreadPoolExecutor ICON_WRITER = new ThreadPoolExecutor(
            0, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(32),
            runnable -> new Thread(runnable, "web-favicon-cache"),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Context context;
    private final SharedPreferences preferences;
    private final File iconDirectory;
    private final ArrayList<Node> roots = new ArrayList<Node>();
    private final LinkedHashMap<String, Bitmap> iconMemory = new LinkedHashMap<String, Bitmap>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> entry) {
            return size() > 128;
        }
    };

    WebBookmarkStore(Context context, SharedPreferences preferences) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        // v1 could cache the reused WebView's previous favicon under a new host
        // during onPageStarted/onPageFinished. Do not migrate those untrusted
        // pixels; bookmarks and browsing data remain untouched.
        iconDirectory = new File(new File(context.getCacheDir(), "browser"), "favicons-native-v2");
        load();
    }

    ArrayList<Node> roots() { return roots; }

    Node addBookmark(String url, String title) {
        Node node = new Node(false, title, canonical(url));
        roots.add(node);
        save();
        return node;
    }

    Node addFolder(Node parent, String title) {
        Node node = new Node(true, cleanFolderTitle(title), "");
        children(parent).add(node);
        save();
        return node;
    }

    Node findBookmark(String url) {
        return findBookmark(roots, canonical(url));
    }

    Node parentOf(Node wanted) { return parentOf(roots, null, wanted); }

    boolean remove(Node wanted) {
        ArrayList<Node> owner = ownerOf(wanted);
        if (owner == null || !owner.remove(wanted)) return false;
        save();
        return true;
    }

    boolean move(Node moving, Node folder, int index) {
        if (moving == null || moving == folder || folder != null && !folder.folder
                || moving.folder && isDescendant(moving, folder)) return false;
        ArrayList<Node> source = ownerOf(moving);
        ArrayList<Node> destination = children(folder);
        if (source == null) return false;
        int old = source.indexOf(moving);
        if (old < 0) return false;
        source.remove(old);
        if (source == destination && old < index) index--;
        destination.add(Math.max(0, Math.min(index, destination.size())), moving);
        save();
        return true;
    }

    void rename(Node node, String title) {
        if (node == null) return;
        String value = node.folder ? cleanFolderTitle(title) : safe(title).trim();
        if (value.length() == 0 || value.equals(node.title)) return;
        node.title = value;
        save();
    }

    Bitmap icon(Node node) {
        if (node == null || node.folder) return null;
        Bitmap cached = node.icon == null ? null : node.icon.get();
        if (!node.iconLoaded || node.icon != null && (cached == null || cached.isRecycled())) {
            node.iconLoaded = true;
            cached = iconForUrl(node.url);
            node.icon = cached == null ? null : new java.lang.ref.WeakReference<>(cached);
        }
        return cached;
    }

    void trimMemory() { iconMemory.clear(); }

    Bitmap iconForUrl(String url) {
        String key = digestKey(url);
        Bitmap memory = iconMemory.get(key);
        if (memory != null && !memory.isRecycled()) return memory;
        if (memory == null && iconMemory.containsKey(key)) return null;
        File file = iconFile(url);
        Bitmap decoded = null;
        if (file.isFile()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 48) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
        iconMemory.put(key, decoded);
        return decoded;
    }

    void cacheIcon(String url, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        final Bitmap scaled = largest <= 48 ? bitmap : Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * 48f / largest)),
                Math.max(1, Math.round(bitmap.getHeight() * 48f / largest)), true);
        iconMemory.put(digestKey(url), scaled);
        applyIcon(roots, digestKey(url), scaled);
        final File target = iconFile(url);
        ICON_WRITER.execute(new Runnable() {
            @Override public void run() {
                if (!iconDirectory.exists() && !iconDirectory.mkdirs()) return;
                File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
                try {
                    FileOutputStream output = new FileOutputStream(temporary);
                    try { scaled.compress(Bitmap.CompressFormat.PNG, 100, output); }
                    finally { output.close(); }
                    if (target.exists()) target.delete();
                    temporary.renameTo(target);
                } catch (Exception ignored) {
                    temporary.delete();
                }
            }
        });
    }

    void save() {
        preferences.edit().putString(TREE, writeNodes(roots).toString()).apply();
    }

    private void load() {
        String stored = preferences.getString(TREE, "");
        if (stored.length() > 0) {
            try { readNodes(new JSONArray(stored), roots, 0); }
            catch (Exception ignored) { roots.clear(); }
            return;
        }
        try {
            JSONArray legacy = new JSONArray(preferences.getString(LEGACY, "[]"));
            for (int i = 0; i < legacy.length(); i++) {
                JSONObject item = legacy.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (!isWebUrl(url)) continue;
                roots.add(new Node(false, item.optString("title", host(url)), canonical(url)));
            }
        } catch (Exception ignored) { roots.clear(); }
        save();
    }

    private static void readNodes(JSONArray source, ArrayList<Node> output, int depth) {
        if (depth > 12) return;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            boolean folder = "folder".equals(item.optString("type"));
            String url = canonical(item.optString("url", ""));
            if (!folder && !isWebUrl(url)) continue;
            Node node = new Node(folder,
                    folder ? cleanFolderTitle(item.optString("title", "文件夹"))
                            : item.optString("title", host(url)), url);
            if (folder) readNodes(item.optJSONArray("children") == null
                    ? new JSONArray() : item.optJSONArray("children"), node.children, depth + 1);
            output.add(node);
        }
    }

    private static JSONArray writeNodes(ArrayList<Node> nodes) {
        JSONArray result = new JSONArray();
        for (Node node : nodes) {
            try {
                JSONObject item = new JSONObject().put("type", node.folder ? "folder" : "bookmark")
                        .put("title", node.title);
                if (node.folder) item.put("children", writeNodes(node.children));
                else item.put("url", node.url);
                result.put(item);
            } catch (Exception ignored) { }
        }
        return result;
    }

    private static Node findBookmark(ArrayList<Node> nodes, String url) {
        for (Node node : nodes) {
            if (!node.folder && url.equals(node.url)) return node;
            Node nested = findBookmark(node.children, url);
            if (nested != null) return nested;
        }
        return null;
    }

    private static Node parentOf(ArrayList<Node> nodes, Node parent, Node wanted) {
        for (Node node : nodes) {
            if (node == wanted) return parent;
            Node nested = parentOf(node.children, node, wanted);
            if (nested != null) return nested;
        }
        return null;
    }

    private ArrayList<Node> ownerOf(Node wanted) {
        if (roots.contains(wanted)) return roots;
        Node parent = parentOf(wanted);
        return parent == null ? null : parent.children;
    }

    private ArrayList<Node> children(Node folder) {
        return folder == null ? roots : folder.children;
    }

    private static boolean isDescendant(Node ancestor, Node possible) {
        if (ancestor == null || possible == null) return false;
        for (Node child : ancestor.children) {
            if (child == possible || isDescendant(child, possible)) return true;
        }
        return false;
    }

    private static boolean applyIcon(ArrayList<Node> nodes, String domainKey, Bitmap bitmap) {
        boolean changed = false;
        for (Node node : nodes) {
            if (!node.folder && domainKey.equals(digestKey(node.url))) {
                node.icon = new java.lang.ref.WeakReference<>(bitmap);
                node.iconLoaded = true;
                changed = true;
            }
            changed |= applyIcon(node.children, domainKey, bitmap);
        }
        return changed;
    }

    private File iconFile(String url) { return new File(iconDirectory, digestKey(url) + ".png"); }

    private static String digestKey(String url) {
        String value = host(url);
        if (value.length() == 0) value = canonical(url);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder result = new StringBuilder(32);
            for (int i = 0; i < 16; i++) result.append(String.format(Locale.US, "%02x", bytes[i]));
            return result.toString();
        } catch (Exception ignored) { return Integer.toHexString(value.hashCode()); }
    }

    private static String cleanFolderTitle(String value) {
        String title = safe(value).trim();
        return title.length() == 0 ? "新建文件夹" : title;
    }

    private static String host(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "网页" : host;
        } catch (RuntimeException ignored) { return "网页"; }
    }

    private static String canonical(String url) {
        try { return Uri.parse(safe(url)).buildUpon().fragment(null).build().toString(); }
        catch (RuntimeException ignored) { return safe(url); }
    }

    private static boolean isWebUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
