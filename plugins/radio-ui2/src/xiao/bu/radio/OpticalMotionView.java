package xiao.bu.radio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

/** Animated optical material: circular grooves, moving reflection and outward wave packets. */
final class OpticalMotionView extends View {
    private static final float C = 500f;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint atmosphere = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrum = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix primaryMatrix = new Matrix();
    private final Matrix oppositeMatrix = new Matrix();
    private final Matrix spectrumMatrix = new Matrix();
    private final Path discMask = new Path();
    private SweepGradient primary, opposite, spectral;
    private int tone = Integer.MIN_VALUE;
    private boolean running;
    private long elapsedMs, lastTick;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            elapsedMs += now - lastTick;
            lastTick = now;
            invalidate();
            schedule();
        }
    };

    OpticalMotionView(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        shine.setStyle(Paint.Style.STROKE);
        spectrum.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f);
        discMask.addCircle(C,C,456f,Path.Direction.CW);
        glow.setShader(new RadialGradient(C, C, 190f,
                new int[]{0xfffefbff, 0xf5fff7fc, 0x90ffe9f4, 0x00dfc5d6},
                new float[]{0f, .27f, .62f, 1f}, Shader.TileMode.CLAMP));
        setTone(0xff79616c);
    }

    void setTone(int value) {
        if (tone == value) return;
        tone = value;
        int blush = Color.rgb((Color.red(value)+255*3)/4,
                (Color.green(value)+246*3)/4, (Color.blue(value)+253*3)/4);
        primary = new SweepGradient(C, C,
                new int[]{0xffffffff, 0xc8fff7fb, 0x48fff1f8, 0x00ffffff,
                        0x00ffffff, 0x40fff0f9, 0xc8fff7fb, 0xffffffff},
                new float[]{0f,.035f,.085f,.16f,.80f,.90f,.96f,1f});
        opposite = new SweepGradient(C, C,
                new int[]{0xfffaf4fa,0x80ffe8f4,0x00ffffff,0x00ffffff,0x70fff4fa,0xfffaf4fa},
                new float[]{0f,.09f,.21f,.79f,.94f,1f});
        spectral = new SweepGradient(C, C,
                new int[]{0x00ffffff,0x00ffffff,0x008ebeff,0x75a8cfff,0x68c8a8ff,
                        0x75ffe3ab,0x00ffe3ab,0x00ffffff},
                new float[]{0f,.66f,.76f,.82f,.87f,.92f,.98f,1f});
        atmosphere.setShader(new RadialGradient(C, C, 720f,
                new int[]{0x00ffffff,0x00ffffff,(blush&0xffffff)|0x48000000,0x00ffffff},
                new float[]{0f,.36f,.60f,1f},Shader.TileMode.CLAMP));
        invalidate();
    }

    void setRunning(boolean value) {
        if (value == running) return;
        if (running) elapsedMs += SystemClock.uptimeMillis() - lastTick;
        running = value;
        removeCallbacks(tick);
        if (running) {lastTick=SystemClock.uptimeMillis();schedule();}
        invalidate();
    }
    private void schedule() {
        if (Build.VERSION.SDK_INT >= 16) postOnAnimation(tick);
        else postDelayed(tick,16L);
    }
    private float seconds() {
        return (elapsedMs + (running ? SystemClock.uptimeMillis()-lastTick : 0)) * .001f;
    }
    @Override protected void onDetachedFromWindow() {setRunning(false);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas) {drawScene(canvas,getWidth(),getHeight(),seconds());}

    void drawSnapshot(Canvas canvas, float size) {drawScene(canvas,size,size,seconds());}

    private void drawScene(Canvas canvas,float width,float height,float t) {
        if (width<=0 || height<=0) return;
        int save=canvas.save();canvas.scale(width/1000f,height/1000f);
        float breathe=.5f+.5f*(float)Math.sin(t*1.22f);
        float flash=(float)Math.pow(Math.max(0f,Math.cos(t*1.96f)),10);
        float beam=-39f+15f*(float)Math.sin(t*.93f)+5f*(float)Math.sin(t*2.17f);
        primaryMatrix.setRotate(beam,C,C);primary.setLocalMatrix(primaryMatrix);
        oppositeMatrix.setRotate(142f+12f*(float)Math.sin(t*.61f+1.4f),C,C);
        opposite.setLocalMatrix(oppositeMatrix);
        spectrumMatrix.setRotate(19f*(float)Math.sin(t*.72f)+7f*(float)Math.sin(t*1.51f),C,C);
        spectral.setLocalMatrix(spectrumMatrix);
        atmosphere.setAlpha(Math.round(55f+85f*breathe+100f*flash));
        canvas.drawCircle(C,C,720f,atmosphere);

        // A soft, anisotropic glare travels across the groove band, rather than rotating the record texture.
        drawGlare(canvas,beam,375f+28f*(float)Math.sin(t*2.65f),
                180f+55f*breathe+20f*flash,.95f,.58f);
        drawGlare(canvas,145f+8f*(float)Math.sin(t*.83f),
                375f-24f*(float)Math.sin(t*2.65f+.7f),100f+65f*(1f-breathe),.82f,.50f);

        for(int i=0;i<12;i++) {
            float r=310f+i*13.1f;
            float travel=.5f+.5f*(float)Math.sin(t*3.1f-i*.48f);
            float oppositeTravel=.5f+.5f*(float)Math.sin(t*2.4f+i*.37f+1.1f);
            line.setShader(null);line.setColor(0xffeee7ee);
            line.setAlpha(Math.round(65f+45f*(1f-i/14f)+16f*travel));
            line.setStrokeWidth(i==11?2.7f:2.2f);canvas.drawCircle(C,C,r,line);

            // Several low-opacity strokes form the bloom without unsupported hardware blur filters.
            shine.setShader(primary);shine.setStrokeWidth(17f);
            shine.setAlpha(Math.round(25f+29f*travel+17f*flash));canvas.drawCircle(C,C,r,shine);
            shine.setStrokeWidth(8f);shine.setAlpha(Math.round(35f+63f*travel));canvas.drawCircle(C,C,r,shine);
            shine.setStrokeWidth(3f);shine.setAlpha(Math.round(145f+105f*travel));canvas.drawCircle(C,C,r,shine);
            shine.setShader(opposite);shine.setStrokeWidth(7f);
            shine.setAlpha(Math.round(12f+17f*oppositeTravel));canvas.drawCircle(C,C,r,shine);
            shine.setStrokeWidth(2.65f);shine.setAlpha(Math.round(115f+135f*oppositeTravel));canvas.drawCircle(C,C,r,shine);
            spectrum.setShader(spectral);spectrum.setStrokeWidth(2.2f);
            spectrum.setAlpha(Math.round(25f+70f*travel*(1f-breathe*.65f)));canvas.drawCircle(C,C,r,spectrum);
        }

        // Reference-style bursts: three blurred rings expand beyond the disc, then disappear.
        float cycle=t%4.8f;
        for(int i=0;i<3;i++) {
            float age=cycle-1.10f-i*.14f;
            if(age<0f || age>1.7f)continue;
            float progress=age/1.7f;
            float envelope=(float)Math.sin(Math.PI*progress)*(1f-progress);
            float r=454f+175f*progress+i*4f;
            shine.setShader(primary);shine.setStrokeWidth(18f);
            shine.setAlpha(Math.round(64f*envelope));canvas.drawCircle(C,C,r,shine);
            shine.setStrokeWidth(7f);shine.setAlpha(Math.round(110f*envelope));canvas.drawCircle(C,C,r,shine);
            shine.setStrokeWidth(1.8f);shine.setAlpha(Math.round(185f*envelope));canvas.drawCircle(C,C,r,shine);
            shine.setShader(opposite);shine.setStrokeWidth(3f);
            shine.setAlpha(Math.round(85f*envelope));canvas.drawCircle(C,C,r,shine);
        }
        // Clean inner rim and a fine outer contour, matching the clear glass edge in the reference.
        line.setShader(primary);line.setAlpha(220);line.setStrokeWidth(1.7f);canvas.drawCircle(C,C,300f,line);
        canvas.restoreToCount(save);
    }

    private void drawGlare(Canvas canvas,float angle,float radius,float alpha,float sx,float sy) {
        int save=canvas.save();
        if (!canvas.isHardwareAccelerated() || Build.VERSION.SDK_INT>=18) canvas.clipPath(discMask);
        canvas.rotate(angle,C,C);canvas.translate(radius,0f);canvas.scale(sx,sy,C,C);
        glow.setAlpha(Math.min(255,Math.round(alpha)));canvas.drawCircle(C,C,190f,glow);
        canvas.restoreToCount(save);
    }
}
