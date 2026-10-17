package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.*;

/** Exercises the bundled page in the device's real WebView against the real control server. */
final class ManagementChannelChecks {
    private final Instrumentation test;
    private ManagementActivity activity;
    private WebView web;
    private boolean before;
    private volatile boolean failNextChannelRead;
    private ManagementChannelChecks(Instrumentation test) { this.test=test; }
    private void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private String js(String script) throws Exception {
        CountDownLatch latch=new CountDownLatch(1); String[] result={null};
        test.runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));
        check(latch.await(8,TimeUnit.SECONDS),"JavaScript timeout");return result[0];
    }
    private void await(String script) throws Exception {
        long limit=SystemClock.elapsedRealtime()+15000;
        while(!"true".equals(js(script))) {
            check(SystemClock.elapsedRealtime()<limit,"Not ready: "+script+" state="+js("JSON.stringify({url:location.href,groups:typeof playlistGroups==='undefined'?-1:playlistGroups.length,text:document.body.innerText.slice(0,300)})"));
            SystemClock.sleep(60);
        }
    }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(300);
        File dir=new File(test.getTargetContext().getExternalFilesDir(null),"channel-review");dir.mkdirs();
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();check(bitmap!=null,"No screenshot");
        try(FileOutputStream output=new FileOutputStream(new File(dir,name+".png"))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }
    private String scrollSample() throws Exception { return scrollSample(false); }
    private String scrollSample(boolean list) throws Exception {
        js("window.scrollProbe=null;" + (list ? "channelEditor.viewport.scrollTop=0;" : "window.scrollTo(0,0);") + "(function(){var samples=[],last=0,n=0,start=0;function tick(t){if(!start)start=t;if(last)samples.push(t-last);last=t;" + (list ? "channelEditor.viewport.scrollTop=(++n)*18;" : "window.scrollTo(0,(++n)*18);") + "if(t-start<3000)requestAnimationFrame(tick);else{samples.sort(function(a,b){return a-b;});window.scrollProbe={frames:samples.length,fps:samples.length*1000/(t-start),median:samples[Math.floor(samples.length/2)],p95:samples[Math.floor(samples.length*.95)],over25:samples.filter(function(v){return v>25;}).length};}}requestAnimationFrame(tick);})()");
        await("!!window.scrollProbe");return js("JSON.stringify(window.scrollProbe)");
    }
    private void pointer(int action,float x,float y,long down) {
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        check(test.getUiAutomation().injectInputEvent(event,true),"Touch injection rejected");event.recycle();
    }
    private void dragToEdge() throws Exception {
        js("window.channelTouchLog=[];['pointerdown','touchstart','mousedown','pointercancel','pointerup'].forEach(function(type){document.addEventListener(type,function(e){channelTouchLog.push([type,e.clientX,e.clientY,e.target.className]);},true);})");
        JSONArray coords=new JSONArray(js("JSON.stringify((function(){var h=document.querySelector('.channel-drag-handle').getBoundingClientRect(),v=channelEditor.viewport.getBoundingClientRect();return [h.left+h.width/2,h.top+h.height/2,v.bottom-10,window.innerWidth];})())").replaceAll("^\"|\"$", ""));
        // JSON.stringify's numeric array is returned as a quoted string by evaluateJavascript.
        int[] location=new int[2];test.runOnMainSync(()->web.getLocationOnScreen(location));
        float scale=web.getWidth()/(float)coords.getDouble(3),x=location[0]+(float)coords.getDouble(0)*scale,
                y=location[1]+(float)coords.getDouble(1)*scale,end=location[1]+(float)coords.getDouble(2)*scale;
        long down=SystemClock.uptimeMillis();pointer(MotionEvent.ACTION_DOWN,x,y,down);
        SystemClock.sleep(100);
        check("true".equals(js("!!channelEditor.drag")),"Drag did not begin at "+x+","+y+" coords="+coords+" location="+location[0]+","+location[1]+" events="+js("JSON.stringify(channelTouchLog)"));
        for(int i=1;i<=16;i++){pointer(MotionEvent.ACTION_MOVE,x,y+(end-y)*i/16,down);SystemClock.sleep(18);}
        SystemClock.sleep(700);pointer(MotionEvent.ACTION_UP,x,end,down);
    }
    static String run(Instrumentation test,Context isolated,PlaylistManager manager) throws Exception {
        return new ManagementChannelChecks(test).runChecks(isolated,manager);
    }
    private String runChecks(Context isolated,PlaylistManager manager) throws Exception {
        // One 10,000-channel merged group plus 200 small groups to stress both scroll surfaces.
        StringBuilder text=new StringBuilder("#EXTM3U\n");
        for(int i=0;i<10000;i++) text.append("#EXTINF:-1 group-title=\"测试分组 ").append(1)
                .append("\",测试频道 ").append(i+1).append("\nhttp://example.test/").append(i).append('\n');
        for(int i=2;i<=201;i++) text.append("#EXTINF:-1 group-title=\"测试分组 ").append(i)
                .append("\",分组频道 ").append(i).append("\nhttp://example.test/group/").append(i).append('\n');
        PlaylistManager.ImportedFile file=manager.importLocalPlaylist("scroll","scroll.m3u",text.toString().getBytes("UTF-8"));
        manager.updateSources(new JSONArray().put(new JSONObject().put("id","scroll").put("name","频道测试")
                .put("location",file.location).put("enabled",true)));
        LocalControlServer.Listener listener=(LocalControlServer.Listener)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{LocalControlServer.Listener.class},(proxy,method,args)->{
            if(method.getName().equals("stateJson"))return new JSONObject().put("settings",new JSONObject()
                    .put("playlistGroups",manager.getGroupSettingsJson())).toString();
            if(method.getName().equals("playlistChannels")) {
                if(failNextChannelRead){failNextChannelRead=false;throw new IOException("test sync failure");}
                return manager.getChannelSettingsJson((String)args[0]).toString();
            }
            if(method.getName().equals("settings")) {
                JSONObject body=(JSONObject)args[0];
                if(body.has("playlistChannelSettings"))manager.updateChannelSettings(body.getJSONObject("playlistChannelSettings"));
                if(body.has("playlistGroupStates"))manager.updateGroupStates(body.getJSONArray("playlistGroupStates"));
                return "{\"ok\":true}";
            }
            if(method.getName().equals("page")) {
                String path=(String)args[0];if(!path.startsWith("/"))path="/"+path;
                String asset=ControlSite.assetPath(path);
                InputStream input;
                try {input=before?test.getContext().getAssets().open("management-before/"+asset):test.getTargetContext().getAssets().open(asset);}
                catch(IOException missing){input=test.getTargetContext().getAssets().open(asset);}
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int size;
                try {while((size=input.read(buffer))!=-1)out.write(buffer,0,size);}finally{input.close();}
                return new LocalControlServer.Resource(ControlSite.contentType(path),out.toByteArray());
            }
            return null;
        });
        LocalControlServer server=new LocalControlServer(listener);server.start();
        String metrics;
        try {
            try(InputStream baseline=test.getContext().getAssets().open("management-before/control/pages/groups.html")) { before=true; }
            catch(IOException absent) { before=false; }
            activity=(ManagementActivity)test.startActivitySync(new Intent(test.getTargetContext(),ManagementActivity.class)
                    .putExtra(ManagementActivity.EXTRA_URL,server.getLoopbackUrl().replace("/index.html","/pages/groups.html"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field field=ManagementActivity.class.getDeclaredField("webView");field.setAccessible(true);web=(WebView)field.get(activity);
            if(before) {
                test.runOnMainSync(()->{web.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);web.clearCache(true);
                    web.loadUrl(server.getLoopbackUrl().replace("/index.html","/pages/groups.html?baseline=1"));});
                await("typeof playlistGroups!=='undefined'&&playlistGroups.length>=201&&typeof openGroupChannels==='function'");
            } else await("typeof playlistGroups!=='undefined'&&playlistGroups.length>=201");
            if(before) {screenshot("groups-before-merged-android71");metrics="before="+scrollSample();}
            else metrics="baseline assets not packaged;";
            before=false;
            js("window.beforeChannelReload=true");
            test.runOnMainSync(()->{web.setLayerType(android.view.View.LAYER_TYPE_NONE,null);web.clearCache(true);web.reload();});
            await("typeof window.beforeChannelReload==='undefined'&&typeof openGroupChannels==='function'&&playlistGroups.length>=201");
            js("window.scrollTo(0,0)");screenshot("groups-after-fullscreen-android71");metrics+=" after="+scrollSample();
            check("true".equals(js("(function(){var n=document.getElementById('playlistGroupSettings').firstChild;renderPageState();return n===document.getElementById('playlistGroupSettings').firstChild;})()")),"unchanged state rebuilt DOM");
            js("(function(){var buttons=document.querySelectorAll('.group-manage');for(var i=0;i<buttons.length;i++)if(buttons[i]._group.id==='group:测试分组 1'){buttons[i].scrollIntoView();buttons[i].click();break;}})()");
            await("channelEditor&&channelEditor.channels.length===10000&&!!channelEditor.viewport");
            check("true".equals(js("channelEditor.space.children.length<=Math.ceil(channelEditor.viewport.clientHeight/60)+6&&document.body.scrollWidth<=window.innerWidth")),"unbounded rows or horizontal overflow");
            js("window.retainedChannelRow=channelEditor.space.children[5];channelEditor.viewport.scrollTop=240");
            await("channelEditor.first===1");
            check("true".equals(js("channelEditor.space.children[4]===window.retainedChannelRow&&window.retainedChannelRow._index===5")),"scroll rebuilt an unchanged visible row");
            js("channelEditor.viewport.scrollTop=0;renderChannelRows(true)");
            js("window.scrollBy(0,channelEditor.button.getBoundingClientRect().top-60)");
            screenshot("channels-inline-compact-android71");
            metrics+=" inlineList="+scrollSample(true);
            js("channelEditor.viewport.scrollTop=0;renderChannelRows(true);window.inlineEditorHeight=channelEditor.viewport.clientHeight;channelEditor.fullButton.click()");
            await("channelEditor.fullscreen&&channelEditor.viewport.clientHeight>window.innerHeight*.75");
            check("true".equals(js("channelEditor.panel.parentNode===document.body&&channelEditor.panel.getAttribute('aria-modal')==='true'&&channelEditor.hint.hidden&&document.getElementById('groupManagerPage').getAttribute('aria-hidden')==='true'")),"fullscreen did not expose a single clean dialog");
            screenshot("channels-fullscreen-android71");
            js("moveGroupChannel(0,1);channelEditor.viewport.scrollTop=240;window.beforeFullscreenBack=channelEditor.channels[0].id");
            test.runOnMainSync(()->activity.onBackPressed());
            await("!channelEditor.fullscreen&&!history.state.ntvOverlay");
            check("true".equals(js("channelEditor.dirty&&channelEditor.channels[0].id===window.beforeFullscreenBack&&channelEditor.viewport.scrollTop===240&&document.body.className.indexOf('channel-editor-open')<0")),"APP Back lost edits/scroll or kept scroll lock");
            js("channelEditor.fullButton.click()");await("channelEditor.fullscreen&&!!history.state.ntvOverlay");
            js("history.back()");await("!channelEditor.fullscreen&&!history.state.ntvOverlay");
            check("true".equals(js("channelEditor.dirty&&channelEditor.channels[0].id===window.beforeFullscreenBack")),"browser Back discarded fullscreen edits");
            js("channelEditor.fullButton.click()");await("channelEditor.fullscreen&&!!history.state.ntvOverlay");
            metrics+=" fullscreenList="+scrollSample(true);
            js("channelEditor.viewport.scrollTop=0;renderChannelRows(true);window.dragFirstBefore=channelEditor.channels[0].id");
            dragToEdge();
            await("channelEditor.dirty&&channelEditor.viewport.scrollTop>100&&channelEditor.channels[0].id!==window.dragFirstBefore");
            js("channelEditor.viewport.scrollTop=0;renderChannelRows(true);window.savedFirstId=channelEditor.channels[0].id;channelEditor.space.children[0].children[3].click()");
            screenshot("channels-fullscreen-dirty-android71");
            failNextChannelRead=true;
            js("window.savedFullscreenPanel=channelEditor.panel;saveGroupChannels()");
            await("channelEditor.needsReload&&!channelEditor.saving");
            check("true".equals(js("channelEditor.fullscreen&&!channelEditor.dirty&&!channelEditor.save.disabled&&channelEditor.space.children[0].children[3].disabled")),"failed sync unlocked stale channel data");
            js("channelEditor.save.click()");await("!channelEditor.needsReload&&!channelEditor.saving");
            check("true".equals(js("channelEditor.fullscreen&&channelEditor.panel===window.savedFullscreenPanel&&channelEditor.viewport.scrollTop===0")),"save/retry exited fullscreen or rebuilt the editor");
            check("true".equals(js("channelEditor.channels[0].enabled===false&&channelEditor.channels[0].id===window.savedFirstId")),"saved order/hidden not reloaded");
            JSONObject saved=manager.getChannelSettingsJson("group:测试分组 1");
            check(!saved.getJSONArray("channels").getJSONObject(0).getBoolean("enabled"),"actual server did not persist visibility");
            PlaylistManager cold=new PlaylistManager(isolated);cold.loadCached();
            check(saved.toString().equals(cold.getChannelSettingsJson("group:测试分组 1").toString()),"cold startup lost channel settings");
            // A large scroll jump must recycle the bounded row window.
            js("channelEditor.viewport.scrollTop=300000");SystemClock.sleep(150);
            check("true".equals(js("channelEditor.space.children.length<=Math.ceil(channelEditor.viewport.clientHeight/60)+6&&channelEditor.space.children[0]._index>4900")),"virtual list did not recycle");
            js("channelEditor.viewport.scrollTop=0;window.PointerEvent=undefined;renderChannelRows(true);window.fallbackFirst=channelEditor.channels[0].id");
            dragToEdge();await("channelEditor.dirty&&channelEditor.channels[0].id!==window.fallbackFirst");
            js("channelEditor.cancel.click();document.querySelector('.group-manage[aria-expanded=\"true\"]')");
            js("(function(){var buttons=document.querySelectorAll('.group-manage');for(var i=0;i<buttons.length;i++)if(buttons[i]._group.id==='group:测试分组 1'){buttons[i].click();break;}})()");
            await("channelEditor&&!!channelEditor.viewport&&!channelEditor.dirty");
            js("document.documentElement.setAttribute('data-theme','dark');channelEditor.viewport.scrollTop=0;channelEditor.fullButton.click()");
            await("channelEditor.fullscreen");
            screenshot("channels-fullscreen-dark-android71");
            check("true".equals(js("!channelEditor.drag&&channelEditor.space.children.length<=Math.ceil(channelEditor.viewport.clientHeight/60)+6")),"drag leaked");
            return "PASS Android "+android.os.Build.VERSION.RELEASE+" real WebView 10,000 channels, fullscreen sizing, APP/browser Back preserves edits/scroll, native pointer/touch drag/autoscroll, in-place save/sync retry, cold start, recycled rows, day/night; "+metrics+"\n";
        } finally {if(activity!=null)test.runOnMainSync(()->activity.finish());server.close();}
    }
}
