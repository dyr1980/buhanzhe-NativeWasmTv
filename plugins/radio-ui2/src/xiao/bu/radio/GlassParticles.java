package xiao.bu.radio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Two depth planes of the downward drifting lights in the glass-record reference. */
final class GlassParticles {
    private static final int BACK_COUNT = 92;
    private static final int FRONT_COUNT = 56;
    private static final int COUNT = BACK_COUNT + FRONT_COUNT;
    private static final int SINE_SIZE = 2048;
    private static final float TAU = 6.28318530718f;
    private static final float SINE_SCALE = SINE_SIZE / TAU;
    private static final int FOG = 0, PEARL = 1, PIN = 2, STAR = 3;

    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final float[] drift = new float[COUNT];
    private final float[] radius = new float[COUNT];
    private final float[] opacity = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final float[] flutter = new float[COUNT];
    private final int[] kind = new int[COUNT];
    private final float[] sine = new float[SINE_SIZE];
    private final Bitmap[] sprites = new Bitmap[4];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private int seed = 0x7c309b21;

    GlassParticles() {
        for (int i = 0; i < SINE_SIZE; i++) sine[i] = (float) Math.sin(i * TAU / SINE_SIZE);
        sprites[FOG] = makeSprite(48, 48, FOG);
        sprites[PEARL] = makeSprite(48, 48, PEARL);
        sprites[PIN] = makeSprite(32, 32, PIN);
        sprites[STAR] = makeSprite(128, 192, STAR);
        for (int i = 0; i < COUNT; i++) {
            boolean front = i >= BACK_COUNT;
            x[i] = random();
            y[i] = random();
            // Optical flow in the reference separates 120--170 px/s far lights from
            // 240--310 px/s lights passing above the record. Lateral travel is minimal.
            speed[i] = (front ? 240f : 120f) + random() * (front ? 70f : 50f);
            drift[i] = (random() - .5f) * 3.5f;
            phase[i] = random() * TAU;
            flutter[i] = .34f + random() * .60f;
            radius[i] = front ? 2.1f + random() * 5.1f : 2f + random() * 6.6f;
            opacity[i] = front ? .26f + random() * .45f : .11f + random() * .29f;
            kind[i] = front ? (random() < .30f ? PIN : PEARL)
                    : (random() < .25f ? PEARL : FOG);
            if (i % 11 == 0) {
                radius[i] = 9f + random() * (front ? 12f : 11f);
                opacity[i] = .10f + random() * (front ? .15f : .16f);
                kind[i] = front ? PEARL : FOG;
            }
        }
        // The far plane has more stars; opaque groove and label material hide them.
        // Their initial placements also keep the first frame close to the supplied recording.
        star(0, .197f, .513f, 14.7f, 151f, 1.40f);
        star(1, .184f, .538f, 11.3f, 171f, 1.70f);
        star(2, .922f, .600f, 8.7f, 143f, 2.60f);
        star(3, .381f, .715f, 7.4f, 127f, 3.20f);
        star(4, .067f, .235f, 7.7f, 138f, .30f);
        star(5, .632f, .092f, 9.8f, 146f, 4.20f);
        star(6, .852f, .842f, 8.0f, 164f, 5.20f);
        for (int i = 7; i < 12; i++) star(i, x[i], y[i], 5f + random() * 5f,
                120f + random() * 50f, phase[i]);
        // Close glints can pass over the silver and black rings, independently of their rotation.
        // The first trace was measured at x406,y913,t9.38s with a speed of 291 px/s.
        star(BACK_COUNT, .376f, .3265f, 7.1f, 290.9f, 5.32f);
        star(BACK_COUNT + 1, .662f, .978f, 6.8f, 268f, 3.60f);
        star(BACK_COUNT + 2, .900f, 1.016f, 6.0f, 303f, 4.50f);
        star(BACK_COUNT + 3, .266f, .091f, 5.8f, 280f, .20f);
        flutter[BACK_COUNT] = .94f;
    }

    /** Draw before the glass for the far plane and after it for the close plane. */
    void draw(Canvas canvas, float width, float height, RectF disc, float seconds, boolean foreground) {
        if (width <= 0f || height <= 0f) return;
        // disc is the 1,000 px layout square; its visible glass diameter is 908 px.
        float unit = disc != null && disc.width() > 0f ? disc.width() / 1000f
                : Math.min(width / 1080f, height / 2400f);
        if (!(unit > 0f)) return;
        // Work in reference pixels so lights retain their size relative to the record on tablets.
        float fieldWidth = width / unit;
        float fieldHeight = height / unit;
        float t = seconds >= 0f && seconds < Float.MAX_VALUE ? seconds : 0f;
        int begin = foreground ? BACK_COUNT : 0;
        int end = foreground ? COUNT : BACK_COUNT;
        int save = canvas.save();
        canvas.scale(unit, unit);
        for (int i = begin; i < end; i++) {
            float margin = kind[i] == STAR ? 100f : radius[i] * 3f;
            float px = wrap(x[i] * fieldWidth + drift[i] * t
                    + 2.2f * (sin(phase[i] + t * .23f) - sin(phase[i])), fieldWidth, margin);
            float py = wrap(y[i] * fieldHeight + speed[i] * t
                    + 2.4f * sin(phase[i] + t * .41f), fieldHeight, margin);
            float breathing = .5f + .5f * sin(phase[i] + t * flutter[i]);
            float alpha, halfWidth, halfHeight;
            if (kind[i] == STAR) {
                // Keep the bright lens core alive. Only the halo breathes: a fourth-power
                // extinction made every prominent star disappear around the same frame.
                alpha = opacity[i] * (.92f + .16f * breathing);
                float size = radius[i] * (.82f + .22f * breathing * breathing);
                halfWidth = size * 3.4f;
                halfHeight = size * 5.1f;
            } else {
                alpha = opacity[i] * (foreground ? .68f + .32f * breathing : .57f + .43f * breathing);
                halfWidth = radius[i] * (kind[i] == FOG ? 1.75f : 1.30f);
                halfHeight = halfWidth;
            }
            paint.setAlpha(Math.min(255, Math.round(alpha * 255f)));
            destination.set(px - halfWidth, py - halfHeight, px + halfWidth, py + halfHeight);
            canvas.drawBitmap(sprites[kind[i]], null, destination, paint);
        }
        canvas.restoreToCount(save);
    }

    private void star(int i, float px, float py, float size, float velocity, float offset) {
        kind[i] = STAR;
        x[i] = px; y[i] = py; radius[i] = size; speed[i] = velocity;
        opacity[i] = 1f;
        phase[i] = offset;
        flutter[i] = .70f + (i % 12) * .063f;
    }

    private float sin(float angle) { return sine[((int) (angle * SINE_SCALE)) & (SINE_SIZE - 1)]; }

    private static float wrap(float value, float extent, float margin) {
        float period = extent + margin * 2f;
        value = (value + margin) % period;
        if (value < 0f) value += period;
        return value - margin;
    }

    private float random() {
        seed = seed * 1664525 + 1013904223;
        return (seed >>> 8) * (1f / 16777216f);
    }

    /** Rasterized once: soft focus and light bloom work on API14 hardware without a blur filter. */
    private static Bitmap makeSprite(int width, int height, int type) {
        int[] pixels = new int[width * height];
        float cx = (width - 1f) * .5f, cy = (height - 1f) * .5f;
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                float dx = (ix - cx) / (width * .5f), dy = (iy - cy) / (height * .5f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha, white;
                if (type == FOG) {
                    alpha = (float) Math.exp(-distance * distance * 5.5f) * .81f;
                    alpha *= Math.max(0f, 1f - distance * distance);
                    white = .18f;
                } else if (type == PEARL) {
                    // Defocused translucent light; the broad soft edge avoids flat polka dots.
                    alpha = clamp((1f - distance) * 1.85f);
                    alpha = alpha * alpha * (3f - 2f * alpha);
                    white = .60f;
                } else if (type == PIN) {
                    alpha = clamp((.67f - distance) * 3.7f)
                            + (float) Math.exp(-distance * distance * 6f) * .20f;
                    white = .93f;
                } else {
                    // Physical pixels keep the core circular although the sprite includes longer Y rays.
                    float gx = (ix - cx) / width, gy = (iy - cy) / width;
                    float r = (float) Math.sqrt(gx * gx + gy * gy);
                    float core = clamp((.157f - r) * 35f);
                    float halo = (float) Math.exp(-r * r * 42f) * .70f;
                    float vertical = (float) (Math.exp(-gx * gx * 1900f)
                            * Math.exp(-Math.abs(gy) * 4.6f)) * .94f;
                    float horizontal = (float) (Math.exp(-gy * gy * 2900f)
                            * Math.exp(-Math.abs(gx) * 8f)) * .70f;
                    float diagonal = (float) (Math.exp(-(Math.abs(gx) - Math.abs(gy))
                            * (Math.abs(gx) - Math.abs(gy)) * 2200f)
                            * Math.exp(-r * 13f)) * .22f;
                    alpha = Math.max(core, Math.max(halo, Math.max(vertical, horizontal))) + diagonal;
                    // Fade only at the sprite border. Applying this ramp over the whole
                    // sprite dimmed the white core and erased thin rays after downsampling.
                    alpha *= clamp((1f - Math.max(Math.abs(dx), Math.abs(dy))) * 4f);
                    white = clamp(core + .21f);
                }
                int a = Math.round(clamp(alpha) * 255f);
                int red = Math.round(146f + white * 109f);
                int green = Math.round(185f + white * 70f);
                int blue = Math.round(232f + white * 23f);
                pixels[iy * width + ix] = (a << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private static float clamp(float value) { return value < 0f ? 0f : value > 1f ? 1f : value; }
}
