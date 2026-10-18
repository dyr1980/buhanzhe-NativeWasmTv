package xiao.bu.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Render the real control on the device without creating a decoder or taking foreground. */
public final class VideoControlsRenderingInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args) {super.onCreate(args);this.args=args;start();}
    @Override public void onStart() {
        Bundle result=new Bundle();int code=-1;
        final Throwable[] failure={null};final JSONObject report=new JSONObject();
        runOnMainSync(() -> {
            try {
                Activity owner=new Activity();
                Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);
                attach.setAccessible(true);attach.invoke(owner,getTargetContext());owner.setTheme(R.style.AppTheme);
                final long[] duration={40000},position={20000};final int[] seeks={0};
                VideoControlsOverlay controls=new VideoControlsOverlay(owner,new VideoControlsOverlay.Playback() {
                        public boolean completed(){return false;}
                        public boolean catchup(){return false;}
                        public void live(){}
                    public String status(){return "";}
                    public boolean loading(){return false;}
                    public boolean playing(){return true;} public long duration(){return duration[0];}
                    public long position(){return position[0];} public void toggle(){}
                    public void seek(long value){position[0]=value;seeks[0]++;} public String title(){return "Controls regression";}
                    public boolean speedSupported(){return false;} public float speed(){return 1f;} public void speed(float value){}
                },true);
                controls.show();controls.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));controls.layout(0,0,800,480);
                Field field=VideoControlsOverlay.class.getDeclaredField("seek");field.setAccessible(true);
                SeekBar seek=(SeekBar)field.get(controls);
                Bitmap bitmap=Bitmap.createBitmap(seek.getWidth(),seek.getHeight(),Bitmap.Config.ARGB_8888);
                seek.draw(new Canvas(bitmap));
                int pixels=0;int center=seek.getHeight()/2;
                for(int x=seek.getWidth()/10;x<seek.getWidth()*4/10;x++)
                    for(int y=Math.max(0,center-5);y<Math.min(seek.getHeight(),center+6);y++)
                        if((bitmap.getPixel(x,y)>>>24)>50)pixels++;
                File output=new File(getTargetContext().getExternalFilesDir(null),args.getString("name","controls-render")+".png");
                try(FileOutputStream file=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
                report.put("sdk",android.os.Build.VERSION.SDK_INT).put("width",seek.getWidth()).put("height",seek.getHeight())
                        .put("trackPixels",pixels).put("trackIntrinsicHeight",seek.getProgressDrawable().getIntrinsicHeight());
                if("true".equals(args.getString("expectTrack","false")) && pixels<seek.getWidth()/5)
                    throw new AssertionError("Seek track not rendered: "+report);
                long now=SystemClock.uptimeMillis();
                for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}) {
                    MotionEvent event=MotionEvent.obtain(now,now+50,action,seek.getWidth()*.75f,center,0);
                    seek.dispatchTouchEvent(event);event.recycle();
                }
                if(seeks[0]!=1 || position[0]<25000 || position[0]>35000)throw new AssertionError("Seek gesture did not commit expected target");
                report.put("seekTargetMs",position[0]);duration[0]=0;controls.show();
                if(seek.getVisibility()!=View.GONE)throw new AssertionError("Live source showed a seek track");
                report.put("liveGuard",true);controls.hide();
                try(FileOutputStream file=new FileOutputStream(new File(output.getParentFile(),args.getString("name","controls-render")+".json"))) {
                    file.write(report.toString(2).getBytes("UTF-8"));
                }
            } catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null){code=0;result.putString("stream",android.util.Log.getStackTraceString(failure[0]));}
        else result.putString("stream","PASS "+report+"\n");
        finish(code,result);
    }
}
