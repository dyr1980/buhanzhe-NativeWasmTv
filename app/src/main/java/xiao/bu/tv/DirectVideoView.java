package xiao.bu.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

/** Direct video surface, preserving native video resolution independently of the UI layer. */
public final class DirectVideoView extends FrameLayout implements SurfaceHolder.Callback {
    private final SurfaceView surfaceView;
    private SurfaceCallback callback;
    private SurfaceHolder activeHolder;
    private int videoWidth, videoHeight, sarNum = 1, sarDen = 1;
    private boolean stretchVideo, legacySurfaceMode;

    public DirectVideoView(Context context) { this(context, null); }

    public DirectVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().setFormat(PixelFormat.OPAQUE);
        applySurfaceType();
        surfaceView.getHolder().addCallback(this);
        addView(surfaceView, new LayoutParams(-1, -1));
        setKeepScreenOn(true);
    }

    SurfaceView getSurfaceView() { return surfaceView; }
    void setLegacySurfaceMode(boolean enabled) {
        if (legacySurfaceMode == enabled) return;
        legacySurfaceMode = enabled;
        applySurfaceType();
    }

    private void applySurfaceType() {
        if (surfaceView == null) return;
        //noinspection deprecation
        surfaceView.getHolder().setType(legacySurfaceMode
                ? SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS : SurfaceHolder.SURFACE_TYPE_NORMAL);
    }

    void setSurfaceCallback(SurfaceCallback listener) {
        callback = listener;
        if (listener != null && isSurfaceReady())
            listener.onVideoSurfaceCreated(getVideoSurfaceHolder(), getVideoSurface());
    }

    boolean isSurfaceReady() {
        Surface surface = getVideoSurface();
        return surface != null && surface.isValid();
    }

    SurfaceHolder getVideoSurfaceHolder() { return activeHolder; }
    Surface getVideoSurface() {
        return activeHolder == null ? null : activeHolder.getSurface();
    }

    void setStretchVideo(boolean enabled) {
        if (stretchVideo == enabled) return;
        stretchVideo = enabled;
        requestLayout();
    }

    void setVideoSize(int width, int height, int sarNum, int sarDen) {
        videoWidth = Math.max(0, width);
        videoHeight = Math.max(0, height);
        this.sarNum = sarNum > 0 ? sarNum : 1;
        this.sarDen = sarDen > 0 ? sarDen : 1;
        if (surfaceView != null) {
            if (videoWidth > 0 && videoHeight > 0)
                surfaceView.getHolder().setFixedSize(videoWidth, videoHeight);
            else surfaceView.getHolder().setSizeFromLayout();
        }
        requestLayout();
    }

    void resetSurfaceBufferSizePreservingAspect() {
        if (surfaceView != null) surfaceView.getHolder().setSizeFromLayout();
        requestLayout();
    }

    boolean clearLastFrame() {
        if (surfaceView == null || !isSurfaceReady()) return false;
        SurfaceHolder holder = activeHolder;
        boolean cleared = false;
        for (int pass = 0; pass < 2; pass++) {
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) break;
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
                cleared = true;
            } catch (RuntimeException error) {
                return cleared;
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); }
                    catch (RuntimeException ignored) { }
                }
            }
        }
        return cleared;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        int width = availableWidth, height = availableHeight;
        if (!stretchVideo && videoWidth > 0 && videoHeight > 0
                && availableWidth > 0 && availableHeight > 0) {
            float videoAspect = (float) videoWidth * sarNum / ((float) videoHeight * sarDen);
            if ((float) availableWidth / availableHeight > videoAspect)
                width = Math.round(availableHeight * videoAspect);
            else height = Math.round(availableWidth / videoAspect);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        activeHolder = holder;
        if (callback != null) callback.onVideoSurfaceCreated(holder, holder.getSurface());
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (callback != null) callback.onVideoSurfaceDestroyed(holder, holder.getSurface());
        if (activeHolder == holder) activeHolder = null;
    }

    void releaseOutput() {
        callback = null;
        activeHolder = null;
        // SurfaceView owns its Surface; only detach our listener on Activity teardown.
        surfaceView.getHolder().removeCallback(this);
    }

    interface SurfaceCallback {
        void onVideoSurfaceCreated(SurfaceHolder holder, Surface surface);
        void onVideoSurfaceDestroyed(SurfaceHolder holder, Surface surface);
    }
}
