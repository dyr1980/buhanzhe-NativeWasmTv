package xiao.bu.tv;

/** UI-thread seek queue. Only the latest pending target matters. */
final class PlaybackSeekState {
    private static final long TIMEOUT_MS = 10000L;
    private long target = -1L, submitted = -1L, requestedAt;

    void reset() { target = submitted = -1L; }

    long request(long position, long now) {
        target = position;
        requestedAt = now;
        if (submitted >= 0L) return -1L;
        submitted = target;
        return submitted;
    }

    long complete() {
        if (submitted < 0L) return -1L;
        if (target != submitted) {
            submitted = target;
            return submitted;
        }
        submitted = -1L;
        return -1L;
    }

    long position(long actual, long now) {
        // Stop masking a stalled clock, but keep an outstanding native seek in
        // the queue: resetting it here could drop the user's next request.
        if (target >= 0L && now - requestedAt >= TIMEOUT_MS) return actual;
        // Seek-complete precedes the first new decoder clock on some devices.
        if (target >= 0L && submitted < 0L && actual >= target + 250L)
            target = -1L;
        return target >= 0L ? target : actual;
    }
}
