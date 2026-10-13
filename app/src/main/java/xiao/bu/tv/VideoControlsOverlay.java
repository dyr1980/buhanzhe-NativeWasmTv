package xiao.bu.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ClipDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Owns the whole touch sequence while visible; no gesture reaches the player. */
final class VideoControlsOverlay extends FrameLayout {
    interface Playback {
        boolean playing();
        long duration();
        long position();
        void toggle();
        void seek(long position);
        String title();
        boolean speedSupported();
        float speed();
        void speed(float value);
    }
    private final Activity activity;
    private final Playback playback;
    private final boolean touchAdjustments;
    private final Icon toggle;
    private final TextView time, feedback, speed, title;
    private final LinearLayout speedOptions;
    private final SeekBar seek;
    private float downX, downY, startBrightness;
    private int startVolume;
    private long startPosition, gestureDuration, seekPosition;
    private int gestureAxis;
    private boolean moved, dragging;
    private final Runnable dismiss = new Runnable() { public void run() { hide(); } };
    private final Runnable tick = new Runnable() {
        public void run() {
            if (getVisibility() != VISIBLE) return;
            refresh();
            postDelayed(this, 500L);
        }
    };

    VideoControlsOverlay(Activity owner, Playback source, boolean television) {
        super(owner);
        activity = owner;
        playback = source;
        touchAdjustments = !television;
        setWillNotDraw(false);
        setClickable(true);
        setVisibility(GONE);
        toggle = new Icon(owner, "pause");
        toggle.setContentDescription("暂停");
        addView(toggle, new LayoutParams(dp(88), dp(88), Gravity.CENTER));
        toggle.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { playback.toggle(); refresh(); arm(); }
        });
        Icon close = new Icon(owner, "back");
        close.setContentDescription("隐藏控制栏");
        LayoutParams corner = new LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.LEFT);
        corner.topMargin = dp(20); corner.leftMargin = dp(24);
        addView(close, corner);
        close.setOnClickListener(new OnClickListener() { public void onClick(View v) { hide(); } });
        title = label(playback.title(), 18);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams heading = new LayoutParams(-1, dp(48), Gravity.TOP);
        heading.topMargin = dp(20); heading.leftMargin = dp(84); heading.rightMargin = dp(32);
        addView(title, heading);
        if (touchAdjustments) {
            Icon volume = new Icon(owner, "volume"), brightness = new Icon(owner, "sun");
            LayoutParams left = new LayoutParams(dp(48), dp(48), Gravity.LEFT | Gravity.CENTER_VERTICAL);
            left.leftMargin = dp(32); addView(brightness, left);
            LayoutParams right = new LayoutParams(dp(48), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            right.rightMargin = dp(32); addView(volume, right);
        }
        feedback = label("", 15);
        LayoutParams hint = new LayoutParams(dp(180), dp(40), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        hint.topMargin = dp(74); addView(feedback, hint);
        LinearLayout bottom = new LinearLayout(owner);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(32), 0, dp(32), dp(16));
        time = label("", 15);
        time.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        bottom.addView(time, new LinearLayout.LayoutParams(-1, dp(28)));
        seek = new SeekBar(owner);
        seek.setMax(1000);
        GradientDrawable track = new GradientDrawable(); track.setColor(0x66ffffff); track.setCornerRadius(dp(2));
        GradientDrawable fill = new GradientDrawable(); fill.setColor(0xffffffff); fill.setCornerRadius(dp(2));
        LayerDrawable progress = new LayerDrawable(new android.graphics.drawable.Drawable[] {
                track, new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL) });
        progress.setId(0, android.R.id.background); progress.setId(1, android.R.id.progress);
        seek.setProgressDrawable(progress);
        GradientDrawable thumb = new GradientDrawable(); thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xffffffff); thumb.setSize(dp(10), dp(10)); seek.setThumb(thumb);
        seek.setThumbOffset(dp(5)); seek.setPadding(dp(5), dp(19), dp(5), dp(19));
        if (android.os.Build.VERSION.SDK_INT >= 21) seek.setSplitTrack(false);
        bottom.addView(seek, new LinearLayout.LayoutParams(-1, dp(40)));
        speedOptions = new LinearLayout(owner);
        speedOptions.setGravity(Gravity.RIGHT); speedOptions.setVisibility(GONE);
        for (final float rate : new float[] {0.5f, 1f, 1.25f, 1.5f, 2f, 3f}) {
            TextView choice = label(rate + "×", 15);
            speedOptions.addView(choice, new LinearLayout.LayoutParams(dp(58), dp(44)));
            choice.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    playback.speed(rate); speedOptions.setVisibility(GONE); refresh(); arm();
                }
            });
        }
        bottom.addView(speedOptions);
        speed = label("倍速", 16); speed.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bottom.addView(speed, new LinearLayout.LayoutParams(-1, dp(44)));
        speed.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!playback.speedSupported()) return;
                speedOptions.setVisibility(speedOptions.getVisibility() == VISIBLE ? GONE : VISIBLE); arm();
            }
        });
        addView(bottom, new LayoutParams(-1, -2, Gravity.BOTTOM));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; removeCallbacks(dismiss); }
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) time.setText(stamp(playback.duration() * value / 1000L) + " / " + stamp(playback.duration()));
            }
            public void onStopTrackingTouch(SeekBar bar) {
                playback.seek(playback.duration() * bar.getProgress() / 1000L);
                dragging = false; refresh(); arm();
            }
        });
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextColor(0xffffffff); view.setTextSize(size);
        view.setGravity(Gravity.CENTER); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    void show() { title.setText(playback.title()); feedback.setText(""); speedOptions.setVisibility(GONE); setVisibility(VISIBLE); bringToFront(); refresh(); removeCallbacks(tick); post(tick); arm(); }
    void hide() { setVisibility(GONE); removeCallbacks(tick); removeCallbacks(dismiss); dragging = false; gestureAxis = 0; }
    void step(long delta) {
        long duration = playback.duration();
        if (duration <= 0L) return;
        playback.seek(Math.max(0L, Math.min(duration - 1L, playback.position() + delta)));
        refresh(); arm();
    }
    boolean visible() { return getVisibility() == VISIBLE; }
    private void arm() { removeCallbacks(dismiss); postDelayed(dismiss, 5000L); }
    private void refresh() {
        toggle.kind = playback.playing() ? "pause" : "play"; toggle.invalidate();
        toggle.setContentDescription(playback.playing() ? "暂停" : "播放");
        speed.setText(playback.speed() == 1f ? "倍速" : playback.speed() + "×");
        speed.setEnabled(playback.speedSupported());
        speed.setAlpha(playback.speedSupported() ? 1f : 0.4f);
        if (dragging) return;
        long duration = playback.duration(), position = playback.position();
        speed.setVisibility(duration > 0 ? VISIBLE : GONE);
        if (duration <= 0) speedOptions.setVisibility(GONE);
        seek.setVisibility(duration > 0 ? VISIBLE : GONE);
        seek.setEnabled(duration > 0);
        seek.setProgress(duration > 0 ? (int) (Math.min(position, duration) * 1000L / duration) : 0);
        time.setText(duration > 0 ? stamp(position) + " / " + stamp(duration) : "直播");
    }
    private static String stamp(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, 0, 0, getHeight(),
                new int[] {0x88000000, 0x08000000, 0x00000000, 0x99000000},
                new float[] {0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), shade);
    }
    /** Small native vector shapes keep icons sharp even on Android 4.x. */
    private static final class Icon extends View {
        String kind;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context context, String value) { super(context); kind = value; }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            float size = Math.min(getWidth(), getHeight()) * ("pause".equals(kind) || "play".equals(kind) ? 0.66f : 0.56f);
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(0xeeffffff); paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            if ("back".equals(kind)) {
                Path path = new Path(); path.moveTo(15, 4); path.lineTo(7, 12); path.lineTo(15, 20); canvas.drawPath(path, paint);
            } else if ("pause".equals(kind)) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(5, 3, 9, 21), 1.3f, 1.3f, paint);
                canvas.drawRoundRect(new RectF(15, 3, 19, 21), 1.3f, 1.3f, paint);
            } else if ("play".equals(kind)) {
                paint.setStyle(Paint.Style.FILL); Path path = new Path();
                path.moveTo(7, 3); path.lineTo(21, 12); path.lineTo(7, 21); path.close(); canvas.drawPath(path, paint);
            } else if ("sun".equals(kind)) {
                canvas.drawCircle(12, 12, 4, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    canvas.drawLine(12 + (float)Math.cos(angle)*8, 12 + (float)Math.sin(angle)*8,
                            12 + (float)Math.cos(angle)*10, 12 + (float)Math.sin(angle)*10, paint);
                }
            } else {
                Path path = new Path(); path.moveTo(3, 9); path.lineTo(7, 9); path.lineTo(12, 5);
                path.lineTo(12, 19); path.lineTo(7, 15); path.lineTo(3, 15); path.close();
                paint.setStyle(Paint.Style.FILL); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.STROKE);
                canvas.drawArc(new RectF(10, 7, 20, 17), -50, 100, false, paint);
                canvas.drawArc(new RectF(8, 3, 24, 21), -48, 96, false, paint);
            }
            canvas.restore();
        }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) removeCallbacks(dismiss);
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) arm();
        return handled;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        AudioManager audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); moved = false;
                gestureAxis = 0;
                gestureDuration = playback.duration();
                startPosition = playback.position();
                seekPosition = startPosition;
                if (!touchAdjustments) return true;
                startVolume = audio == null ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                startBrightness = activity.getWindow().getAttributes().screenBrightness;
                if (startBrightness < 0) {
                    startBrightness = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = downY - event.getY();
                if (gestureAxis == 0) {
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.max(Math.abs(dx), Math.abs(dy)) <= slop) return true;
                    moved = true;
                    gestureAxis = Math.abs(dx) > Math.abs(dy) ? 1 : 2;
                }
                if (gestureAxis == 1) {
                    if (gestureDuration <= 0L) return true;
                    dragging = true;
                    seekPosition = Math.max(0L, Math.min(gestureDuration - 1L,
                            startPosition + Math.round(dx / Math.max(1f, getWidth()) * gestureDuration)));
                    seek.setProgress((int) (seekPosition * 1000L / gestureDuration));
                    time.setText(stamp(seekPosition) + " / " + stamp(gestureDuration));
                    feedback.setText("进度 " + stamp(seekPosition));
                    return true;
                }
                if (!touchAdjustments) return true;
                float delta = dy / Math.max(1f, getHeight()) * 1.5f;
                if (downX >= getWidth() / 2f && audio != null) {
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int value = Math.max(0, Math.min(max, startVolume + Math.round(delta * max)));
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                    feedback.setText("音量 " + (max > 0 ? value * 100 / max : 0) + "%");
                } else if (downX < getWidth() / 2f) {
                    float value = Math.max(0.02f, Math.min(1f, startBrightness + delta));
                    WindowManager.LayoutParams params = activity.getWindow().getAttributes();
                    params.screenBrightness = value; activity.getWindow().setAttributes(params);
                    feedback.setText("亮度 " + Math.round(value * 100f) + "%");
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureAxis == 1 && dragging) playback.seek(seekPosition);
                dragging = false; gestureAxis = 0; refresh();
                if (!moved) hide();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; gestureAxis = 0; feedback.setText(""); refresh();
                return true;
            default: return true;
        }
    }
}
