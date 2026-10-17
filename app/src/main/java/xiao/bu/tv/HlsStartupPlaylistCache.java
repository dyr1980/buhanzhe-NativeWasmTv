package xiao.bu.tv;

import java.util.LinkedHashMap;

/** One-use handoff of freshly prepared manifests; never a live reload cache. */
final class HlsStartupPlaylistCache {
    private static final long MAX_AGE_MS = 1000;
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_ENTRIES = 6;
    private static final class Entry {
        final byte[] body;
        final long created;
        Entry(byte[] body, long created) { this.body = body.clone(); this.created = created; }
    }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    synchronized void put(String url, byte[] body, long now) {
        entries.remove(url);
        if (body == null || body.length > MAX_BYTES) return;
        entries.put(url, new Entry(body, now));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
    }

    synchronized byte[] take(String url, long now) {
        Entry entry = entries.remove(url);
        return entry != null && now >= entry.created && now - entry.created < MAX_AGE_MS
                ? entry.body : null;
    }
}
