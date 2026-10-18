package xiao.bu.radio;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

/** Glass / silver material measured in the reference's 1,000 px record square. */
final class OpticalMotionView extends View {
    private static final float C=500f;
    private final Paint glass=new Paint(3),metal=new Paint(3);
    private final Paint line=new Paint(3),body=new Paint(3),rim=new Paint(3);
    private final Matrix materialMatrix=new Matrix();
    private final SweepGradient edge,innerEdge,blackReflection;
    private final Bitmap innerGrooves,silver;
    private final Rect metalBounds=new Rect(0,0,1000,1000);
    private static Bitmap sharedSilver;
    private Runnable frameListener;
    private boolean running;
    private long elapsedMs,lastTick;
    private final Runnable tick=new Runnable(){public void run(){
        if(!running)return;
        long now=SystemClock.uptimeMillis();elapsedMs+=now-lastTick;lastTick=now;
        if(frameListener!=null)frameListener.run();
        invalidate();
        // Both full-screen particle passes share this one material clock.
        if(getParent() instanceof View&&((View)getParent()).getParent() instanceof View)
            ((View)((View)getParent()).getParent()).invalidate();
        schedule();
    }};
    OpticalMotionView(Context context,ClassLoader assets){
        super(context);
        silver=loadSilver(assets);
        edge=new SweepGradient(C,C,new int[]{0xffeffbff,0xd09ab9d8,0x30415d86,
                0x904c6a95,0xffd2e0eb,0x905d78a0,0x29435f8e,0xffeffbff},
                new float[]{0f,.12f,.28f,.44f,.53f,.70f,.86f,1f});
        innerEdge=new SweepGradient(C,C,new int[]{0xffe7edf2,0x8093abc3,0x20365374,
                0x687a96b1,0xffd0dce7,0x50536f93,0x203a5374,0xffe7edf2},
                new float[]{0f,.12f,.28f,.44f,.53f,.70f,.86f,1f});
        Matrix fixed=new Matrix();fixed.setRotate(31.1f,C,C);edge.setLocalMatrix(fixed);
        fixed=new Matrix();fixed.setRotate(56.9f,C,C);innerEdge.setLocalMatrix(fixed);
        blackReflection=new SweepGradient(C,C,new int[]{0x426889a5,0x001c3658,
                0x00000000,0x3a6b8fae,0x001b3455,0x00000000,0x426889a5},
                new float[]{0f,.08f,.40f,.5f,.59f,.91f,1f});
        glass.setShader(new RadialGradient(C,C,454f,
                new int[]{0x001d4181,0x001d4181,0x0a356bb0,0x12578ac0,0x1b86abd0},
                new float[]{0f,.73f,.86f,.985f,1f},Shader.TileMode.CLAMP));
        body.setShader(new RadialGradient(C,C,205f,
                new int[]{0xff061629,0xff041522,0xff061c30,0xff031220},
                new float[]{0f,.54f,.90f,1f},Shader.TileMode.CLAMP));
        line.setStyle(Paint.Style.STROKE);rim.setStyle(Paint.Style.STROKE);
        innerGrooves=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888);
        Canvas cached=new Canvas(innerGrooves);Paint p=new Paint(3);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.65f);
        for(int i=0;i<42;i++){p.setColor((i&1)==0?0x0b9ebbd2:0x08000714);cached.drawCircle(210,210,113+i*1.75f,p);}
    }
    private static synchronized Bitmap loadSilver(ClassLoader assets){
        if(sharedSilver!=null&&!sharedSilver.isRecycled())return sharedSilver;
        try(java.io.InputStream input=assets.getResourceAsStream("radio/optical-silver.png")){
            if(input==null)throw new IllegalStateException("Missing optical silver texture");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap bitmap=BitmapFactory.decodeStream(input,null,options);
            if(bitmap==null)throw new IllegalStateException("Invalid optical silver texture");
            bitmap.setDensity(Bitmap.DENSITY_NONE);sharedSilver=bitmap;return bitmap;
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    void setTone(int ignored){/* The reference keeps its midnight-blue environment. */}
    void setFrameListener(Runnable listener){frameListener=listener;}
    void setRunning(boolean value){
        if(value==running)return;
        if(running)elapsedMs+=SystemClock.uptimeMillis()-lastTick;
        running=value;removeCallbacks(tick);
        if(running){lastTick=SystemClock.uptimeMillis();schedule();}
        invalidate();
    }
    private void schedule(){postDelayed(tick,Build.VERSION.SDK_INT<=19?40L:33L);}
    float seconds(){return (elapsedMs+(running?SystemClock.uptimeMillis()-lastTick:0L))*.001f;}
    @Override protected void onDetachedFromWindow(){setRunning(false);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){drawScene(c,getWidth(),getHeight(),seconds());}
    void drawSnapshot(Canvas c,float size){drawScene(c,size,size,seconds());}
    private void drawScene(Canvas c,float width,float height,float time){
        if(width<=0||height<=0)return;
        int save=c.save();c.scale(width/1000f,height/1000f);
        materialMatrix.setRotate(-32f+18f*time,C,C);
        blackReflection.setLocalMatrix(materialMatrix);
        c.drawCircle(C,C,454f,glass);
        c.drawCircle(C,C,205f,body);
        c.drawBitmap(innerGrooves,290,290,null);
        int materialSave=c.save();c.rotate(18f*time,C,C);
        c.drawBitmap(silver,null,metalBounds,metal);c.restoreToCount(materialSave);
        rim.setShader(blackReflection);rim.setStrokeWidth(69f);c.drawCircle(C,C,148f,rim);
        line.setShader(innerEdge);line.setAlpha(255);line.setStrokeWidth(2.35f);c.drawCircle(C,C,184.5f,line);
        line.setShader(edge);line.setAlpha(160);line.setStrokeWidth(.9f);c.drawCircle(C,C,451.5f,line);
        line.setAlpha(255);line.setStrokeWidth(2.5f);c.drawCircle(C,C,453.5f,line);
        c.restoreToCount(save);
    }
}
