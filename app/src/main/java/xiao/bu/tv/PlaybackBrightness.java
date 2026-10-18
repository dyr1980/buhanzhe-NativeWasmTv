package xiao.bu.tv;

import android.app.Activity;
import android.provider.Settings;
import android.view.WindowManager;

/** Per-window brightness; never changes the device's global brightness setting. */
final class PlaybackBrightness {
    static int systemPercent(Activity activity) {
        try {
            int value = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            if (value >= 0 && value <= 255)
                return Math.max(1, Math.round(value * 100f / 255f));
        } catch (Settings.SettingNotFoundException | RuntimeException unavailable) {
            // Some televisions do not expose a readable brightness setting.
        }
        return 50;
    }

    static int percent(Activity activity) {
        float value = activity.getWindow().getAttributes().screenBrightness;
        return value < 0f ? systemPercent(activity)
                : Math.max(1, Math.min(100, Math.round(value * 100f)));
    }

    static void apply(Activity activity, int percent) {
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        float value = Math.max(1, Math.min(100, percent)) / 100f;
        if (params.screenBrightness == value) return;
        params.screenBrightness = value;
        activity.getWindow().setAttributes(params);
    }
}
