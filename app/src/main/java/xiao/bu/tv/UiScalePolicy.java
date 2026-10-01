package xiao.bu.tv;

/** Computes bounded native UI scaling from the live viewport and the user preset. */
final class UiScalePolicy {
    private static final double REFERENCE_AREA = 1920.0d * 1080.0d;
    private static final float MIN_VIEWPORT_SCALE = 0.35f;
    private static final float MAX_VIEWPORT_SCALE = 1.20f;
    private static final float MIN_EFFECTIVE_SCALE = 0.35f;
    private static final float MAX_EFFECTIVE_SCALE = 1.60f;

    private UiScalePolicy() {
    }

    static float resolve(int viewportWidth, int viewportHeight, String mode,
            float displayDiagonalInches, float density) {
        float preset = 1f;
        if ("large".equals(mode)) {
            preset = 1.25f;
        } else if ("extra_large".equals(mode)) {
            preset = 1.50f;
        } else if ("extra_extra_large".equals(mode)) {
            preset = 2.00f;
        } else if ("auto".equals(mode)
                && displayDiagonalInches >= 32f && displayDiagonalInches <= 100f) {
            // Preserve roughly the same physical control size on common televisions.
            preset = clamp(65f / displayDiagonalInches, 0.95f, 1.30f);
        }
        return round(clamp(viewportScale(viewportWidth, viewportHeight, density) * preset,
                MIN_EFFECTIVE_SCALE, MAX_EFFECTIVE_SCALE));
    }

    static float viewportScale(int viewportWidth, int viewportHeight, float density) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return 1f;
        }
        double areaRatio = ((double) viewportWidth * (double) viewportHeight)
                / REFERENCE_AREA;
        // Keep the same screen-area proportion as 1080p. A density-aware floor keeps
        // the smallest text and touch targets readable on low-density televisions.
        float proportional = (float) Math.sqrt(Math.max(0.0001d, areaRatio));
        float readableFloor = clamp(0.90f / Math.max(0.1f, density),
                0.35f, 0.80f);
        return round(clamp(Math.max(proportional, readableFloor),
                MIN_VIEWPORT_SCALE, MAX_VIEWPORT_SCALE));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float round(float value) {
        return Math.round(value * 100f) / 100f;
    }
}
