package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.WebView;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.*;
import org.json.*;

/** Real KitKat browser transitions with bounded UI calls and independent stall snapshots. */
public final class LegacyWebStabilityInstrumentation extends Instrumentation {
    private Bundle args;
    private MainActivity activity;
    private WebSourceView source;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean watching;
    private volatile String operation = "startup";
    private volatile long longestGap;
    private int stalls, rendererTimeouts, readyPages, playingVideos;
    private ChannelCatalog.Group[] savedGroups;
    private PrintWriter evidence;
    private final JSONArray actions = new JSONArray();
    private interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void set(Object object,String name,Object value) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    private static Object invoke(Object object,String name,Class<?>[] types,Object... values) throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,values);
    }
    private void main(String label,Work work) throws Exception {
        operation=label;long begin=SystemClock.elapsedRealtime();
        CountDownLatch done=new CountDownLatch(1);Throwable[] failure={null};
        ui.post(()->{try{work.run();}catch(Throwable error){failure[0]=error;}finally{done.countDown();}});
        if(!done.await(12,TimeUnit.SECONDS))throw new AssertionError("UI blocked: "+label);
        if(failure[0]!=null)throw new AssertionError(label,failure[0]);
        JSONObject record=new JSONObject().put("action",label).put("elapsedMs",SystemClock.elapsedRealtime()-begin);
        actions.put(record);write(record);
    }
    private synchronized void write(JSONObject record) {
        if(evidence!=null){evidence.println(record);evidence.flush();}
    }
    private void watch() {
        watching=true;
        Thread thread=new Thread(()->{
            while(watching) {
                long begin=SystemClock.elapsedRealtime();CountDownLatch beat=new CountDownLatch(1);
                ui.post(beat::countDown);
                try {
                    if(!beat.await(2,TimeUnit.SECONDS)) {
                        stalls++;
                        for(int i=0;i<4 && watching;i++) {
                            StringBuilder stacks=new StringBuilder();
                            for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()) {
                                if(entry.getKey()==Looper.getMainLooper().getThread()
                                        || entry.getKey().getName().contains("Chrome")
                                        || entry.getKey().getName().contains("WebView")) {
                                    stacks.append(entry.getKey().getName()).append(' ').append(entry.getKey().getState()).append('\n');
                                    for(StackTraceElement frame:entry.getValue())stacks.append("  ").append(frame).append('\n');
                                }
                            }
                            JSONObject record=new JSONObject().put("stallMs",SystemClock.elapsedRealtime()-begin)
                                    .put("operation",operation).put("stacks",stacks.toString());
                            write(record);android.util.Log.w("LegacyWebStress",record.toString());
                            if(beat.await(2,TimeUnit.SECONDS))break;
                        }
                    }
                    longestGap=Math.max(longestGap,SystemClock.elapsedRealtime()-begin);
                    Thread.sleep(250);
                }catch(Exception ignored) { }
            }
        },"web-stress-watchdog");thread.setDaemon(true);thread.start();
    }
    private String evaluate(String script,long timeoutMs) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] value={null};
        main("evaluate",()->{
            WebView view=(WebView)field(source,"webView");
            if(view==null){done.countDown();return;}
            view.evaluateJavascript(script,answer->{value[0]=answer;done.countDown();});
        });
        if(!done.await(timeoutMs,TimeUnit.MILLISECONDS)){rendererTimeouts++;return null;}
        return value[0];
    }
    private void checkAudioCompatibility() throws Exception {
        String result=evaluate("(function(){var out={installed:window.__ntvWebAudio===true};"
                +"function media(muted){var v=document.createElement('video');v.muted=muted;v.volume=0;document.body.appendChild(v);return v;}"
                +"function playing(v){var e=document.createEvent('Event');e.initEvent('playing',false,false);v.dispatchEvent(e);}"
                +"var a=media(false),b=media(true);playing(a);playing(b);out.initial=a.volume;out.muted=b.volume;"
                +"a.volume=0;playing(a);out.repeat=a.volume;"
                +"var e=document.createEvent('Event');e.initEvent('mousedown',true,true);document.dispatchEvent(e);"
                +"var c=media(false);playing(c);out.afterInteraction=c.volume;"
                +"a.parentNode.removeChild(a);b.parentNode.removeChild(b);c.parentNode.removeChild(c);return out;})()",2500);
        write(new JSONObject().put("audioCompatibility",result));
        if(result==null)throw new AssertionError("Audio script evaluation timed out");
        JSONObject audio=new JSONObject(result);
        if(!audio.optBoolean("installed") || Math.abs(audio.optDouble("initial")-0.6)>0.001
                || audio.optDouble("muted")!=0 || audio.optDouble("repeat")!=0 || audio.optDouble("afterInteraction")!=0)
            throw new AssertionError("Legacy audio compatibility failed: "+result);
    }
    private void local(String base,int cycles) throws Exception {
        for(int i=0;i<cycles;i++) {
            final int cycle=i;
            main("heavy-open-"+i,()->{
                source.open((Integer)field(activity,"playRequestId"),base+"/heavy?cycle="+cycle,"");
            });
            long until=SystemClock.elapsedRealtime()+10000;String status=null;
            while(SystemClock.elapsedRealtime()<until) {
                status=evaluate("({ready:document.readyState,url:location.href,n:document.querySelectorAll('span').length,"
                        +"playing:!!(window.video&&video.currentTime>0&&!video.paused)})",1500);
                if(status!=null && status.contains("complete") && status.contains("\"n\":"+args.getString("nodes","2000")) && status.contains("\"playing\":true"))break;
                SystemClock.sleep(200);
            }
            if(status!=null && status.contains("complete") && status.contains("\"n\":"+args.getString("nodes","2000")))readyPages++;
            if(status!=null && status.contains("\"playing\":true"))playingVideos++;
            if(status==null || !status.contains("\"n\":"+args.getString("nodes","2000"))
                    || !status.contains(base+"/heavy?cycle="+cycle) || !status.contains("\"playing\":true"))
                throw new AssertionError("Requested video fixture not playing: "+status);
            if(i==0) checkAudioCompatibility();
            write(new JSONObject().put("cycle",i).put("dom",status));
            main("tab-open-"+i,()->source.openLinkInNewTab(base+"/light"));
            SystemClock.sleep(700);
            main("tab-close-"+i,()->{
                WebTabBar bar=(WebTabBar)field(source,"tabBar");
                invoke(bar,"closeNow",new Class<?>[]{WebTabBar.Tab.class},bar.active());
            });
            SystemClock.sleep(400);
            main("pause-resume-"+i,()->{source.pausePage();source.resumePage();});
            main("return-"+i,()->source.goBackIfPossible());
            main("close-"+i,source::closePage);
            SystemClock.sleep(i%2==0?600:30);
            if("true".equals(args.getString("management"))) {
                final android.app.Activity[] manager={null};
                main("management-open-"+i,()->{
                    android.app.Instrumentation.ActivityMonitor monitor=addMonitor(ManagementActivity.class.getName(),null,false);
                    activity.startActivity(new Intent(activity,ManagementActivity.class)
                            .putExtra(ManagementActivity.EXTRA_URL,"http://127.0.0.1:9966/pages/playback.html"));
                    // ActivityMonitor receives lifecycle callbacks asynchronously.
                    ui.postDelayed(()->{manager[0]=monitor.getLastActivity();removeMonitor(monitor);},300);
                });
                SystemClock.sleep(1500);
                main("management-return-"+i,()->{if(manager[0]!=null)manager[0].finish();});
                SystemClock.sleep(700);
            }
        }
    }
    @Override public void onStart() {
        Bundle result=new Bundle();int code=-1;ServerSocket server=null;
        Map<String,?> saved=null;
        try {
            evidence=new PrintWriter(new File(getTargetContext().getExternalFilesDir(null),"web-stress.jsonl"),"UTF-8");
            watch();
            server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
            final ServerSocket fixture=server;
            ExecutorService requests=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"web-stress-http");t.setDaemon(true);return t;});
            Thread accept=new Thread(()->{while(!fixture.isClosed())try{
                Socket socket=fixture.accept();requests.execute(()->serve(socket));
            }catch(Exception ignored){}},"web-stress-accept");accept.setDaemon(true);accept.start();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);
            saved=activity.getSharedPreferences(MainActivity.PREFERENCES,0).getAll();
            savedGroups=ChannelCatalog.GROUPS;
            source=(WebSourceView)field(activity,"webSourceView");
            main("isolate",()->{
                ((java.util.concurrent.atomic.AtomicInteger)field(activity,"catalogLoadGeneration")).incrementAndGet();
                set(activity,"pendingCjsChannelIndex",-1);set(activity,"playRequestId",(Integer)field(activity,"playRequestId")+1);
                for(String name:new String[]{"cancelCustomSourceTimeout","clearPendingPlayer","cancelRemoteResolve","releasePlayer","closeWebSource"})
                    invoke(activity,name,new Class<?>[0]);
                set(activity,"webViewAutoPlaySniffed",false);
            });
            if("renderer".equals(args.getString("phase"))) {
                if(android.os.Build.VERSION.SDK_INT<26)throw new AssertionError("Modern renderer test requires API 26+");
                String base="http://127.0.0.1:"+server.getLocalPort();
                main("install-renderer-fixture",()->{
                    ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("Renderer recovery",
                            ChannelCatalog.SOURCE_CUSTOM,new Channel[]{
                            new Channel("1","Web video","renderer-web","webview://"+base+"/heavy",null,null),
                            new Channel("2","Native recovery","renderer-native",base+"/video.mp4",null,null)})};
                    set(activity,"currentGroupIndex",0);set(activity,"autoSwitchSource",false);
                    invoke(activity,"switchChannel",new Class<?>[]{int.class},0);
                });
                SystemClock.sleep(4000);
                main("retained-browser-tab",()->source.openLinkInNewTab(base+"/light"));
                SystemClock.sleep(2000);
                int mainPid=android.os.Process.myPid();
                main("crash-renderer",()->((WebView)field(source,"webView")).loadUrl("chrome://crash"));
                long begin=SystemClock.elapsedRealtime(),until=begin+30000;
                boolean recovered=false;
                while(SystemClock.elapsedRealtime()<until) {
                    Object[] status={null,null};
                    main("renderer-recovery",()->{status[0]=field(activity,"currentChannelIndex");status[1]=field(activity,"videoRenderingStarted");});
                    if((Integer)status[0]==1 && Boolean.TRUE.equals(status[1])){recovered=true;break;}
                    SystemClock.sleep(300);
                }
                write(new JSONObject().put("modernRendererRecovery",recovered).put("mainPidUnchanged",mainPid==android.os.Process.myPid())
                        .put("elapsedMs",SystemClock.elapsedRealtime()-begin));
                if(!recovered)throw new AssertionError("Renderer failure did not recover to native video");
            } else if("oom".equals(args.getString("phase")) || "input".equals(args.getString("phase"))) {
                String base="http://127.0.0.1:"+server.getLocalPort();
                final int mainPid=android.os.Process.myPid();
                main("install-oom-fixture",()->{
                    ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("Web OOM",
                            ChannelCatalog.SOURCE_CUSTOM,new Channel[]{
                            new Channel("1","Web stress","stress-web","webview://"+base+"/heavy",null,null),
                            new Channel("2","Native recovery","stress-native",base+"/video.mp4",null,null)})};
                    set(activity,"currentGroupIndex",0);set(activity,"autoSwitchSource",false);
                });
                // A real playing page must remain playable after isolation, including media IPC.
                main("normal-web-video",()->invoke(activity,"switchChannel",new Class<?>[]{int.class},0));
                long until=SystemClock.elapsedRealtime()+20000;
                JSONObject[] media={new JSONObject()};
                while(SystemClock.elapsedRealtime()<until) {
                    main("web-media-snapshot",()->media[0]=source.currentWebMediaState());
                    if(media[0].optBoolean("playing") && media[0].optLong("positionMs")>1000)break;
                    SystemClock.sleep(500);
                }
                write(new JSONObject().put("isolatedVideo",media[0]));
                if(!media[0].optBoolean("playing"))throw new AssertionError("Isolated real video not playing: "+media[0]);
                if(field(source,"webView")!=null)throw new AssertionError("Legacy WebView exists in main process");
                for(String action:new String[]{"pause","play"}) {
                    CountDownLatch commandDone=new CountDownLatch(1);String[] failure={null};
                    main("media-"+action,()->source.controlWebMedia(action,source.currentResourcePageKey(),
                            source.currentWebMediaState().optString("token"), error->{failure[0]=error;commandDone.countDown();}));
                    if(!commandDone.await(5,TimeUnit.SECONDS) || failure[0]!=null)throw new AssertionError("Media IPC: "+failure[0]);
                    SystemClock.sleep(1600);
                    main("media-verify-"+action,()->media[0]=source.currentWebMediaState());
                    write(new JSONObject().put("mediaAction",action).put("state",media[0]));
                    if(media[0].optBoolean("playing")!=action.equals("play"))throw new AssertionError("Media action did not apply: "+media[0]);
                }
                final float pointX=activity.getResources().getDisplayMetrics().widthPixels/2f;
                final float pointY=activity.getResources().getDisplayMetrics().heightPixels/2f;
                main("mouse-hover",()->{
                    android.view.MotionEvent.PointerProperties prop=new android.view.MotionEvent.PointerProperties();
                    prop.id=0;prop.toolType=android.view.MotionEvent.TOOL_TYPE_MOUSE;
                    android.view.MotionEvent.PointerCoords coords=new android.view.MotionEvent.PointerCoords();coords.x=pointX;coords.y=pointY;coords.pressure=0;coords.size=1;
                    long now=SystemClock.uptimeMillis();
                    android.view.MotionEvent motion=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_HOVER_MOVE,
                            1,new android.view.MotionEvent.PointerProperties[]{prop},new android.view.MotionEvent.PointerCoords[]{coords},
                            0,0,1,1,0,0,android.view.InputDevice.SOURCE_MOUSE,0);
                    try { if(!source.dispatchRemoteMouseHover(source,motion))throw new AssertionError("Remote mouse ignored"); }
                    finally {motion.recycle();}
                });
                CountDownLatch contextDone=new CountDownLatch(1);JSONObject[] context={null};
                main("inspect-browser-context",()->source.inspectSmartContext(pointX,pointY,value->{context[0]=value;contextDone.countDown();}));
                if(!contextDone.await(3,TimeUnit.SECONDS) || context[0]==null || context[0].length()==0)throw new AssertionError("Remote context inspection failed");
                write(new JSONObject().put("mouseContext",context[0]));
                if(!"input".equals(args.getString("phase"))) {
                Object[] legacy={null};main("read-session",()->legacy[0]=field(source,"legacy"));
                int previousBrowser=(Integer)field(legacy[0],"pid");
                String[] pages={"true".equals(args.getString("skipBench"))?"/oom":"/bench/octane/index.html?auto=1", "/heavy", "/hang", "/missing"};
                for(int attempt=0;attempt<pages.length;attempt++) {
                    final int test=attempt;
                    main("prepare-failure-"+test,()->{
                        invoke(activity,"closeWebSource",new Class<?>[0]);
                        ChannelCatalog.GROUPS[0].channels[0]=new Channel("1","Web stress","stress-web","webview://"+base+pages[test],null,null);
                        invoke(activity,"switchChannel",new Class<?>[]{int.class},0);
                    });
                    long begin=SystemClock.elapsedRealtime();
                    int child=0;
                    until=begin+20000;
                    while(SystemClock.elapsedRealtime()<until) {
                        child=(Integer)field(legacy[0],"pid");
                        if(child>0)break;SystemClock.sleep(100);
                    }
                    if(child==mainPid || child==previousBrowser)throw new AssertionError("Browser was not replaced/isolated");
                    final int childPid=child;
                    if(test==1) {
                        SystemClock.sleep(3000);
                        main("abrupt-browser-kill",()->android.os.Process.killProcess(childPid));
                    }
                    until=begin+120000;
                    boolean recovered=false;
                    while(SystemClock.elapsedRealtime()<until) {
                        Object[] status={null,null};
                        main("recovery-status",()->{status[0]=field(activity,"currentChannelIndex");status[1]=field(activity,"videoRenderingStarted");});
                        if((Integer)status[0]==1 && Boolean.TRUE.equals(status[1])){recovered=true;break;}
                        SystemClock.sleep(300);
                    }
                    JSONObject event=new JSONObject().put("case",pages[test]).put("mainPid",mainPid).put("browserPid",child)
                            .put("elapsedMs",SystemClock.elapsedRealtime()-begin).put("nativeFirstFrame",recovered)
                            .put("mainPidUnchanged",mainPid==android.os.Process.myPid());
                    write(event);android.util.Log.i("LegacyWebStress",event.toString());
                    if(!recovered)throw new AssertionError("Native next channel did not render: "+event);
                    previousBrowser=child;SystemClock.sleep(1000);
                }
                // Deliberately cancelling a page must ignore a late death callback, not skip the selected native channel.
                main("cancel-stale-open",()->invoke(activity,"switchChannel",new Class<?>[]{int.class},0));
                SystemClock.sleep(100);
                main("manual-native",()->invoke(activity,"switchChannel",new Class<?>[]{int.class},1));
                SystemClock.sleep(3000);
                main("verify-manual-native",()->{
                    if((Integer)field(activity,"currentChannelIndex")!=1 || !Boolean.TRUE.equals(field(activity,"videoRenderingStarted")))
                        throw new AssertionError("Stale browser callback interrupted manual channel");
                });
                main("all-unavailable",()->{
                    ChannelCatalog.GROUPS[0].channels[0]=new Channel("1","Bad A","bad-a","webview://"+base+"/missing?a",null,null);
                    ChannelCatalog.GROUPS[0].channels[1]=new Channel("2","Bad B","bad-b","webview://"+base+"/missing?b",null,null);
                    invoke(activity,"switchChannel",new Class<?>[]{int.class},0);
                });
                until=SystemClock.elapsedRealtime()+20000;
                while(SystemClock.elapsedRealtime()<until) {
                    if(((java.util.Set<?>)field(activity,"unavailableWebChannels")).size()==2 && !source.hasRetainedPage())break;
                    SystemClock.sleep(200);
                }
                if(((java.util.Set<?>)field(activity,"unavailableWebChannels")).size()!=2 || source.hasRetainedPage())throw new AssertionError("Bad channels looped");
                int stableRequest=(Integer)field(activity,"playRequestId"); SystemClock.sleep(3000);
                if((Integer)field(activity,"playRequestId")!=stableRequest)throw new AssertionError("All unavailable channels retried indefinitely");
                write(new JSONObject().put("allUnavailableStopped",true).put("mainPid",mainPid));
                }
            } else if("probe".equals(args.getString("phase"))) {
                File command=new File(getTargetContext().getExternalFilesDir(null),"web-command.json");
                command.delete();String last="";
                long until=SystemClock.elapsedRealtime()+Integer.parseInt(args.getString("seconds","600"))*1000L;
                while(SystemClock.elapsedRealtime()<until) {
                    if(!command.exists()){SystemClock.sleep(300);continue;}
                    JSONObject request;
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(command),"UTF-8"))) {
                        StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);
                        request=new JSONObject(text.toString());
                    }catch(Exception incomplete){SystemClock.sleep(300);continue;}
                    String id=request.getString("id");if(id.equals(last)){SystemClock.sleep(300);continue;}last=id;
                    String action=request.getString("action");JSONObject response=new JSONObject().put("id",id).put("action",action);
                    if("stop".equals(action)){write(response);break;}
                    if("open".equals(action))main("site-open",()->source.open((Integer)field(activity,"playRequestId"),
                            request.getString("url").replace("{fixture}","http://127.0.0.1:"+fixture.getLocalPort()),""));
                    else if("evaluate".equals(action))response.put("value",evaluate(request.getString("script"),5000));
                    else if("back".equals(action))main("site-back",source::goBackIfPossible);
                    else if("close".equals(action))main("site-close",source::closePage);
                    else if("tab".equals(action))main("site-tab",()->source.openLinkInNewTab(request.getString("url")));
                    write(response);
                }
            } else if("switch".equals(args.getString("phase"))) {
                String base="http://127.0.0.1:"+server.getLocalPort();
                main("install-channel-fixture",()->{
                    ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("Web stress",
                            ChannelCatalog.SOURCE_CUSTOM,new Channel[]{
                            new Channel("1","Web video","stress-web","webview://"+base+"/heavy",null,null),
                            new Channel("2","Native video","stress-native",base+"/video.mp4",null,null)})};
                    set(activity,"currentGroupIndex",0);set(activity,"autoSwitchSource",false);
                });
                for(int i=0;i<Integer.parseInt(args.getString("cycles","20"));i++) {
                    final int index=i%2;
                    main("switch-channel-"+i,()->invoke(activity,"switchChannel",new Class<?>[]{int.class},index));
                    SystemClock.sleep(2500);
                    write(new JSONObject().put("switch",i).put("web",index==0)
                            .put("rendering",field(activity,"videoRenderingStarted"))
                            .put("pageVisible",source.isPageVisible()));
                }
                main("stop-native",()->invoke(activity,"releasePlayer",new Class<?>[0]));
            } else if("remote".equals(args.getString("phase"))) {
                String[] urls=args.getString("urls","https://www.yangshipin.cn/tv/home?pid=600001859|https://tv.cctv.com/live/cctv1/").split("\\|");
                for(int i=0;i<Integer.parseInt(args.getString("cycles","12"));i++) {
                    final String url=urls[i%urls.length];
                    main("remote-open-"+i,()->{
                        source.open((Integer)field(activity,"playRequestId"),url,"");
                    });
                    SystemClock.sleep(10000);
                    write(new JSONObject().put("remote",url).put("dom",evaluate("JSON.stringify({ready:document.readyState,title:document.title,video:document.querySelectorAll('video').length})",2500)));
                    main("remote-close-"+i,source::closePage);SystemClock.sleep(600);
                }
            } else local("http://127.0.0.1:"+server.getLocalPort(),Integer.parseInt(args.getString("cycles","20")));
            main("destroy-page",source::destroyPage);
            SystemClock.sleep(3000);
            main("finish-activity",activity::finish);
            SystemClock.sleep(3000);
            result.putString("stream",new JSONObject().put("phase",args.getString("phase","local"))
                    .put("actions",actions.length()).put("readyPages",readyPages).put("playingVideos",playingVideos)
                    .put("uiStallsOver2s",stalls).put("longestUiGapMs",longestGap)
                    .put("rendererTimeouts",rendererTimeouts).put("operations",actions).toString()+"\n");
        } catch(Throwable error) {code=0;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            watching=false;if(server!=null)try{server.close();}catch(Exception ignored){}
            if(savedGroups!=null)ChannelCatalog.GROUPS=savedGroups;
            if(saved!=null) {
                SharedPreferences.Editor editor=activity.getSharedPreferences(MainActivity.PREFERENCES,0).edit().clear();
                for(Map.Entry<String,?> entry:saved.entrySet()) {
                    Object value=entry.getValue();String key=entry.getKey();
                    if(value instanceof String)editor.putString(key,(String)value);
                    else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                    else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                    else if(value instanceof Long)editor.putLong(key,(Long)value);
                    else if(value instanceof Float)editor.putFloat(key,(Float)value);
                    else if(value instanceof java.util.Set)editor.putStringSet(key,(java.util.Set<String>)value);
                }
                editor.commit();
            }
            if(activity!=null)ui.post(activity::finish);
            if(evidence!=null)evidence.close();
        }
        finish(code,result);
    }
    private void serve(Socket socket) {
        try(Socket client=socket) {
            client.setSoTimeout(3000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));
            String request=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            byte[] body;String type;
            if(request.startsWith("GET /bench/")) {
                String path=request.split(" ")[1].split("\\?")[0].substring("/bench/".length());
                File root=new File(getTargetContext().getExternalFilesDir(null),"web-stress-assets").getCanonicalFile();
                File asset=new File(root,path).getCanonicalFile();
                if(!asset.getPath().startsWith(root.getPath()+File.separator) || !asset.isFile())throw new IOException("Unknown benchmark asset");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=new FileInputStream(asset)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0)out.write(buffer,0,n);}
                body=out.toByteArray();type=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html; charset=utf-8";
            } else if(request.contains("/video.mp4")) {
                File video=new File(getTargetContext().getExternalFilesDir(null),"web-anr-fixture.mp4");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=new FileInputStream(video)) {byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0)out.write(buffer,0,n);}
                body=out.toByteArray();type="video/mp4";
            } else if(request.contains("/missing")) {
                return; // Closed without a response: a real main-document network error on API 19 too.
            } else if(request.contains("/hang")) {
                body="<!doctype html><title>CPU guard test</title><script>setTimeout(function(){while(true){}},1000);</script>".getBytes("UTF-8");type="text/html";
            } else if(request.contains("/oom")) {
                body="<!doctype html><title>Memory guard test</title><script>var held=[];setInterval(function(){var a=new Uint8Array(32*1024*1024);for(var i=0;i<a.length;i+=4096)a[i]=1;held.push(a);},300);</script>".getBytes("UTF-8");type="text/html";
            } else {
                boolean child=request.contains("/child"),heavy=request.contains("/heavy")
                        || child && "true".equals(args.getString("childVideo"));
                String content=heavy?"<video id='video' src='/video.mp4' autoplay muted loop width='320'></video>"
                        +"<canvas id='c' width='320' height='180'></canvas><script>for(var i=0;i<"+args.getString("nodes","2000")+";i++){var s=document.createElement('span');"
                        +"s.textContent='频道'+i+' ';document.body.appendChild(s);}var ctx=c.getContext('2d'),frame=0;"
                        +"function draw(){ctx.fillStyle='hsl('+(frame++%360)+',50%,50%)';ctx.fillRect(0,0,320,180);requestAnimationFrame(draw);}draw();video.play();</script>"
                        +(child?"":"<iframe src='/child'></iframe>"):"<a href='/heavy'>打开视频频道</a>";
                body=("<!doctype html><meta charset='utf-8'><title>Web stress "+(heavy?"heavy":"light")+"</title><body>"+content+"</body>").getBytes("UTF-8");type="text/html; charset=utf-8";
            }
            OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
            output.write(body);output.flush();
        } catch(Exception ignored) { }
    }
}
