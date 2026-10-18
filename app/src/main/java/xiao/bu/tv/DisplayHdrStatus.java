package xiao.bu.tv;

import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import java.lang.reflect.Method;
import java.util.Locale;

/** Public display telemetry, independent of source tags and the decoder's output route. */
final class DisplayHdrStatus {
    static final Snapshot UNKNOWN = new Snapshot(Float.NaN);
    private Object lastDisplay;
    private long lastReadAt;
    private Snapshot cached = UNKNOWN;

    static final class Snapshot {
        final float ratio;
        Snapshot(float value) { ratio = value; }
        boolean available() { return !Float.isNaN(ratio); }
        String label() {
            // Headroom is NOT proof that this app's HDR video activated the panel.
            return available() ? String.format(Locale.US,"屏幕HDR余量 %.2f×",ratio) : "屏幕HDR状态未知";
        }
    }

    static Snapshot ratio(boolean available,float value) {
        return available && !Float.isNaN(value) && !Float.isInfinite(value) && value>=1f
                ? new Snapshot(value) : UNKNOWN;
    }

    synchronized Snapshot read(View output) {
        // No display query/reflection on old versions; do not load newer APIs there.
        if(Build.VERSION.SDK_INT<34 || output==null)return UNKNOWN;
        Display display=Api34.display(output);
        if(display==null || !display.isValid()) {
            lastDisplay=null;cached=UNKNOWN;return UNKNOWN;
        }
        long now=SystemClock.elapsedRealtime();
        if(display==lastDisplay && now-lastReadAt<1000)return cached;
        lastDisplay=display;lastReadAt=now;cached=Api34.read(display);
        return cached;
    }

    private static final class Api34 {
        // Public API reflection keeps compileSdk 30 / minSdk 14 and APK size intact.
        private static final Method AVAILABLE=find("isHdrSdrRatioAvailable"),RATIO=find("getHdrSdrRatio");
        private static Method find(String name) {
            try { return Display.class.getMethod(name); }
            catch(ReflectiveOperationException | SecurityException ignored) { return null; }
        }
        static Display display(View output) {
            try { return output.getDisplay(); }
            catch(RuntimeException ignored) { return null; }
        }
        static Snapshot read(Display display) {
            if(AVAILABLE==null || RATIO==null)return UNKNOWN;
            try {
                if(!Boolean.TRUE.equals(AVAILABLE.invoke(display)))return UNKNOWN;
                Object value=RATIO.invoke(display);
                return value instanceof Number ? ratio(true,((Number)value).floatValue()) : UNKNOWN;
            } catch(ReflectiveOperationException | RuntimeException ignored) { return UNKNOWN; }
        }
    }
}
