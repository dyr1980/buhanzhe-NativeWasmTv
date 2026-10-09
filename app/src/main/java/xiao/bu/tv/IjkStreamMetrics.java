package xiao.bu.tv;

import java.lang.reflect.Method;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/** Optional nTv properties, independent of the bundled upstream Java API. */
final class IjkStreamMetrics {
    private static final Method GET_FLOAT = findGetter("_getPropertyFloat", int.class, float.class);
    private static final Method GET_LONG = findGetter("_getPropertyLong", int.class, long.class);
    private static final Method SET_LONG = findGetter("_setPropertyLong", int.class, long.class);
    private static final Method GET_CODEC = findGetter("_getVideoCodecInfo");

    private static Method findGetter(String name, Class<?>... parameters) {
        try {
            Method method = IjkMediaPlayer.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void excludeProxyWarmup(IjkMediaPlayer player, long milliseconds, boolean enough) {
        if (player != null && SET_LONG != null) try {
            SET_LONG.invoke(player, 22040, Math.min(2147483000L, Math.max(0L, milliseconds)));
            SET_LONG.invoke(player, 22041, enough ? 1L : 0L);
        } catch (Exception ignored) { }
    }

    static VideoStreamDescription videoDescription(IjkMediaPlayer player) {
        if (player != null && GET_LONG != null) try {
            return new VideoStreamDescription(((Number) GET_LONG.invoke(player, 22010, 0L)).longValue());
        } catch (Exception ignored) {}
        return VideoStreamDescription.UNKNOWN;
    }

    static String videoDecoderName(IjkMediaPlayer player) {
        if (player != null && GET_CODEC != null) try {
            String value = (String) GET_CODEC.invoke(player);
            if (value != null && value.trim().length() > 0) {
                int comma = value.indexOf(',');
                String name = (comma >= 0 ? value.substring(comma + 1) : value).trim();
                if (name.length() > 0) return name;
            }
        } catch (Exception ignored) {}
        return "未知";
    }

    static float sourceFrameRate(IjkMediaPlayer player) {
        if (player == null || GET_FLOAT == null) return 0f;
        try {
            return ((Number) GET_FLOAT.invoke(player, 11001, 0f)).floatValue();
        } catch (Exception ignored) {
            return 0f;
        }
    }

    static float outputFrameRate(IjkMediaPlayer player) {
        if (player == null || !player.isPlaying()) return 0f;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            float gpu=NativeHdrOutput.renderedFrameRate();
            if (!Float.isNaN(gpu)) return gpu;
        }
        return player.getVideoOutputFramesPerSecond();
    }

    static boolean extendedMetricsAvailable(IjkMediaPlayer player) {
        if (player == null || GET_FLOAT == null) return false;
        try {
            return ((Number) GET_FLOAT.invoke(player, 11001, -1f)).floatValue() >= 0f;
        } catch (Exception ignored) {
            return false;
        }
    }

    private IjkStreamMetrics() {}
}
