package xiao.bu.cryptotrial;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Replays recorded real CMG native calls without network or a video decoder. */
public final class CmgReplayTrial extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    static int integer(DataInputStream in)throws Exception{return Integer.reverseBytes(in.readInt());}
    static long number(DataInputStream in)throws Exception{return Long.reverseBytes(in.readLong());}
    static byte[] bytes(DataInputStream in)throws Exception {
        int len=integer(in);if(len==-1)return null;
        if(len<0||len>32*1024*1024)throw new IOException("Invalid trace field size "+len);
        byte[] b=new byte[len];in.readFully(b);return b;
    }
    static String string(DataInputStream in)throws Exception{byte[] b=bytes(in);return b==null?null:new String(b,"UTF-8");}
    static boolean matches(Object a,Object b){return a instanceof byte[]?Arrays.equals((byte[])a,(byte[])b):Objects.equals(a,b);}
    public void onStart(){Bundle result=new Bundle();try {
        String variant=args.getString("variant","cmg_base");
        System.load(new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"lib"+variant+".so").getPath());
        byte[] metadata=CctvCryptoTrial.read(new File(args.getString("metadata")));
        JSONArray definitions=new JSONArray(new String(metadata,"UTF-8")),rows=new JSONArray();
        Method[] methods=new Method[definitions.length()+1];
        for(int i=0;i<definitions.length();i++) {
            JSONObject def=definitions.getJSONObject(i);JSONArray types=def.getJSONArray("args");Class<?>[] classes=new Class<?>[types.length()];
            for(int n=0;n<classes.length;n++){String t=types.getString(n);classes[n]="jstring".equals(t)?String.class:"jbyteArray".equals(t)?byte[].class:"jlong".equals(t)?long.class:"jboolean".equals(t)?boolean.class:int.class;}
            methods[def.getInt("opcode")]=CmgReplayBridge.class.getDeclaredMethod(def.getString("name"),classes);
        }
        for(int repeat=0;repeat<Integer.parseInt(args.getString("repeats","3"));repeat++) {
            CmgReplayBridge.resetRuntimeForProbe();String channel="bootstrap";JSONObject costs=new JSONObject(),calls=new JSONObject();int verified=0;
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(args.getString("trace"))))) {
                if(integer(in)!=0x31474d43)throw new IOException("Wrong trace magic");
                for(;;) {
                    int op=integer(in);if(op==-1)break;
                    long clock=number(in);
                    if(op==0) {
                        if(calls.length()>0)rows.put(new JSONObject().put("variant",variant).put("repeat",repeat).put("channel",channel).put("costNs",costs).put("calls",calls).put("verifiedOutputs",verified));
                        channel=string(in);number(in);costs=new JSONObject();calls=new JSONObject();verified=0;continue;
                    }
                    JSONObject def=definitions.getJSONObject(op-1);String name=def.getString("name"),returns=def.getString("returns");JSONArray types=def.getJSONArray("args");Object[] params=new Object[types.length()];
                    for(int n=0;n<params.length;n++) {
                        String type=types.getString(n);
                        if("jstring".equals(type))params[n]=string(in);
                        else if("jlong".equals(type))params[n]=number(in);
                        else if("jboolean".equals(type))params[n]=integer(in)!=0;
                        else if("jbyteArray".equals(type)) {
                            if("decodeNalRangeInPlace".equals(name)) {
                                int size=integer(in),offset=integer(in);byte[] nal=bytes(in);
                                if(size<0||size>32*1024*1024||offset<0||nal==null||offset>size-nal.length)throw new IOException("Invalid captured NAL range");
                                byte[] data=new byte[size];Arrays.fill(data,(byte)0x3c);System.arraycopy(nal,0,data,offset,nal.length);params[n]=data;
                            }else params[n]=bytes(in);
                        }else params[n]=integer(in);
                    }
                    Object expected="void".equals(returns)?null:"jbyteArray".equals(returns)?bytes(in):"jstring".equals(returns)?string(in):"jboolean".equals(returns)?Boolean.valueOf(integer(in)!=0):Integer.valueOf(integer(in));
                    byte[] expectedRange="decodeNalRangeInPlace".equals(name)?bytes(in):null;number(in);
                    CmgReplayBridge.setClockForProbe(clock);
                    long start=System.nanoTime();Object actual=methods[op].invoke(null,params);long elapsed=System.nanoTime()-start;
                    if(!matches(expected,actual))throw new AssertionError(channel+" native output mismatch at "+name+" expected="+expected+" actual="+actual);
                    if(expectedRange!=null) {
                        byte[] data=(byte[])params[0];int offset=(Integer)params[1];
                        if(!Arrays.equals(expectedRange,Arrays.copyOfRange(data,offset,offset+expectedRange.length)))throw new AssertionError(channel+" NAL byte mismatch");
                    }
                    verified++;costs.put(name,costs.optLong(name)+elapsed);calls.put(name,calls.optInt(name)+1);
                }
            }
            rows.put(new JSONObject().put("variant",variant).put("repeat",repeat).put("channel",channel).put("costNs",costs).put("calls",calls).put("verifiedOutputs",verified));
            Bundle update=new Bundle();update.putString("stream",variant+" repeat="+repeat+" exact replay PASS\n");sendStatus(0,update);
        }
        CmgReplayBridge.resetRuntimeForProbe();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),variant+".json"))){out.write(rows.toString(2).getBytes("UTF-8"));}
        result.putString("stream","PASS exact CMG native replay\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(1,result);}}
}
