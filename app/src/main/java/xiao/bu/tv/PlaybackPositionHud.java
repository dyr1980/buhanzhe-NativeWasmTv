package xiao.bu.tv;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** A passive label sharing the clock's style and the host's existing one-second tick. */
final class PlaybackPositionHud {
    @SuppressWarnings("deprecation")
    static void style(TextView position, TextView clock) {
        position.setTextSize(TypedValue.COMPLEX_UNIT_PX, clock.getTextSize());
        position.setTypeface(clock.getTypeface()); position.setTextColor(clock.getCurrentTextColor());
        position.setIncludeFontPadding(false); position.setSingleLine(true);
        position.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        position.setShadowLayer(Math.max(1.5f, clock.getTextSize() * .09f), 1, 1, 0xe6000000);
        Drawable background = clock.getBackground();
        // The clock background is built in physical pixels by the theme. Supplying
        // Resources here applies density scaling again (including corner radii).
        position.setBackgroundDrawable(background != null && background.getConstantState() != null
                ? background.getConstantState().newDrawable().mutate() : null);
        position.setPadding(clock.getPaddingLeft(), clock.getPaddingTop(), clock.getPaddingRight(), clock.getPaddingBottom());
    }
    static void update(TextView position, TextView clock, String text, boolean clockVisible, boolean clockRight,
            int width, int edge, int top, int gap) {
        boolean visible = !text.isEmpty();
        if (!TextUtils.equals(position.getText(), text)) position.setText(text);
        if (position.getVisibility() != (visible ? View.VISIBLE : View.GONE)) position.setVisibility(visible ? View.VISIBLE : View.GONE);
        int available = Math.max(1, width - 2 * edge);
        int positionWidth = Math.min(visible && clockVisible ? Math.max(1, available - gap - 1) : available, (int)Math.ceil(position.getPaint().measureText(text))
                + position.getPaddingLeft() + position.getPaddingRight());
        maxWidth(position, positionWidth);
        position.setEllipsize(TextUtils.TruncateAt.END);
        int right = edge;
        if (visible && clockVisible) {
            int clockWidth = Math.min(Math.max(1, available - positionWidth - gap),
                    (int)Math.ceil(clock.getPaint().measureText(clock.getText().toString())) + clock.getPaddingLeft() + clock.getPaddingRight());
            maxWidth(clock, clockWidth);
            clock.setEllipsize(TextUtils.TruncateAt.START);
            FrameLayout.LayoutParams c = (FrameLayout.LayoutParams)clock.getLayoutParams();
            if (c.width != -2) { c.width = -2; clock.setLayoutParams(c); }
            if (clockRight) right += clockWidth + gap;
        } else maxWidth(clock, Integer.MAX_VALUE);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams)position.getLayoutParams();
        int height = ((FrameLayout.LayoutParams)clock.getLayoutParams()).height;
        if (p.gravity != (Gravity.TOP | Gravity.RIGHT) || p.rightMargin != right || p.topMargin != top || p.height != height) {
            p.width = -2; p.height = height; p.gravity = Gravity.TOP | Gravity.RIGHT;
            p.leftMargin = 0; p.rightMargin = right; p.topMargin = top; position.setLayoutParams(p);
        }
    }
    private static void maxWidth(TextView view, int width) {
        if (android.os.Build.VERSION.SDK_INT < 16 || view.getMaxWidth() != width) view.setMaxWidth(width);
    }
}
