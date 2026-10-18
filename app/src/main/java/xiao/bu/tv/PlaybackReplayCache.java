package xiao.bu.tv;

import java.io.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Best-effort, per-channel disk cache. Neither network delivery nor the UI waits for writes. */
final class PlaybackReplayCache implements Closeable {
    static final long RESERVE = 400L * 1024 * 1024;
    static final long LIMIT = 512L * 1024 * 1024;
    private static final int BLOCK = 64 * 1024;
    interface Disk {
        long free(File directory);
        long nanos();
        void write(RandomAccessFile file, byte[] bytes, int count) throws IOException;
    }
    private static final Disk SYSTEM = new Disk() {
        public long free(File directory) { return directory.getUsableSpace(); }
        public long nanos() { return System.nanoTime(); }
        public void write(RandomAccessFile file, byte[] bytes, int count) throws IOException { file.write(bytes, 0, count); }
    };
    private static final Set<String> ACTIVE = new HashSet<String>();
    private final File directory;
    private final Disk disk;
    // At most 4 MiB queued, plus a single 64 KiB write in progress.
    private final ArrayBlockingQueue<Write> queue = new ArrayBlockingQueue<Write>(64);
    private final Map<String, Entry> entries = new HashMap<String, Entry>();
    private volatile boolean stopped, closed;
    private volatile String stopReason = "";
    private Thread worker;
    private long allocated, sampledBytes, sampledNanos;
    private int fileId, rangeCount;

    PlaybackReplayCache(File parent) { this(parent, SYSTEM); }
    PlaybackReplayCache(File parent, Disk disk) {
        this.disk = disk;
        directory = parent == null ? null : new File(new File(parent, "playback-replay"), UUID.randomUUID().toString());
        if (directory == null) stopped = true;
        else synchronized (ACTIVE) { ACTIVE.add(directory.getAbsolutePath()); }
    }
    String stopReason() { return stopReason; }
    synchronized void readFailed(String url) { Entry old = entries.remove(url); if (old != null) rangeCount -= old.ranges.size(); stop("disk read failed"); }

    void offer(String url, String type, String tag, long total, long offset, byte[] body, int start, int count) {
        if (stopped || closed || total <= 0 || total > LIMIT || offset < 0 || count <= 0 || offset > total - count) return;
        synchronized (this) {
            if (stopped || closed) return;
            if (worker == null) {
                worker = new Thread(new Runnable() { public void run() { drain(); } }, "playback-cache");
                worker.setDaemon(true); worker.start();
            }
        }
        for (int copied = 0; copied < count && !stopped && !closed;) {
            int size = Math.min(BLOCK, count - copied);
            byte[] bytes = new byte[size]; System.arraycopy(body, start + copied, bytes, 0, size);
            if (!queue.offer(new Write(url, type, tag, total, offset + copied, bytes))) {
                stop("write queue busy"); return;
            }
            copied += size;
        }
    }
    private void stop(String reason) { stopReason = reason; stopped = true; queue.clear(); }

    private void drain() {
        try {
            File parent = directory.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) { stop("cache directory unavailable"); return; }
            // Clean abandoned process sessions without touching another active proxy.
            File[] stale = parent.listFiles();
            if (stale != null) for (File file : stale) {
                boolean active; synchronized (ACTIVE) { active = ACTIVE.contains(file.getAbsolutePath()); }
                if (!active) remove(file);
            }
            if (disk.free(parent) < RESERVE) { stop("storage below 400 MiB"); return; }
            if (!directory.mkdir()) { stop("cache directory unavailable"); return; }
            while (!closed && !stopped) {
                Write write;
                try { write = queue.take(); } catch (InterruptedException ignored) { break; }
                if (closed || stopped) break;
                store(write);
            }
        } catch (IOException | RuntimeException error) { stop("disk write failed"); }
    }
    private void store(Write write) throws IOException {
        long started = disk.nanos();
        if (disk.free(directory) < RESERVE + write.bytes.length) { stop("storage below 400 MiB"); return; }
        Entry entry;
        synchronized (this) {
            entry = entries.get(write.url);
            if (entry == null || entry.total != write.total || !(entry.tag == null ? write.tag == null : entry.tag.equals(write.tag))) {
                if (entries.size() >= 4096 || fileId >= 8192) { stop("cache entry limit"); return; }
                if (entry != null) rangeCount -= entry.ranges.size();
                // A changed entity never inherits byte coverage from its previous revision.
                entry = new Entry(new File(directory, Integer.toString(++fileId)), write);
                entries.put(write.url, entry);
            }
            if (entry.covers(write.offset, write.offset + write.bytes.length)) return;
            if (rangeCount >= 8192) { stop("fragment limit"); return; }
        }
        try (RandomAccessFile file = new RandomAccessFile(entry.file, "rw")) {
            long growth = Math.max(0L, write.offset + write.bytes.length - file.length());
            // Includes sparse tail requests: do not risk allocating through the reserve.
            if (allocated > LIMIT - growth || disk.free(directory) < RESERVE + growth) { stop("storage reserve or cache limit"); return; }
            file.seek(write.offset); disk.write(file, write.bytes, write.bytes.length);
            allocated += growth;
        }
        long elapsed = Math.max(0L, disk.nanos() - started);
        synchronized (this) {
            rangeCount -= entry.ranges.size(); entry.add(write.offset, write.offset + write.bytes.length); rangeCount += entry.ranges.size();
        }
        sampledBytes += write.bytes.length; sampledNanos += elapsed;
        if (elapsed >= 250000000L || sampledBytes >= 1024 * 1024 && sampledNanos >= 1000000000L) {
            stop("slow disk write");
        } else if (sampledBytes >= 1024 * 1024) { sampledBytes = 0; sampledNanos = 0; }
    }

    /** Return only fully committed coverage. Misses keep streaming from the origin. */
    Hit open(String url, String range) {
        Entry entry;
        synchronized (this) { if (closed) return null; entry = entries.get(url); }
        if (entry == null) return null;
        long start = 0, end = entry.total - 1;
        if (range != null) {
            if (!range.matches("bytes=(?:[0-9]+-[0-9]*|-[0-9]+)")) return null;
            try {
                String value = range.substring(6); int dash = value.indexOf('-');
                if (dash == 0) { long suffix = Long.parseLong(value.substring(1)); if (suffix <= 0) return null; start = Math.max(0, entry.total - suffix); }
                else {
                    start = Long.parseLong(value.substring(0, dash));
                    if (dash < value.length() - 1) end = Math.min(end, Long.parseLong(value.substring(dash + 1)));
                }
            } catch (NumberFormatException invalid) { return null; }
        }
        synchronized (this) {
            if (closed || entries.get(url) != entry || start < 0 || start > end || !entry.covers(start, end + 1)) return null;
        }
        try { return new Hit(entry, start, end, range != null); }
        catch (IOException ignored) {
            synchronized (this) { if (entries.get(url) == entry) { entries.remove(url); rangeCount -= entry.ranges.size(); } }
            return null;
        }
    }
    @Override public void close() {
        Thread thread;
        synchronized (this) { if (closed) return; closed = true; queue.clear(); entries.clear(); thread = worker; }
        if (thread != null && thread.isAlive()) thread.interrupt();
        if (directory != null) {
            final Thread writer = thread;
            Thread cleanup = new Thread(new Runnable() { public void run() {
                if (writer != null) try { writer.join(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                remove(directory);
                synchronized (ACTIVE) { ACTIVE.remove(directory.getAbsolutePath()); }
            } }, "playback-cache-clear");
            cleanup.setDaemon(true); cleanup.start();
        }
    }
    private static void remove(File file) {
        if (file == null) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) remove(child);
        file.delete();
    }
    static final class Hit implements Closeable {
        final long start, end, total;
        final String type;
        final boolean partial;
        private final RandomAccessFile file;
        Hit(Entry entry, long start, long end, boolean partial) throws IOException {
            this.start = start; this.end = end; total = entry.total; type = entry.type; this.partial = partial;
            file = new RandomAccessFile(entry.file, "r"); file.seek(start);
        }
        void copy(OutputStream out, byte[] buffer) throws IOException {
            long remaining = end - start + 1;
            while (remaining > 0) {
                int size;
                try {
                    size = file.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                    if (size < 0) throw new EOFException("Replay cache truncated");
                } catch (IOException error) { throw new ReadFailure(error); }
                out.write(buffer, 0, size); remaining -= size;
            }
            out.flush();
        }
        public void close() throws IOException { file.close(); }
    }
    static final class ReadFailure extends IOException {
        ReadFailure(IOException cause) { super(cause); }
    }
    private static final class Entry {
        final File file; final String type, tag; final long total;
        final TreeMap<Long, Long> ranges = new TreeMap<Long, Long>();
        Entry(File file, Write write) { this.file = file; type = write.type; tag = write.tag; total = write.total; }
        boolean covers(long start, long end) { Map.Entry<Long, Long> span = ranges.floorEntry(start); return span != null && span.getValue() >= end; }
        void add(long start, long end) {
            Map.Entry<Long, Long> before = ranges.floorEntry(start);
            if (before != null && before.getValue() >= start) { start = before.getKey(); end = Math.max(end, before.getValue()); ranges.remove(before.getKey()); }
            Map.Entry<Long, Long> next;
            while ((next = ranges.ceilingEntry(start)) != null && next.getKey() <= end) { end = Math.max(end, next.getValue()); ranges.remove(next.getKey()); }
            ranges.put(start, end);
        }
    }
    private static final class Write {
        final String url, type, tag; final long total, offset; final byte[] bytes;
        Write(String url, String type, String tag, long total, long offset, byte[] bytes) { this.url = url; this.type = type; this.tag = tag; this.total = total; this.offset = offset; this.bytes = bytes; }
    }
}
