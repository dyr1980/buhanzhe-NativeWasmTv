package xiao.bu.tv;

/** Screen-aware 44dp channel-row baseline, with user presets applied on top. */
final class UiScalePolicy {
    private static final float CHANNEL_ROW_DP = 46f;
    private static final float DEFAULT_ROW_DP = 44f;

    private UiScalePolicy() { }

    static float resolve(int width, int height, String mode, float diagonalInches, float density) {
        float preset = 1f;
        if ("small".equals(mode)) preset = .80f;
        else if ("slightly_small".equals(mode)) preset = .90f;
        else if ("large".equals(mode)) preset = 1.25f;
        else if ("extra_large".equals(mode)) preset = 1.50f;
        else if ("extra_extra_large".equals(mode)) preset = 2f;
        float baseline = viewportScale(width, height, density);
        if (width <= 0 || height <= 0) return baseline * preset;
        // Manual enlargement can use more space, but keep at least three rows accessible.
        float maximum = Math.min(width, height) / (3f * CHANNEL_ROW_DP * Math.max(.1f, density));
        return round(Math.min(baseline * preset, maximum));
    }

    static float viewportScale(int width, int height, float density) {
        if (width <= 0 || height <= 0) return DEFAULT_ROW_DP / CHANNEL_ROW_DP;
        float dpiScale = Math.max(.1f, density);
        int shortSide = Math.min(width, height);
        // Start at 44dp. Low-DPI large screens need larger controls; high-DPI small
        // screens need a cap so the list still has at least six rows of vertical space.
        float rowPixels = Math.max(DEFAULT_ROW_DP * dpiScale, Math.max(60f, shortSide / 9f));
        rowPixels = Math.min(rowPixels, shortSide / 6f);
        return rowPixels / (CHANNEL_ROW_DP * dpiScale);
    }

    private static float round(float value) { return Math.round(value * 100f) / 100f; }
}
