package xiao.bu.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.View;

import org.json.JSONObject;

/**
 * Rendering surface for the private lightweight browser engine.
 *
 * <p>The engine streams painted frames here as JSON packets. Without the
 * authorized {@code browser-core} the engine never starts, so this view only
 * paints a neutral background and ignores input.</p>
 */
final class LiteCanvas extends View {
    LiteEngine engine;
    int fixedViewportWidth;
    int fixedViewportHeight;

    private final Paint background = new Paint();
    private final Runnable invalidator = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    LiteCanvas(Context context) {
        super(context);
        background.setColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void frame(JSONObject packet) {
        if (packet == null) return;
        postInvalidateOnAnimationCompat();
    }

    void textInput(JSONObject state) {
    }

    boolean editingText() {
        return false;
    }

    boolean textInputKey(KeyEvent event) {
        return false;
    }

    void endTextInput() {
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
    }

    private void postInvalidateOnAnimationCompat() {
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            postInvalidateOnAnimation();
        } else {
            removeCallbacks(invalidator);
            postDelayed(invalidator, 16L);
        }
    }
}
