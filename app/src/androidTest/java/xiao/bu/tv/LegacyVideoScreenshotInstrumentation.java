package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Replays actual decrypted segments without network or the playing decoder. */
public final class LegacyVideoScreenshotInstrumentation extends Instrumentation {
    private Bundle args;
    private byte[] capture(VideoScreenshot screenshots, VideoScreenshot.Source source, boolean preview)
            throws Exception {
        if(!"true".equals(args.getString("forceLegacy")))
            return preview?screenshots.capturePreview(source):screenshots.capture(source);
        java.lang.reflect.Method method=VideoScreenshot.class.getDeclaredMethod(
                "captureLegacy",VideoScreenshot.Source.class,boolean.class);
        method.setAccessible(true);return(byte[])method.invoke(screenshots,source,preview);
    }
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        java.lang.reflect.Field nativeFlag=null;boolean nativeWasAvailable=false;
        try {
            if("true".equals(args.getString("forcePlatform"))) {
                nativeFlag=NativeVideoFrame.class.getDeclaredField("AVAILABLE");nativeFlag.setAccessible(true);
                nativeWasAvailable=nativeFlag.getBoolean(null);nativeFlag.setBoolean(null,false);
            }
            File file=new File(args.getString("frameFile","/sdcard/frame-0.ts"));
            byte[] segment=new byte[(int)file.length()];
            FileInputStream input=new FileInputStream(file);
            try {int at=0,n;while(at<segment.length&&(n=input.read(segment,at,segment.length-at))>0)at+=n;}
            finally{input.close();}
            Object[] session={new Object()};
            byte[][] segments={segment};
            int width=Integer.parseInt(args.getString("width","1920"));
            int height=Integer.parseInt(args.getString("height","1080"));
            VideoScreenshot.Source source=()->new VideoScreenshot.Target(null,session[0],width,height,
                    segments[0],getTargetContext().getCacheDir());
            VideoScreenshot screenshots=new VideoScreenshot();
            long start=SystemClock.elapsedRealtime();
            boolean preview="true".equals(args.getString("preview"));
            byte[] image=capture(screenshots,source,preview);
            long elapsed=SystemClock.elapsedRealtime()-start;
            Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);
            if(bitmap==null)throw new AssertionError("No decoded image");
            File outputRoot=android.os.Build.VERSION.SDK_INT<23?file.getParentFile():getTargetContext().getCacheDir();
            FileOutputStream output=new FileOutputStream(new File(outputRoot,"frame-offline.jpg"));
            try{output.write(image);}finally{output.close();}
            result.putString("stream","PASS frame="+bitmap.getWidth()+"x"+bitmap.getHeight()
                    +" bytes="+image.length+" elapsedMs="+elapsed);
            bitmap.recycle();
            StringBuilder report=new StringBuilder(result.getString("stream"));
            int iterations=Integer.parseInt(args.getString("iterations","1"));
            for(int i=1;i<iterations;i++) {
                screenshots.clear();start=SystemClock.elapsedRealtime();
                long cpu=android.os.Debug.threadCpuTimeNanos();
                image=capture(screenshots,source,preview);
                report.append("\nuncached elapsedMs=").append(SystemClock.elapsedRealtime()-start)
                        .append(" callerCpuMs=").append((android.os.Debug.threadCpuTimeNanos()-cpu)/1000000L);
            }
            if(preview) {
                start=SystemClock.elapsedRealtime();
                if(capture(screenshots,source,true)!=image)throw new AssertionError("Preview cache missed");
                report.append("\ncached elapsedMs=").append(SystemClock.elapsedRealtime()-start);
                byte[] original=capture(screenshots,source,false);
                bitmap=BitmapFactory.decodeByteArray(original,0,original.length);
                if(bitmap==null||bitmap.getWidth()!=width||bitmap.getHeight()!=height)
                    throw new AssertionError("Preview replaced full-resolution screenshot");
                report.append("\nfull after preview=").append(bitmap.getWidth()).append('x').append(bitmap.getHeight());
                bitmap.recycle();
                FileOutputStream fullOut=new FileOutputStream(new File(outputRoot,"frame-original.jpg"));
                try{fullOut.write(original);}finally{fullOut.close();}
                byte[] previous=capture(screenshots,source,true);
                session[0]=new Object();
                if(capture(screenshots,source,true)==previous)throw new AssertionError("Old-session preview reused");
                report.append("\npreview cache invalidates on channel change=true");
                if("true".equals(args.getString("testRefresh"))) {
                    previous=capture(screenshots,source,true);
                    segments[0]=segment.clone();SystemClock.sleep(3100);
                    if(capture(screenshots,source,true)==previous)throw new AssertionError("Old-segment preview reused");
                    report.append("\npreview refreshes after segment change=true");
                }
            }
            result.putString("stream",report.toString());
        }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));}
        finally{if(nativeFlag!=null)try{nativeFlag.setBoolean(null,nativeWasAvailable);}catch(Exception ignored){}}
        finish(-1,result);
    }
}
