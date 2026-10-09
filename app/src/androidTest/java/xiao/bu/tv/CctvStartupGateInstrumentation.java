package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** A slow second segment must not hold a ready first segment behind the startup gate. */
public final class CctvStartupGateInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static Object field(Object target,String name) throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();int code=-1;
        try {
            NetworkClient.initialize(getTargetContext());
            for(int playableCount:new int[]{0,1,2}) verify(playableCount,report);
            verifyCmgPrefetch(report);
        } catch(Throwable error) {code=0;report.append(android.util.Log.getStackTraceString(error));}
        result.putString("stream",report.toString());finish(code,result);
    }
    private void verifyCmgPrefetch(StringBuilder report) throws Exception {
        HlsProxyServer proxy=new HlsProxyServer(getTargetContext(),true,true,true,
                2,true,"high",2,1,1);
        ServerSocket server=new ServerSocket(0);
        CountDownLatch downloaded=new CountDownLatch(2),release=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        Thread upstream=new Thread(()->{try {
            while(!server.isClosed())try(Socket socket=server.accept()) {
                java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                String line;while((line=reader.readLine())!=null && line.length()>0){}
                byte[] body=new byte[188*5];for(int i=0;i<body.length;i+=188)body[i]=0x47;
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length
                        +"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                socket.getOutputStream().write(body);socket.getOutputStream().flush();
                requests.incrementAndGet();downloaded.countDown();
            }
        }catch(Exception ignored){}});upstream.setDaemon(true);upstream.start();
        java.util.List<FutureTask<byte[]>> pending=new java.util.ArrayList<>();
        try {
            proxy.start();
            ExecutorService decrypt=(ExecutorService)field(proxy,"cctvPrefetchWorkers");
            decrypt.submit(()->{try {release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            Method prefetch=HlsProxyServer.class.getDeclaredMethod("prefetchCmgSegment",String.class);
            prefetch.setAccessible(true);
            String base="http://127.0.0.1:"+server.getLocalPort();
            Map<String,FutureTask<byte[]>> tasks=(Map<String,FutureTask<byte[]>>)field(proxy,"cmgSegmentTasks");
            Map<String,byte[]> cache=(Map<String,byte[]>)field(proxy,"cmgSegmentCache");
            Method get=HlsProxyServer.class.getDeclaredMethod("getCmgSegment",String.class);get.setAccessible(true);
            byte[] completed={1};
            FutureTask<byte[]> reader=new FutureTask<>(()->(byte[])get.invoke(proxy,base+"/cached.ts"));
            Thread reading=new Thread(reader,"cmg-cache-transition");reading.setDaemon(true);
            synchronized(tasks) {
                reading.start();long until=SystemClock.elapsedRealtime()+1000;
                while(reading.getState()!=Thread.State.BLOCKED && SystemClock.elapsedRealtime()<until)SystemClock.sleep(10);
                if(reading.getState()!=Thread.State.BLOCKED)throw new AssertionError("Cache reader did not reach task lock");
                synchronized(cache){cache.put(base+"/cached.ts",completed);}
            }
            if(reader.get(1000,TimeUnit.MILLISECONDS)!=completed)
                throw new AssertionError("Completed cache transition started duplicate CMG work");
            Field last=HlsProxyServer.class.getDeclaredField("lastCctvRequestedUrl");
            last.setAccessible(true);last.set(proxy,null);
            java.util.List<String> playlist=Arrays.asList(base+"/unused0.ts",base+"/unused1.ts",
                    base+"/first.ts",base+"/second.ts",base+"/third.ts");
            Map<String,String> next=(Map<String,String>)field(proxy,"cctvNextSegments");
            for(int i=0;i+1<playlist.size();i++)next.put(playlist.get(i),playlist.get(i+1));
            Method window=HlsProxyServer.class.getDeclaredMethod("buildCmgPrefetchWindowFromUrlsLocked",java.util.List.class);
            window.setAccessible(true);
            if(!Arrays.asList(base+"/first.ts",base+"/second.ts").equals(window.invoke(proxy,playlist)))
                throw new AssertionError("CMG prefetch does not match IJK live_start_index=-3");
            for(String part:new String[]{"first","second","first","second"})
                prefetch.invoke(proxy,base+"/"+part+".ts");
            if(!downloaded.await(3000,TimeUnit.MILLISECONDS))
                throw new AssertionError("CMG I/O waited behind the occupied decrypt worker");
            SystemClock.sleep(100);
            if(requests.get()!=2)throw new AssertionError("CMG duplicate downloads: "+requests.get());
            if(((java.util.concurrent.atomic.AtomicInteger)field(proxy,"cmgTsRequestIndex")).get()!=0)
                throw new AssertionError("CMG state advanced outside the ordered worker");
            synchronized(tasks){pending.addAll(tasks.values());}
            proxy.close();
            for(FutureTask<byte[]> task:pending)if(!task.isCancelled())
                throw new AssertionError("CMG task survived channel close");
            report.append("PASS CMG prefetch matches IJK start, overlaps occupied decrypt worker, coalesces requests, and cancels on close\n");
        } finally {proxy.close();release.countDown();server.close();}
    }
    private void verify(int playableCount,StringBuilder report) throws Exception {
        HlsProxyServer proxy=new HlsProxyServer(getTargetContext(),false,true,true,
                2,true,"high",2,playableCount,1);
        ServerSocket server=new ServerSocket(0);Socket[] accepted={null};
        CountDownLatch ready=new CountDownLatch(1);
        ExecutorService gateWorker=Executors.newSingleThreadExecutor();
        FutureTask<byte[]> second=null;
        Thread stalled=new Thread(()->{try {
            accepted[0]=server.accept();while(accepted[0].getInputStream().read()!=-1){}
        } catch(Exception ignored){}});stalled.setDaemon(true);stalled.start();
        try {
            proxy.start();
            String base="http://127.0.0.1:"+server.getLocalPort();
            String firstUrl=base+"/first.ts",secondUrl=base+"/second.ts";
            Class<?> segment=Class.forName("xiao.bu.tv.HlsProxyServer$PlaylistSegment");
            Constructor<?> ctor=segment.getDeclaredConstructor(long.class,String.class,java.util.List.class);
            ctor.setAccessible(true);
            java.util.List<Object> segments=Arrays.asList(
                    ctor.newInstance(1L,firstUrl,Collections.emptyList()),
                    ctor.newInstance(2L,secondUrl,Collections.emptyList()));
            Map<String,byte[]> cache=(Map<String,byte[]>)field(proxy,"cctvSegmentCache");
            byte[] body=new byte[188*5];
            synchronized(cache){cache.put(firstUrl,body);}
            Map<String,FutureTask<byte[]>> tasks=(Map<String,FutureTask<byte[]>>)field(proxy,"cctvSegmentTasks");
            second=new FutureTask<>(()->{
                ready.await();synchronized(cache){cache.put(secondUrl,body);}return body;
            });
            synchronized(tasks){tasks.put(secondUrl,second);}
            Thread secondWorker=new Thread(second,"held-second-segment");
            secondWorker.setDaemon(true);secondWorker.start();
            Method method=HlsProxyServer.class.getDeclaredMethod("ensureCctvStartupGate",String.class,java.util.List.class);
            method.setAccessible(true);
            long began=SystemClock.elapsedRealtime();
            Future<Boolean> gate=gateWorker.submit(()->(Boolean)method.invoke(proxy,base+"/live.m3u8",segments));
            if(playableCount==0) {
                if(gate.get(1000,TimeUnit.MILLISECONDS))throw new AssertionError("Low mode opened a preload gate");
                if(second.isDone())throw new AssertionError("Low mode waited for preload");
                report.append("PASS low mode skips the preload gate\n");
            } else if(playableCount==1) {
                if(!gate.get(1000,TimeUnit.MILLISECONDS))throw new AssertionError("Startup did not open");
                if(second.isDone())throw new AssertionError("Second segment unexpectedly ready");
                report.append("PASS one-playable mode opens while second segment is stalled elapsedMs=")
                        .append(SystemClock.elapsedRealtime()-began).append('\n');
            } else {
                SystemClock.sleep(500);
                if(gate.isDone())throw new AssertionError("Two-playable mode opened before second segment");
                ready.countDown();
                if(!gate.get(3000,TimeUnit.MILLISECONDS))throw new AssertionError("Two-playable gate did not open");
                report.append("PASS two-playable mode waits for both segments\n");
            }
        } finally {
            ready.countDown();server.close();if(accepted[0]!=null)accepted[0].close();
            if(second!=null)second.cancel(true);
            proxy.close();gateWorker.shutdownNow();
        }
    }
}
