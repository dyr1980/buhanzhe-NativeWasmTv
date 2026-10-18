package xiao.bu.radio;

import android.graphics.*;

/** Immutable brushed-metal spindle. Artwork stays available to the host. */
final class OpticalTexture {
    static final int SIZE=1024;
    static final float COVER_RADIUS=.110f;
    private static Bitmap spindle;
    static synchronized Bitmap cover(Bitmap ignored){
        if(spindle!=null&&!spindle.isRecycled())return spindle;
        int size=Math.round(SIZE*COVER_RADIUS*2);float c=size*.5f,r=c-1f;
        Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);Paint p=new Paint(3);
        SweepGradient metal=new SweepGradient(c,c,new int[]{0xff55667e,0xff465873,0xff2e3e57,
                0xff354862,0xff586b83,0xff60748a,0xff465b76,0xff30445e,0xff55667e},
                new float[]{0f,.12f,.24f,.38f,.50f,.61f,.74f,.88f,1f});
        Matrix matrix=new Matrix();matrix.setRotate(-60.6f,c,c);metal.setLocalMatrix(matrix);p.setShader(metal);canvas.drawCircle(c,c,r,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.55f);
        for(float radius=24f;radius<r;radius+=.95f){p.setColor(0x0bd3dfeb);canvas.drawCircle(c,c,radius,p);}
        p.setStrokeWidth(1.5f);p.setColor(0x8fc0d2e1);canvas.drawCircle(c,c,r-.6f,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xff031427);canvas.drawCircle(c,c,size*(23f/220f),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);p.setColor(0x759db4cb);canvas.drawCircle(c,c,size*(23f/220f),p);
        spindle=image;return image;
    }
    static int tone(Bitmap ignored){return 0xff0b2350;}
}
