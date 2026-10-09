package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.*;

/** Real HTTP classification and production channel switching, preserving user data. */
public final class HttpContentRoutingInstrumentation extends Instrumentation {
    private Bundle args;
    private MainActivity activity;
    private ServerSocket server;
    private final CopyOnWriteArrayList<Socket> clients=new CopyOnWriteArrayList<>();
    @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
    private Field field(String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private void invoke(String name,Class<?>[] types,Object... values)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);
    }
    private void startServer()throws Exception {
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        Thread accept=new Thread(()->{
            while(!server.isClosed())try{
                Socket s=server.accept();clients.add(s);
                Thread client=new Thread(()->{
                    try(Socket socket=s){
                        socket.setSoTimeout(3000);
                        BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),"US-ASCII"));
                        String request=in.readLine(),line;
                        while((line=in.readLine())!=null&&!line.isEmpty()){}
                        String path=request.split(" ")[1];
                        if(path.equals("/slow-old.m3u8"))try{Thread.sleep(2000);}catch(InterruptedException ignored){}
                        boolean redirect=path.equals("/redirect.mp4");
                        String body="<!doctype html><html><head><title>HTTP routing fixture</title></head><body><h1>HTTP_ROUTING_OK</h1><a href='https://example.test/other.m3u8'>Example</a></body></html>";
                        String type=path.equals("/wrong.m3u8")?"application/vnd.apple.mpegurl":"text/html";
                        if(path.equals("/wrong.mp4"))type="video/mp4";
                        if(path.equals("/playlist.html")){body="#EXTM3U\n#EXT-X-ENDLIST\n";type="text/html";}
                        byte[] data=body.getBytes("UTF-8");
                        OutputStream out=socket.getOutputStream();
                        if(path.equals("/dribble")){
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 1024\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));
                            for(int i=0;i<1024;i++){out.write('x');out.flush();try{Thread.sleep(400);}catch(InterruptedException ignored){break;}}
                            return;
                        }
                        out.write(("HTTP/1.1 "+(redirect?"302 Found":"200 OK")+"\r\nContent-Type: "+type+
                                "\r\nContent-Length: "+data.length+(redirect?"\r\nLocation: /page":"")+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
                        out.write(data);out.flush();
                    }catch(IOException ignored){}finally{clients.remove(s);}
                },"HTTP routing fixture client");client.setDaemon(true);client.start();
            }catch(IOException ignored){if(server.isClosed())break;}
        },"HTTP routing fixture server");accept.setDaemon(true);accept.start();
    }
    private void switchChannel(String url)throws Exception {
        runOnMainSync(()->{try{
            ((java.util.concurrent.atomic.AtomicInteger)field("catalogLoadGeneration").get(activity)).incrementAndGet();
            field("autoSwitchSource").setBoolean(activity,false);
            Channel channel=new Channel("1","HTTP_ROUTING","diag",url,null,null);
            ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("HTTP_ROUTING",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
            field("currentGroupIndex").setInt(activity,0);field("currentChannelIndex").setInt(activity,0);field("currentSourceIndex").setInt(activity,0);
            invoke("startChannel",new Class<?>[]{int.class},0);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private void waitPage(String expected)throws Exception {
        long until=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<until){
            boolean[] ok={false};runOnMainSync(()->{try{
                WebSourceView view=(WebSourceView)field("webSourceView").get(activity);
                ok[0]=view.isPageVisible()&&expected.equals(view.activePageUrl())&&field("player").get(activity)==null;
            }catch(Exception e){throw new RuntimeException(e);}});
            if(ok[0])return;SystemClock.sleep(100);
        }
        throw new AssertionError("Did not route to WebView: "+expected);
    }
    private void waitTitle(String expected)throws Exception {
        long until=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<until){
            boolean[] ok={false};runOnMainSync(()->{try{
                WebSourceView source=(WebSourceView)field("webSourceView").get(activity);
                ok[0]=source.currentPageTitle()!=null&&source.currentPageTitle().contains(expected);
            }catch(Exception e){throw new RuntimeException(e);}});
            if(ok[0])return;SystemClock.sleep(100);
        }
        throw new AssertionError("Web document did not load: "+expected);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();JSONArray evidence=new JSONArray();int code=1;
        SharedPreferences prefs=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=prefs.getAll();ChannelCatalog.Group[] groups=ChannelCatalog.GROUPS;
        try{
            if(!"true".equals(args.getString("classifierOnly"))){
                activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                SystemClock.sleep(2000);groups=ChannelCatalog.GROUPS;
            }
            NetworkClient.initialize(getTargetContext());startServer();
            String base="http://127.0.0.1:"+server.getLocalPort();
            for(String path:new String[]{"/page","/wrong.m3u8","/wrong.mp4","/redirect.mp4","/playlist.html"}){
                HttpStreamResolver.Result r=HttpStreamResolver.resolve(base+path);
                if(r.webPage==path.equals("/playlist.html"))throw new AssertionError("Classifier: "+path);
                evidence.put(new JSONObject().put("fixture",path).put("webPage",r.webPage).put("url",r.url));
            }
            if(activity!=null){switchChannel(base+"/page");waitPage(base+"/page");waitTitle("HTTP routing fixture");}
            long deadlineBegin=SystemClock.elapsedRealtime();
            try{HttpStreamResolver.resolve(base+"/dribble");throw new AssertionError("Dribbling source did not time out");}
            catch(IOException expected){
                long elapsed=SystemClock.elapsedRealtime()-deadlineBegin;
                if(elapsed>14500||elapsed<10000)throw new AssertionError("Total deadline: "+elapsed,expected);
                evidence.put(new JSONObject().put("dribbleDeadlineMs",elapsed));
            }
            if(!"true".equals(args.getString("classifierOnly"))){
                for(String path:new String[]{"/page","/wrong.m3u8","/wrong.mp4","/redirect.mp4"}){
                    switchChannel(base+path);waitPage(base+(path.equals("/redirect.mp4")?"/page":path));
                    waitTitle("HTTP routing fixture");
                    evidence.put(new JSONObject().put("channel",path).put("webVisible",true).put("nativePlayer",false));
                }
                switchChannel(base+"/slow-old.m3u8");SystemClock.sleep(250);
                Thread stale=(Thread)field("httpResolveThread").get(activity);
                switchChannel(base+"/page");waitPage(base+"/page");SystemClock.sleep(2500);waitPage(base+"/page");
                if(stale!=null&&stale.isAlive())throw new AssertionError("Old HTTP resolver remained active");
                evidence.put(new JSONObject().put("staleChannelIgnored",true));
                String live="https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8";
                switchChannel(live);long until=SystemClock.elapsedRealtime()+45000;boolean ready=false;
                while(SystemClock.elapsedRealtime()<until){
                    if(field("videoRenderingStarted").getBoolean(activity)&&field("player").get(activity)!=null){ready=true;break;}
                    SystemClock.sleep(100);
                }
                if(!ready)throw new AssertionError("Native HLS did not render after web channel");
                evidence.put(new JSONObject().put("channel",live).put("nativeRendered",true));
                String online="https://developer.apple.com/streaming/examples/advanced-stream-dv-atmos.html";
                // Keep the real Apple document visible for visual QA; the page's
                // genuine video otherwise follows the user's normal auto-play policy.
                runOnMainSync(()->{try{field("webViewAutoPlaySniffed").setBoolean(activity,false);}catch(Exception e){throw new RuntimeException(e);}});
                switchChannel(online);waitPage(online);waitTitle("Apple");SystemClock.sleep(1500);
                waitPage(online);
                File screenshot=new File(getTargetContext().getExternalFilesDir(null),"http-routing-apple.png");
                android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
                if(image==null)throw new AssertionError("No browser screenshot");
                try(FileOutputStream out=new FileOutputStream(screenshot)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
                evidence.put(new JSONObject().put("channel",online).put("webVisible",true).put("documentLoaded",true).put("nativePlayer",false).put("autoPlaySniffed",false).put("screenshot",screenshot.toString()));
            }
            if(args.containsKey("sourceFile")){
                int pages=0,media=0,unavailable=0;
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(new FileInputStream(args.getString("sourceFile")),"UTF-8"))){
                    String line;while((line=lines.readLine())!=null){
                        int comma=line.indexOf(',');if(comma<0)continue;
                        String url=line.substring(comma+1).trim();if(!HttpStreamResolver.shouldResolve(url))continue;
                        long begin=SystemClock.elapsedRealtime();
                        try{
                            HttpStreamResolver.Result r=HttpStreamResolver.resolve(url);
                            if(url.startsWith("https://developer.apple.com/")&&!r.webPage)throw new AssertionError("Apple page incorrectly sent to player: "+url);
                            if(r.webPage)pages++;else media++;
                            evidence.put(new JSONObject().put("source",url).put("webPage",r.webPage).put("resolved",r.url).put("elapsedMs",SystemClock.elapsedRealtime()-begin));
                        }catch(IOException error){unavailable++;evidence.put(new JSONObject().put("source",url).put("error",error.toString()));}
                    }
                }
                evidence.put(new JSONObject().put("sourcePages",pages).put("sourceMedia",media).put("sourceUnavailable",unavailable));
                if(pages!=8)throw new AssertionError("Expected eight Apple webpages, got "+pages);
            }
            code=-1;
        }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));result.putString("failure",trace.toString());}
        finally{
            if(activity!=null){runOnMainSync(()->{try{invoke("closeWebSource",new Class<?>[0]);invoke("releasePlayer",new Class<?>[0]);}catch(Exception ignored){}activity.finish();});waitForIdleSync();}
            ChannelCatalog.GROUPS=groups;
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> e:saved.entrySet()){
                Object v=e.getValue();String k=e.getKey();
                if(v instanceof String)restore.putString(k,(String)v);else if(v instanceof Integer)restore.putInt(k,(Integer)v);
                else if(v instanceof Long)restore.putLong(k,(Long)v);else if(v instanceof Boolean)restore.putBoolean(k,(Boolean)v);
                else if(v instanceof Float)restore.putFloat(k,(Float)v);else if(v instanceof java.util.Set)restore.putStringSet(k,(java.util.Set<String>)v);
            }
            restore.commit();try{if(server!=null)server.close();}catch(IOException ignored){}
            for(Socket s:clients)try{s.close();}catch(IOException ignored){}
        }
        result.putString("routing",evidence.toString());finish(code,result);
    }
}
