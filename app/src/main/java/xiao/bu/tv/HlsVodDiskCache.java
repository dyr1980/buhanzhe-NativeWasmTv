package xiao.bu.tv;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Per-playback, bounded file reserve. Never allocates an entire media fragment. */
final class HlsVodDiskCache implements Closeable {
    interface Connections {
        HttpURLConnection open(String url) throws IOException;
        default void received(long bytes) { }
        default void warmed(long milliseconds, boolean enough) { }
    }
    private static final Pattern MAP = Pattern.compile("URI=\"([^\"]+)\"");
    // A few milliseconds of HTTP overhead must not buy an entire extra fragment.
    // This tolerance consumes at most 0.25s of the 2s/5s jitter margin.
    private static final double RESERVE_TOLERANCE_SECONDS = 0.25;
    private static final long FREE_SPACE_MARGIN = 32L * 1024 * 1024;
    private static final long SPACE_CHECK_BYTES = 1024 * 1024;
    private static final Set<String> LIVE_DIRECTORIES = new HashSet<>();
    private final File directory;
    private final Connections connections;
    private final int mode;
    private final long quota;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<Track> trackOrder = new ArrayList<>();
    private final List<Entry> initEntries = new ArrayList<>();
    private final Set<Entry> pending = new HashSet<>();
    private final Set<HttpURLConnection> active = new HashSet<>();
    private long used, promised;
    private long usableSpace, spaceCheckTime, bytesSinceSpaceCheck;
    private long warmupMillis;
    private int sequence, jobs, nextTrack;
    private boolean closed;

    private static final class Entry {
        String url, type;
        File file;
        boolean downloading, failed, init, storageBlocked, headersReceived;
        long bytes;
        long received, expected = -1;
        long reservation;
        double seconds;
    }
    private static final class Track {
        final List<Entry> segments = new ArrayList<>();
        final Map<Entry, Integer> positions = new IdentityHashMap<>();
        double[] durations;
        int current, next, evicted;
        long started = System.nanoTime();
        boolean warmed;
        int expectedWait = -1;
        boolean slow;
    }

    HlsVodDiskCache(File root, int mode, Connections connections) throws IOException {
        this.mode = mode; this.connections = connections;
        quota = (mode == 2 ? 256L : 128L) * 1024 * 1024;
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create HLS cache");
        File canonicalRoot = root.getCanonicalFile();
        synchronized (LIVE_DIRECTORIES) {
            File[] abandoned = root.listFiles();
            if (abandoned != null) for (File old : abandoned) {
                if (!old.getName().matches("session-[0-9a-f-]{36}")
                        || LIVE_DIRECTORIES.contains(old.getCanonicalPath())
                        || !canonicalRoot.equals(old.getCanonicalFile().getParentFile())) continue;
                File[] files = old.listFiles();
                if (files != null) for (File file : files) if (file.isFile()) file.delete();
                old.delete();
            }
        }
        if (root.getUsableSpace() < quota + 64L * 1024 * 1024)
            throw new IOException("Insufficient HLS cache space");
        directory = new File(root, "session-" + UUID.randomUUID());
        if (!directory.mkdir()) throw new IOException("Cannot create HLS cache session");
        synchronized (LIVE_DIRECTORIES) { LIVE_DIRECTORIES.add(directory.getCanonicalPath()); }
    }

    /** Only complete, unencrypted, separate-fragment VOD. Other HLS keeps its existing path. */
    void register(String url, String[] lines) throws IOException {
        if (mode == 0) return;
        boolean finite = false; int count = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.equals("#EXT-X-ENDLIST")) finite = true;
            if (line.startsWith("#EXT-X-BYTERANGE") || line.contains("BYTERANGE=")
                    || line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE")) return;
            if (!line.isEmpty() && !line.startsWith("#") && ++count > 4096) return;
        }
        if (!finite || count == 0) return;
        Track track;
        synchronized (this) {
            if (closed || tracks.containsKey(url)) return;
            // Bound manifest metadata too, including alternate audio tracks.
            if (tracks.size() >= 4 || entries.size() + count > 8192) return;
            track = new Track(); URI base = URI.create(url); double seconds = 0;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("#EXT-X-MAP:")) {
                    Matcher map = MAP.matcher(line);
                    if (map.find()) entry(base.resolve(map.group(1)).toString(), true, 0);
                } else if (line.startsWith("#EXTINF:")) {
                    try { seconds = Double.parseDouble(line.substring(8).split(",", 2)[0]); }
                    catch (NumberFormatException invalid) { return; }
                    if (!(seconds > 0 && seconds <= 3600)) return;
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    if (seconds <= 0) return;
                    track.segments.add(entry(base.resolve(line).toString(), false, seconds));
                    seconds = 0;
                }
            }
            track.durations = new double[track.segments.size() + 1];
            for (int i = 0; i < track.segments.size(); i++) {
                Entry entry = track.segments.get(i);
                if (!track.positions.containsKey(entry)) track.positions.put(entry, i);
                track.durations[i + 1] = track.durations[i] + entry.seconds;
            }
            tracks.put(url, track); trackOrder.add(track); schedule();
        }
        // Build a reserve independently of IJK's small compressed-packet RAM cap.
        boolean enough = false;
        long elapsedWarmup;
        synchronized (this) {
            while (!closed) {
                double ready = readySeconds(track);
                double elapsed = (System.nanoTime() - track.started) / 1e9;
                double duration = totalSeconds(track), downloaded = 0, samples = 0;
                int complete = 0;
                for (Entry entry : track.segments) {
                    if (entry.bytes > 0) { downloaded += entry.seconds; complete++; }
                    else if (entry.expected > 0) downloaded += entry.seconds * entry.received / entry.expected;
                }
                for (int i = 0; i < Math.min(2, track.segments.size()); i++) samples += track.segments.get(i).seconds;
                double rate = elapsed > 0 ? downloaded / elapsed : 0;
                double target = reserveSeconds(mode, duration, rate);
                // One fragment is sufficient for a short clip; longer clips need
                // a second sample so an unusually small first fragment cannot skew startup.
                boolean measured = complete >= Math.min(duration <= 30 ? 1 : 2, track.segments.size());
                if (!measured) target = samples;
                track.slow = measured && rate < 0.98;
                double deliverableTarget = 0;
                for (int i = track.current; i < track.segments.size()
                        && deliverableTarget + RESERVE_TOLERANCE_SECONDS < target; i++)
                    deliverableTarget += track.segments.get(i).seconds;
                double progress = ready;
                for (int i = track.current; i < track.segments.size(); i++) {
                    Entry entry = track.segments.get(i);
                    if (entry.bytes > 0) continue;
                    if (entry.expected > 0) progress += entry.seconds * entry.received / entry.expected;
                    break;
                }
                track.expectedWait = waitSeconds(deliverableTarget, progress, rate);
                enough = measured && reserveSatisfied(ready, target);
                if (enough || ready >= duration || exhausted(track) || used >= quota
                        || jobs == 0 && !hasSpace(0)) break;
                try { wait(200); }
                catch (InterruptedException cancelled) {
                    Thread.currentThread().interrupt(); throw new InterruptedIOException("HLS reserve cancelled");
                }
            }
            track.warmed = true;
            warmupMillis += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - track.started);
            elapsedWarmup = warmupMillis;
        }
        connections.warmed(elapsedWarmup, enough);
    }

    static double reserveSeconds(int mode, double duration, double downloadRate) {
        if (mode == 0 || duration <= 0) return 0;
        double safety = mode == 2 ? 5 : 2;
        return Math.min(duration, Math.max(safety, duration * Math.max(0, 1 - downloadRate) + safety));
    }
    static int waitSeconds(double target, double progress, double rate) {
        return rate > 0 ? (int) Math.ceil(Math.max(0, target - progress) / rate) : -1;
    }
    static boolean reserveSatisfied(double ready, double target) {
        return ready + RESERVE_TOLERANCE_SECONDS >= target;
    }

    private Entry entry(String url, boolean init, double seconds) {
        Entry entry = entries.get(url);
        if (entry == null) {
            entry = new Entry(); entry.url = url; entry.init = init; entry.seconds = seconds;
            entry.file = new File(directory, Integer.toString(++sequence)); entries.put(url, entry);
            if (init) initEntries.add(entry);
        }
        return entry;
    }
    private double readySeconds(Track track) {
        double seconds = 0;
        for (int i = track.current; i < track.segments.size(); i++) {
            Entry entry = track.segments.get(i);
            if (entry.bytes == 0) break;
            seconds += entry.seconds;
        }
        return seconds;
    }
    private double totalSeconds(Track track) {
        return track.durations[track.segments.size()] - track.durations[track.current];
    }
    private boolean exhausted(Track track) {
        for (int i = track.current; i < track.segments.size(); i++)
            if (track.segments.get(i).bytes == 0)
                return track.segments.get(i).failed || track.segments.get(i).storageBlocked;
        return true;
    }
    private void schedule() {
        if (closed || jobs >= 2 || used + promised >= quota) return;
        // Do not let a later job take quota before an earlier in-flight request
        // has received its size headers and reserved space.
        for (Entry entry : pending) if (!entry.headersReceived) return;
        for (Entry entry : initEntries) {
            int before = jobs; offer(entry);
            if (jobs > before) return;
        }
        for (int attempt = 0; attempt < trackOrder.size(); attempt++) {
            Track track = trackOrder.get(nextTrack);
            nextTrack = (nextTrack + 1) % trackOrder.size();
            // The completed prefix only advances once, rather than being scanned
            // for every fragment. A seek rewinds this cursor in get().
            while (track.next < track.segments.size()) {
                Entry entry = track.segments.get(track.next);
                if (entry.bytes == 0 && !entry.failed) break;
                track.next++;
            }
            int before = jobs;
            for (int i = track.next; i < track.segments.size(); i++) {
                offer(track.segments.get(i));
                // Only start the next request after this one's headers arrive.
                if (jobs > before) return;
            }
        }
    }
    private void offer(Entry entry) {
        if (jobs >= 2 || used + promised >= quota || entry.downloading || entry.failed || entry.bytes > 0) return;
        if (entry.expected > 0 && used + promised + entry.expected > quota) {
            entry.storageBlocked = true; return;
        }
        if (entry.storageBlocked && entry.expected <= 0) return;
        if (!hasSpace(0)) return;
        entry.storageBlocked = false;
        entry.headersReceived = false;
        if (entry.expected > 0) { entry.reservation = entry.expected; promised += entry.expected; }
        entry.downloading = true; pending.add(entry); jobs++;
        workers.execute(() -> download(entry));
    }
    private static final class StorageLimit extends IOException { }
    // Called under the cache lock. Charge both workers' writes against the same
    // conservative snapshot; refresh at most every MiB or second, not every read.
    private boolean hasSpace(long incoming) {
        long now = System.nanoTime();
        if (spaceCheckTime == 0 || bytesSinceSpaceCheck >= SPACE_CHECK_BYTES
                || now - spaceCheckTime >= TimeUnit.SECONDS.toNanos(1)) {
            usableSpace = directory.getUsableSpace();
            bytesSinceSpaceCheck = 0; spaceCheckTime = now;
        }
        return usableSpace - bytesSinceSpaceCheck >= FREE_SPACE_MARGIN + incoming;
    }
    private void download(Entry entry) {
        File partial = new File(entry.file.toString() + ".part");
        HttpURLConnection connection = null; long size = 0;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            connection = connections.open(entry.url);
            connection.setConnectTimeout(7000); connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            synchronized (this) { if (closed) return; active.add(connection); }
            if (connection.getResponseCode() != 200) throw new IOException("HLS reserve HTTP response");
            long expected = connection.getContentLength(); entry.type = connection.getContentType();
            if (expected > quota) throw new IOException("Media object exceeds disk reserve quota");
            synchronized (this) {
                entry.expected = expected; entry.headersReceived = true;
                if (entry.reservation == 0 && expected > 0) {
                    if (used + promised + expected > quota) throw new StorageLimit();
                    entry.reservation = expected; promised += expected;
                }
                schedule();
            }
            try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(partial)) {
                byte[] block = new byte[64 * 1024]; int read;
                while ((read = input.read(block)) != -1) {
                    synchronized (this) {
                        if (closed || System.nanoTime() > deadline) throw new IOException("HLS reserve cancelled");
                        long covered = Math.min(entry.reservation, read);
                        if (used + promised + read - covered > quota || !hasSpace(read))
                            throw new StorageLimit();
                        entry.reservation -= covered; promised -= covered;
                        used += read; size += read;
                        bytesSinceSpaceCheck += read;
                        entry.received = size;
                    }
                    output.write(block, 0, read);
                    connections.received(read);
                }
            }
            if (size == 0 || expected >= 0 && size != expected) throw new IOException("Incomplete HLS reserve");
            synchronized (this) {
                if (closed || !partial.renameTo(entry.file)) throw new IOException("Cannot commit HLS reserve");
                entry.bytes = size; size = 0;
            }
        } catch (StorageLimit limit) {
            synchronized (this) { entry.storageBlocked = true; }
        } catch (IOException | RuntimeException error) {
            synchronized (this) { entry.failed = true; }
        } finally {
            if (connection != null) connection.disconnect();
            partial.delete();
            synchronized (this) {
                active.remove(connection); pending.remove(entry);
                used -= size; jobs--; entry.downloading = false;
                if (size > 0) spaceCheckTime = 0;
                promised -= entry.reservation; entry.reservation = 0;
                if (entry.bytes == 0) entry.received = 0;
                if (closed) { entry.file.delete(); directory.delete(); }
                else schedule();
                notifyAll();
            }
        }
    }

    /** Claim a complete file; incomplete/failed jobs fall back to the normal streaming path. */
    synchronized File get(String url) throws IOException {
        Entry entry = entries.get(url);
        if (closed || entry == null) return null;
        for (Track track : tracks.values()) {
            Integer index = track.positions.get(entry);
            if (index != null) {
                track.next = index < track.current ? Math.min(track.next, index) : Math.max(track.next, index);
                track.current = index;
                track.evicted = Math.min(track.evicted, index);
                while (track.evicted < index - 2) {
                    Entry old = track.segments.get(track.evicted);
                    if (old.bytes > 0) {
                        if (!old.file.delete()) break;
                        used -= old.bytes; old.bytes = 0; spaceCheckTime = 0;
                    }
                    track.evicted++;
                }
            }
        }
        schedule();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!closed && entry.downloading && System.nanoTime() < deadline) {
            try { wait(200); }
            catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); return null; }
        }
        return !closed && entry.bytes > 0 && entry.file.isFile() ? entry.file : null;
    }
    synchronized String type(String url) {
        Entry entry = entries.get(url); return entry == null || entry.type == null ? "application/octet-stream" : entry.type;
    }
    synchronized long bytes() { return closed ? 0 : used; }
    synchronized boolean preparing() {
        for (Track track : tracks.values()) if (!track.warmed) return true;
        return false;
    }
    synchronized int preparingSeconds() {
        int ready = Integer.MAX_VALUE;
        for (Track track : tracks.values()) if (!track.warmed)
            ready = Math.min(ready, (int) readySeconds(track));
        return ready == Integer.MAX_VALUE ? 0 : ready;
    }
    synchronized int estimatedWaitSeconds() {
        int remaining = -1;
        for (Track track : tracks.values()) if (!track.warmed && track.slow)
            remaining = Math.max(remaining, track.expectedWait);
        return remaining;
    }
    @Override public void close() {
        List<HttpURLConnection> pending;
        synchronized (this) { closed = true; pending = new ArrayList<>(active); notifyAll(); }
        for (HttpURLConnection connection : pending) connection.disconnect();
        workers.shutdownNow();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        directory.delete();
        synchronized (LIVE_DIRECTORIES) {
            try { LIVE_DIRECTORIES.remove(directory.getCanonicalPath()); }
            catch (IOException ignored) { }
        }
    }
}
