package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;

/** Inject transient stream resets into the actual component HTTP stack. */
public final class CjsDownloadInstrumentation extends Instrumentation {
    private ServerSocket server;
    private volatile String mode="";
    private final AtomicInteger attempts=new AtomicInteger();
    private final AtomicInteger responses=new AtomicInteger();
    private final List<Boolean> http1=new ArrayList<>();
    private final List<Integer> deadlines=new ArrayList<>();
    private Method download;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void pass(String text){Bundle b=new Bundle();b.putString("stream","PASS "+text+"\n");sendStatus(0,b);}
    private void set(String name,Object value)throws Exception{Field f=NetworkClient.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private Object get(String name)throws Exception{Field f=NetworkClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static Object member(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void builderField(OkHttpClient.Builder builder,String name,Object value)throws Exception{Field f=OkHttpClient.Builder.class.getDeclaredField(name);f.setAccessible(true);f.set(builder,value);}
    private byte[] read(String url,int limit)throws Exception {
        try{return (byte[])download.invoke(null,url,limit);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}
    }
    private void reset(String value){mode=value;attempts.set(0);responses.set(0);http1.clear();deadlines.clear();}
    @Override public void onStart(){
        Bundle result=new Bundle();int status=-1;Object oldClient=null,oldFactory=null;
        try {
            oldClient=get("client");oldFactory=get("factory");
            download=CjsPluginRuntime.class.getDeclaredMethod("download",String.class,int.class);download.setAccessible(true);
            server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
            new Thread(()->{
                while(!server.isClosed())try(Socket socket=server.accept()){
                    socket.setSoTimeout(2000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
                    if(input.readLine()==null)continue;String line;while((line=input.readLine())!=null&&!line.isEmpty()){}
                    int n=responses.incrementAndGet();boolean shortBody="truncated".equals(mode)&&n==1;
                    byte[] data=(shortBody?"abc":"plugin-body").getBytes("UTF-8");
                    int code="forbidden".equals(mode)?403:200;
                    OutputStream output=socket.getOutputStream();
                    output.write(("HTTP/1.1 "+code+" Test\r\nConnection: close\r\nContent-Length: 11\r\n\r\n").getBytes("UTF-8"));
                    output.write(data);output.flush();
                }catch(IOException ignored){}
            },"plugin-download-fixture").start();
            java.util.concurrent.atomic.AtomicBoolean resetRead=new java.util.concurrent.atomic.AtomicBoolean();
            javax.net.SocketFactory sockets=new javax.net.SocketFactory(){
                public Socket createSocket()throws IOException {
                    return new Socket(){
                        private InputStream body;
                        @Override public InputStream getInputStream()throws IOException {
                            if(body==null)body=new FilterInputStream(super.getInputStream()){
                                private void checkReset()throws IOException {
                                    if(resetRead.compareAndSet(true,false))throw new okhttp3.internal.http2.StreamResetException(okhttp3.internal.http2.ErrorCode.CANCEL);
                                }
                                @Override public int read()throws IOException {checkReset();return super.read();}
                                @Override public int read(byte[] bytes,int offset,int count)throws IOException {checkReset();return in.read(bytes,offset,count);}
                            };
                            return body;
                        }
                    };
                }
                private Socket connect(SocketAddress remote,SocketAddress local)throws IOException {Socket socket=createSocket();if(local!=null)socket.bind(local);socket.connect(remote);return socket;}
                public Socket createSocket(String host,int port)throws IOException{return connect(new InetSocketAddress(host,port),null);}
                public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return connect(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort));}
                public Socket createSocket(InetAddress host,int port)throws IOException{return connect(new InetSocketAddress(host,port),null);}
                public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return connect(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort));}
            };
            OkHttpClient.Builder builder=NetworkClient.sharedClient().newBuilder().dns(Dns.SYSTEM);
            // These setters are unused by production and removed in minified APKs.
            // Configure retained fields rather than keeping extra release APIs.
            builderField(builder,"socketFactory",sockets);
            okhttp3.EventListener listener=new okhttp3.EventListener(){
                        @Override public void connectionAcquired(Call call,Connection connection){
                            try{synchronized(http1){http1.add(connection.route().address().protocols().equals(Collections.singletonList(Protocol.HTTP_1_1)));deadlines.add(((OkHttpClient)member(call,"client")).callTimeoutMillis());}}catch(Exception error){throw new AssertionError(error);}
                        }
                        @Override public void requestHeadersStart(Call call){
                            int n=attempts.incrementAndGet();resetRead.set("reset".equals(mode)&&n==1 || "always-reset".equals(mode));
                        }
                    };
            builderField(builder,"eventListenerFactory",(okhttp3.EventListener.Factory)call -> listener);
            OkHttpClient client=builder.build();
            set("client",client);set("factory",new OkUrlFactory(client));
            String url="http://127.0.0.1:"+server.getLocalPort()+"/runtime.json";
            for(String failure:new String[]{"reset","truncated"}){
                reset(failure);check(Arrays.equals(read(url,1024),"plugin-body".getBytes("UTF-8")),"Incomplete response escaped");
                check(attempts.get()==2&&!http1.get(0)&&http1.get(1),"Missing single HTTP/1 fallback attempts="+attempts.get()+" http1="+http1);
                check(deadlines.get(0)>0&&deadlines.get(1)<=deadlines.get(0)&&deadlines.get(0)<=8000,"Retry reset request budget");
                pass(failure+": complete fresh body, exactly one HTTP/1.1 retry, original time budget");
            }
            for(String failure:new String[]{"always-reset","forbidden","oversized"}){
                reset(failure);try{read(url,"oversized".equals(failure)?3:1024);throw new AssertionError("Expected failure");}catch(IOException expected){}
                check(attempts.get()==("always-reset".equals(failure)?2:1),"Unexpected retry count: "+failure);
                pass(failure+": bounded failure, no retry loop");
            }
            reset("interrupted");final Throwable[] workerError={null};Thread worker=new Thread(()->{
                Thread.currentThread().interrupt();
                try{read(url,1024);workerError[0]=new AssertionError("Interrupted download started");}
                catch(InterruptedIOException expected){}catch(Throwable error){workerError[0]=error;}
            });worker.start();worker.join(3000);
            check(!worker.isAlive()&&workerError[0]==null&&attempts.get()==0,"Cancellation was retried");
            pass("explicit thread interruption makes zero requests");
            result.putString("stream","ALL PLUGIN DOWNLOAD CHECKS PASSED\n");
        }catch(Throwable error){status=0;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{try{if(server!=null)server.close();set("client",oldClient);set("factory",oldFactory);}catch(Exception ignored){}}
        finish(status,result);
    }
}
