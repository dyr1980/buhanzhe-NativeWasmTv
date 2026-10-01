package xiao.bu.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;

/** Keeps API 24 multi-window calls out of the verifier path on legacy televisions. */
final class MultiWindowCompat {
    private MultiWindowCompat() {
    }

    static boolean isInMultiWindowMode(Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && Api24.isInMultiWindowMode(activity);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static final class Api24 {
        static boolean isInMultiWindowMode(Activity activity) {
            return activity.isInMultiWindowMode();
        }
    }
}
