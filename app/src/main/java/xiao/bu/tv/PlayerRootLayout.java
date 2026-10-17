package xiao.bu.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Player root without the platform full-screen focus tint. */
public final class PlayerRootLayout extends FrameLayout {
    private android.view.View receiverVideo, receiverCursor;
    void setReceiverLayers(android.view.View video, android.view.View cursor) {
        receiverVideo=video; receiverCursor=cursor; invalidate();
    }
    @Override protected boolean drawChild(android.graphics.Canvas canvas,
            android.view.View child, long drawingTime) {
        if(receiverVideo!=null && child!=receiverVideo && child!=receiverCursor)return true;
        return super.drawChild(canvas,child,drawingTime);
    }

    public PlayerRootLayout(Context context) {
        this(context, null);
    }

    public PlayerRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The player root receives focus when menus close so remote keys keep
        // working. It is not a selectable control: Oreo's default focus highlight
        // would otherwise tint the entire playback area in mouse/remote mode.
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
        }
    }

}
