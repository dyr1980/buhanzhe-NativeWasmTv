package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLSocket;
import org.json.JSONObject;

/** Regression for Bugly 1040: high-fd TLS and cancellation of old proxy requests. */
public final class NetworkCrashInstrumentation extends Instrumentation {
    private Bundle args;
    public void onCreate(Bundle args) { super.onCreate(args); this.args=args;start(); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static int maxFd() {
        int max=0;String[] entries=fdEntries();
        if(entries==null)throw new AssertionError("Cannot inspect process descriptors");
        for(String entry:entries)try{max=Math.max(max,Integer.parseInt(entry));}catch(NumberFormatException ignored){}
        return max;
    }
    private static String[] fdEntries() {String[] files=new File("/proc/self/fd").list();return files==null?new File("/proc/"+android.os.Process.myPid()+"/fd").list():files;}
    private static int fdCount() { String[] files=fdEntries();return files==null?-1:files.length; }
    private static int fdSoftLimit() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/limits"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Max open files")) {
                    String value = line.trim().split("\\s+")[3];
                    return "unlimited".equals(value) ? Integer.MAX_VALUE : Integer.parseInt(value);
                }
            }
        }
        throw new IOException("Cannot determine descriptor limit");
    }
    private static void cancellation() throws Exception {
        final ServerSocket server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        final CountDownLatch accepted=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread peer=new Thread(new Runnable(){public void run(){
            try(Socket socket=server.accept()) {
                socket.getInputStream().read();accepted.countDown();release.await(10,TimeUnit.SECONDS);
            }catch(Exception ignored){}
        }},"cancel-test-peer");peer.start();
        HlsProxyServer proxy=new HlsProxyServer(null,true,false,true,2,true,"high",2,2,0);proxy.start();
        final Throwable[] failure=new Throwable[1];
        final ProxyHttpConnections owner=(ProxyHttpConnections)field(proxy,"upstreamConnections");
        final HttpURLConnection request=owner.track(NetworkClient.open(new URL("http://127.0.0.1:"+server.getLocalPort()+"/wait")));
        request.setConnectTimeout(1000);request.setReadTimeout(15000);
        Thread reader=new Thread(new Runnable(){public void run(){try{request.getResponseCode();}catch(Throwable error){failure[0]=error;}}},"cancel-test-reader");
        try {
            reader.start();check(accepted.await(3,TimeUnit.SECONDS),"Upstream request never started");
            long start=SystemClock.elapsedRealtime();proxy.close();reader.join(2000);
            check(!reader.isAlive()&&failure[0] instanceof IOException,"Proxy close failed to cancel blocked HTTP: "+failure[0]);
            check(SystemClock.elapsedRealtime()-start<2000&&((Set<?>)field(owner,"active")).isEmpty(),"In-flight request registry not released");
            try{request.getResponseCode();throw new AssertionError("Cancelled request restarted");}catch(IOException expected){}
            try{owner.track(NetworkClient.open(new URL("http://127.0.0.1:"+server.getLocalPort()+"/late")));throw new AssertionError("Closed proxy accepted request");}catch(IOException expected){}
        }finally{proxy.close();release.countDown();server.close();peer.join(2000);reader.join(2000);}
    }
    private static Object field(Object object,String name) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    public void onStart() {
        Bundle result=new Bundle();List<FileInputStream> held=new ArrayList<FileInputStream>();
        try {
            NetworkClient.initialize(getTargetContext());
            check(TlsCompat.usesLegacyTransport(14)&&TlsCompat.usesLegacyTransport(23)&&!TlsCompat.usesLegacyTransport(24),"TLS API boundary");
            int before=fdCount();for(int i=0;i<5;i++)cancellation();SystemClock.sleep(300);
            int warm=fdCount();for(int i=5;i<30;i++)cancellation();SystemClock.sleep(1000);
            int after=fdCount();if(before<0||warm<0||after<0){result.putString("stream","PASS 30 proxy cancellation cycles; SKIP descriptor leak/pressure: kernel denies FD enumeration\n");finish(-1,result);return;}
            check(after<=warm+4,"Proxy switches accumulated descriptors after warmup: "+warm+" -> "+after);
            // Old app processes often have a 1024 descriptor soft limit. Keep
            // headroom for directory enumeration, TLS sockets and test logging.
            int limit=fdSoftLimit(), target=Math.min(1100,limit-64);
            check(target>maxFd(),"Insufficient descriptor headroom");
            while(maxFd()<target&&held.size()<4096)held.add(new FileInputStream("/dev/null"));
            check(maxFd()>=target,"Could not create descriptor pressure condition");
            long started=SystemClock.elapsedRealtime();int peak=maxFd();
            URL endpoint=new URL(args.getString("endpoint","https://raw.githubusercontent.com/TvWasm/cjs/main/README.md"));
            // Explicit factory exercises the exact transport used on Android 4-6,
            // even when this instrumentation runs on a modern emulator.
            for(int i=0;i<3;i++) {
                Socket transport=new Socket();transport.connect(new InetSocketAddress(endpoint.getHost(),endpoint.getPort()<0?443:endpoint.getPort()),10000);
                SSLSocket tls=null;
                try {
                    tls=(SSLSocket)new LegacyTlsSocket.Factory(TlsCompat.trustManager()).createSocket(transport,endpoint.getHost(),endpoint.getPort()<0?443:endpoint.getPort(),true);
                    tls.setSoTimeout(10000);peak=Math.max(peak,maxFd());tls.startHandshake();
                    tls.getOutputStream().write(("GET "+endpoint.getFile()+" HTTP/1.1\r\nHost: "+endpoint.getHost()+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    ByteArrayOutputStream body=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                    while((count=tls.getInputStream().read(buffer))!=-1){body.write(buffer,0,count);check(body.size()<2*1024*1024,"Unexpected response size");}
                    check(new String(body.toByteArray(),"UTF-8").startsWith("HTTP/1.1 200"),"High-FD HTTPS did not succeed");
                }finally{if(tls!=null)tls.close();transport.close();}
            }
            for(FileInputStream file:held)file.close();held.clear();SystemClock.sleep(300);
            JSONObject report=new JSONObject().put("abi",BuildConfig.CJS_PLUGIN_ABI).put("sdk",android.os.Build.VERSION.SDK_INT)
                .put("proxyCloseCycles",30).put("fdBefore",before).put("fdAfterWarmup",warm).put("fdAfterCycles",after).put("peakFd",peak)
                .put("fdSoftLimit",limit).put("aboveFdSetSize",peak>=1024).put("highFdHttpsRequests",3).put("tlsMs",SystemClock.elapsedRealtime()-started).put("fdFinal",fdCount());
            report.put("endpoint",endpoint.toString());
            try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"network-crash-1040.json"))){file.write(report.toString(2).getBytes("UTF-8"));}
            result.putString("stream","PASS Bugly 1040 regression "+report+"\n");finish(-1,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(1,result);}
        finally{for(FileInputStream file:held)try{file.close();}catch(IOException ignored){}}
    }
}
