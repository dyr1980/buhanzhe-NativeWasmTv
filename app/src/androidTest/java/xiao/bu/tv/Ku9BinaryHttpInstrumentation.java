package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/** Binary POST/response must survive UTF-8 bridges and decompression limits. */
public final class Ku9BinaryHttpInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            NetworkClient.initialize(getTargetContext());
            checkSignedPlaylistRefresh();
            byte[] payload = new byte[] {0, (byte) 255, (byte) 128, 1, 31, (byte) 195};
            AtomicReference<Throwable> failure = new AtomicReference<>();
            try (ServerSocket server = new ServerSocket(0)) {
                server.setSoTimeout(10000);
                Thread responder = new Thread(() -> {
                    try {
                        for (int attempt=0; attempt<3; attempt++) try (Socket socket=server.accept()) {
                            socket.setSoTimeout(5000);
                            InputStream input=socket.getInputStream();
                            ByteArrayOutputStream head=new ByteArrayOutputStream();
                            while (head.size()<8192) {
                                int b=input.read(); if (b<0) throw new AssertionError("Missing request headers");
                                head.write(b); byte[] raw=head.toByteArray(); int n=raw.length;
                                if(n>=4&&raw[n-4]==13&&raw[n-3]==10&&raw[n-2]==13&&raw[n-1]==10) break;
                            }
                            String headers=head.toString("UTF-8");
                            check(!headers.toLowerCase(java.util.Locale.US).contains("x-ku9-"), "Host options leaked");
                            int length=0;
                            for(String line:headers.split("\r\n")) if(line.toLowerCase(java.util.Locale.US).startsWith("content-length:"))
                                length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                            check(length==payload.length,"Binary request length");
                            byte[] body=new byte[length];
                            for(int p=0;p<length;) {int n=input.read(body,p,length-p);if(n<0)throw new AssertionError("Truncated POST");p+=n;}
                            check(Arrays.equals(body,payload),"Binary POST corrupted");
                            ByteArrayOutputStream compressed=new ByteArrayOutputStream();
                            try(GZIPOutputStream gzip=new GZIPOutputStream(compressed)){gzip.write(payload);}
                            byte[] bytes=compressed.toByteArray();
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                            socket.getOutputStream().write(bytes);
                        }
                    } catch(Throwable error) {failure.set(error);}
                },"ku9-binary-regression");
                responder.start();
                String url="http://127.0.0.1:"+server.getLocalPort(),headers="{\"Connection\":\"close\",\"Accept-Encoding\":\"gzip\",\"X-Ku9-Request-Encoding\":\"base64\",\"X-Ku9-Response-Encoding\":\"base64\"}";
                String body=Base64.encodeToString(payload,Base64.NO_WRAP);
                JSONObject response=new JSONObject(Ku9HttpClient.requestJson(url,"POST",headers,body,false,1024));
                check(response.optInt("code")==200&&"base64".equals(response.optString("bodyEncoding")), "Missing binary response: "+response);
                check(Arrays.equals(payload,Base64.decode(response.getString("body"),Base64.DEFAULT)),"Binary response corrupted");
                String limited=new JSONObject(headers).put("X-Ku9-Response-Limit",3).toString();
                response=new JSONObject(Ku9HttpClient.requestJson(url,"POST",limited,body,false,1024));
                check(response.optInt("code")==200&&Arrays.equals(Arrays.copyOf(payload,3),
                        Base64.decode(response.getString("body"),Base64.DEFAULT)),"Binary prefix ignored response limit");
                response=new JSONObject(Ku9HttpClient.requestJson(url,"POST",headers,body,false,3));
                check(response.optInt("code")==0&&response.optString("error").contains("限制"),"Inflated response exceeds bound");
                responder.join(10000);check(!responder.isAlive(),"Responder did not finish");
                if(failure.get()!=null)throw new AssertionError(failure.get());
            }
            result.putString("stream","PASS signed playlist refresh keeps unique sequences; binary POST + gzip/base64 response, no host-header leak, inflated size bound\n");finish(-1,result);
        } catch(Throwable error) {result.putString("stream","FAIL "+android.util.Log.getStackTraceString(error));finish(0,result);}
    }
    private static void checkSignedPlaylistRefresh() throws Exception {
        HlsProxyServer proxy=new HlsProxyServer(null,false,false,false,0,false,"high",0,0,0);
        proxy.start();
        try {
        java.lang.reflect.Method rewrite=HlsProxyServer.class.getDeclaredMethod("rewriteBufferedMediaPlaylist",String.class,java.net.URI.class,String[].class);
        rewrite.setAccessible(true);
        String url="https://cdn.test/live.m3u8";
        String first="#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:10\n#EXTINF:5,\na.ts?sig=one\n#EXTINF:5,\nb.ts?sig=one\n";
        rewrite.invoke(proxy,url,new java.net.URI(url),first.split("\n"));
        String refreshed=(String)rewrite.invoke(proxy,url,new java.net.URI(url),first.replace("sig=one","sig=two").split("\n"));
        check(refreshed.contains("MEDIA-SEQUENCE:10")&&refreshed.contains("aHR0cHM6Ly9jZG4udGVzdC9hLnRzP3NpZz10d28="),"Signed refresh must keep both unique segments");
        check(refreshed.split("#EXTINF:",-1).length==3,"Duplicate signed segments");
        String shifted="#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:11\n#EXTINF:5,\nb.ts?sig=three\n#EXTINF:5,\nc.ts?sig=three\n";
        refreshed=(String)rewrite.invoke(proxy,url,new java.net.URI(url),shifted.split("\n"));
        check(refreshed.contains("MEDIA-SEQUENCE:11")&&refreshed.split("#EXTINF:",-1).length==3,"Window must advance exactly once");
        } finally {proxy.close();}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
