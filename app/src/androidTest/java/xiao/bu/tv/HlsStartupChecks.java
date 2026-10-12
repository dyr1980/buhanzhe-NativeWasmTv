package xiao.bu.tv;

import android.app.Activity;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real proxy requests against a local TS source: no duplicate startup downloads. */
final class HlsStartupChecks {
    static String radioForwarding(Activity activity)throws Exception {
        ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));
        CountDownLatch release=new CountDownLatch(1);
        Thread source=new Thread(()->{
            try(Socket socket=server.accept()) {
                BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
                String line;while((line=input.readLine())!=null&&line.length()>0){}
                OutputStream output=socket.getOutputStream();
                output.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
                output.write(new byte[]{42,43,44});output.flush();
                release.await(5,TimeUnit.SECONDS);
            }catch(Exception ignored){}
        },"paced-radio-fixture");source.start();
        HlsProxyServer proxy=new HlsProxyServer(activity,false,true,false,2,false,"high",0,0,0);
        HttpURLConnection connection=null;
        try {
            proxy.start();
            connection=(HttpURLConnection)new URL(proxy.mediaUrl("http://127.0.0.1:"+server.getLocalPort()+"/radio")).openConnection();
            connection.setConnectTimeout(1500);connection.setReadTimeout(1500);
            long started=android.os.SystemClock.elapsedRealtime();
            try(InputStream input=connection.getInputStream()) {
                if(input.read()!=42||input.read()!=43||input.read()!=44)throw new AssertionError("Paced radio bytes changed");
            }
            if(android.os.SystemClock.elapsedRealtime()-started>=1500)
                throw new AssertionError("Live audio waited for the output buffer to fill");
            return "PASS paced audio forwarding without waiting for 8 KiB or end-of-stream\n";
        }finally{release.countDown();if(connection!=null)connection.disconnect();proxy.close();server.close();source.join(2000);}
    }
    static String run(Activity activity,File fixture)throws Exception {
        ServerSocket server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        ExecutorService workers=Executors.newCachedThreadPool();
        ConcurrentHashMap<String,AtomicInteger> counts=new ConcurrentHashMap<>();
        CountDownLatch stalled=new CountDownLatch(1),allow=new CountDownLatch(1),readDone=new CountDownLatch(1);
        Thread accept=new Thread(()->{
            while(!server.isClosed())try {
                Socket socket=server.accept();workers.execute(()->{
                    try(Socket owned=socket) {
                        BufferedReader input=new BufferedReader(new InputStreamReader(owned.getInputStream(),"UTF-8"));
                        String request=input.readLine(),line;while((line=input.readLine())!=null&&line.length()>0){}
                        String path=request.split(" ")[1].substring(1);
                        AtomicInteger count=counts.get(path);if(count==null){counts.putIfAbsent(path,new AtomicInteger());count=counts.get(path);}count.incrementAndGet();
                        byte[] body;
                        if(path.endsWith("index.m3u8")) {
                            StringBuilder list=new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:0\n");
                            if(path.startsWith("aes/"))list.append("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x00000000000000000000000000000000\n");
                            for(int n=0;n<8;n++)list.append("#EXTINF:2,\nclip00").append(n).append(".ts\n");
                            body=list.toString().getBytes("UTF-8");
                        }else if(path.equals("aes/key.bin"))body=new byte[16];
                        else {
                            body=read(new File(fixture,path.equals("slow.ts")?"clip000.ts":path.substring(path.lastIndexOf('/')+1)));
                            if(path.startsWith("aes/")) {
                                javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
                                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(new byte[16],"AES"),
                                        new javax.crypto.spec.IvParameterSpec(new byte[16]));
                                body=cipher.doFinal(body);
                            }
                        }
                        owned.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: "+(path.endsWith("m3u8")?"application/vnd.apple.mpegurl":"video/MP2T")
                                +"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                        if(path.equals("slow.ts")) {
                            int prefix=Math.min(body.length-1,64*1024);owned.getOutputStream().write(body,0,prefix);owned.getOutputStream().flush();
                            stalled.countDown();allow.await(10,TimeUnit.SECONDS);owned.getOutputStream().write(body,prefix,body.length-prefix);
                        }else owned.getOutputStream().write(body);
                    }catch(Exception ignored){}
                });
            }catch(IOException ignored){}
        },"hls-startup-fixture");accept.start();
        HlsProxyServer proxy=new HlsProxyServer(activity,false,true,false,2,false,"high",0,0,2);
        try {
            proxy.start();
            proxy.enableSourceVideoProbe();
            String origin="http://127.0.0.1:"+server.getLocalPort()+"/";
            HlsSwitchSession.Prepared ready=proxy.prepareChannel(origin+"index.m3u8");
            if(counts.containsKey("clip006.ts"))throw new AssertionError("Clear HLS waited for an unnecessary second startup segment");
            if(!ready.eligible)throw new AssertionError("Fixture must support HLS hot switching");
            proxy.enableChannelSession(ready);
            proxy.enableSourceVideoProbe();
            String list=Ku9HttpClient.getText(proxy.playerHlsUrl(origin+"index.m3u8"),null,64*1024);
            int media=0;for(String line:list.split("\n"))if(line.startsWith("http")&&media++<2) {
                HttpURLConnection connection=(HttpURLConnection)new URL(line).openConnection();
                try(InputStream input=connection.getInputStream()){byte[] block=new byte[8192];while(input.read(block)>=0){}}
                finally{connection.disconnect();}
            }
            if(counts.get("clip005.ts").get()!=1||counts.get("clip006.ts").get()!=1)
                throw new AssertionError("Startup probe and player downloaded the same segment more than once");
            if(Math.abs(proxy.sourceVideoFrameRate()-25f)>0.1f||!"AAC".equals(proxy.sourceAudioCodec()))
                throw new AssertionError("Shared startup files must retain FPS/audio diagnostics");
            long deadline=android.os.SystemClock.elapsedRealtime()+5000;
            while(proxy.sourceVideoDescription().bitDepth==0 && android.os.SystemClock.elapsedRealtime()<deadline)
                android.os.SystemClock.sleep(50);
            VideoStreamDescription metadata=proxy.sourceVideoDescription();
            if(metadata.bitDepth!=8||!"SDR".equals(metadata.dynamicRange)
                    ||!metadata.colorInferred||!"BT.601-525".equals(metadata.colorPrimaries))
                throw new AssertionError("Cached IJK source description: "+metadata.debugLabel());
            String slow=proxy.proxyUrl(origin+"slow.ts");
            Thread reader=new Thread(()->{
                try {HttpURLConnection connection=(HttpURLConnection)new URL(slow).openConnection();
                    connection.setReadTimeout(5000);try(InputStream input=connection.getInputStream()){byte[] block=new byte[8192];while(input.read(block)>=0){}}
                    finally{connection.disconnect();}
                }catch(IOException expected){}finally{readDone.countDown();}
            },"old-player-http-reader");reader.start();
            if(!stalled.await(4,TimeUnit.SECONDS))throw new AssertionError("Stalled source was not requested");
            android.os.SystemClock.sleep(200);
            proxy.disconnectPlayerRequests();
            if(!readDone.await(1500,TimeUnit.MILLISECONDS))throw new AssertionError("Retired player's HTTP reader did not wake");
            HlsProxyServer encrypted=new HlsProxyServer(activity,false,true,false,2,false,"high",0,0,2);
            try {
                encrypted.start();
                HlsSwitchSession.Prepared aes=encrypted.prepareChannel(origin+"aes/index.m3u8");
                if(aes.eligible||counts.get("aes/clip000.ts")==null||counts.get("aes/clip001.ts")==null
                        ||counts.get("aes/clip000.ts").get()!=1||counts.get("aes/clip001.ts").get()!=1
                        ||counts.containsKey("aes/clip002.ts"))
                    throw new AssertionError("Encrypted HLS did not preserve its configured two-segment reserve");
            }finally{encrypted.close();}
            return "PASS HLS startup: one clear startup segment, encrypted two-segment reserve, shared playback downloads, IJK source description, retained decoder profile, stalled old HTTP reader interrupted\n";
        }finally{allow.countDown();proxy.close();server.close();workers.shutdownNow();accept.join(2000);}
    }
    private static byte[] read(File file)throws IOException {
        try(InputStream input=new FileInputStream(file);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] block=new byte[8192];int n;while((n=input.read(block))>=0)output.write(block,0,n);return output.toByteArray();
        }
    }
}
