package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Method;
import xiao.bu.tv.skin.SharedTvUi;

/** Capture actual Canvas text and real Android font widths; no activity/preferences changes. */
public final class FourDigitChannelNumberInstrumentation extends Instrumentation {
    private int checks;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static final class ScaledContext extends ContextWrapper implements SharedTvUi.Viewport {
        final float scale;
        ScaledContext(Context context,float scale){super(context);this.scale=scale;}
        public float themeScale(){return scale;}
        public int themeHeight(){return Math.round(540*scale);}
    }
    private static final class TextCanvas extends Canvas {
        String expected;int seen;float left,right,size,triangleRight;
        TextCanvas(Bitmap bitmap){super(bitmap);}
        void expect(String value){expected=value;seen=0;triangleRight=-1;}
        public void drawText(String text,float x,float y,Paint paint){
            if(text.equals(expected)){seen++;left=x;right=x+paint.measureText(text);size=paint.getTextSize();}
            if(text.equals("▶"))triangleRight=x+paint.measureText(text);
            super.drawText(text,x,y,paint);
        }
    }
    private void row(int theme,float scale)throws Exception{
        SharedTvUi renderer=new SharedTvUi(){protected int theme(){return theme;}};
        Context context=new ScaledContext(getTargetContext(),scale);
        Bundle state=new Bundle();state.putString("title","Channel");state.putString("program","");
        state.putInt("sources",1);state.putString("number","1219");
        View view=renderer.create(context,"channel-row",state,new View[0],null);
        int width=Math.round(320*scale),height=Math.round(SharedTvUi.preferredRowHeight("ui"+theme)*scale);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
        Method draw=view.getClass().getDeclaredMethod("onDraw",Canvas.class);draw.setAccessible(true);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);TextCanvas canvas=new TextCanvas(bitmap);
        int[] examples={1000,1111,1219,1220,1221,1222,1223,1327,1888,8888,9999};
        try{
            for(boolean selected:new boolean[]{false,true})for(int number:examples){
                String value=String.valueOf(number);state.putString("number",value);state.putBoolean("selected",selected);
                state.putBoolean("focused",selected);renderer.update(view,state);canvas.expect(value);draw.invoke(view,canvas);
                check(canvas.seen==1,"Missing/truncated "+value+" theme="+theme+" scale="+scale);
                float l=theme==6?28:theme==5?10:theme==2?24:theme==3?6:17;
                float r=theme==6?68:theme==5?46:theme==2?66:theme==3?40:58;
                check(canvas.left>=l-.1f&&canvas.right<=r+.1f,"Number outside its column: "+value);
            }
            if(theme==6){
                state.putBoolean("playing",true);state.putString("number","8888");renderer.update(view,state);
                canvas.expect("8888");draw.invoke(view,canvas);
                check(canvas.seen==1&&canvas.triangleRight<canvas.left,"Playing marker overlaps four-digit number");
                if(scale==1f){
                    state.putBoolean("playing",false);
                    for(int number=1000;number<=9999;number++){
                        String value=String.valueOf(number);state.putString("number",value);renderer.update(view,state);
                        canvas.expect(value);draw.invoke(view,canvas);
                        check(canvas.seen==1&&canvas.left>=27.9f&&canvas.right<=68.1f,"Four-digit regression: "+value);
                    }
                }
            }
        }finally{bitmap.recycle();}
    }
    public void onStart(){
        Bundle result=new Bundle();Throwable[] error={null};
        runOnMainSync(()->{try{
            for(float scale:new float[]{.5f,.9f,1f,1.5f,2f})for(int theme=2;theme<=6;theme++)row(theme,scale);
            View classic=LayoutInflater.from(getTargetContext()).inflate(R.layout.item_channel,null);
            TextView number=(TextView)classic.findViewById(R.id.channel_number);
            for(String value:new String[]{"1219","1220","1888","8888","9999"}){
                number.setText(value);classic.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST));
                check(number.getLayout()!=null&&number.getLayout().getLineCount()==1,"Classic number wraps: "+value);
                check(number.getPaint().measureText(value)<=number.getMeasuredWidth()-number.getCompoundPaddingLeft()-number.getCompoundPaddingRight(),"Classic number clipped");
            }
        }catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null){result.putString("stream",android.util.Log.getStackTraceString(error[0]));finish(0,result);}
        else{result.putString("stream","PASS "+checks+" Android font/render checks: every number 1000-9999, all themes, 50/90/100/150/200% scales and playing marker");finish(-1,result);}
    }
}
