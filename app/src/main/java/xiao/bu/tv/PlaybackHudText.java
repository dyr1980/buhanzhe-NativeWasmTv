package xiao.bu.tv;

/** HUD strings only: no probing, player access or recurring work. */
final class PlaybackHudText {
    static String position(long position, long duration) {
        return duration <= 0 ? "" : time(Math.max(0, Math.min(position, duration))) + "/" + time(duration);
    }
    private static String time(long millis) {
        long seconds = millis / 1000, minutes = seconds / 60;
        return (minutes >= 60 ? minutes / 60 + ":" + two(minutes % 60) : two(minutes)) + ":" + two(seconds % 60);
    }
    private static String two(long number) { return number < 10 ? "0" + number : Long.toString(number); }
    static String source(String source) {
        // The view applies width-based ellipsis. Query/fragment presence alone
        // does not mean that an address is too long to display.
        return source == null ? "" : source.replace('\n', ' ').replace('\r', ' ');
    }
}
