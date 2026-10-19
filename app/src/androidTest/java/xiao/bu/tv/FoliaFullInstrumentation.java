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
        String result=js(web,"(function(){var e=("+JSONObject.quote(text)+"==='关闭'?document.querySelector('[data-testid=release-notes-close]'):null)||Array.from(document.querySelectorAll('span,div,h3,button')).find(e=>(e.childElementCount===0||e.tagName==='BUTTON')&&e.textContent.trim()==="+JSONObject.quote(text)+");if(!e)return null;var r=e.getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})()");
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
            while(SystemClock.elapsedRealtime()<until){web=(WebView)field(runtime,"browser");if(web!=null&&js(web,"!!document.querySelector('#root')&&document.querySelector('#root').innerText.length>30").equals("true"))break;SystemClock.sleep(250);}
            check(web!=null,"No full application WebView");final WebView browser=web;
            check(js(browser,"location.origin").contains("127.0.0.1"),"Frontend loaded from remote website");
            check(js(browser,"!!window.__NTV_FOLIA_FULL__").equals("true"),"Java transport bridge missing");
            String body=js(browser,"document.body.innerText");check(body.length()>30,"Upstream app did not mount: "+body);

            SystemClock.sleep(1500);
            String closePoint=js(browser,"(function(){var e=document.querySelector('[data-testid=release-notes-close]');if(!e)return null;var r=e.getBoundingClientRect();return JSON.stringify({r:[r.x,r.y,r.width,r.height],inner:[innerWidth,innerHeight],hit:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.outerHTML})})()");
            tapText(browser,"关闭");SystemClock.sleep(1000);
            check(!js(browser,"document.body.innerText").contains("Sabbat-of-the-witch Update"),"Original release dialog close button did not respond: "+closePoint+" web="+browser.getWidth()+"x"+browser.getHeight());
            if(js(browser,"!!document.querySelector('[data-testid=ponder-onboarding]')").equals("true")) {
                tapText(browser,"思索当前页面");SystemClock.sleep(800);
                check(js(browser,"!document.querySelector('[data-testid=ponder-onboarding]')").equals("true"),"Original onboarding button did not respond");
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(700);
            }
            // Real pointer delivery must survive both browser zoom and inert host overlays.
            runOnMainSync(()->{try{
                ((android.view.View)field(activity,"channelBar")).setVisibility(android.view.View.GONE);
                ((android.view.View)field(activity,"channelListPanel")).setVisibility(android.view.View.GONE);
                ((android.view.View)field(activity,"managementPanel")).setVisibility(android.view.View.GONE);
            }catch(Exception e){throw new RuntimeException(e);}});
            for(float zoom:new float[]{1f,1.5f}) {
                runOnMainSync(()->runtime.configure("1080p",zoom));SystemClock.sleep(600);
                js(browser,"window.__trustedClicks=0;var b=document.createElement('button');b.id='ntv-click-fixture';b.innerHTML='<span>NativeTouchFixture</span>';b.style.cssText='position:fixed;left:100px;top:100px;width:240px;height:80px;z-index:2147483647';b.onclick=e=>{if(e.isTrusted)window.__trustedClicks++};document.body.appendChild(b)");
                tapText(browser,"NativeTouchFixture");SystemClock.sleep(300);
                check(js(browser,"window.__trustedClicks").equals("1"),"Touch swallowed at zoom "+zoom);
                // Root coordinates are also used by the phone fly mouse transport.
                String point=js(browser,"(function(){var r=document.getElementById('ntv-click-fixture').getBoundingClientRect();return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerWidth]})()");
                JSONArray xy=new JSONArray(point);android.view.View root=(android.view.View)field(activity,"root");
                float x=(float)xy.getDouble(0)*browser.getWidth()+browser.getLeft(),y=(float)xy.getDouble(1)*browser.getWidth()+browser.getTop();
                runOnMainSync(()->{long t=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(t,t,0,x,y,0),u=android.view.MotionEvent.obtain(t,t+60,1,x,y,0);runtime.dispatchPointer(d,true);runtime.dispatchPointer(u,true);d.recycle();u.recycle();});SystemClock.sleep(300);
                check(js(browser,"window.__trustedClicks").equals("2"),"Fly mouse click swallowed at zoom "+zoom);
                runOnMainSync(()->{try {
                    Field enabled=MainActivity.class.getDeclaredField("flyMouseEnabled");enabled.setAccessible(true);enabled.setBoolean(activity,true);
                    Method show=MainActivity.class.getDeclaredMethod("applyFlyMouseVisibility");show.setAccessible(true);show.invoke(activity);
                }catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);
                runOnMainSync(()->{try {
                    FlyMouseCursorView cursor=(FlyMouseCursorView)field(activity,"flyMouseCursor");cursor.moveBy(x-cursor.cursorX(),y-cursor.cursorY());
                    Method click=MainActivity.class.getDeclaredMethod("dispatchFlyMouseClick");click.setAccessible(true);click.invoke(activity);
                }catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(300);
                check(js(browser,"window.__trustedClicks").equals("3"),"Actual fly mouse transport failed at zoom "+zoom+" count="+js(browser,"window.__trustedClicks")+" point="+point+" size="+browser.getWidth()+"x"+browser.getHeight());
                js(browser,"document.getElementById('ntv-click-fixture').remove()");
            }
            // Reproduce the reported dark Settings sidebar: legacy targetSdk disables hex-alpha CSS.
            js(browser,"var settingsButton=Array.from(document.querySelectorAll('button')).find(b=>b.getBoundingClientRect().width>0&&((b.getAttribute('aria-label')||b.title||'').startsWith('设置')||b.querySelector('svg.lucide-settings')));if(settingsButton)settingsButton.click();");SystemClock.sleep(900);
            js(browser,"var help=Array.from(document.querySelectorAll('.st-window aside button')).find(b=>b.textContent.trim()==='帮助');if(help)help.click();");SystemClock.sleep(800);
            String colors=js(browser,"JSON.stringify(Array.from(document.querySelectorAll('.st-window aside button,.st-window aside input')).filter(e=>e.getBoundingClientRect().width>0).map(e=>({text:e.textContent.trim(),bg:getComputedStyle(e).backgroundColor,color:getComputedStyle(e).color})))");
            check(!colors.equals("\"[]\"")&&!colors.equals("[]"),"Settings sidebar was not opened: "+js(browser,"JSON.stringify(Array.from(document.querySelectorAll('button')).map(b=>({label:b.getAttribute('aria-label'),title:b.title,text:b.textContent.slice(0,20)})))"));
            check(!colors.contains("rgb(239, 239, 239)")&&!colors.contains("rgb(255, 255, 255)"),"Native white control backgrounds returned: "+colors);
            result.putString("sidebarColors",colors);
            check(js(browser,"typeof Promise.withResolvers==='function'&&typeof Object.groupBy==='function'").equals("true"),"Compatibility polyfills missing");
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"folia-full.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(400);
            // Browser history must not trap the user: two BACK presses always close the plugin.
            js(browser,"history.pushState({},'', '/?test-history=1')");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(150);
            check(runtime.visible(),"First BACK closed the plugin");
            check(!activity.isFinishing(),"First BACK exited the host");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(100);
            check(runtime.visible(),"Cancel did not reset close confirmation");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);
            check(!runtime.visible(),"Second BACK did not close the plugin");
            check(!activity.isFinishing(),"Second BACK exited the host instead of returning home");
            check(((android.view.View)field(activity,"channelListPanel")).getVisibility()==android.view.View.VISIBLE,"Channel home not shown");
            result.putString("stream","PASS: official source frontend mounted; real touch and fly mouse clicks at 100%/150%; original navigation; dark Settings controls and polyfills; double BACK closes plugin and returns home");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(wake.isHeld())wake.release();}finish(code,result);
    }
}
