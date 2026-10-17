package xiao.bu.tv;

/** Requires a moving media clock and buffered video, not just a low FPS reading. */
final class InterlacedDecodeMonitor {
    private Object player;
    private long began, lastAt, lastPosition, lowSince;
    private boolean attempted;

    boolean sample(Object current, long now, long position, boolean healthy, float fps) {
        if (player != current) {
            player = current;
            began = now;
            lastAt = now;
            lastPosition = position;
            lowSince = 0L;
            attempted = false;
            return false;
        }
        long elapsed = now - lastAt, progress = position - lastPosition;
        lastAt = now;
        lastPosition = position;
        // Seek, pause, buffering, clock stalls and missing/late samples break the window.
        if (!healthy || elapsed <= 0L || elapsed > 2500L
                || progress < elapsed * 0.8d || progress > elapsed * 1.3d) {
            began = now;
            lowSince = 0L;
            return false;
        }
        if (now - began < 3000L || Float.isNaN(fps) || fps < 1f || fps >= 40f) {
            lowSince = 0L;
            return false;
        }
        if (attempted) return false;
        if (lowSince == 0L) lowSince = now;
        if (now - lowSince < 5000L) return false;
        attempted = true;
        return true;
    }
}
