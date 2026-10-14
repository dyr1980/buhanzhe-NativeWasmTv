package xiao.bu.radio;

import android.graphics.*;

/** Software rasterization happens only on artwork changes, never during rotation. */
final class OpticalTexture {
    static final int SIZE = 1024;
    static final float COVER_RADIUS = .300f;

    static Bitmap cover(Bitmap cover) {
        int size = Math.round(SIZE * COVER_RADIUS * 2);
        Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float c = size * .5f, radius = c - 1f;
        if (cover != null && !cover.isRecycled()) {
            BitmapShader shader = new BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            float scale = radius * 2f / Math.min(cover.getWidth(), cover.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(c - cover.getWidth() * scale * .5f, c - cover.getHeight() * scale * .5f);
            shader.setLocalMatrix(matrix);
            p.setShader(shader);
        } else {
            p.setShader(new RadialGradient(c, c, radius,
                    new int[]{0xff4d6c82, 0xff1d3444, 0xff101f2a}, null, Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(c, c, radius, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(0xe0fff5fc);
        canvas.drawCircle(c, c, radius, p);
        return image;
    }

    static int tone(Bitmap cover) {
        if (cover == null || cover.isRecycled()) return 0xff65545f;
        long r = 0, g = 0, b = 0;
        int count = 0;
        // A bounded sample, independent of the downloaded image dimensions.
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int color = cover.getPixel((x * 2 + 1) * cover.getWidth() / 16,
                    (y * 2 + 1) * cover.getHeight() / 16);
            if (Color.alpha(color) < 128) continue;
            r += Color.red(color); g += Color.green(color); b += Color.blue(color); count++;
        }
        if (count == 0) return 0xff365462;
        return Color.rgb((int)(r / count), (int)(g / count), (int)(b / count));
    }
}
