package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;

/** A rejected CDN node is replaced; successful nodes and non-retryable methods stay put. */
public final class Http1TransportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private Field field(String name) throws Exception {
        Field field = NetworkClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Object savedClient=null,savedFactory=null,savedHttp1=null;
        try {
            NetworkClient.initialize(getTargetContext());
            savedClient=field("client").get(null);savedFactory=field("factory").get(null);
            savedHttp1=field("http1Factory").get(null);
            OkHttpClient client = NetworkClient.sharedClient().newBuilder().proxy(java.net.Proxy.NO_PROXY).dns(host -> Arrays.asList(
                    InetAddress.getByName("127.0.0.1"),InetAddress.getByName("127.0.0.2"))).build();
            field("client").set(null,client);field("factory").set(null,new OkUrlFactory(client));
            field("http1Factory").set(null,null);
            try (ServerSocket server = new ServerSocket(0)) {
                AtomicInteger calls=new AtomicInteger();
                java.util.concurrent.atomic.AtomicBoolean leakedLimit=new java.util.concurrent.atomic.AtomicBoolean();
                Thread responder=new Thread(() -> {
                    try {
                        while (!server.isClosed()) {
                            try (Socket socket=server.accept()) {
                                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                String request=reader.readLine(),header;
                                while ((header=reader.readLine())!=null&&!header.isEmpty()) {
                                    if (header.startsWith("X-Ku9-Response-Limit:")) leakedLimit.set(true);
                                }
                                calls.incrementAndGet();
                                String path=request.split(" ")[1];
                                int status=path.equals("/large")?200:path.equals("/missing")?404:path.equals("/deny")||request.startsWith("POST ")
                                        ||socket.getLocalAddress().getHostAddress().equals("127.0.0.1")?403:200;
                                char[] content=new char[path.equals("/large")?1024:2];Arrays.fill(content,'x');
                                socket.getOutputStream().write(("HTTP/1.1 "+status+" Result\r\nContent-Length: "+content.length+"\r\nConnection: close\r\n\r\n"+new String(content)).getBytes("UTF-8"));
                            }
                        }
                    } catch (Exception ignored) { }
                },"http1-regression");
                responder.start();
                String base="http://cdn.test:"+server.getLocalPort();
                int initial = request(base+"/ok","GET");
                check(initial==200&&calls.get()==2,"403 failover: status="+initial+", requests="+calls.get());
                check(request(base+"/ok","GET")==200&&calls.get()==3,"keep successful node");
                check(request(base+"/deny","GET")==403&&calls.get()==6,"three attempt limit");
                check(request(base+"/missing","GET")==404&&calls.get()==7,"404 is not retried");
                check(request(base+"/deny","POST")==403&&calls.get()==8,"POST is not retried");
                org.json.JSONObject probe=new org.json.JSONObject(Ku9HttpClient.requestJson(base+"/large","GET",
                        "{\"Connection\":\"close\",\"Range\":\"bytes=0-187\",\"X-Ku9-Response-Limit\":188}","",true,8*1024*1024));
                check(probe.getInt("code")==200&&probe.getString("body").length()==188&&!leakedLimit.get(),
                        "ignored Range probe is bounded; host option is not sent upstream");
                probe=new org.json.JSONObject(Ku9HttpClient.requestJson(base+"/ok","GET",
                        "{\"connection\":\" close \"}","",true,1024));
                check(probe.getInt("code")==200,"Lowercase Connection must keep successful HTTP1 node: "+probe);
                server.close();responder.join(1000);
            }
            result.putString("stream","PASS HTTP1 CDN failover, successful-node reuse, GET retry limit, no POST/404 retries\n");
            finish(-1,result);
        } catch (Throwable error) {
            result.putString("stream","FAIL "+error+"\n");finish(0,result);
        } finally {
            try {field("client").set(null,savedClient);field("factory").set(null,savedFactory);
                field("http1Factory").set(null,savedHttp1);}catch(Exception ignored) { }
        }
    }
    private int request(String url,String method) throws Exception {
        HttpURLConnection connection=NetworkClient.openHttp1(new URL(url),3000);
        try {connection.setRequestMethod(method);connection.setRequestProperty("Connection","close");
            return connection.getResponseCode();}finally{connection.disconnect();}
    }
    private void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
