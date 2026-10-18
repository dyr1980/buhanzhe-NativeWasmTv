package xiao.bu.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Small Material-style vector icon rendered crisply on every Android API. */
final class BrowserIconView extends View {
    static final int HOME = 1;
    static final int BACK = 2;
    static final int FORWARD = 3;
    static final int REFRESH = 4;
    static final int ADD = 5;
    static final int BOOKMARKS = 6;
    static final int IMPORT = 7;
    static final int GLOBE = 8;
    static final int PIN = 9;
    static final int CLOSE = 10;
    static final int MORE = 11;
    static final int FAVORITE = 12;
    static final int FAVORITE_FILLED = 13;
    static final int FOLDER = 14;
    static final int COVER = 15;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int icon;
    private int color = 0xff303134;

    BrowserIconView(Context context, int icon) {
        this(context, null);
        this.icon = icon;
    }

    BrowserIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        focusPaint.setColor(0x18303134);
    }

    void setIconColor(int value) {
        color = value;
        invalidate();
    }

    void setIcon(int value) {
        icon = value;
        invalidate();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0f) return;
        if (isPressed() || isFocused()) {
            float radius = side * .42f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, focusPaint);
        }
        float drawing = side * .62f;
        float scale = drawing / 24f;
        int save = canvas.save();
        canvas.translate((getWidth() - drawing) / 2f, (getHeight() - drawing) / 2f);
        canvas.scale(scale, scale);
        paint.setColor(isEnabled() ? color : 0x66303134);
        paint.setAlpha(isPressed() ? 180 : 255);
        paint.setStrokeWidth(2.15f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        switch (icon) {
            case HOME: drawHome(canvas); break;
            case BACK: drawArrow(canvas, false); break;
            case FORWARD: drawArrow(canvas, true); break;
            case REFRESH: drawRefresh(canvas); break;
            case ADD: drawAdd(canvas); break;
            case BOOKMARKS: drawBookmarks(canvas); break;
            case IMPORT: drawImport(canvas); break;
            case GLOBE: drawGlobe(canvas); break;
            case PIN: drawPin(canvas); break;
            case CLOSE: drawClose(canvas); break;
            case MORE: drawMore(canvas); break;
            case FAVORITE: drawFavorite(canvas, false); break;
            case FAVORITE_FILLED: drawFavorite(canvas, true); break;
            case FOLDER: drawFolder(canvas); break;
            case COVER:
                paint.setStyle(Paint.Style.STROKE);
                path.moveTo(2f, 12f);
                path.cubicTo(7f, 4f, 17f, 4f, 22f, 12f);
                path.cubicTo(17f, 20f, 7f, 20f, 2f, 12f);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12f, 12f, 3f, paint);
                canvas.drawLine(4f, 3f, 20f, 21f, paint);
                break;
            default: break;
        }
        canvas.restoreToCount(save);
    }

    private void drawHome(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        path.moveTo(2.5f, 11f);
        path.lineTo(12f, 3f);
        path.lineTo(21.5f, 11f);
        path.lineTo(19f, 11f);
        path.lineTo(19f, 21f);
        path.lineTo(14.5f, 21f);
        path.lineTo(14.5f, 15f);
        path.lineTo(9.5f, 15f);
        path.lineTo(9.5f, 21f);
        path.lineTo(5f, 21f);
        path.lineTo(5f, 11f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawArrow(Canvas canvas, boolean forward) {
        paint.setStyle(Paint.Style.STROKE);
        float left = forward ? 5f : 19f;
        float right = forward ? 19f : 5f;
        canvas.drawLine(left, 12f, right, 12f, paint);
        path.moveTo(forward ? 13f : 11f, 6f);
        path.lineTo(right, 12f);
        path.lineTo(forward ? 13f : 11f, 18f);
        canvas.drawPath(path, paint);
    }

    private void drawRefresh(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        // Material refresh silhouette: a continuous ring and arrow head avoids
        // the disconnected triangle used by the old icon at television scale.
        path.moveTo(17.65f, 6.35f);
        path.cubicTo(16.20f, 4.90f, 14.21f, 4f, 12f, 4f);
        path.cubicTo(7.58f, 4f, 4f, 7.58f, 4f, 12f);
        path.cubicTo(4f, 16.42f, 7.58f, 20f, 12f, 20f);
        path.cubicTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f);
        path.lineTo(17.65f, 14f);
        path.cubicTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f);
        path.cubicTo(8.69f, 18f, 6f, 15.31f, 6f, 12f);
        path.cubicTo(6f, 8.69f, 8.69f, 6f, 12f, 6f);
        path.cubicTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f);
        path.lineTo(13f, 11f);
        path.lineTo(20f, 11f);
        path.lineTo(20f, 4f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawAdd(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(12f, 5f, 12f, 19f, paint);
        canvas.drawLine(5f, 12f, 19f, 12f, paint);
    }

    private void drawBookmarks(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        path.moveTo(7f, 4f);
        path.lineTo(18f, 4f);
        path.lineTo(18f, 20f);
        path.lineTo(12.5f, 16.5f);
        path.lineTo(7f, 20f);
        path.close();
        canvas.drawPath(path, paint);
        canvas.drawLine(4f, 6f, 4f, 20f, paint);
    }

    private void drawImport(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(12f, 5f, 12f, 16f, paint);
        path.moveTo(7.5f, 9.5f);
        path.lineTo(12f, 5f);
        path.lineTo(16.5f, 9.5f);
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(5f, 15f);
        path.lineTo(5f, 20f);
        path.lineTo(19f, 20f);
        path.lineTo(19f, 15f);
        canvas.drawPath(path, paint);
    }

    private void drawGlobe(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(12f, 12f, 8f, paint);
        oval.set(8f, 4f, 16f, 20f);
        canvas.drawOval(oval, paint);
        canvas.drawLine(4.5f, 12f, 19.5f, 12f, paint);
    }

    private void drawPin(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        path.moveTo(8f, 3f);
        path.lineTo(16f, 3f);
        path.lineTo(15f, 8f);
        path.lineTo(19f, 12f);
        path.lineTo(19f, 14f);
        path.lineTo(13f, 14f);
        path.lineTo(13f, 21f);
        path.lineTo(11f, 21f);
        path.lineTo(11f, 14f);
        path.lineTo(5f, 14f);
        path.lineTo(5f, 12f);
        path.lineTo(9f, 8f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawClose(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(6f, 6f, 18f, 18f, paint);
        canvas.drawLine(18f, 6f, 6f, 18f, paint);
    }

    private void drawMore(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(5f, 12f, 1.75f, paint);
        canvas.drawCircle(12f, 12f, 1.75f, paint);
        canvas.drawCircle(19f, 12f, 1.75f, paint);
    }

    /** Same five-point collection symbol used by the channel favorite control. */
    private void drawFavorite(Canvas canvas, boolean filled) {
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        final double start = -Math.PI / 2d;
        for (int i = 0; i < 10; i++) {
            double angle = start + i * Math.PI / 5d;
            float radius = (i & 1) == 0 ? 9f : 4.2f;
            float x = 12f + (float) Math.cos(angle) * radius;
            float y = 12f + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawFolder(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        path.moveTo(3f, 7f);
        path.lineTo(9.5f, 7f);
        path.lineTo(11.5f, 9f);
        path.lineTo(21f, 9f);
        path.lineTo(21f, 19f);
        path.lineTo(3f, 19f);
        path.close();
        canvas.drawPath(path, paint);
    }
}
