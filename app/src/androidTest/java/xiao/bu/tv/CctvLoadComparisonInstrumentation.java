package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Same-device, alternating CCTV-1 sources; time actual native first frames. */
public final class CctvLoadComparisonInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private Field field(String name) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private void invoke(String name) throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity);
    }
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder();
        Map<String,?> saved=null;
        ChannelCatalog.Group[] groups=null;
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(3000);
            saved=activity.getSharedPreferences(MainActivity.PREFERENCES,0).getAll();
            groups=ChannelCatalog.GROUPS;
            if(!"true".equals(args.getString("skipPluginSetup"))) for(String site:new String[]{"tv.cctv.com","yangshipin.cn"}) {
                try { if(!CjsPluginRuntime.isInstalled(site)) CjsPluginRuntime.installOrUpdate(site); }
                catch(Exception e) { report.append("SETUP ").append(site).append(' ').append(e).append('\n'); }
            }
            int rounds=Integer.parseInt(args.getString("rounds","3"));
            long timeout=Long.parseLong(args.getString("timeoutMs","90000"));
            for(int round=0;round<rounds;round++) for(int turn=0;turn<2;turn++) {
                int provider=(round+turn)%2;
                String name=provider==0?"央视频":"央视网";
                String url=provider==0?"webview://https://yangshipin.cn/tv/home?pid=600001859"
                        :"webview://https://tv.cctv.com/live/cctv1/";
                Channel channel=new Channel("1","CCTV-1 综合","compare",url,null,null);
                final Throwable[] failure={null};
                final long[] switchedAt={0};
                runOnMainSync(()->{try {
                    field("autoSwitchSource").setBoolean(activity,false);
                    if(args.containsKey("delayMode")) field("liveDelayMode").set(activity,args.getString("delayMode"));
                    ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group(
                            "LOAD_COMPARE",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
                    field("currentGroupIndex").setInt(activity,0);
                    field("currentChannelIndex").setInt(activity,0);
                    field("currentSourceIndex").setInt(activity,0);
                    Method m=MainActivity.class.getDeclaredMethod("switchChannel",int.class);
                    m.setAccessible(true);
                    switchedAt[0]=SystemClock.elapsedRealtime();
                    m.invoke(activity,0);
                }catch(Throwable e){failure[0]=e;}});
                long start=switchedAt[0];
                int requestId=field("playRequestId").getInt(activity);
                android.util.Log.i("CctvLoadCompare","SAMPLE round="+(round+1)
                        +" provider="+name+" request="+requestId);
                String status="TIMEOUT", text=""; boolean[] ready={false};
                if(failure[0]!=null) { status="TEST_ERROR"; text=failure[0].toString(); }
                else while(SystemClock.elapsedRealtime()-start<timeout) {
                    SystemClock.sleep(100);
                    final String[] value={""};
                    runOnMainSync(()->{try {
                        ready[0]=field("videoRenderingStarted").getBoolean(activity)
                                && field("playbackReadyRequestId").getInt(activity)==field("playRequestId").getInt(activity);
                        value[0]=((TextView)field("statusText").get(activity)).getText().toString();
                    }catch(Throwable e){failure[0]=e;}});
                    text=value[0];
                    if(ready[0]) { status="FIRST_FRAME"; break; }
                    if(text.contains("解析失败")||text.contains("脚本执行失败")||text.contains("设备性能太弱")
                            ||text.contains("网络连接异常")||text.contains("没有备用线路")) {
                        status="FAILED"; break;
                    }
                    if(failure[0]!=null) {status="TEST_ERROR"; text=failure[0].toString(); break;}
                }
                JSONObject row=new JSONObject().put("round",round+1).put("provider",name)
                        .put("version",args.getString("versionLabel","current"))
                        .put("requestId",requestId).put("status",status)
                        .put("observedElapsedMs",SystemClock.elapsedRealtime()-start)
                        .put("statusText",text);
                int observe=Integer.parseInt(args.getString("observeSeconds","0"));
                if(ready[0] && observe>0) {
                    tv.danmaku.ijk.media.player.IMediaPlayer media=
                            (tv.danmaku.ijk.media.player.IMediaPlayer)field("player").get(activity);
                    long positionBefore=media.getCurrentPosition();
                    row.put("videoWidth",media.getVideoWidth()).put("videoHeight",media.getVideoHeight());
                    boolean audio=false;
                    // Release shrinking can remove the unused interface declaration.
                    // The concrete player's public API is retained for production track selection.
                    tv.danmaku.ijk.media.player.misc.ITrackInfo[] tracks=
                            ((tv.danmaku.ijk.media.player.IjkMediaPlayer)media).getTrackInfo();
                    if(tracks!=null)for(tv.danmaku.ijk.media.player.misc.ITrackInfo track:tracks)
                        if(track.getTrackType()==tv.danmaku.ijk.media.player.misc.ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) audio=true;
                    row.put("audioTrackPresent",audio);
                    runOnMainSync(()->{try {
                        DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
                        android.graphics.Rect buffer=view.getVideoSurfaceHolder().getSurfaceFrame();
                        row.put("outputViewWidth",view.getWidth()).put("outputViewHeight",view.getHeight())
                                .put("surfaceFrameWidth",buffer.width()).put("surfaceFrameHeight",buffer.height())
                                .put("outputType",view.getChildAt(0).getClass().getSimpleName());
                        Method available=MainActivity.class.getDeclaredMethod("isVideoScreenshotAvailable");
                        available.setAccessible(true);row.put("screenshotAvailable",available.invoke(activity));
                    }catch(Exception e){throw new RuntimeException(e);}});
                    if("true".equals(args.getString("exerciseUiResize"))) {
                        DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
                        Field stretch=DirectVideoView.class.getDeclaredField("stretchVideo");stretch.setAccessible(true);
                        boolean original=stretch.getBoolean(view);
                        runOnMainSync(()->view.setStretchVideo(!original));SystemClock.sleep(1000);
                        runOnMainSync(()->view.setStretchVideo(original));SystemClock.sleep(1000);
                        if(field("player").get(activity)!=media || !view.isSurfaceReady())
                            throw new AssertionError("Resizing video output restarted decoder or lost its Surface");
                        row.put("resizePreservesDecoder",true);
                    }
                    if("true".equals(args.getString("captureScreenshot"))) {
                        DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
                        HlsProxyServer proxy=(HlsProxyServer)field("proxy").get(activity);
                        if("true".equals(args.getString("dumpCapture"))) {
                            java.io.FileOutputStream segmentOut=new java.io.FileOutputStream(new java.io.File(
                                    android.os.Environment.getExternalStorageDirectory(),"frame-"+provider+".ts"));
                            try{segmentOut.write(proxy.screenshotSegment());}finally{segmentOut.close();}
                        }
                        final VideoScreenshot.Source capture=()->new VideoScreenshot.Target(view,media,
                                media.getVideoWidth(),media.getVideoHeight(),
                                proxy.screenshotSegment(),activity.getCacheDir());
                        boolean overHttp="true".equals(args.getString("captureOverHttp"));
                        VideoScreenshot screenshots=overHttp?(VideoScreenshot)field("videoScreenshot").get(activity)
                                :new VideoScreenshot();
                        String screenshotUrl="http://127.0.0.1:"+((LocalControlServer)field("controlServer")
                                .get(activity)).getPort()+VideoScreenshot.PATH;
                        long previewStarted=SystemClock.elapsedRealtime();
                        long previewCpu=android.os.Debug.threadCpuTimeNanos();
                        byte[] previewImage=overHttp?VideoScreenshot.download(screenshotUrl+"?preview=1")
                                :screenshots.capturePreview(capture);
                        row.put("captureTransport",overHttp?"HTTP":"direct");
                        row.put("previewElapsedMs",SystemClock.elapsedRealtime()-previewStarted)
                                .put("previewCallerCpuMs",(android.os.Debug.threadCpuTimeNanos()-previewCpu)/1000000L);
                        android.graphics.Bitmap previewBitmap=android.graphics.BitmapFactory.decodeByteArray(
                                previewImage,0,previewImage.length);
                        if(previewBitmap==null || previewBitmap.getWidth()>640 || previewBitmap.getHeight()>360)
                            throw new AssertionError("Preview missing or exceeds its size limit");
                        row.put("previewWidth",previewBitmap.getWidth()).put("previewHeight",previewBitmap.getHeight());
                        previewBitmap.recycle();
                        long shotStarted=SystemClock.elapsedRealtime();
                        long shotCpu=android.os.Debug.threadCpuTimeNanos();
                        byte[] jpeg=overHttp?VideoScreenshot.download(screenshotUrl):screenshots.capture(capture);
                        row.put("screenshotElapsedMs",SystemClock.elapsedRealtime()-shotStarted)
                                .put("screenshotCallerCpuMs",(android.os.Debug.threadCpuTimeNanos()-shotCpu)/1000000L);
                        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
                        if(bitmap==null)throw new AssertionError("Capture produced no image");
                        int first=bitmap.getPixel(0,0);boolean varied=false;
                        for(int y=0;y<bitmap.getHeight()&&!varied;y+=8)for(int x=0;x<bitmap.getWidth();x+=8)
                            if(bitmap.getPixel(x,y)!=first){varied=true;break;}
                        if(!varied)throw new AssertionError("Capture produced a blank image");
                        row.put("screenshotBytes",jpeg.length).put("screenshotWidth",bitmap.getWidth())
                                .put("screenshotHeight",bitmap.getHeight());bitmap.recycle();
                        if("true".equals(args.getString("dumpCapture"))) {
                            java.io.FileOutputStream imageOut=new java.io.FileOutputStream(new java.io.File(
                                    android.os.Environment.getExternalStorageDirectory(),"frame-"+provider+".jpg"));
                            try {imageOut.write(jpeg);}finally{imageOut.close();}
                        }
                        if(android.os.Build.VERSION.SDK_INT<24) {
                            long repeatStart=SystemClock.elapsedRealtime();
                            android.graphics.Bitmap preview=android.graphics.BitmapFactory.decodeByteArray(
                                    screenshots.capturePreview(capture),0,screenshots.capturePreview(capture).length);
                            if(preview==null || preview.getWidth()>640 || preview.getHeight()>360)
                                throw new AssertionError("Legacy preview not bounded");
                            preview.recycle();
                            if(!java.util.Arrays.equals(screenshots.capture(capture),jpeg))
                                throw new AssertionError("Legacy repeated capture did not reuse image");
                            row.put("cachedScreenshotElapsedMs",SystemClock.elapsedRealtime()-repeatStart);
                        }
                        if(field("player").get(activity)!=media || !view.isSurfaceReady())
                            throw new AssertionError("Screenshot disturbed the playing decoder");
                    }
                    final int[] buffered={0};
                    long until=SystemClock.elapsedRealtime()+observe*1000L;
                    while(SystemClock.elapsedRealtime()<until) {
                        SystemClock.sleep(250);
                        runOnMainSync(()->{try {if(field("buffering").getBoolean(activity))buffered[0]++;}
                            catch(Exception e){failure[0]=e;}});
                    }
                    row.put("observeSeconds",observe).put("bufferingObservedMs",buffered[0]*250);
                    row.put("requestChangedDuringObservation",field("playRequestId").getInt(activity)!=requestId);
                    row.put("playerReplacedDuringObservation",field("player").get(activity)!=media);
                    if(field("player").get(activity)==media)
                        row.put("playbackAdvancedMs",media.getCurrentPosition()-positionBefore);
                }
                if(ready[0] && "true".equals(args.getString("exerciseSurfaceRecreate"))) {
                    DirectVideoView view=(DirectVideoView)field("videoView").get(activity);
                    android.view.View child=view.getChildAt(0);
                    runOnMainSync(()->view.removeView(child));SystemClock.sleep(300);
                    runOnMainSync(()->view.addView(child));
                    long recoveryStarted=SystemClock.elapsedRealtime();boolean[] recovered={false};
                    while(SystemClock.elapsedRealtime()-recoveryStarted<45000) {
                        runOnMainSync(()->{try {
                            recovered[0]=view.isSurfaceReady() && field("videoRenderingStarted").getBoolean(activity)
                                    && field("player").get(activity)!=null;
                        }catch(Exception e){throw new RuntimeException(e);}});
                        if(recovered[0])break;SystemClock.sleep(100);
                    }
                    if(!recovered[0])throw new AssertionError("SurfaceView did not recover after detaching");
                    row.put("surfaceRecreateRecovered",true)
                            .put("surfaceRecreateElapsedMs",SystemClock.elapsedRealtime()-recoveryStarted);
                }
                report.append(row).append('\n'); android.util.Log.i("CctvLoadCompare",row.toString());
            }
        } catch(Throwable error) { report.append(android.util.Log.getStackTraceString(error)); }
        finally {
            final Map<String,?> prefs=saved; final ChannelCatalog.Group[] original=groups;
            if(activity!=null) runOnMainSync(()->{try {
                invoke("closeWebSource"); invoke("releasePlayer");
                if(original!=null) ChannelCatalog.GROUPS=original;
                if(prefs!=null) {
                    SharedPreferences.Editor edit=activity.getSharedPreferences(MainActivity.PREFERENCES,0).edit().clear();
                    for(Map.Entry<String,?> entry:prefs.entrySet()) {
                        String k=entry.getKey(); Object v=entry.getValue();
                        if(v instanceof String)edit.putString(k,(String)v);
                        else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                        else if(v instanceof Integer)edit.putInt(k,(Integer)v);
                        else if(v instanceof Long)edit.putLong(k,(Long)v);
                        else if(v instanceof Float)edit.putFloat(k,(Float)v);
                        else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);
                    }
                    edit.commit();
                }
                activity.finish();
            }catch(Exception error){report.append(error);}});
        }
        result.putString("stream",report.toString()); finish(-1,result);
    }
}
