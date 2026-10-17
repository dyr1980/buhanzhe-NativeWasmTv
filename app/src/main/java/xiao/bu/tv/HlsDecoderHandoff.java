package xiao.bu.tv;

import java.util.LinkedHashMap;
import java.util.Map;

/** Playback-time segment boundaries; download read-ahead is never a playback cursor. */
final class HlsDecoderHandoff {
    private final LinkedHashMap<Long, Long> durations = new LinkedHashMap<>();
    private long firstSequence = -1L, nextSequence = -1L;
    private String mediaUrl;
    private int startIndex = -3;
    private boolean unsupported;

    synchronized void begin(int index) {
        durations.clear(); firstSequence = -1L; mediaUrl = null;
        startIndex = index; unsupported = false; nextSequence = -1L;
    }

    synchronized void request(long sequence) { nextSequence = sequence; }
    synchronized String mediaPlaylistUrl() { return mediaUrl; }

    synchronized String playlist(String url, String body) {
        if (body.contains("#EXT-X-STREAM-INF")) {
            if (body.contains("TYPE=AUDIO")) unsupported = true;
            return body;
        }
        if (body.contains("#EXT-X-BYTERANGE") || body.contains("BYTERANGE=")
                || body.contains("#EXT-X-DISCONTINUITY")
                || !body.contains("#EXT-X-MEDIA-SEQUENCE:")) unsupported = true;
        if (unsupported || body.contains("#EXT-X-ENDLIST")) return body;
        if (mediaUrl != null && !mediaUrl.equals(url)) return body;
        mediaUrl = url;
        String[] lines = body.split("\n", -1);
        long sequence = 0L, duration = 0L;
        for (String line : lines) if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
            try { sequence = Long.parseLong(line.substring(22).trim()); }
            catch (NumberFormatException invalid) { return body; }
        }
        long base = sequence;
        LinkedHashMap<Long, Long> window = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("#EXTINF:")) {
                try { duration = Math.round(Double.parseDouble(line.substring(8).split(",",2)[0]) * 1000d); }
                catch (NumberFormatException invalid) { return body; }
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                if (duration <= 0L) return body;
                window.put(sequence++, duration); duration = 0L;
            }
        }
        if (window.isEmpty()) return body;
        durations.putAll(window);
        while (durations.size() > 512) durations.remove(durations.keySet().iterator().next());
        if (firstSequence < 0L) firstSequence = base + (nextSequence >= base && nextSequence < sequence
                ? nextSequence - base : startIndex < 0 ? Math.max(0, window.size() + startIndex)
                : Math.min(startIndex, window.size() - 1));
        if (nextSequence < 0L) return body;
        // Keep filtering live refreshes too: a reconnect must never re-expose old media.
        long wanted = Math.max(base, nextSequence);
        if (wanted >= sequence) return body; // Next segment is not published yet.
        StringBuilder out = new StringBuilder(body.length());
        long cursor = base; String inf = null;
        for (String line : lines) {
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) out.append("#EXT-X-MEDIA-SEQUENCE:").append(wanted).append('\n');
            else if (line.startsWith("#EXTINF:")) inf = line;
            else if (!line.isEmpty() && !line.startsWith("#")) {
                if (cursor++ >= wanted) out.append(inf).append('\n').append(line).append('\n');
                inf = null;
            } else if (!line.startsWith("#EXT-X-START:")) out.append(line).append('\n');
        }
        return out.toString();
    }

    synchronized long[] nextBoundary(long position) {
        if (unsupported || firstSequence < 0L || position < 0L) return null;
        long elapsed = 0L;
        for (long seq = firstSequence; ; seq++) {
            Long duration = durations.get(seq);
            if (duration == null) return null;
            elapsed += duration;
            if (elapsed > position) {
                // Only switch to a published segment; never guess the next URL.
                return durations.containsKey(seq + 1L) ? new long[]{seq + 1L, elapsed - position} : null;
            }
        }
    }
}
