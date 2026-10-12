package xiao.bu.tv;

/** Conservative live processing budget, independent of Android/player APIs. */
final class AdaptiveLiveBuffer {
    private int level, slow, fast;
    private double average;
    private boolean sampled;
    private long changedAt;
    int level() { return level; }
    double averageRatio() { return average; }
    int sample(long durationMs, long downloadMs, long decryptMs, long nowMs) {
        if (durationMs <= 0 || downloadMs < 0 || decryptMs < 0) return level;
        double ratio = (downloadMs + (double) decryptMs) / durationMs;
        average = sampled ? average * .75 + ratio * .25 : ratio;
        sampled = true;
        int previous = level;
        if (ratio > 1) {
            fast = 0; slow++;
            level = Math.max(level, slow >= 2 ? 2 : 1);
        } else {
            slow = 0;
            fast = ratio < .7 && average < .8 ? fast + 1 : 0;
            if (fast >= 6 && nowMs - changedAt >= 30000 && level > 0) {
                level--; fast = 0;
            }
        }
        if (previous != level) changedAt = nowMs;
        return level;
    }
}
