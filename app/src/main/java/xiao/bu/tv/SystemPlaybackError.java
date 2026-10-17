package xiao.bu.tv;

/** Framework error codes describe different failures; only SERVER_DIED proves a crash. */
final class SystemPlaybackError {
    static boolean crashed(int what) { return what == 100; }
    static String message(int what, int extra) {
        String reason;
        if (crashed(what)) reason = "系统播放器服务崩溃";
        else if (extra == -1007) reason = "系统播放器无法解析媒体";
        else if (extra == -1010) reason = "系统播放器不支持此媒体格式";
        else if (extra == -1004) reason = "系统播放器读取媒体失败";
        else if (extra == -110) reason = "系统播放器读取超时";
        else reason = "系统播放器发生错误";
        return reason + "（" + what + "/" + extra + "）";
    }
}
