package xiao.bu.channel;

import android.content.Context;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Loopback origin for packaged ES modules, IndexedDB and Java HTTP API forwarding. */
final class FoliaServer {
    private final ServerSocket server;
    private final ClassLoader assets;
    private final String upstream,token=UUID.randomUUID().toString();
    private final ExecutorService workers=Executors.newFixedThreadPool(8);
    private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<Socket>());
    private final Set<HttpURLConnection> connections=Collections.synchronizedSet(new HashSet<HttpURLConnection>());
    private volatile boolean closed;
    FoliaServer(Context context,ClassLoader assets,String upstream)throws Exception {
        URL base=new URL(upstream);
        if(!"https".equals(base.getProtocol())||base.getHost().isEmpty()||base.getUserInfo()!=null)throw new IOException("服务地址需要 HTTPS");
        this.assets=assets;this.upstream=upstream.replaceAll("/+$","");
        int preferred=context.getSharedPreferences("folia_full",0).getInt("port",39741);
        ServerSocket candidate=null;
        for(int i=0;i<20&&candidate==null;i++)try{candidate=new ServerSocket();candidate.setReuseAddress(true);candidate.bind(new InetSocketAddress("127.0.0.1",preferred+i),32);}catch(IOException e){if(candidate!=null)candidate.close();candidate=null;}
        if(candidate==null)throw new IOException("无法创建 Folia 本地页面服务");
        server=candidate;
        context.getSharedPreferences("folia_full",0).edit().putInt("port",server.getLocalPort()).apply();
        Thread accept=new Thread(()->{while(!closed)try{Socket socket=server.accept();socket.setSoTimeout(30000);sockets.add(socket);workers.execute(()->handle(socket));}catch(IOException|RejectedExecutionException e){if(!closed)android.util.Log.w("FoliaFull","Local server",e);}},"folia-web");
        accept.setDaemon(true);accept.start();
    }
    String origin(){return "http://127.0.0.1:"+server.getLocalPort();}
    boolean exportToken(String value){return !closed&&token.equals(value);}
    private void handle(Socket socket) {
        try {
            BufferedInputStream in=new BufferedInputStream(socket.getInputStream());
            OutputStream out=socket.getOutputStream();
            String first=line(in);if(first==null)return;
            String[] request=first.split(" ");if(request.length!=3)return;
            String method=request[0],path=request[1];
            Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for(int n=0;n<100;n++){String value=line(in);if(value==null||value.isEmpty())break;int colon=value.indexOf(':');if(colon>0)headers.put(value.substring(0,colon),value.substring(colon+1).trim());}
            if(!("127.0.0.1:"+server.getLocalPort()).equals(headers.get("Host"))){reply(out,403,"text/plain","Invalid host".getBytes("UTF-8"));return;}
            if(headers.containsKey("Origin")&&!origin().equals(headers.get("Origin"))){reply(out,403,"text/plain",new byte[0]);return;}
            if(path.startsWith("/__ntv_proxy/"+token+"?url=")) {
                String target=URLDecoder.decode(path.substring(path.indexOf("?url=")+5),"UTF-8");
                int length=headers.containsKey("Content-Length")?Integer.parseInt(headers.get("Content-Length")):0;
                if(length<0||length>16*1024*1024)throw new IOException("请求体超限");
                if(headers.containsKey("Transfer-Encoding"))throw new IOException("不支持分块请求体");
                relay(method,target,headers,in,length,out);
                return;
            }
            if(!"GET".equals(method)&&!"HEAD".equals(method)){reply(out,405,"text/plain",new byte[0]);return;}
            path=URLDecoder.decode(path.split("\\?",2)[0],"UTF-8");
            if(path.contains("..")||path.contains("\\")){reply(out,403,"text/plain",new byte[0]);return;}
            if("/".equals(path))path="/index.html";
            if("/ntv-bridge.js".equals(path)) {
                try(InputStream bridge=assets.getResourceAsStream("ntv-bridge.js")){
                    String js=new String(read(bridge,256*1024),"UTF-8").replace("__NTV_TOKEN__",token).replace("__NTV_UPSTREAM__",upstream);
                    reply(out,200,"application/javascript",js.getBytes("UTF-8"));
                }
                return;
            }
            try(InputStream asset=assets.getResourceAsStream("web"+path)) {
                if(asset==null){reply(out,404,"text/plain",("未打包资源: "+path).getBytes("UTF-8"));return;}
                byte[] data=read(asset,24*1024*1024);
                if(path.endsWith("index.html")) {
                    String html=new String(data,"UTF-8");
                    html=html.replaceAll("<script[^>]*src=\"https://music\\.aikun-bili\\.top/\\.webmcp/[^>]*></script>","")
                            .replaceAll("<script[^>]*src=\"https://static\\.cloudflareinsights\\.com/[^>]*></script>","");
                    html=html.replace("<head>","<head><script src=\"/ntv-bridge.js\"></script>");
                    data=html.getBytes("UTF-8");
                }
                reply(out,200,mime(path),"HEAD".equals(method)?new byte[0]:data);
            }
        }catch(Exception e){try{reply(socket.getOutputStream(),502,"application/json; charset=utf-8",("{\"code\":502,\"message\":"+org.json.JSONObject.quote("Folia 网络错误: "+e.getMessage())+"}").getBytes("UTF-8"));}catch(IOException ignored){}android.util.Log.w("FoliaFull","Request failed",e);}
        finally{sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
    }
    private void relay(String method,String target,Map<String,String> headers,InputStream body,int length,OutputStream out)throws Exception {
        URL url=new URL(target);
        android.util.Log.d("FoliaFull","API begin "+method+" "+url.getHost()+url.getPath());
        if(!("https".equals(url.getProtocol())||"http".equals(url.getProtocol()))||url.getUserInfo()!=null)throw new IOException("不支持的目标地址");
        // The secret route cannot be used to recursively proxy this server.
        if((url.getHost().equals("127.0.0.1")||url.getHost().equals("localhost"))&&url.getPort()==server.getLocalPort())throw new IOException("循环请求");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connections.add(connection);
        connection.setConnectTimeout(12000);connection.setReadTimeout(30000);connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(true);
        try {
            for(Map.Entry<String,String> h:headers.entrySet()) {
                String name=h.getKey();
                if(Arrays.asList("host","connection","content-length","accept-encoding","origin","referer","cookie","sec-fetch-site","sec-fetch-mode","sec-fetch-dest").contains(name.toLowerCase(Locale.US)))continue;
                connection.setRequestProperty(name,h.getValue());
            }
            connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Origin",upstream);connection.setRequestProperty("Referer",upstream+"/");
            String cookie=CookieManager.getInstance().getCookie(target);if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
            if(length>0){connection.setDoOutput(true);connection.setFixedLengthStreamingMode(length);try(OutputStream request=connection.getOutputStream()){copy(body,request,length);}}
            int code=connection.getResponseCode();
            android.util.Log.d("FoliaFull","API response "+code+" "+url.getHost()+url.getPath());
            for(Map.Entry<String,List<String>> h:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(h.getKey()))for(String value:h.getValue())CookieManager.getInstance().setCookie(target,value);
            CookieManager.getInstance().flush();
            StringBuilder response=new StringBuilder("HTTP/1.1 "+code+" Upstream\r\nConnection: close\r\nAccess-Control-Allow-Origin: "+origin()+"\r\n");
            for(String name:new String[]{"Content-Type","Content-Length","Content-Range","Accept-Ranges","Content-Encoding","ETag","Cache-Control"}) {
                String value=connection.getHeaderField(name);if(value!=null)response.append(name).append(": ").append(value.replace("\r","").replace("\n","")).append("\r\n");
            }
            response.append("\r\n");out.write(response.toString().getBytes("UTF-8"));
            if(!"HEAD".equals(method))try(InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream()){if(stream!=null)copy(stream,out,Long.MAX_VALUE);}
        } finally {connections.remove(connection);connection.disconnect();}
    }
    private static void reply(OutputStream out,int code,String mime,byte[] body)throws IOException {
        out.write(("HTTP/1.1 "+code+" Response\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-cache\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes("UTF-8"));out.write(body);
    }
    private static String mime(String path){if(path.endsWith(".js"))return "application/javascript";if(path.endsWith(".css"))return "text/css";if(path.endsWith(".json"))return "application/json";if(path.endsWith(".html"))return "text/html; charset=utf-8";if(path.endsWith(".svg"))return "image/svg+xml";if(path.endsWith(".png"))return "image/png";if(path.endsWith(".webp"))return "image/webp";if(path.endsWith(".woff2"))return "font/woff2";if(path.endsWith(".wasm"))return "application/wasm";return "application/octet-stream";}
    private static String line(InputStream in)throws IOException {ByteArrayOutputStream buffer=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')break;if(b!='\r')buffer.write(b);if(buffer.size()>16384)throw new IOException("HTTP 头过长");}return b==-1&&buffer.size()==0?null:buffer.toString("UTF-8");}
    private static byte[] read(InputStream in,int limit)throws IOException {if(in==null)throw new IOException("资源不存在");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("资源过大");out.write(buffer,0,n);}return out.toByteArray();}
    private static void copy(InputStream in,OutputStream out,long count)throws IOException {byte[] buffer=new byte[16384];int n;while(count>0&&(n=in.read(buffer,0,(int)Math.min(count,buffer.length)))!=-1){out.write(buffer,0,n);count-=n;}}
    void close(){closed=true;try{server.close();}catch(IOException ignored){}synchronized(sockets){for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}}synchronized(connections){for(HttpURLConnection connection:connections)connection.disconnect();}workers.shutdownNow();}
}
