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
}
