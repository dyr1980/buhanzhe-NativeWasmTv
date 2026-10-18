"""Fault-inject the production Java HDR configure transaction, including its real retry/cache code."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java=root/'.codex-tmp/jdk8/runtime/jdk8u504-b01/bin'
source=(root/'app/src/main/java/xiao/bu/tv/NativeHdrOutput.java').read_text(encoding='utf-8')
configure=source[source.index('    public static NativeHdrOutput configure('):source.index('    private static float peak(')]
verify=source[source.index('    public static void verifyToneMapping('):source.index('    public void outputFormat(')]
stub=r"""package xiao.bu.tv;
import java.util.*;
class Surface {String name;Surface(String n){name=n;}}
class MediaCrypto {}
class Build {static class VERSION {static int SDK_INT=31;}}
class Log {static void w(String tag,String text,Throwable error){}}
class MediaFormat {Map<String,Object> map=new HashMap<>();void setInteger(String k,int n){map.put(k,n);}int getInteger(String k){return (Integer)map.get(k);}String getString(String k){return (String)map.get(k);}boolean containsKey(String k){return map.containsKey(k);}}
class MediaCodec {
 int calls,resets,accept=3;boolean configureThrows,failGpu,failReset;String name="test.hardware";Surface target;
 String getName(){return name;}
 void configure(MediaFormat f,Surface s,MediaCrypto c,int flags){calls++;target=s;if((calls==1&&configureThrows)||(s.name.equals("GPU")&&failGpu))throw new IllegalStateException("codec rejected configuration");}
 void reset(){resets++;if(failReset)throw new IllegalStateException("reset failed");}
 MediaFormat getInputFormat(){MediaFormat f=new MediaFormat();f.setInteger("color-transfer-request",accept);return f;}
}
class NativeHdrOutput {
 static final String TAG="HDR";static final LinkedHashMap<String,Boolean> rejectedMapping=new LinkedHashMap<>();
 static NativeHdrOutput last;boolean released;
 Surface getSurface(){return new Surface("GPU");}void release(){released=true;}
 static int integer(MediaFormat f,String key,int fallback){return f.containsKey(key)?f.getInteger(key):fallback;}
 static NativeHdrOutput create(MediaFormat f,Surface s){
  if(integer(f,"ntv-force-gpu",0)!=0)f.setInteger("color-transfer-request",0);
  if(integer(f,"hdr",0)==0)return null;
  if(Build.VERSION.SDK_INT>=31&&integer(f,"ntv-force-gpu",0)==0){f.setInteger("color-transfer-request",3);return null;}
  return last=new NativeHdrOutput();
 }
"""+configure+verify+r"""
 static void ok(boolean v,String why){if(!v)throw new AssertionError(why);}
 static MediaFormat format(int transfer){MediaFormat f=new MediaFormat();f.map.put("mime","video/hevc");f.setInteger("hdr",1);f.setInteger("ntv-source-transfer",transfer);f.setInteger("width",1920);f.setInteger("height",1080);return f;}
 public static void main(String[] args){
  Surface target=new Surface("DISPLAY");
  MediaCodec accepted=new MediaCodec();ok(configure(accepted,format(6),target,null,0)==null,"Accepted codec mapping must stay direct");ok(accepted.calls==1&&accepted.resets==0,"Accepted path must not restart codec");
  MediaCodec declined=new MediaCodec();declined.accept=0;NativeHdrOutput bridge=configure(declined,format(6),target,null,0);ok(bridge!=null&&!bridge.released&&declined.calls==2&&declined.resets==1&&declined.target.name.equals("GPU"),"Rejected mapping must retry same hardware codec via GPU exactly once");
  MediaCodec remembered=new MediaCodec();ok(configure(remembered,format(6),target,null,0)!=null&&remembered.calls==1&&remembered.resets==0,"Remember rejection for matching codec/source profile");
  MediaCodec otherTransfer=new MediaCodec();ok(configure(otherTransfer,format(7),target,null,0)==null&&otherTransfer.calls==1,"Do not apply rejection to other transfer");
  MediaCodec thrown=new MediaCodec();thrown.name="throwing.hardware";thrown.configureThrows=true;ok(configure(thrown,format(6),target,null,0)!=null&&thrown.calls==2&&thrown.resets==1,"Configure exception also uses bounded GPU retry");
  MediaCodec broken=new MediaCodec();broken.name="broken.hardware";broken.accept=0;broken.failGpu=true;try{configure(broken,format(6),target,null,0);throw new AssertionError("Failure swallowed");}catch(IllegalStateException e){ok(broken.calls==2&&last.released&&e.getSuppressed().length==1,"Failed GPU attempt releases bridge and keeps original failure");}
  MediaCodec reset=new MediaCodec();reset.name="reset.failure";reset.accept=0;reset.failReset=true;try{configure(reset,format(6),target,null,0);throw new AssertionError("Failure swallowed");}catch(IllegalStateException e){ok(reset.calls==1&&reset.resets==1,"Reset failure cannot loop or configure again");}
  MediaFormat sdr=format(0);sdr.setInteger("hdr",0);MediaCodec normal=new MediaCodec();ok(configure(normal,sdr,target,null,0)==null&&normal.calls==1&&normal.resets==0,"SDR path remains direct");
  for(int sdk:new int[]{17,19,25,29,30}){Build.VERSION.SDK_INT=sdk;MediaCodec old=new MediaCodec();ok(configure(old,format(7),target,null,0)!=null&&old.calls==1&&old.resets==0,"Legacy HDR keeps GPU without modern codec calls");}
  System.out.println("PASS accepted/rejected/throwing/reset-failure HDR codec transactions, bounded retry, bridge cleanup, per-profile cache, old SDK and SDR");
 }
}
"""
with tempfile.TemporaryDirectory() as directory:
 out=Path(directory);(out/'NativeHdrOutput.java').write_text(stub,encoding='utf-8')
 subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(out),str(out/'NativeHdrOutput.java')],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(out),'xiao.bu.tv.NativeHdrOutput'],check=True)
 subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(out),str(root/'app/src/main/java/xiao/bu/tv/HdrMode.java'),str(root/'app/src/main/java/xiao/bu/tv/HdrDisplayPolicy.java'),str(root/'tools/ijk/test-hdr-display-policy.java')],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(out),'xiao.bu.tv.HdrDisplayPolicyTest'],check=True)
