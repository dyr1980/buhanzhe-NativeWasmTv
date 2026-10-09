package xiao.bu.tv;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.media.MediaCodec;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.Display;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** API 17+ GPU tone mapping/BT.709 bridge. Hardware decoding and SurfaceView remain. */
public final class NativeHdrOutput implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "nTvHdr";
    private static volatile Context context;
    private static volatile Display targetDisplay;
    private static volatile NativeHdrOutput active;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final HandlerThread thread = new HandlerThread("nTv HDR output");
    private Handler handler;
    private final Surface target;
    private final int width, height, transfer;
    private final boolean forceBt709;
    private final float peak;
    private volatile boolean closed;
    private volatile Throwable failure;
    private Surface input;
    private SurfaceTexture texture;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface window = EGL14.EGL_NO_SURFACE;
    private int program, textureId, position, texcoord, transform;
    private int renderWidth, renderHeight;
    private final float[] matrix = new float[16];
    private final int[] windowSize = new int[1];
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private long lastTextureTimestamp, rateStart, rateFrames;
    private volatile long renderedFrames, lastRenderedAt;
    private volatile float renderedRate;

    static void initialize(Context value, View output) {
        context = value.getApplicationContext();
        // Bind the display that owns this SurfaceView, including external displays.
        // Called on the UI thread; native configure can query the saved Display later.
        targetDisplay = output == null ? null : output.getDisplay();
    }

    private static int[] supportedHdrTypes() {
        if (Build.VERSION.SDK_INT < 24) return null;
        Display display = targetDisplay;
        if (display == null) return null;
        try {
            if (!display.isValid()) return null;
            return DisplayApi24.supportedHdrTypes(display);
        } catch (RuntimeException error) {
            Log.w(TAG, "HDR display capability query failed; use SDR mapping", error);
            return null;
        }
    }

    private static final class DisplayApi24 {
        static int[] supportedHdrTypes(Display display) {
            return display.getHdrCapabilities().getSupportedHdrTypes();
        }
    }

    /** Called by IJK before configure; null means native HDR/normal SDR output. */
    public static synchronized NativeHdrOutput create(MediaFormat format, Surface surface) {
        NativeHdrOutput previous = active;
        if (previous != null && previous.target == surface) {
            previous.release();
            try {
                if (!previous.stopped.await(1500,TimeUnit.MILLISECONDS))
                    throw new IllegalStateException("Previous HDR output has not released the Surface");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); throw new IllegalStateException(error);
            }
        }
        int transfer = integer(format, "color-transfer", 0);
        int mode = integer(format, "ntv-hdr-mode", HdrMode.nativeValue(HdrMode.DEFAULT));
        int sourceTransfer = integer(format,"ntv-source-transfer",transfer);
        int sourceStandard = integer(format,"ntv-source-standard",1);
        boolean forceBt709 = mode == 2 && (sourceTransfer == 6 || sourceTransfer == 7
                || sourceStandard != 0 && sourceStandard != 1);
        if ((!forceBt709 && transfer != 6 && transfer != 7) || surface == null) return null;
        int[] hdrTypes = supportedHdrTypes();
        if (HdrDisplayPolicy.passthrough(mode, transfer, hdrTypes)) {
            Log.i(TAG, "HDR hardware passthrough transfer=" + transfer);
            return null;
        }
        if (mode == 1) Log.i(TAG, "HDR display fallback: transfer=" + transfer
                + " hdrTypes=" + java.util.Arrays.toString(hdrTypes) + " -> SDR mapping");
        Context ctx = context;
        if (ctx == null) throw new IllegalStateException("HDR output context is unavailable");
        // Do not silently tag PQ/HLG as SDR. New codecs may perform real tone mapping.
        if (!forceBt709 && HdrDisplayPolicy.decoderToneMapping(mode, Build.VERSION.SDK_INT)) {
            format.setInteger("color-transfer-request", 3);
            Log.i(TAG, "Request decoder HDR -> SDR transfer=" + transfer);
            return null;
        }
        float peak = peak(format,1000f);
        NativeHdrOutput bridge = new NativeHdrOutput(surface, integer(format, "width", 0),
                integer(format, "height", 0), forceBt709 ? 0 : transfer, peak, forceBt709);
        Log.i(TAG, "GPU HDR output mode=" + mode + " transfer=" + transfer
                + " display=" + (targetDisplay == null ? "unknown" : targetDisplay.getDisplayId()));
        active = bridge;
        return bridge;
    }

    private static float peak(MediaFormat format, float fallback) {
        float peak = fallback;
        try {
            ByteBuffer info = format.getByteBuffer("hdr-static-info");
            if (info != null && info.remaining() >= 25) {
                ByteBuffer copy = info.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                int start = copy.position();
                int mastering = copy.getShort(start + 17) & 0xffff;
                int content = copy.getShort(start + 21) & 0xffff;
                if (content > 0) peak = content;
                else if (mastering > 0) peak = mastering;
            }
        } catch (RuntimeException ignored) { }
        return Math.max(100f, Math.min(10000f, peak));
    }

    private static int integer(MediaFormat format, String key, int fallback) {
        try { return format.containsKey(key) ? format.getInteger(key) : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private NativeHdrOutput(Surface surface, int w, int h, int trc, float max, boolean bt709) {
        target = surface; width = w; height = h; transfer = trc; peak = max;
        forceBt709 = bt709;
        vertices.put(new float[]{-1,-1, 0,0, 1,-1, 1,0, -1,1, 0,1, 1,1, 1,1}).position(0);
        thread.start(); handler = new Handler(thread.getLooper());
        CountDownLatch ready = new CountDownLatch(1);
        handler.post(() -> {
            try { if (!closed) initializeGl(); }
            catch (Throwable error) { failure = error; destroyGl(); }
            finally { ready.countDown(); if (closed) destroyGl(); }
        });
        boolean completed = false;
        try { completed = ready.await(2500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        if (!completed || failure != null || input == null) {
            release();
            throw new IllegalStateException("HDR GPU output initialization failed", failure);
        }
        Log.i(TAG, (forceBt709 ? "GPU BT.709 override " : "GPU HDR -> SDR ")
                + width + "x" + height + " transfer=" + transfer + " peak=" + peak);
    }

    public Surface getSurface() { return input; }

    public static void prepareSoftwareOutput() {
        NativeHdrOutput previous=active;
        if (previous == null) return;
        previous.release();
        try {
            if (!previous.stopped.await(1500,TimeUnit.MILLISECONDS))
                throw new IllegalStateException("HDR output is still connected");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(error);
        }
    }

    public static void verifyToneMapping(MediaCodec codec, MediaFormat format) {
        if (Build.VERSION.SDK_INT >= 31 && integer(format,"color-transfer-request",0) == 3) {
            int accepted = integer(codec.getInputFormat(),"color-transfer-request",0);
            if (accepted != 3) throw new IllegalStateException("Decoder does not support HDR -> SDR tone mapping");
        }
    }

    public void outputFormat(MediaFormat format) {
        Log.i(TAG,"HDR decoder output " + format);
        int outputTransfer = integer(format,"color-transfer",transfer);
        int outputStandard = integer(format,"color-standard",1);
        float outputPeak = peak(format,peak);
        if (!closed) handler.post(() -> {
            if (closed || program == 0) return;
            GLES20.glUseProgram(program);
            if (forceBt709) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvBt709Source"),outputStandard);
                return;
            }
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvPeak"),outputPeak);
            if (outputTransfer == 3) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvTransfer"),0f);
                Log.i(TAG,"Decoder already converted to SDR; bypass GPU tone mapping");
            }
        });
    }

    static float renderedFrameRate() {
        NativeHdrOutput output = active;
        if (output == null || output.closed) return Float.NaN;
        return SystemClock.elapsedRealtime()-output.lastRenderedAt > 1000 ? 0f : output.renderedRate;
    }

    public static long renderedFrameCount() {
        NativeHdrOutput output=active;return output==null ? 0 : output.renderedFrames;
    }

    private void initializeGl() throws Exception {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("eglInitialize");
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        int[] attrs = {EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE};
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0)
            throw new IllegalStateException("eglChooseConfig");
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE}, 0);
        window = EGL14.eglCreateWindowSurface(display, configs[0], target, new int[]{EGL14.EGL_NONE}, 0);
        if (window == EGL14.EGL_NO_SURFACE || eglContext == EGL14.EGL_NO_CONTEXT
                || !EGL14.eglMakeCurrent(display, window, window, eglContext))
            throw new IllegalStateException("HDR EGL window error " + EGL14.eglGetError());
        EGL14.eglSwapInterval(display, 1);
        int[] size = new int[1];
        EGL14.eglQuerySurface(display, window, EGL14.EGL_WIDTH, size, 0); renderWidth = size[0];
        EGL14.eglQuerySurface(display, window, EGL14.EGL_HEIGHT, size, 0); renderHeight = size[0];
        String vertex = "attribute vec2 position; attribute vec2 texcoord; uniform mat4 transform;"
                + "varying vec2 uv; void main(){ gl_Position=vec4(position,0.0,1.0);"
                + "uv=(transform*vec4(texcoord,0.0,1.0)).xy; }";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream source = context.getResources().openRawResource(R.raw.hdr_tonemap)) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = source.read(buffer)) > 0) bytes.write(buffer, 0, n);
        }
        String fragment = "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
                + "uniform samplerExternalOES video; varying vec2 uv;\n" + bytes.toString("UTF-8")
                + "\nvoid main(){gl_FragColor=vec4(ntvToSdr(texture2D(video,uv).rgb),1.0);}";
        int vs = shader(GLES20.GL_VERTEX_SHADER, vertex), fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment);
        program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program); GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
        int[] linked = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        position = GLES20.glGetAttribLocation(program,"position"); texcoord = GLES20.glGetAttribLocation(program,"texcoord");
        transform = GLES20.glGetUniformLocation(program,"transform"); GLES20.glUseProgram(program);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvTransfer"), transfer);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvBt709Source"),forceBt709 ? 1f : 0f);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"ntvPeak"), peak);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"video"), 0);
        int[] textures = new int[1]; GLES20.glGenTextures(1, textures, 0); textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        texture = new SurfaceTexture(textureId); texture.setDefaultBufferSize(width,height);
        texture.setOnFrameAvailableListener(this); input = new Surface(texture);
    }

    private static int shader(int kind, String text) {
        int id = GLES20.glCreateShader(kind); GLES20.glShaderSource(id,text); GLES20.glCompileShader(id);
        int[] valid = new int[1]; GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,valid,0);
        if (valid[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id);
            throw new IllegalStateException(error);
        }
        return id;
    }

    @Override public void onFrameAvailable(SurfaceTexture value) {
        if (closed) return;
        // Created on this HandlerThread; SurfaceTexture delivers callbacks on its Looper.
        try {
            texture.updateTexImage();
            long timestamp=texture.getTimestamp();
            if (timestamp == lastTextureTimestamp && renderedFrames > 0) return;
            lastTextureTimestamp=timestamp; texture.getTransformMatrix(matrix);
            EGL14.eglQuerySurface(display,window,EGL14.EGL_WIDTH,windowSize,0); renderWidth=windowSize[0];
            EGL14.eglQuerySurface(display,window,EGL14.EGL_HEIGHT,windowSize,0); renderHeight=windowSize[0];
            GLES20.glViewport(0,0,renderWidth,renderHeight); GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(transform,1,false,matrix,0);
            vertices.position(0); GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices);
            vertices.position(2); GLES20.glVertexAttribPointer(texcoord,2,GLES20.GL_FLOAT,false,16,vertices);
            GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(texcoord);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            if (!EGL14.eglSwapBuffers(display,window)) throw new IllegalStateException("HDR swap failed");
            long now=SystemClock.elapsedRealtime();
            renderedFrames++; lastRenderedAt=now;
            if (rateStart == 0) {rateStart=now;rateFrames=renderedFrames;}
            if (now-rateStart >= 500) {
                renderedRate=(renderedFrames-rateFrames)*1000f/(now-rateStart);
                rateStart=now;rateFrames=renderedFrames;
            }
        } catch (RuntimeException error) {
            failure = error; Log.e(TAG,"HDR output failed",error); release();
        }
    }

    public void release() {
        if (closed) return; closed = true;
        Runnable close = () -> {
            try { destroyGl(); }
            finally { stopped.countDown(); thread.quit(); if (active==this) active=null; }
        };
        if (Looper.myLooper()==thread.getLooper()) close.run();
        else handler.post(close);
    }

    private void destroyGl() {
        if (texture != null) texture.setOnFrameAvailableListener(null);
        if (input != null) { input.release(); input = null; }
        if (texture != null) { texture.release(); texture = null; }
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (program != 0) GLES20.glDeleteProgram(program);
            if (textureId != 0) GLES20.glDeleteTextures(1,new int[]{textureId},0);
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,window);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext);
            EGL14.eglTerminate(display); EGL14.eglReleaseThread();
        }
        display = EGL14.EGL_NO_DISPLAY; window = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT;
        program = textureId = 0;
    }

}
