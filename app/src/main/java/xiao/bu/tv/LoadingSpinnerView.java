package xiao.bu.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** A small neutral loading ring shared by channel and WebView loading cards. */
public final class LoadingSpinnerView extends View {
    private static final long ROTATION_MILLIS = 900L;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private boolean attached, framePosted;
    private final Runnable nextFrame = new Runnable() {
        @Override
        public void run() {
            framePosted = false;
            if (!canAnimate()) return;
            invalidate();
            scheduleFrame();
        }
    };

    public LoadingSpinnerView(Context context) {
        this(context, null);
    }

    public LoadingSpinnerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x52ffffff);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // A parent can become visible after this child was attached while hidden.
        scheduleFrame();
        float stroke = Math.max(2f, Math.min(getWidth(), getHeight()) * 0.085f);
        float inset = stroke / 2f + 1f;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);
        canvas.drawOval(bounds, trackPaint);
        float rotation = (SystemClock.uptimeMillis() % ROTATION_MILLIS)
                * 360f / ROTATION_MILLIS;
        canvas.drawArc(bounds, rotation - 90f, 78f, false, arcPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        restartAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        restartAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        restartAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        restartAnimation();
    }

    private boolean canAnimate() {
        return attached && getWindowVisibility() == VISIBLE && isShown();
    }

    private void scheduleFrame() {
        if (nextFrame != null && !framePosted && canAnimate()) {
            framePosted = true;
            postDelayed(nextFrame, 16L);
        }
    }

    private void restartAnimation() {
        if (nextFrame == null) return;
        removeCallbacks(nextFrame);
        framePosted = false;
        scheduleFrame();
    }
}
