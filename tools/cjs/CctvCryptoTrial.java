package xiao.bu.cryptotrial;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native-only test of captured encrypted segments; excludes network and player. */
public final class CctvCryptoTrial extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    static native byte[] decode(byte[] input);
    static native String profile();
    static native void release();
    static byte[] read(File f)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        return out.toByteArray();
    }
    static String hash(byte[] b)throws Exception {
        if(b==null)throw new AssertionError("Native decrypt rejected segment");
        StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format(Locale.US,"%02x",v&255));return s.toString();
    }
    public void onStart(){Bundle result=new Bundle();try {
        String variant=args.getString("variant","cctv_base");
        System.load(new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"lib"+variant+".so").getPath());
        final int count=Integer.parseInt(args.getString("count","3"));
        final byte[][] fixtures=new byte[count][];
        for(int i=0;i<count;i++)fixtures[i]=read(new File(args.getString("fixtures"),"cctv-"+i+".ts"));
        JSONArray rows=new JSONArray();final String[] hashes=new String[count];
        String[] threadOptions=args.getString("threads","1,2,3").split(",");
        int repeats=Integer.parseInt(args.getString("repeats","3"));
        for(String threadOption:threadOptions)for(int repeat=0;repeat<repeats;repeat++) {
            final int threads=Integer.parseInt(threadOption);
            ExecutorService pool=Executors.newFixedThreadPool(threads);
            final CountDownLatch ready=new CountDownLatch(threads),go=new CountDownLatch(1);
            List<Future<JSONArray>> tasks=new ArrayList<Future<JSONArray>>();
            for(int worker=0;worker<threads;worker++) {
                final int workerIndex=worker;
                tasks.add(pool.submit(new Callable<JSONArray>(){public JSONArray call()throws Exception {
                    JSONArray samples=new JSONArray();ready.countDown();go.await();
                    try {for(int i=workerIndex;i<count*2;i+=threads) {
                        int segment=i%count;byte[] bytes=fixtures[segment].clone();long start=System.nanoTime();
                        byte[] clear=decode(bytes);long elapsed=System.nanoTime()-start;String digest=hash(clear);
                        synchronized(hashes){if(hashes[segment]!=null&&!hashes[segment].equals(digest))throw new AssertionError("Parallel/serial output mismatch");hashes[segment]=digest;}
                        samples.put(new JSONObject().put("segment",segment).put("decodeNs",elapsed).put("sha256",digest).put("profile",new JSONObject(profile())));
                    }}finally{release();}return samples;
                }}));
            }
            ready.await();long start=System.nanoTime();go.countDown();JSONArray samples=new JSONArray();
            try {for(Future<JSONArray> task:tasks){JSONArray a=task.get(120,TimeUnit.SECONDS);for(int i=0;i<a.length();i++)samples.put(a.get(i));}}
            finally{pool.shutdownNow();}
            JSONObject row=new JSONObject().put("variant",variant).put("threads",threads).put("repeat",repeat).put("batchNs",System.nanoTime()-start).put("samples",samples);
            rows.put(row);Bundle update=new Bundle();update.putString("stream",variant+" threads="+threads+" batchMs="+row.getLong("batchNs")/1000000+"\n");sendStatus(0,update);
        }
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),variant+".json"))){out.write(rows.toString(2).getBytes("UTF-8"));}
        result.putString("stream","PASS "+variant+": output hashes verified\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(1,result);}}
}
