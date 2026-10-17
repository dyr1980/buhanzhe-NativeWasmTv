package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Real overlay touch/key paths and actual player landing clocks, without relaunching. */
final class SeekRegressionChecks {
    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static String stamp(long value) {
        long s=Math.max(0,value)/1000;return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);
    }
    private static long labelPosition(TextView label) {
        String[] parts=label.getText().toString().split(" / ")[0].split(":");
        return (Long.parseLong(parts[0])*60+Long.parseLong(parts[1]))*1000;
    }
    static void run(Instrumentation test,MainActivity activity,JSONObject row) throws Exception {
        Method show=MainActivity.class.getDeclaredMethod("showVideoControls");show.setAccessible(true);
        test.runOnMainSync(()->{try{show.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        SystemClock.sleep(200);
        VideoControlsOverlay overlay=(VideoControlsOverlay)field(activity,"videoControlsOverlay");
        IMediaPlayer player=(IMediaPlayer)field(activity,"player");
        SeekBar bar=(SeekBar)field(overlay,"seek");
        TextView label=(TextView)field(overlay,"time");
        JSONArray checks=new JSONArray();row.put("seekChecks",checks);
        if(player.getDuration()<16000)throw new AssertionError("Seek fixture too short");
        test.runOnMainSync(()->{try{if(player.isPlaying())((View)field(overlay,"toggle")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
        test.runOnMainSync(()->{
            for(long target:new long[]{12000,4000,14000,6000}) {
                drag(bar,player.getDuration(),target);
                if(Math.abs(labelPosition(label)-target)>1100)
                    throw new AssertionError("Seek label rebounded immediately: "+label.getText());
            }
        });
        landed(test,activity,overlay,player,6000,checks,"rapid thumb forward/backward");
        final long[] stepTarget=new long[1];
        test.runOnMainSync(()->{try{
            VideoControlsOverlay.Playback playback=(VideoControlsOverlay.Playback)field(overlay,"playback");
            stepTarget[0]=playback.position()+4000;
            for(int n=0;n<4;n++)overlay.step(1000);
            if(Math.abs(labelPosition(label)-stepTarget[0])>1100)
                throw new AssertionError("Repeated steps used stale playback position: "+label.getText());
        }catch(Exception e){throw new RuntimeException(e);}});
        landed(test,activity,overlay,player,stepTarget[0],checks,"four repeated forward keys");
        test.runOnMainSync(()->drag(bar,player.getDuration(),2000));
        landed(test,activity,overlay,player,2000,checks,"backward thumb seek");
        final long[] swipeTarget=new long[1];
        test.runOnMainSync(()->{try{
            VideoControlsOverlay.Playback playback=(VideoControlsOverlay.Playback)field(overlay,"playback");
            swipeTarget[0]=playback.position()+5000;
            float x=overlay.getWidth()*.2f,y=overlay.getHeight()*.3f;
            float end=x+overlay.getWidth()*5000f/player.getDuration();
            touch(overlay,MotionEvent.ACTION_DOWN,x,y);
            touch(overlay,MotionEvent.ACTION_MOVE,end,y);
            touch(overlay,MotionEvent.ACTION_UP,end,y);
        }catch(Exception e){throw new RuntimeException(e);}});
        landed(test,activity,overlay,player,swipeTarget[0],checks,"horizontal gesture");
        long before=player.getCurrentPosition();
        test.runOnMainSync(()->{player.start();overlay.show();});
        long resume=SystemClock.elapsedRealtime(),after=before;
        while(SystemClock.elapsedRealtime()-resume<10000) {
            SystemClock.sleep(250);after=player.getCurrentPosition();
            if(after-before>=750)break;
        }
        if(after-before<750)throw new AssertionError("Playback did not advance after seek: "+before+" -> "+after);
        checks.put(new JSONObject().put("case","resume after seeking").put("advancedMs",after-before)
                .put("resumeMs",SystemClock.elapsedRealtime()-resume).put("status","passed"));
        test.runOnMainSync(()->drag(bar,player.getDuration(),12000));
        landed(test,activity,overlay,player,12000,checks,"forward while playing");
        test.runOnMainSync(()->drag(bar,player.getDuration(),4000));
        landed(test,activity,overlay,player,4000,checks,"backward while playing");
        final long[] backTarget=new long[1];
        test.runOnMainSync(()->{try{
            VideoControlsOverlay.Playback playback=(VideoControlsOverlay.Playback)field(overlay,"playback");
            backTarget[0]=playback.position()-2000;
            overlay.step(-1000);overlay.step(-1000);
            if(Math.abs(labelPosition(label)-backTarget[0])>1100)
                throw new AssertionError("Repeated backward keys lost a step");
        }catch(Exception e){throw new RuntimeException(e);}});
        landed(test,activity,overlay,player,backTarget[0],checks,"repeated backward keys while playing");
        test.runOnMainSync(()->{try{
            int generation=(Integer)field(activity,"mediaTrackChangeGeneration");
            float x=overlay.getWidth()*.2f,y=overlay.getHeight()*.3f;
            touch(overlay,MotionEvent.ACTION_DOWN,x,y);
            touch(overlay,MotionEvent.ACTION_MOVE,x+overlay.getWidth()*.3f,y);
            touch(overlay,MotionEvent.ACTION_CANCEL,x+overlay.getWidth()*.3f,y);
            if((Integer)field(activity,"mediaTrackChangeGeneration")!=generation)
                throw new AssertionError("Cancelled gesture performed a seek");
        }catch(Exception e){throw new RuntimeException(e);}});
        checks.put(new JSONObject().put("case","cancel gesture without seeking").put("status","passed"));
        if(field(activity,"player")!=player)throw new AssertionError("Seek replaced player instead of seeking");
    }
    private static void drag(SeekBar bar,long duration,long target) {
        float x=bar.getPaddingLeft()+(bar.getWidth()-bar.getPaddingLeft()-bar.getPaddingRight())*(float)target/duration;
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(now,now,action,x,bar.getHeight()/2f,0);
            bar.dispatchTouchEvent(event);event.recycle();
        }
    }
    private static void touch(VideoControlsOverlay overlay,int action,float x,float y) {
        long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);
        overlay.onTouchEvent(event);event.recycle();
    }
    private static void landed(Instrumentation test,MainActivity activity,VideoControlsOverlay overlay,
            IMediaPlayer player,long target,JSONArray checks,String name) throws Exception {
        long begin=SystemClock.elapsedRealtime();boolean done=false;
        while(SystemClock.elapsedRealtime()-begin<12000) {
            final boolean[] ready={false};
            test.runOnMainSync(()->{try{
                PlaybackSeekState state=(PlaybackSeekState)field(activity,"playbackSeekState");
                ready[0]=((Long)field(state,"submitted"))<0 && Math.abs(player.getCurrentPosition()-target)<1800;
            }catch(Exception e){throw new RuntimeException(e);}});
            if(ready[0]){done=true;break;}
            SystemClock.sleep(100);
        }
        if(!done)throw new AssertionError(name+" failed to land at "+target+" actual="+player.getCurrentPosition());
        long landedAt=SystemClock.elapsedRealtime();
        SystemClock.sleep(650);
        long actual=player.getCurrentPosition();
        long forwardAllowance=1800+(player.isPlaying()?SystemClock.elapsedRealtime()-landedAt:0);
        if(actual<target-1800 || actual>target+forwardAllowance)
            throw new AssertionError(name+" rebounded after completion: "+actual+" target="+target);
        test.runOnMainSync(()->overlay.show());
        checks.put(new JSONObject().put("case",name).put("targetMs",target).put("actualMs",actual)
                .put("seekMs",SystemClock.elapsedRealtime()-begin).put("status","passed"));
    }
}
