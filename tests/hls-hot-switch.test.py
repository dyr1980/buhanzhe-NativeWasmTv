"""Run the production TS/profile/timeline classes against real HLS segments."""
from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parent.parent
out=root/'.codex-tmp/hls-hot-switch/unit'; out.mkdir(parents=True,exist_ok=True)
fixture=out/'fixtures'; fixture.mkdir(exist_ok=True)
# Generate small, repeatable streams; never rely on samples left by a manual run.
for channel,size,tone in (('a','640x360',440),('b','640x360',880),('c','320x240',440)):
    folder=fixture/channel; folder.mkdir(exist_ok=True)
    subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','lavfi','-i',f'testsrc2=size={size}:rate=25',
        '-f','lavfi','-i',f'sine=frequency={tone}:sample_rate=48000','-t','6',
        *(['-vf','hue=h=150'] if channel=='b' else []),'-c:v','libx264','-threads','2','-preset','ultrafast',
        '-tune','zerolatency','-profile:v','baseline','-level','3','-pix_fmt','yuv420p','-g','50','-keyint_min','50',
        '-sc_threshold','0','-c:a','aac','-b:a','96k','-f','hls','-hls_time','2','-hls_list_size','0',
        '-hls_segment_filename',str(folder/'clip%03d.ts'),str(folder/'index.m3u8')],check=True)
for name,codec,transfer,channels in (('hevc-hlg','libx265','arib-std-b67',1),
                                    ('hevc-pq','libx265','smpte2084',1),
                                    ('avc-mono','libx264','bt709',1),
                                    ('avc-stereo','libx264','bt709',2)):
    subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-f','lavfi','-i','testsrc2=size=640x360:rate=25',
        '-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','2','-c:v',codec,'-threads','2',
        '-preset','ultrafast',*(['-x265-params',f'pools=1:frame-threads=1:log-level=error:colorprim=9:transfer={18 if transfer=="arib-std-b67" else 16}:colormatrix=9','-pix_fmt','yuv420p10le']
            if codec=='libx265' else ['-tune','zerolatency','-profile:v','baseline','-level','3','-pix_fmt','yuv420p']),
        '-g','50','-color_primaries','bt2020' if codec=='libx265' else 'bt709','-color_trc',transfer,
        '-colorspace','bt2020nc' if codec=='libx265' else 'bt709','-c:a','aac','-ac',str(channels),'-b:a','96k',
        '-f','mpegts',str(fixture/f'{name}.ts')],check=True)
java=Path(os.environ.get('JAVA_HOME',r'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
source=r'''package xiao.bu.tv;
import java.io.*;import java.util.*;
public class HlsSwitchCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static class Transport implements HlsSwitchSession.Transport {
  File root;int originSequence;boolean changedProfile;Transport(File root){this.root=root;}
  public String playlist(String url){return "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:"+originSequence+"\n#EXTINF:2,\n"+url+"/clip000.ts\n#EXTINF:2,\n"+url+"/clip001.ts\n#EXTINF:2,\n"+url+"/clip002.ts\n";}
  public String origin(String url){return url;}
  public File segment(String url){return new File(root,changedProfile?"c/"+url.substring(url.lastIndexOf('/')+1):url);}
  public boolean safe(String url){return true;}
 }
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);Transport transport=new Transport(root);
  for(final String tag:new String[]{"#EXT-X-ENDLIST", "#EXT-X-MAP:URI=\"init.mp4\"",
      "#EXT-X-BYTERANGE:100@0", "#EXT-X-KEY:METHOD=AES-128,URI=\"key\""}) {
   Transport unsupported=new Transport(root) {
    public String playlist(String url){return super.playlist(url)+tag+"\n";}
    public File segment(String url){throw new AssertionError("Unsupported HLS entered TS warmup: "+tag);}
    public void prefetch(List<String> urls){throw new AssertionError("Unsupported HLS prefetched: "+tag);}
   };
   check(HlsSwitchSession.prepare(unsupported,"a")==null,"Unsupported HLS must use normal demuxer");
  }
  Transport separateAudio=new Transport(root) {
   public String playlist(String url){return url.equals("master")
     ? "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO=\"audio\"\na\n"
     : super.playlist(url);}
   public File segment(String url){throw new AssertionError("Separate audio entered TS warmup");}
  };
  check(HlsSwitchSession.prepare(separateAudio,"master")==null,"Separate audio must use normal demuxer");
  HlsSwitchSession.Prepared a=HlsSwitchSession.prepare(transport,"a"),b=HlsSwitchSession.prepare(transport,"b"),c=HlsSwitchSession.prepare(transport,"c");
  check(a.eligible&&b.eligible&&c.eligible,"Real TS profiles must be readable");
  check(a.profile.signature.equals(b.profile.signature),"Matching formats rejected");
  check(!a.profile.signature.equals(c.profile.signature),"Resolution change must reopen");
  TsSwitchProfile hlg=TsSwitchProfile.inspect(new File(root,"hevc-hlg.ts")),pq=TsSwitchProfile.inspect(new File(root,"hevc-pq.ts"));
  check(hlg!=null&&pq!=null,"HEVC Main10 profiles unreadable");
  check(!hlg.signature.equals(pq.signature),"HLG/PQ rendering change reused decoder");
  TsSwitchProfile stereo=TsSwitchProfile.inspect(new File(root,"avc-stereo.ts"));
  TsSwitchProfile mono=TsSwitchProfile.inspect(new File(root,"avc-mono.ts"));
  check(stereo!=null&&mono!=null&&!stereo.signature.equals(mono.signature),"Changed AAC channels reused decoder");
  check(!mono.signature.equals(a.profile.signature),"Changed AVC colour reused decoder");
  HlsSwitchSession session=new HlsSwitchSession(transport,"http://local/live-switch/",a);
  List<HlsSwitchSession.Part> longer=new ArrayList<HlsSwitchSession.Part>();
  longer.add(new HlsSwitchSession.Part("b/clip000.ts",6,0));
  check(!session.compatible(new HlsSwitchSession.Prepared("b","b",longer,0,true,b.profile)),"Target duration change accepted");
  HlsSwitchSession.Entry old=session.reserve(0);check(!session.targetDelivered(),"No new output yet");
  HlsSwitchSession resumed=new HlsSwitchSession(transport,"http://local/live-switch/",a);
  HlsSwitchSession.Entry reserved=resumed.reserve(0);
  transport.originSequence=5;
  java.lang.reflect.Field reload=HlsSwitchSession.class.getDeclaredField("lastReloadAt");reload.setAccessible(true);reload.setLong(resumed,0);
  check(resumed.playlist().contains("#EXT-X-DISCONTINUITY\n"),"A missing upstream interval must be declared");
  check(resumed.reserve(0)==reserved&&resumed.reserve(3).epoch==2,"Gap recovery changed reserved media or failed to resume");
  transport.originSequence=10;transport.changedProfile=true;reload.setLong(resumed,0);
  try{resumed.playlist();throw new AssertionError("Changed decoder parameters accepted across missing interval");}catch(IOException expected){}
  check(resumed.reserve(0)==reserved&&resumed.epoch()==2,"Rejected gap recovery changed playback");
  transport.changedProfile=false;
  transport.originSequence=0;resumed.close();
  ByteArrayOutputStream oldBytes=new ByteArrayOutputStream();session.copy(old,session.file(old),oldBytes);session.delivered(old);
  try(FileOutputStream file=new FileOutputStream(new File(root,"unit-a.ts"))){file.write(oldBytes.toByteArray());}
  try{session.switchTo(b,0,1000);throw new AssertionError("Long queued handoff allowed");}catch(IOException expected){}
  check(session.epoch()==1&&session.reserve(0)==old,"Rejected handoff changed active playback");
  long boundary=session.switchTo(b);check(boundary>=2000&&boundary<2200,"Wrong output boundary: "+boundary);
  check(session.reserve(0)==old,"Reserved old media changed");
  check(!session.targetDelivered(),"A manifest change is not rendered output");
  HlsSwitchSession.Entry next=session.reserve(1);check(next.epoch==2&&next.part.url.startsWith("b/"),"Old unrequested queue not replaced");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();session.copy(next,session.file(next),bytes);
  check(!session.targetDelivered(),"Incomplete delivery claimed ready");session.delivered(next);check(session.targetDelivered(),"New target never marked delivered");
  try(FileOutputStream file=new FileOutputStream(new File(root,"unit-b.ts"))){file.write(bytes.toByteArray());}
  check(session.playlist().contains("#EXT-X-DISCONTINUITY\n#EXTINF:2.0,\nhttp://local/live-switch/1.ts"),"Missing discontinuity");
  try{session.switchTo(c);throw new AssertionError("Incompatible switch allowed");}catch(IOException expected){}
  long again=session.switchTo(a);check(again>=4000,"Timeline moved backwards");
  check(session.reserve(1)==next,"In-flight target was overwritten by a rapid switch");
  byte[] packet=new byte[188];Arrays.fill(packet,(byte)255);packet[0]=0x47;packet[1]=64;packet[2]=1;packet[3]=48;packet[4]=7;packet[5]=16;
  packet[6]=(byte)255;packet[7]=(byte)255;packet[8]=(byte)255;packet[9]=(byte)255;packet[10]=(byte)254;packet[11]=12;
  long pcr=((long)(packet[6]&255)<<25)|((long)(packet[7]&255)<<17)|((long)(packet[8]&255)<<9)|((long)(packet[9]&255)<<1)|((packet[10]&128)>>7);
  TsSwitchProfile.rewrite(packet,90000);long mapped=((long)(packet[6]&255)<<25)|((long)(packet[7]&255)<<17)|((long)(packet[8]&255)<<9)|((long)(packet[9]&255)<<1)|((packet[10]&128)>>7);
  check(mapped==((pcr+90000)&TsSwitchProfile.MASK)&&packet[11]==12,"PCR wrap or extension corrupted");
  session.close();try{session.reserve(2);throw new AssertionError("Closed timeline reused");}catch(IOException expected){}
  System.out.println("PASS: exact parameter gating, reserved queue, discontinuity, monotonic/33-bit clocks, rapid changes, cancellation");
 }
}'''
(out/'HlsSwitchCheck.java').write_text(source,encoding='utf-8')
sources=[root/'app/src/main/java/xiao/bu/tv'/name for name in ('HlsSwitchSession.java','TsSwitchProfile.java')]
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(out),*map(str,sources),str(out/'HlsSwitchCheck.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'xiao.bu.tv.HlsSwitchCheck',str(fixture)],check=True)
for channel in ('a','b'):
    def frames(path):
        result=subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-i',str(path),'-map','0:v:0','-f','framemd5','-'],check=True,capture_output=True,text=True)
        return [line.split(',')[-1].strip() for line in result.stdout.splitlines() if line and not line.startswith('#')]
    original=frames(fixture/channel/'clip000.ts'); actual=frames(fixture/f'unit-{channel}.ts')
    assert original==actual, f'{channel}: pixel output changed after timestamp remapping'
print('PASS: all 100 decoded frames match original pixels exactly; no transcoding')
