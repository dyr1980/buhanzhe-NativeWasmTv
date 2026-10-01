package xiao.bu.tv;

/** Uses playback samples, not an uncancellable delay after selecting a URL. */
final class SniffedPlaybackWarmup {
    static final long RETENTION_MS = 15000L;
    private long started = -1, sampled = -1, position = -1;
    private boolean completed;

    void reset() { started = sampled = position = -1; completed = false; }

    boolean sample(long now, long currentPosition, boolean healthy) {
        if (completed) return false;
        if (!healthy || currentPosition < 0) {
            started = sampled = position = -1;
            return false;
        }
        if (started < 0 || now < sampled || now - sampled > 2000
                || currentPosition <= position) started = now;
        sampled = now;
        position = currentPosition;
        if (now - started < RETENTION_MS) return false;
        completed = true;
        return true;
    }
}
