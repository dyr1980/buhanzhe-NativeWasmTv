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
        String result=js(web,"(function(){var icon=document.querySelector('button svg.lucide-play');var e=document.querySelector('button[title=\"播放\"]')||(icon&&icon.closest('button'));if(!e)return null;var r=e.getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})()");
        if("null".equals(result))return;JSONArray p=new JSONArray(result);int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));float scale=web.getWidth()/(float)p.getDouble(2),x=location[0]+(float)p.getDouble(0)*scale,y=location[1]+(float)p.getDouble(1)*scale;
        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,1,x,y,0);sendPointerSync(down);SystemClock.sleep(60);sendPointerSync(up);down.recycle();up.recycle();
    }
    private void capture(WebView web,String name)throws Exception {
        SystemClock.sleep(700);
        android.graphics.Bitmap screen=getUiAutomation().takeScreenshot();
        if(screen!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}
        runOnMainSync(()->{try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+"-views.txt"))){StringBuilder tree=new StringBuilder();dumpView(activity.getWindow().getDecorView(),tree,0);out.write(tree.toString().getBytes("UTF-8"));}catch(IOException e){throw new RuntimeException(e);}});
        if("true".equals(args.getString("drawView")))runOnMainSync(()->{android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(web.getWidth(),web.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+"-view.png"))){web.draw(new android.graphics.Canvas(bitmap));bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{bitmap.recycle();}});
    }
    private void dumpView(android.view.View view,StringBuilder text,int depth){
        if(depth>5)return;
        for(int i=0;i<depth;i++)text.append(' ');
        text.append(view.getClass().getSimpleName()).append(" id=").append(view.getId()).append(" vis=").append(view.getVisibility()).append(" alpha=").append(view.getAlpha()).append(" size=").append(view.getWidth()).append('x').append(view.getHeight()).append(" pos=").append(view.getX()).append(',').append(view.getY()).append(" elevation=").append(view.getElevation()).append(" background=").append(view.getBackground()).append(" layer=").append(view.getLayerType()).append(" hardware=").append(view.isHardwareAccelerated()).append('\n');
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)dumpView(group.getChildAt(i),text,depth+1);}
    }
    private String verifyHomeLayout(WebView web)throws Exception {
        String state=js(web,"(function(){var canvases=Array.from(document.querySelectorAll('[data-paper-shader] canvas')).map(function(c){var a=c.getBoundingClientRect(),b=c.parentElement.getBoundingClientRect();return {width:a.width,height:a.height,parentWidth:b.width,parentHeight:b.height,ok:Math.abs(a.width-b.width)<2&&Math.abs(a.height-b.height)<2};});var slider=document.querySelector('[data-grid3d-slider]'),r=slider&&slider.getBoundingClientRect(),cards=slider?Array.from(slider.querySelectorAll('[data-grid3d-index]')):[],distance=cards.length?Math.min.apply(Math,cards.map(function(c){var a=c.getBoundingClientRect();return Math.abs(a.left+a.width/2-r.left-r.width/2);})):0;var covers=Array.from(document.querySelectorAll('.theme-polaroid-card>.aspect-square')).map(function(c){return {width:c.offsetWidth,height:c.offsetHeight,ok:Math.abs(c.offsetWidth-c.offsetHeight)<2};});return {viewport:[innerWidth,innerHeight],canvases:canvases,covers:covers,centerDistance:distance,centered:distance<3};})()");
        JSONObject metrics=new JSONObject(state);
        for(String key:new String[]{"canvases","covers"}){JSONArray values=metrics.getJSONArray(key);for(int i=0;i<values.length();i++)check(values.getJSONObject(i).getBoolean("ok"),"Home "+key+" size mismatch: "+state);}
        check(metrics.getBoolean("centered"),"Home carousel did not center its focused card: "+state);
        Bundle evidence=new Bundle();evidence.putString("stream","\nHome layout: "+state+"\n");sendStatus(0,evidence);
        return state;
    }
    private void tapNativeText(String text)throws Exception {
        for(int attempt=0;attempt<8;attempt++){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null){
                for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
                    if(node.isVisibleToUser()&&text.contentEquals(node.getText()==null?"":node.getText())){
                        android.view.accessibility.AccessibilityNodeInfo target=node;
                        while(target!=null&&!target.isClickable())target=target.getParent();
                        if(target!=null&&target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(200);return;}

                    }
                }
                java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo> queue=new java.util.ArrayDeque<>();queue.add(root);
                while(!queue.isEmpty()){android.view.accessibility.AccessibilityNodeInfo node=queue.remove();if(node.isScrollable()){node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);break;}for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}}
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("Native chooser control missing: "+text);
    }
    private boolean nativeText(String text){
        long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return true;SystemClock.sleep(100);}return false;
    }
    private void mediaAndFiles(WebView browser)throws Exception {
        java.nio.ByteBuffer wav=java.nio.ByteBuffer.allocate(44+8000*2*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes("US-ASCII")).putInt(wav.capacity()-8).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(wav.capacity()-44);
        byte[] audio=wav.array();java.net.ServerSocket server=new java.net.ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));
        Thread fixture=new Thread(()->{while(!server.isClosed())try(java.net.Socket socket=server.accept()){
            java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),"US-ASCII"));String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            java.io.OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: "+audio.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(audio);out.flush();
        }catch(Exception ignored){}},"folia-audio-fixture");fixture.setDaemon(true);fixture.start();
        try {
            String url="http://127.0.0.1:"+server.getLocalPort()+"/fixture.wav";
            js(browser,"window.__mediaResult='pending';window.__mediaFixture=new Audio();__mediaFixture.setAttribute('src',"+JSONObject.quote(url)+");document.body.appendChild(__mediaFixture);if(__mediaFixture.getAttribute('src')!=="+JSONObject.quote(url)+")window.__mediaResult='source-mismatch';else __mediaFixture.play().then(()=>{window.__mediaResult='playing'}).catch(e=>window.__mediaResult=String(e))");
            long until=SystemClock.elapsedRealtime()+12000;while(js(browser,"window.__mediaResult").contains("pending")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            check(js(browser,"window.__mediaResult").contains("playing"),"Autoplay failed: "+js(browser,"window.__mediaResult"));SystemClock.sleep(500);
            check(js(browser,"__mediaFixture.currentTime>0&&!__mediaFixture.paused").equals("true"),"Audio time did not advance");
            js(browser,"__mediaFixture.pause();__mediaFixture.remove()");
        } finally {server.close();}
        File directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),"000-ntv-folia-fixture");check(!directory.exists()||directory.isDirectory(),"Invalid test fixture path");directory.mkdirs();
        File first=new File(directory,"NativeSelected.wav"),second=new File(directory,"NextSelected.wav"),lyric=new File(directory,"NativeSelected.lrc");
        for(File file:new File[]{first,second})try(FileOutputStream out=new FileOutputStream(file)){out.write(audio);}
        try(FileOutputStream out=new FileOutputStream(lyric)){out.write("[00:00.00]Native selection fixture".getBytes("UTF-8"));}
        File nested=new File(directory,"NestedFixture");nested.mkdirs();File excluded=new File(nested,"Excluded.wav");try(FileOutputStream out=new FileOutputStream(excluded)){out.write(audio);}
        js(browser,"window.__pickResult='pending';var b=document.createElement('button');b.id='ntv-file-pick';b.innerHTML='<span>NativeFileSelectionFixture</span>';b.style.cssText='position:fixed;left:100px;top:100px;width:300px;height:80px;z-index:2147483647';b.onclick=()=>{window.__pickResult='pending';window.showDirectoryPicker().then(d=>{window.__pickedDirectory=d;window.__pickResult='selected'}).catch(e=>window.__pickResult=e.name)};document.body.appendChild(b)");
        tapText(browser,"NativeFileSelectionFixture");SystemClock.sleep(500);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);
        check(js(browser,"window.__pickResult").contains("AbortError"),"Cancel did not settle picker: "+js(browser,"window.__pickResult"));
        js(browser,"window.__pickResult='pending'");tapText(browser,"NativeFileSelectionFixture");SystemClock.sleep(400);
        {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(300);
            check(nativeText("3 个文件 · 2 首音乐 · 可导入 3 个文件"),"Folder counts differ");
            check(nativeText("1 个文件 · 1 首音乐"),"Child folder counts differ");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(200);
            check(nativeText("1 个文件 · 1 首音乐 · 可导入 1 个文件"),"Remote OK did not enter child folder");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);
            check(nativeText("3 个文件 · 2 首音乐 · 可导入 3 个文件"),"Remote BACK did not return to parent folder");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(100);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(100);
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
            if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"folia-native-picker.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
        }
        long until=SystemClock.elapsedRealtime()+10000;
        while(js(browser,"window.__pickResult").contains("pending")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(js(browser,"window.__pickResult").contains("selected"),"Selection failed: "+js(browser,"window.__pickResult"));
        js(browser,"window.__persist='pending';(async()=>{var files=[];for(var h of await Array.fromAsync(__pickedDirectory.values()))files.push([h.name,(await h.getFile()).size]);window.__chosenFiles=JSON.stringify(files);var r=indexedDB.open('ntv-file-test',1);r.onupgradeneeded=()=>r.result.createObjectStore('handles');r.onsuccess=()=>{var db=r.result,t=db.transaction('handles','readwrite');t.objectStore('handles').put(__pickedDirectory,'selected');t.oncomplete=()=>{db.close();window.__persist='saved'};t.onerror=()=>window.__persist=String(t.error)};})().catch(e=>window.__persist=String(e))");
        until=SystemClock.elapsedRealtime()+8000;while(js(browser,"window.__persist").contains("pending")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(js(browser,"window.__persist").contains("saved"),"Handles were not persisted: "+js(browser,"window.__persist"));
        check(js(browser,"window.__chosenFiles").contains("NativeSelected.lrc"),"Lyrics file was filtered out");
        js(browser,"window.__testReloadSentinel='old'");runOnMainSync(browser::reload);until=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<until){if(js(browser,"!!window.__NTV_FOLIA_FILES__&&typeof Array.fromAsync==='function'&&typeof window.__testReloadSentinel==='undefined'").equals("true"))break;SystemClock.sleep(100);}
        js(browser,"window.__restored='pending';var r=indexedDB.open('ntv-file-test',1);r.onsuccess=()=>{var db=r.result,g=db.transaction('handles').objectStore('handles').get('selected');g.onsuccess=async()=>{try{var d=g.result,names=[];window.__restoredDirectory=d;for(var h of await Array.fromAsync(d.values())){var f=await h.getFile();names.push(f.name+':'+f.size);if(f.name.endsWith('.lrc'))window.__restoredLyric=await f.text()}window.__restored=JSON.stringify(names);db.close()}catch(e){window.__restored=String(e)}}}");
        until=SystemClock.elapsedRealtime()+8000;while(js(browser,"window.__restored").contains("pending")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(js(browser,"window.__restored").contains("NativeSelected.wav"),"Persisted file handles failed after reload: "+js(browser,"window.__restored"));
        check(js(browser,"window.__restoredLyric").contains("Native selection fixture"),"Persisted lyric bytes differ");
        try(InputStream in=getContext().getAssets().open("folia-native-import.js")){js(browser,new String(RadioUiCatalog.read(in,32768),"UTF-8"));}
        until=SystemClock.elapsedRealtime()+25000;while(js(browser,"window.__upstreamImport").contains("pending")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(150);
        check(js(browser,"window.__upstreamImport.startsWith('{')&&JSON.parse(window.__upstreamImport).count===2&&JSON.parse(window.__upstreamImport).localPlayback===true").equals("true"),"Original Folia importer or local playback failed: "+js(browser,"window.__upstreamImport"));
        Bundle evidence=new Bundle();evidence.putString("stream","Native folder evidence: counts=3 files / 2 songs; child=1 file / 1 song; nested audio excluded; "+js(browser,"window.__upstreamImport"));sendStatus(0,evidence);
        js(browser,"indexedDB.deleteDatabase('ntv-file-test')");
        for(File file:new File[]{first,second,lyric,excluded})file.delete();nested.delete();directory.delete();
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
            while(SystemClock.elapsedRealtime()<until){web=(WebView)field(runtime,"browser");if(web!=null){check(js(web,"typeof window.__NTV_FOLIA_STARTUP_ERROR__==='undefined'").equals("true"),"Frontend startup error: "+js(web,"window.__NTV_FOLIA_STARTUP_ERROR__"));if(js(web,"!!document.querySelector('#root')&&document.querySelector('#root').innerText.length>30&&!!getComputedStyle(document.documentElement).getPropertyValue('--spacing').trim()").equals("true"))break;}SystemClock.sleep(250);}
            check(web!=null,"No full application WebView");final WebView browser=web;
            Bundle engineEvidence=new Bundle();engineEvidence.putString("stream","\nActual WebView UA: "+js(browser,"navigator.userAgent")+"\n");sendStatus(0,engineEvidence);
            check(js(browser,"location.origin").contains("127.0.0.1"),"Frontend loaded from remote website");
            check(js(browser,"!!window.__NTV_FOLIA_FULL__").equals("true"),"Java transport bridge missing");
            String spacing=js(browser,"getComputedStyle(document.documentElement).getPropertyValue('--spacing').trim()");
            check(!"\"\"".equals(spacing),"Tailwind root tokens were discarded by the old CSS parser: "+spacing);
            mediaAndFiles(browser);
            long remount=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<remount&&!js(browser,"document.querySelector('#root')&&document.querySelector('#root').innerText.length>30").equals("true"))SystemClock.sleep(100);
            String body=js(browser,"document.body.innerText");check(body.length()>30,"Upstream app did not mount: "+body);

            SystemClock.sleep(1500);
            String closePoint=js(browser,"(function(){var e=document.querySelector('[data-testid=release-notes-close]');if(!e)return null;var r=e.getBoundingClientRect();var hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return JSON.stringify({r:[r.x,r.y,r.width,r.height],inner:[innerWidth,innerHeight],hit:hit&&hit.outerHTML})})()");
            tapText(browser,"关闭");SystemClock.sleep(1000);
            check(!js(browser,"document.body.innerText").contains("Sabbat-of-the-witch Update"),"Original release dialog close button did not respond: "+closePoint+" web="+browser.getWidth()+"x"+browser.getHeight());
            if(js(browser,"!!document.querySelector('[data-testid=ponder-onboarding]')").equals("true")) {
                tapText(browser,"思索当前页面");SystemClock.sleep(800);
                check(js(browser,"!document.querySelector('[data-testid=ponder-onboarding]')").equals("true"),"Original onboarding button did not respond");
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(700);
            }
            // Finish both original first-run choice pages using their real pointer targets.
            for(int step=0;step<3;step++) {
                String label=js(browser,"(function(){var e=document.querySelector('[data-testid=playback-entry-view-prompt-next],[data-testid=playback-entry-view-prompt-confirm]');return e?e.textContent.trim():null})()");
                if("null".equals(label))break;
                tapText(browser,new org.json.JSONTokener(label).nextValue().toString());SystemClock.sleep(700);
            }
            check(js(browser,"!document.querySelector('[data-testid=playback-entry-view-prompt]')").equals("true"),"First-run library choice did not respond");
            // Slower engines can mount the pondering prompt only AFTER the library-choice prompt.
            // Wait through both real prompt sequences before claiming that we exercised the home.
            for(int step=0;step<12;step++) {
                SystemClock.sleep(500);
                if(js(browser,"!!document.querySelector('[data-testid=ponder-onboarding]')").equals("true")) {
                    tapText(browser,"思索当前页面");SystemClock.sleep(800);
                    check(js(browser,"!document.querySelector('[data-testid=ponder-onboarding]')").equals("true"),"Late pondering prompt did not respond");
                    sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(500);
                }
                String label=js(browser,"(function(){var e=document.querySelector('[data-testid=playback-entry-view-prompt-next],[data-testid=playback-entry-view-prompt-confirm]');return e?e.textContent.trim():null})()");
                if(!"null".equals(label))tapText(browser,new org.json.JSONTokener(label).nextValue().toString());
            }
            if(js(browser,"!CSS.supports('container-type','inline-size')||!CSS.supports('color','color-mix(in srgb,red,blue)')").equals("true")) {
                check(js(browser,"!document.querySelector('.bravais-root')").equals("true"),"Unsupported Bravais home remained active");
                check(js(browser,"(function(){var h=document.querySelector('h1');return !!h&&h.parentElement.parentElement.getBoundingClientRect().width>innerWidth*.8})()").equals("true"),"Classic home header collapsed to a narrow column");
                tapText(browser,"本地");
                until=SystemClock.elapsedRealtime()+10000;
                while(SystemClock.elapsedRealtime()<until&&!js(browser,"!!document.querySelector('[data-grid3d-slider]')").equals("true"))SystemClock.sleep(150);
                check(js(browser,"!!document.querySelector('[data-grid3d-slider]')&&!!document.querySelector('.theme-polaroid-card>.aspect-square')").equals("true"),"Local home carousel was not visible after first-run prompts");
            }
            capture(browser,"folia-home");
            result.putString("homeLayout",verifyHomeLayout(browser));
            if("true".equals(args.getString("holdScreenshots"))){Bundle ready=new Bundle();ready.putString("stream","SCREENSHOT_READY_HOME");sendStatus(0,ready);SystemClock.sleep(12000);}
            if(js(browser,"!!document.querySelector('[data-grid3d-slider]')").equals("true")) {
                tapText(browser,"全部歌曲");
                until=SystemClock.elapsedRealtime()+10000;
                while(SystemClock.elapsedRealtime()<until&&!js(browser,"!!document.querySelector('[data-library-surface=collection]')").equals("true"))SystemClock.sleep(100);
                check(js(browser,"!!document.querySelector('[data-library-surface=collection]')").equals("true"),"Home card did not open its collection");
                SystemClock.sleep(1500); // Wait for the original collection's enter transition before Escape.
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);
                until=SystemClock.elapsedRealtime()+10000;
                while(SystemClock.elapsedRealtime()<until&&!js(browser,"!document.querySelector('[data-library-surface=collection]')").equals("true"))SystemClock.sleep(100);
                check(js(browser,"!document.querySelector('[data-library-surface=collection]')").equals("true"),"Collection did not return to home");
            }
            // Real pointer delivery must survive both browser zoom and inert host overlays.
            runOnMainSync(()->{try{
                ((android.view.View)field(activity,"channelBar")).setVisibility(android.view.View.GONE);
                ((android.view.View)field(activity,"channelListPanel")).setVisibility(android.view.View.GONE);
                ((android.view.View)field(activity,"managementPanel")).setVisibility(android.view.View.GONE);
            }catch(Exception e){throw new RuntimeException(e);}});
            for(float zoom:new float[]{1f,1.5f}) {
                runOnMainSync(()->runtime.configure("1080p",zoom));SystemClock.sleep(600);
                verifyHomeLayout(browser);
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
            result.putString("pageGeometry",js(browser,"JSON.stringify({viewport:[innerWidth,innerHeight],root:document.querySelector('#root').getBoundingClientRect().toJSON(),bodyOpacity:getComputedStyle(document.body).opacity,windowRect:document.querySelector('.st-window').getBoundingClientRect().toJSON()})"));
            check(js(browser,"typeof Promise.withResolvers==='function'&&typeof Object.groupBy==='function'").equals("true"),"Compatibility polyfills missing");
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"folia-full.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            capture(browser,"folia-help");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(400);
            // Consume each real WebView history entry before arming the root-page exit prompt.
            String originalLocation=js(browser,"location.href");
            js(browser,"history.pushState({},'', '/?test-history=1');history.pushState({},'', '/?test-history=2')");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
            check(js(browser,"location.search").contains("test-history=1"),"BACK did not pop the newest browser history entry");
            check(runtime.visible()&&((Long)field(activity,"lastJarBackPressedAt"))==0L,"History BACK armed plugin exit");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
            check(js(browser,"location.href").equals(originalLocation),"Second history BACK did not restore the original page");
            check(runtime.visible()&&((Long)field(activity,"lastJarBackPressedAt"))==0L,"History navigation closed the plugin or armed exit");
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
            result.putString("stream","PASS: official source frontend mounted; real touch and fly mouse clicks at 100%/150%; original navigation; dark Settings controls and polyfills; HTTP autoplay; native folder selection/cancel/counts/persist, nested audio excluded, upstream WAV+LRC import and local playback; double BACK closes plugin and returns home");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());if(wake.isHeld())wake.release();}finish(code,result);
    }
}
