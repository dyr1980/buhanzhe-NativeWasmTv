package xiao.bu.cryptotrial;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Isolated native microbenchmark; never loads the production player's plugins. */
public final class CryptoTrial extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle b) { super.onCreate(b); args=b; start(); }
    static native boolean configure(int tag);
    static native int decode(byte[] bytes, int offset, int length);
    static native void close();
    static byte[] read(File f) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(f)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)out.write(b,0,n); }
        return out.toByteArray();
    }
    static String hash(byte[] b) throws Exception {
        StringBuilder s=new StringBuilder();
        for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format(Locale.US,"%02x",v&255));
        return s.toString();
    }
    public void onStart() {
        Bundle result=new Bundle();
        try {
            String variant=args.getString("variant","baseline");
            System.load(new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"lib"+variant+".so").getPath());
            JSONArray rows=new JSONArray();
            // Deliberately synthetic data: validates JNI equivalence, not live-stream keys.
            for(int nalSize:new int[]{256,4096,32768})for(int pesSize:new int[]{65536,1048576}) {
                byte[] seed=new byte[pesSize];new Random(1729+nalSize).nextBytes(seed);
                int offset=127;seed[offset]=0x65;seed[offset+2]=(byte)((seed[offset+2]&252)|1);
                String expected=null;int expectedLength=-99;
                for(int repeat=0;repeat<4;repeat++) {
                    if(!configure(0x12345678))throw new AssertionError("CMG initialization failed");
                    byte[] sample=seed.clone(); long started=System.nanoTime();
                    int len=decode(sample,offset,nalSize);long elapsed=System.nanoTime()-started;
                    if(len<0)throw new AssertionError("Decoder rejected fixture: "+len);
                    String digest=hash(sample);
                    if(expected!=null&&(!expected.equals(digest)||expectedLength!=len))throw new AssertionError("Non-deterministic native output");
                    expected=digest;expectedLength=len;
                    for(int i=0;i<sample.length;i++)if((i<offset||i>=offset+nalSize)&&sample[i]!=seed[i])throw new AssertionError("Write outside NAL");
                    rows.put(new JSONObject().put("variant",variant).put("nalBytes",nalSize).put("pesBytes",pesSize)
                        .put("repeat",repeat).put("decodeNs",elapsed).put("outputLength",len).put("sha256",digest));
                    close();
                }
            }
            if(!configure(0x12345678))throw new AssertionError("CMG initialization failed");
            byte[] guard=new byte[128];String guardHash=hash(guard);
            if(decode(guard,-1,1)!=-1||decode(guard,127,2)!=-1||decode(guard,0,0)!=-1||!guardHash.equals(hash(guard)))throw new AssertionError("Invalid range modified buffer");
            close();
            File report=new File(getTargetContext().getExternalFilesDir(null),variant+".json");
            try(FileOutputStream out=new FileOutputStream(report)){out.write(rows.toString(2).getBytes("UTF-8"));}
            result.putString("stream","PASS "+variant+" "+rows.length()+" measurements, deterministic output and range guards\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(1,result);}
    }
}
