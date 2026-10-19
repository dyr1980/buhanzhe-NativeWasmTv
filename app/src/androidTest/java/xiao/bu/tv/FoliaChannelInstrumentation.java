package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Downloads a pinned HTTPS JAR and opens the real M3U channel in an isolated test app. */
public final class FoliaChannelInstrumentation extends Instrumentation {
    private Bundle args;
    private volatile MainActivity activity;
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    String js(WebView web,String script)throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(script,result->{value.set(result);done.countDown();}));
        check(done.await(5,TimeUnit.SECONDS),"JavaScript timeout");return value.get();
    }
    private void trustFixture()throws Exception {
        java.security.cert.Certificate cert;
        try(InputStream in=new FileInputStream(args.getString("cert"))){cert=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        java.security.KeyStore store=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());store.load(null);store.setCertificateEntry("fixture",cert);
        javax.net.ssl.TrustManagerFactory managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());managers.init(store);
        javax.net.ssl.X509TrustManager trust=(javax.net.ssl.X509TrustManager)managers.getTrustManagers()[0];
        javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,new javax.net.ssl.TrustManager[]{trust},null);
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
        okhttp3.OkHttpClient client=NetworkClient.sharedClient().newBuilder().sslSocketFactory(tls.getSocketFactory(),trust).build();
        for(String name:new String[]{"client","factory"}){Field f=NetworkClient.class.getDeclaredField(name);f.setAccessible(true);f.set(null,"client".equals(name)?client:new okhttp3.OkUrlFactory(client));}
        GithubProxy.setEnabled(false);
    }
    public void onStart() {
        Bundle result=new Bundle();int code=0;
        PowerManager power=(PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wake=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"nTv:FoliaTest");
        wake.acquire(90000);
        try {
            trustFixture();
            String source=args.getString("source");new JarChannelSource(source);
            try{new JarChannelSource(source.replace("sha256=","sha256=bad"));throw new AssertionError("Bad SHA accepted");}catch(IOException expected){}
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,(Object)("#EXTM3U\n#EXTINF:-1 group-title=\"音乐\",Folia 测试\n"+source+"\n").getBytes("UTF-8"));
            check(groups.length==1,"M3U channel missing");
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
            check(activity!=null,"Activity missing");waitForIdleSync();
            SystemClock.sleep(2000);
            runOnMainSync(()->{try{ChannelCatalog.GROUPS=groups;Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class);start.setAccessible(true);start.invoke(activity,0);}catch(Exception e){throw new RuntimeException(e);}});
            JarChannelRuntime runtime=(JarChannelRuntime)field(activity,"jarChannelRuntime");
            WebView web=null;deadline=SystemClock.elapsedRealtime()+30000;
            while(SystemClock.elapsedRealtime()<deadline){waitForIdleSync();web=(WebView)field(runtime,"browser");if(web!=null&&js(web,"document.getElementById('title')&&document.getElementById('title').textContent").contains("Fixture"))break;SystemClock.sleep(100);}
            check(web!=null,"JAR browser missing");
            final WebView browser=web;
            deadline=SystemClock.elapsedRealtime()+10000;
            while(browser.getWidth()==0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            check(js(browser,"document.getElementById('title').textContent").contains("Fixture"),"Bundled page metadata missing");
            deadline=SystemClock.elapsedRealtime()+20000;
            while(SystemClock.elapsedRealtime()<deadline&&!js(browser,"document.querySelectorAll('.line').length").equals("3"))SystemClock.sleep(100);
            check(js(browser,"document.querySelectorAll('.line').length").equals("3"),"Java lyric fetch failed: "+js(browser,"document.getElementById('status').textContent"));
            deadline=SystemClock.elapsedRealtime()+20000;
            while(!(Boolean)field(activity,"prepared")&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            check((Boolean)field(activity,"prepared"),"Native audio did not prepare");
            tv.danmaku.ijk.media.player.IMediaPlayer audio=(tv.danmaku.ijk.media.player.IMediaPlayer)field(activity,"player");
            check(audio!=null&&audio.isPlaying(),"Native audio did not start");
            SystemClock.sleep(3500);
            check(audio.getCurrentPosition()>500,"Native audio clock did not advance");
            runOnMainSync(()->runtime.configure("1080p",1.5f));SystemClock.sleep(400);
            String viewport=js(browser,"document.querySelector('meta[name=viewport]').content");
            check(viewport.contains("width=1280,"),"1080p / 150% viewport mismatch: "+viewport+" physical="+browser.getWidth()+"x"+browser.getHeight()+" parent="+browser.getParent()+" runtimeView="+field(runtime,"view")+" root="+field(activity,"root"));
            runOnMainSync(()->runtime.configure("4k",2f));SystemClock.sleep(400);
            check(js(browser,"document.querySelector('meta[name=viewport]').content").contains("width=1920,"),"4k / 200% viewport mismatch");
            js(browser,"window.folia.clock(2500,false)");
            check(js(browser,"document.querySelector('.line.active').textContent").contains("second"),"Playback seek clock mismatch");
            runOnMainSync(()->{try{((android.view.View)field(activity,"channelBar")).setVisibility(android.view.View.GONE);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(250);
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
            if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"folia-channel.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            runOnMainSync(runtime::close);check(field(runtime,"view")==null,"Plugin view retained after close");
            result.putString("stream","PASS: HTTPS download/hash, M3U dispatch, JAR HTML, Java lyrics, native audio, viewport changes, seek clock, dispose");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(wake.isHeld())wake.release();}
        finish(code,result);
    }
}
