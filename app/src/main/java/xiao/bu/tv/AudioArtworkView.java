package xiao.bu.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.util.AttributeSet;
import android.view.View;
import android.view.TextureView;

/** Compose the record once; animation rotates a texture instead of clipping a shader every frame. */
public final class AudioArtworkView extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint background = new Paint();
    private int backgroundHeight;
    private final RectF disc = new RectF();
    private Bitmap vinyl, cover, record;
    private int coverRevision;
    private String title = "", kind = "音乐";
    private float textSize;
    private final RecordView recordView;
    private final ImageView outgoingView;
    private final ImageView neighborView;
    private final ImageView trailingView;
    private boolean dragging;
    private float dragStart, dragOutgoingStart, dragTrailingStart;
    private String neighborLogo;
    private Bitmap neighborCover;
    private boolean pendingPresentation;
    private String pendingLogo = "";
    private ValueAnimator slideOut, slideIn;
    private int incomingDirection;
    private boolean waitingForIncoming;
    private boolean handoffAnimating;
    private Runnable transitionListener;
    private final Runnable exitTimeout = () -> finishChannelSwitch();
    private ObjectAnimator rotation;
    private boolean legacyRotationRunning;
    private long legacyRotationStartedAt;
    private float legacyRotationStartDegrees;
    private final Runnable legacyRotationTick = new Runnable() {
        @Override public void run() {
            if (!legacyRotationRunning) return;
            if (!shouldRotate()) { updateAnimation(); return; }
            long elapsed = SystemClock.uptimeMillis() - legacyRotationStartedAt;
            recordView.setDiscRotation((legacyRotationStartDegrees
                    + elapsed * 360f / 30000f) % 360f);
            postDelayed(this, 33L);
        }
    };
    private boolean playing, active = true, attached;

    /** Keep the artwork in one GPU texture; old Android only updates its transform. */
    private static final class RecordView extends FrameLayout {
        private final ImageView staticView;
        private final TextureView textureView;
        private final Matrix textureMatrix = new Matrix();
        private final Paint texturePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;
        private boolean legacyAnimating;
        private float discRotation;
        RecordView(Context context) {
            super(context);
            staticView = new ImageView(context);
            staticView.setScaleType(ImageView.ScaleType.FIT_XY);
            addView(staticView, new FrameLayout.LayoutParams(-1, -1));
            if (android.os.Build.VERSION.SDK_INT <= 15) {
                textureView = new TextureView(context);
                textureView.setOpaque(false);
                textureView.setVisibility(INVISIBLE);
                textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        drawTexture();
                    }
                    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                        drawTexture();
                    }
                    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        staticView.setVisibility(VISIBLE);
                        return true;
                    }
                    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
                });
                addView(textureView, new FrameLayout.LayoutParams(-1, -1));
            } else {
                textureView = null;
            }
        }
        void setImageBitmap(Bitmap value) {
            bitmap = value;
            staticView.setImageBitmap(value);
            drawTexture();
        }
        void setImageDrawable(android.graphics.drawable.Drawable value) {
            if (value == null) bitmap = null;
            staticView.setImageDrawable(value);
            drawTexture();
        }
        private void drawTexture() {
            if (textureView == null || !textureView.isAvailable()
                    || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return;
            Canvas canvas = null;
            try {
                canvas = textureView.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(android.graphics.Color.TRANSPARENT,
                        android.graphics.PorterDuff.Mode.CLEAR);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, new android.graphics.Rect(
                            0, 0, textureView.getWidth(), textureView.getHeight()), texturePaint);
                }
            } catch (RuntimeException error) {
                android.util.Log.w("AudioArtworkView", "Unable to update record texture", error);
            } finally {
                if (canvas != null) textureView.unlockCanvasAndPost(canvas);
            }
            if (legacyAnimating && bitmap != null) staticView.setVisibility(INVISIBLE);
        }
        float getDiscRotation() {
            return android.os.Build.VERSION.SDK_INT <= 15 ? discRotation : getRotation();
        }
        void setDiscRotation(float degrees) {
            if (android.os.Build.VERSION.SDK_INT > 15) {
                setRotation(degrees);
            } else if (discRotation != degrees) {
                discRotation = degrees;
                textureMatrix.setRotate(degrees, getWidth() * .5f, getHeight() * .5f);
                if (textureView != null) textureView.setTransform(textureMatrix);
                if (textureView == null || !textureView.isAvailable()) {
                    staticView.setRotation(degrees);
                }
            }
        }
        void setLegacyAnimating(boolean value) {
            if (textureView == null || legacyAnimating == value) return;
            legacyAnimating = value;
            if (value) {
                textureView.setVisibility(VISIBLE);
                drawTexture();
            } else {
                staticView.setRotation(discRotation);
                staticView.setVisibility(VISIBLE);
                textureView.setVisibility(INVISIBLE);
            }
        }
    }

    public AudioArtworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        recordView = new RecordView(context);
        if (android.os.Build.VERSION.SDK_INT >= 16)
            recordView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(recordView, new FrameLayout.LayoutParams(1, 1));
        outgoingView = new ImageView(context);
        outgoingView.setScaleType(ImageView.ScaleType.FIT_XY);
        outgoingView.setVisibility(GONE);
        addView(outgoingView, new FrameLayout.LayoutParams(1, 1));
        neighborView = new ImageView(context);
        neighborView.setScaleType(ImageView.ScaleType.FIT_XY);
        neighborView.setVisibility(GONE);
        addView(neighborView, new FrameLayout.LayoutParams(1, 1));
        trailingView = new ImageView(context);
        trailingView.setScaleType(ImageView.ScaleType.FIT_XY);
        trailingView.setVisibility(GONE);
        addView(trailingView, new FrameLayout.LayoutParams(1, 1));
    }

    void show(String name, boolean live) {
        show(name, live, null);
    }

    void show(String name, boolean live, Bitmap initialCover) {
        if (pendingPresentation && title.equals(name)) {
            pendingPresentation = false;
            pendingLogo = "";
            kind = live ? "音乐电台 · 直播" : "音乐";
            if (initialCover != null) setCover(initialCover);
            invalidate();
            return; // Decoder readiness updates metadata, not the already-running slide.
        }
        if (vinyl == null) vinyl = BitmapFactory.decodeResource(getResources(), R.drawable.vinyl_record);
        dragging = false;
        title = name == null ? "" : name;
        kind = live ? "音乐电台 · 直播" : "音乐";
        recordView.setDiscRotation(0f);
        setCover(initialCover);
        setVisibility(VISIBLE);
        cancelSlides();
        removeCallbacks(exitTimeout);
        waitingForIncoming = false;
        recordView.setTranslationY(0f);
        if (incomingDirection != 0 && active && getHeight() > 0) {
            animateHandoff(true);
        } else finishOutgoing();
        incomingDirection = 0;
        invalidate();
        updateAnimation();
    }

    void showPending(String name, String logo, Bitmap initialCover) {
        hideNeighbor();
        pendingPresentation = false;
        show(name, true, initialCover);
        pendingPresentation = true;
        pendingLogo = logo == null ? "" : logo;
        kind = "正在连接";
        invalidate();
    }

    boolean hasPendingPresentation() { return pendingPresentation; }

    void trimMemory() {
        if (getVisibility() == VISIBLE || isTransitionRunning() || pendingPresentation) return;
        // This is an idle screen only; do not release visible or animating textures.
        vinyl = null;
        background.setShader(null);
        backgroundHeight = 0;
    }

    void clear() {
        clear(false);
    }

    void clear(boolean keepPending) {
        playing = false;
        updateAnimation();
        if (keepPending && pendingPresentation) return;
        dragging = false;
        pendingPresentation = false;
        pendingLogo = "";
        hideNeighbor();
        recordView.animate().cancel();
        if (!waitingForIncoming && !(keepPending && handoffAnimating && slideIn == null)) {
            cancelSlides(); finishOutgoing();
        }
        recordView.setTranslationY(0f);
        setVisibility(outgoingView.getVisibility() == VISIBLE ? VISIBLE : GONE);
        cover = null;
        coverRevision++;
        // Keep the immutable base texture across channel changes.
        record = null;
        recordView.setImageDrawable(null);
        invalidate();
        notifyTransitionChanged();
    }

    void setTransitionListener(Runnable listener) { transitionListener = listener; }

    boolean isTransitionRunning() { return dragging || waitingForIncoming || handoffAnimating; }

    private void notifyTransitionChanged() {
        if (transitionListener != null) transitionListener.run();
    }

    /** Positive direction advances: old record slides up, new record enters below. */
    void beginChannelSwitch(int direction) {
        dragging = false;
        pendingPresentation = false;
        hideNeighbor();
        incomingDirection = direction < 0 ? -1 : 1;
        removeCallbacks(exitTimeout);
        cancelSlides();
        waitingForIncoming = false;
        if (!isShown() || !active) { finishOutgoing(); return; }
        recordView.animate().cancel();
        // Continue every visible record from its actual position. If repeated
        // flicks outrun rendering, skip only discs that have never entered the
        // viewport, instead of building an ever-longer offscreen animation queue.
        if (record != null && (isDiscVisible(recordView)
                || !isDiscVisible(outgoingView) && !isDiscVisible(trailingView))) {
            if (isDiscVisible(outgoingView)) {
                trailingView.setImageDrawable(outgoingView.getDrawable());
                trailingView.setRotation(outgoingView.getRotation());
                trailingView.setTranslationY(outgoingView.getTranslationY());
                trailingView.setVisibility(VISIBLE);
            }
            outgoingView.setImageBitmap(record);
            outgoingView.setRotation(recordView.getDiscRotation());
            outgoingView.setTranslationY(recordView.getTranslationY());
            outgoingView.setVisibility(VISIBLE);
        } else if (!isDiscVisible(outgoingView) && isDiscVisible(trailingView)) {
            outgoingView.setImageDrawable(trailingView.getDrawable());
            outgoingView.setRotation(trailingView.getRotation());
            outgoingView.setTranslationY(trailingView.getTranslationY());
            outgoingView.setVisibility(VISIBLE);
            trailingView.setImageDrawable(null);
            trailingView.setVisibility(GONE);
        }
        if (outgoingView.getVisibility() != VISIBLE) return;
        waitingForIncoming = true;
        float from = outgoingView.getTranslationY();
        float hold = incomingDirection > 0 ? Math.min(from, -getHeight() * .10f)
                : Math.max(from, getHeight() * .10f);
        final ValueAnimator animation = ValueAnimator.ofFloat(from, hold);
        slideOut = animation;
        animation.addUpdateListener(value -> outgoingView.setTranslationY((Float) value.getAnimatedValue()));
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.setDuration(140L);
        animation.start();
        // Bound retention when resolution fails without a terminal callback.
        postDelayed(exitTimeout, 5000L);
        notifyTransitionChanged();
    }

    /** Called when a non-music target is ready, or loading ends with an error. */
    void finishChannelSwitch() {
        if (!waitingForIncoming) return;
        waitingForIncoming = false;
        removeCallbacks(exitTimeout);
        cancelSlides();
        if (outgoingView.getVisibility() == VISIBLE && active) animateHandoff(false);
        else { finishOutgoing(); notifyTransitionChanged(); }
        incomingDirection = 0;
    }

    private void animateHandoff(boolean entering) {
        final boolean hasOutgoing = outgoingView.getVisibility() == VISIBLE;
        // Stack adjacent discs with a small gap inside the record area, rather
        // than separating them by a whole screen and exposing an empty center.
        final float distance = slideDistance(incomingDirection);
        final float start = hasOutgoing ? outgoingView.getTranslationY() : 0f;
        final float tailStart = trailingView.getTranslationY();
        final float target = -incomingDirection * distance;
        final float incomingStart = start - target;
        if (entering) recordView.setTranslationY(incomingStart);
        final ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
        handoffAnimating = true;
        slideOut = hasOutgoing ? animation : null;
        slideIn = entering ? animation : null;
        // Both discs share one clock and one curve. Their separation stays fixed,
        // including when playback becomes ready halfway through the initial nudge.
        animation.addUpdateListener(value -> {
            float progress = (Float) value.getAnimatedValue();
            if (hasOutgoing) outgoingView.setTranslationY(start + (target - start) * progress);
            if (trailingView.getVisibility() == VISIBLE)
                trailingView.setTranslationY(tailStart + (target - start) * progress);
            if (entering) recordView.setTranslationY(incomingStart * (1f - progress));
        });
        animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animation.setDuration(360L);
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animator) {
                if (slideOut != animation && slideIn != animation) return;
                slideOut = null;
                slideIn = null;
                handoffAnimating = false;
                if (entering) recordView.setTranslationY(0f);
                finishOutgoing();
                notifyTransitionChanged();
            }
        });
        animation.start();
        notifyTransitionChanged();
    }

    private void cancelSlides() {
        handoffAnimating = false;
        ValueAnimator out = slideOut, in = slideIn;
        slideOut = null;
        slideIn = null;
        if (out != null) out.cancel();
        if (in != null && in != out) in.cancel();
    }

    private void finishOutgoing() {
        outgoingView.setImageDrawable(null);
        outgoingView.setVisibility(GONE);
        trailingView.setImageDrawable(null);
        trailingView.setVisibility(GONE);
        if (record == null) setVisibility(GONE);
    }

    void previewSlide(float distance) {
        previewSlide(distance, false, "", null);
    }

    void previewSlide(float distance, boolean nextIsAudio, String logo, Bitmap art) {
        if (!dragging) {
            cancelSlides();
            recordView.animate().cancel();
            removeCallbacks(exitTimeout);
            waitingForIncoming = false;
            dragStart = recordView.getTranslationY();
            dragOutgoingStart = outgoingView.getTranslationY();
            dragTrailingStart = trailingView.getTranslationY();
            dragging = true;
            notifyTransitionChanged();
        }
        recordView.setTranslationY(dragStart + distance);
        outgoingView.setTranslationY(dragOutgoingStart + distance);
        trailingView.setTranslationY(dragTrailingStart + distance);
        if (!nextIsAudio || distance == 0f) { hideNeighbor(); return; }
        if (neighborView.getVisibility() != VISIBLE || !logo.equals(neighborLogo) || art != neighborCover) {
            neighborLogo = logo;
            neighborCover = art;
            neighborView.setImageBitmap(composeRecordBitmap(art));
        }
        neighborView.animate().cancel();
        neighborView.setVisibility(VISIBLE);
        int direction = distance < 0f ? 1 : -1;
        neighborView.setTranslationY(dragStart + distance + direction * slideDistance(direction));
    }

    private boolean isDiscVisible(View view) {
        float y = view.getTranslationY();
        return view.getVisibility() == VISIBLE && disc.bottom + y > 0f
                && disc.top + y < disc.bottom;
    }

    private float slideDistance(int direction) {
        float spacing = Math.max(1, disc.height()) + Math.max(2f, getHeight() * .025f);
        return direction > 0 ? Math.max(spacing, disc.bottom + 1f) : spacing;
    }

    private void hideNeighbor() {
        neighborView.animate().cancel();
        neighborView.setVisibility(GONE);
        neighborView.setImageDrawable(null);
        neighborLogo = null;
        neighborCover = null;
    }

    void updateNeighborCover(String logo, Bitmap art) {
        if (neighborView.getVisibility() == VISIBLE && logo.equals(neighborLogo) && art != neighborCover) {
            neighborCover = art;
            neighborView.setImageBitmap(composeRecordBitmap(art));
        }
        if (pendingPresentation && logo.equals(pendingLogo)) setCover(art);
    }

    void restoreSlide() {
        restoreSlide(180L, new android.view.animation.DecelerateInterpolator());
    }

    void restoreSlide(long duration, android.animation.TimeInterpolator interpolator) {
        // ACTION_UP cleanup must not start a second animator over a committed slide.
        if (!dragging && isTransitionRunning()) return;
        dragging = false;
        cancelSlides();
        recordView.animate().cancel();
        final float start = recordView.getTranslationY();
        final float outStart = outgoingView.getTranslationY();
        final float tailStart = trailingView.getTranslationY();
        final float neighborStart = neighborView.getTranslationY();
        final ValueAnimator animation = ValueAnimator.ofFloat(start, 0f);
        slideIn = animation;
        handoffAnimating = true;
        animation.addUpdateListener(value -> {
            float y = (Float) value.getAnimatedValue();
            recordView.setTranslationY(y);
            outgoingView.setTranslationY(outStart + y - start);
            trailingView.setTranslationY(tailStart + y - start);
            neighborView.setTranslationY(neighborStart + y - start);
        });
        animation.setDuration(duration);
        animation.setInterpolator(interpolator);
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animator) {
                if (slideIn != animation) return;
                slideIn = null;
                handoffAnimating = false;
                finishOutgoing();
                hideNeighbor();
                notifyTransitionChanged();
            }
        });
        animation.start();
        notifyTransitionChanged();
    }

    void resetSlide() {
        dragging = false;
        cancelSlides();
        finishOutgoing();
        recordView.animate().cancel();
        recordView.setTranslationY(0f);
        hideNeighbor();
        notifyTransitionChanged();
    }

    void setCover(Bitmap value) {
        if (cover == value && record != null) return;
        cover = value;
        coverRevision++;
        composeRecord();
        invalidate();
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        updateAnimation();
    }

    void setActive(boolean value) { active = value; updateAnimation(); }

    Bitmap cover() { return cover; }
    int coverRevision() { return coverRevision; }

    private boolean shouldRotate() {
        return playing && active && attached && isShown()
                && getWindowVisibility() == VISIBLE;
    }
    private void updateAnimation() {
        if (android.os.Build.VERSION.SDK_INT <= 15) {
            if (shouldRotate()) {
                if (legacyRotationRunning) return;
                legacyRotationRunning = true;
                legacyRotationStartDegrees = recordView.getDiscRotation() % 360f;
                legacyRotationStartedAt = SystemClock.uptimeMillis();
                recordView.setLegacyAnimating(true);
                post(legacyRotationTick);
            } else if (legacyRotationRunning) {
                legacyRotationRunning = false;
                removeCallbacks(legacyRotationTick);
                recordView.setLegacyAnimating(false);
            }
            return;
        }
        if (shouldRotate()) {
            if (rotation != null) return;
            float start = recordView.getRotation() % 360f;
            recordView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            rotation = ObjectAnimator.ofFloat(recordView, "rotation", start, start + 360f);
            rotation.setDuration(30000L);
            rotation.setRepeatCount(ValueAnimator.INFINITE);
            rotation.setInterpolator(new LinearInterpolator());
            rotation.start();
        } else if (rotation != null) {
            rotation.cancel();
            rotation = null;
            recordView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; updateAnimation(); }
    @Override protected void onDetachedFromWindow() {
        attached = false; updateAnimation();
        dragging = false;
        removeCallbacks(exitTimeout);
        waitingForIncoming = false;
        cancelSlides(); finishOutgoing();
        hideNeighbor();
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateAnimation(); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float size = Math.min(w * 0.68f, h * 0.68f);
        float top = (h - size) * 0.31f;
        disc.set((w - size) / 2, top, (w + size) / 2, top + size);
        textSize = Math.min(w * 0.045f, h * 0.04f);
        updateBackground(h);
    }

    private void updateBackground(int height) {
        if (height <= 0 || height == backgroundHeight) return;
        // A one-pixel-wide gradient repeats each quantized color across the whole screen.
        // Stochastic rounding distributes adjacent 8-bit colors spatially instead. Generate
        // this small, full-height strip only on resize; repeat horizontally at 1:1 pixels.
        // Explicit dithering also works where old hardware ignores Paint.setDither().
        final int width = 128;
        int[] pixels = new int[width * height];
        int random = 0x6d2b79f5;
        for (int y = 0; y < height; y++) {
            float fraction = y / (float) Math.max(1, height - 1);
            float red = 55 - 25 * fraction;
            float green = 70 - 32 * fraction;
            float blue = 94 - 43 * fraction;
            for (int x = 0; x < width; x++) {
                random ^= random << 13;
                random ^= random >>> 17;
                random ^= random << 5;
                float noise = (random & 0xffff) / 65536f;
                pixels[y * width + x] = 0xff000000 | ((int) (red + noise) << 16)
                        | ((int) (green + noise) << 8) | (int) (blue + noise);
            }
        }
        Bitmap strip = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        background.setShader(new BitmapShader(strip, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP));
        backgroundHeight = height;
        // The shader owns the bitmap. Do not recycle the previous display-list texture.
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int size = Math.round(Math.min(getMeasuredWidth(), getMeasuredHeight()) * 0.68f);
        int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        recordView.measure(exact, exact);
        outgoingView.measure(exact, exact);
        neighborView.measure(exact, exact);
        trailingView.measure(exact, exact);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = Math.round(disc.left), y = Math.round(disc.top);
        recordView.layout(x, y, x + recordView.getMeasuredWidth(), y + recordView.getMeasuredHeight());
        outgoingView.layout(x, y, x + outgoingView.getMeasuredWidth(), y + outgoingView.getMeasuredHeight());
        neighborView.layout(x, y, x + neighborView.getMeasuredWidth(), y + neighborView.getMeasuredHeight());
        trailingView.layout(x, y, x + trailingView.getMeasuredWidth(), y + trailingView.getMeasuredHeight());
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        // Let the disc move naturally up to the screen edge; only protect the
        // title/controls below, rather than chopping its top at the resting slot.
        canvas.clipRect(0f, 0f, getWidth(), disc.bottom);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    private void composeRecord() {
        record = composeRecordBitmap(cover);
        recordView.setImageBitmap(record);
    }

    private Bitmap composeRecordBitmap(Bitmap art) {
        if (vinyl == null) vinyl = BitmapFactory.decodeResource(getResources(), R.drawable.vinyl_record);
        if (art == null) {
            // The asset already contains the center label and spindle hole.
            return vinyl;
        }
        // Android 4.4's hardware Canvas can leave polygonal/ragged edges on a rotating
        // bitmap-shader circle. Rasterize its antialiased edge with a software Canvas
        // only when the cover changes; keep the window and animation hardware accelerated.
        Bitmap composed = Bitmap.createBitmap(vinyl.getWidth(), vinyl.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composed);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float cx = composed.getWidth() / 2f, cy = composed.getHeight() / 2f;
        float radius = composed.getWidth() * 0.245f;
        canvas.drawBitmap(vinyl, 0, 0, label);
        float scale = radius * 2 / Math.min(art.getWidth(), art.getHeight());
        BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(cx - art.getWidth() * scale / 2, cy - art.getHeight() * scale / 2);
        shader.setLocalMatrix(matrix);
        label.setShader(shader);
        canvas.drawCircle(cx, cy, radius, label);
        label.setShader(null);
        label.setColor(0x55999999);
        label.setStyle(Paint.Style.STROKE);
        label.setStrokeWidth(1);
        canvas.drawCircle(cx, cy, radius, label);
        label.setStyle(Paint.Style.FILL);
        label.setColor(0xff0a1519);
        canvas.drawCircle(cx, cy, composed.getWidth() * 0.011f, label);
        // Never recycle a bitmap still referenced by the render thread's display list.
        return composed;
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        if (record == null) return;
        float cx = disc.centerX();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSize);
        paint.setColor(0xfff2f5f4);
        float width = paint.measureText(title);
        if (width > getWidth() * 0.84f) paint.setTextSize(textSize * getWidth() * 0.84f / width);
        canvas.drawText(title, cx, disc.bottom + textSize * 1.35f, paint);
        paint.setTextSize(textSize * 0.53f);
        paint.setColor(0xff9cafb0);
        canvas.drawText(kind, cx, disc.bottom + textSize * 2.4f, paint);
    }
}
