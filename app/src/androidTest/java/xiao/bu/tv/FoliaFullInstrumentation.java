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

/** Exercises the original bundled application and the Java transport on API25. */
public final class FoliaFullInstrumentation extends Instrumentation {
    Bundle args;volatile MainActivity activity;
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    String js(WebView web,String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(script,value->{result.set(value);latch.countDown();}));check(latch.await(5,TimeUnit.SECONDS),"JS timeout");return result.get();}
    void tapText(WebView web,String text)throws Exception {
        String result=js(web,"(function(){var e=Array.from(document.querySelectorAll('span,div,h3')).find(e=>e.childElementCount===0&&e.textContent.trim()==="+JSONObject.quote(text)+");if(!e)return null;var r=e.getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})()");
        if("null".equals(result))return;
        JSONArray point=new JSONArray(result);int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));
        float scale=web.getWidth()/(float)point.getDouble(2),x=location[0]+(float)point.getDouble(0)*scale,y=location[1]+(float)point.getDouble(1)*scale;
        long time=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,0,x,y,0),up=android.view.MotionEvent.obtain(time,time+60,1,x,y,0);sendPointerSync(down);SystemClock.sleep(60);sendPointerSync(up);down.recycle();up.recycle();
    }
    void tapPlay(WebView web)throws Exception {
        String result=js(web,"(function(){var e=document.querySelector('button[title=\"播放\"]')||document.querySelector('button:has(svg.lucide-play)');if(!e)return null;var r=e.getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})()");
        if("null".equals(result))return;JSONArray p=new JSONArray(result);int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));float scale=web.getWidth()/(float)p.getDouble(2),x=location[0]+(float)p.getDouble(0)*scale,y=location[1]+(float)p.getDouble(1)*scale;
        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,1,x,y,0);sendPointerSync(down);SystemClock.sleep(60);sendPointerSync(up);down.recycle();up.recycle();
    }
    private void trustFixture()throws Exception{
        java.security.cert.Certificate cert;try(InputStream in=new FileInputStream(args.getString("cert"))){cert=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        java.security.KeyStore store=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());store.load(null);store.setCertificateEntry("fixture",cert);
        javax.net.ssl.TrustManagerFactory managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());managers.init(store);
        javax.net.ssl.X509TrustManager trust=(javax.net.ssl.X509TrustManager)managers.getTrustManagers()[0];javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,new javax.net.ssl.TrustManager[]{trust},null);
        okhttp3.OkHttpClient client=NetworkClient.sharedClient().newBuilder().sslSocketFactory(tls.getSocketFactory(),trust).build();
        for(String name:new String[]{"client","factory"}){Field f=NetworkClient.class.getDeclaredField(name);f.setAccessible(true);f.set(null,"client".equals(name)?client:new okhttp3.OkUrlFactory(client));}GithubProxy.setEnabled(false);
    }
    public void onStart(){Bundle result=new Bundle();int code=0;PowerManager power=(PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE);PowerManager.WakeLock wake=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"nTv:FoliaFullTest");wake.acquire(180000);
        try{
            trustFixture();
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.elapsedRealtime()+20000;while(activity==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);check(activity!=null,"Activity missing");SystemClock.sleep(2000);
            runOnMainSync(()->{try{
                Channel channel=new Channel("1","Folia 完整版","",args.getString("source"),null,null);
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("音乐",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
                for(String name:new String[]{"currentGroupIndex","currentChannelIndex","currentSourceIndex"}){Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setInt(activity,0);}
                Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class);start.setAccessible(true);start.invoke(activity,0);
            }catch(Exception e){throw new RuntimeException(e);}});
            JarChannelRuntime runtime=(JarChannelRuntime)field(activity,"jarChannelRuntime");
            WebView web=null;until=SystemClock.elapsedRealtime()+60000;
            while(SystemClock.elapsedRealtime()<until){web=(WebView)field(runtime,"browser");if(web!=null&&js(web,"!!document.querySelector('input[placeholder]')").equals("true"))break;SystemClock.sleep(250);}
            check(web!=null,"No full application WebView");final WebView browser=web;
            check(js(browser,"location.origin").contains("127.0.0.1"),"Frontend loaded from remote website");
            check(js(browser,"!!window.__NTV_FOLIA_FULL__").equals("true"),"Java transport bridge missing");
            String body=js(browser,"document.body.innerText");check(body.contains("Folia")&&body.contains("歌单"),"Upstream app did not mount: "+body);
            js(browser,"window.__ntvApiTest='pending';(async function(){for(var i=0;i<2;i++){try{var r=await fetch('/ncm-api/search?keywords='+encodeURIComponent('周杰伦')+'&limit=3'),j=await r.json();if(j.code===200){window.__ntvApiTest=JSON.stringify({code:j.code,count:j.result&&j.result.songs&&j.result.songs.length});return;}if(i===1)window.__ntvApiTest=JSON.stringify(j);}catch(e){if(i===1)window.__ntvApiTest=String(e);}}})()");
            until=SystemClock.elapsedRealtime()+55000;String api="pending";
            while(SystemClock.elapsedRealtime()<until){api=js(browser,"window.__ntvApiTest");if(!api.contains("pending"))break;SystemClock.sleep(200);}
            check(api.contains("200")&&api.contains("count"),"Java API relay failed: "+api);
            js(browser,"Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='本地').click()");
            SystemClock.sleep(500);
            check(js(browser,"document.body.innerText").contains("导入"),"Original local library did not open");
            js(browser,"window.__ntvInputClick=HTMLInputElement.prototype.click;HTMLInputElement.prototype.click=function(){if(this.type==='file'){window.__ntvImportInput=this;return;}return window.__ntvInputClick.apply(this,arguments)};Array.from(document.querySelectorAll('button')).find(b=>b.textContent.indexOf('导入文件夹')>=0).click()");
            until=SystemClock.elapsedRealtime()+5000;while(!js(browser,"!!window.__ntvImportInput").equals("true")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            check(js(browser,"!!window.__ntvImportInput").equals("true"),"WebView file input fallback missing");
            js(browser,"window.__ntvImportStatus='pending';fetch('http://127.0.0.1:39573/song.mp3').then(r=>r.arrayBuffer()).then(bytes=>{var dt=new DataTransfer();dt.items.add(new File([bytes],'NativeFixture.mp3',{type:'audio/mpeg'}));dt.items.add(new File(['[00:00.00]Native fixture first\\n[00:02.00]Native fixture second'],'NativeFixture.lrc',{type:'text/plain'}));window.__ntvImportInput.files=dt.files;window.__ntvImportInput.dispatchEvent(new Event('change',{bubbles:true}));HTMLInputElement.prototype.click=window.__ntvInputClick;window.__ntvImportStatus='sent'}).catch(e=>window.__ntvImportStatus=String(e))");
            until=SystemClock.elapsedRealtime()+10000;while(!js(browser,"document.body.innerText").contains("Local Import")&&!js(browser,"document.body.innerText").contains("NativeFixture")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(300);
            String library=js(browser,"document.body.innerText");
            check(library.contains("NativeFixture")||library.contains("Local Import"),"Original local music import failed: "+js(browser,"window.__ntvImportStatus")+" body="+library);
            js(browser,"var folder=Array.from(document.querySelectorAll('button,div,span,h3')).find(e=>e.childElementCount===0&&e.textContent.trim()==='Local Import');if(folder){folder.click();folder.dispatchEvent(new MouseEvent('dblclick',{bubbles:true}));}");
            SystemClock.sleep(500);
            js(browser,"var title=Array.from(document.querySelectorAll('span,div')).find(e=>e.childElementCount===0&&e.textContent.trim()==='NativeFixture');if(title)title.dispatchEvent(new MouseEvent('dblclick',{bubbles:true}));");
            SystemClock.sleep(2500);
            result.putString("localImport",js(browser,"JSON.stringify(Array.from(document.querySelectorAll('audio')).map(a=>({paused:a.paused,time:a.currentTime,source:a.currentSrc})))"));
            until=SystemClock.elapsedRealtime()+10000;while(browser.getWidth()==0&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            runOnMainSync(()->runtime.configure("1080p",1.5f));SystemClock.sleep(800);String viewport=js(browser,"document.querySelector('meta[name=viewport]').content");check(viewport.contains("width=1280,"),"Browser viewport not applied: "+viewport+" physical="+browser.getWidth()+"x"+browser.getHeight()+" parent="+browser.getParent());
            tapText(browser,"Local Import");SystemClock.sleep(600);
            tapText(browser,"Local Import");SystemClock.sleep(600);
            tapText(browser,"NativeFixture");SystemClock.sleep(100);tapText(browser,"NativeFixture");SystemClock.sleep(1800);
            js(browser,"var play=document.querySelector('button:has(svg.lucide-play)')||document.querySelector('button[title=\"播放\"]')||document.querySelector('button[aria-label=\"播放\"]');if(play)play.click();");SystemClock.sleep(2500);
            tapPlay(browser);SystemClock.sleep(2500);
            js(browser,"import(new URL('/assets/v2-usePlaybackStore-DDfdWo5W.js',location.href).href).then(m=>{var s=m.y.getState();window.__ntvPlaybackState=JSON.stringify({title:s.currentSong&&s.currentSong.name,state:s.playerState,source:!!s.audioSrc})})");SystemClock.sleep(300);
            result.putString("playbackState",js(browser,"window.__ntvPlaybackState"));
            check(js(browser,"window.__ntvPlaybackState").contains("NativeFixture"),"Original player did not select the imported track: "+js(browser,"window.__ntvPlaybackState")+" buttons="+js(browser,"JSON.stringify(Array.from(document.querySelectorAll('button')).map(b=>({title:b.title,aria:b.getAttribute('aria-label'),svg:b.querySelector('svg')&&b.querySelector('svg').getAttribute('class')})))"));
            String exportName="folia-export-test-"+SystemClock.uptimeMillis()+".lrc";
            js(browser,"var exportLink=document.createElement('a');exportLink.href=URL.createObjectURL(new Blob(['[00:00.00]export fixture'],{type:'text/plain'}));exportLink.download="+JSONObject.quote(exportName)+";document.body.appendChild(exportLink);exportLink.click();exportLink.remove();");
            File exported=new File(getTargetContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),exportName);
            until=SystemClock.elapsedRealtime()+5000;while(!exported.isFile()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            check(exported.isFile(),"Blob export was not saved by Java");
            try(InputStream in=new FileInputStream(exported)){check(new String(RadioUiCatalog.read(in,1024),"UTF-8").equals("[00:00.00]export fixture"),"Exported file contents differ");}exported.delete();
            runOnMainSync(()->{try{((android.view.View)field(activity,"channelBar")).setVisibility(android.view.View.GONE);}catch(Exception ignored){}});SystemClock.sleep(500);
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"folia-full.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            result.putString("stream","PASS: pinned HTTPS JAR, original Folia modules mounted, Java API search, original local MP3/LRC import, browser viewport, Java blob export; "+api+" playback="+js(browser,"window.__ntvPlaybackState"));code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(wake.isHeld())wake.release();}finish(code,result);
    }
}
