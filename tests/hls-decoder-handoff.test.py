from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java=r"""package xiao.bu.tv;
class HandoffCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static String list(int base){String s="#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:"+base+"\n#EXT-X-KEY:METHOD=AES-128,URI=key\n";for(int i=base;i<base+6;i++)s+="#EXTINF:2,\n"+i+".ts\n";return s;}
 public static void main(String[] args){
  HlsDecoderHandoff h=new HlsDecoderHandoff();h.begin(-3);h.playlist("video",list(10));
  long[] next=h.nextBoundary(2500);check(next[0]==15&&next[1]==1500);
  h.playlist("video",list(12));next=h.nextBoundary(6500);check(next[0]==17&&next[1]==1500);
  h.begin(-3);h.request(17);String trimmed=h.playlist("video",list(14));
  check(trimmed.contains("#EXT-X-MEDIA-SEQUENCE:17")&&!trimmed.contains("16.ts")&&trimmed.contains("17.ts")&&trimmed.contains("#EXT-X-KEY:"));
  next=h.nextBoundary(500);check(next[0]==18&&next[1]==1500);
  h.begin(-3);check(h.playlist("video",list(1)).contains("1.ts")); // no channel leakage
  h.begin(-3);h.playlist("vod",list(10)+"#EXT-X-ENDLIST\n");check(h.nextBoundary(0)==null);
  h.begin(-3);h.playlist("video",list(10)+"#EXT-X-DISCONTINUITY\n");check(h.nextBoundary(0)==null);
  h.begin(-3);h.playlist("video",list(10));h.playlist("video",list(12)+"#EXT-X-DISCONTINUITY\n");check(h.nextBoundary(0)==null);
  h.begin(-3);h.playlist("master","#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n#EXT-X-MEDIA:TYPE=AUDIO\n");h.playlist("audio",list(10));check(h.nextBoundary(0)==null);
  System.out.println("PASS rolling boundaries, exact next sequence, key retention, reset, VOD and unsupported manifests");
 }
}
"""
with tempfile.TemporaryDirectory() as directory:
 p=Path(directory);(p/'HandoffCheck.java').write_text(java)
 subprocess.run(['javac','-d',directory,str(root/'app/src/main/java/xiao/bu/tv/HlsDecoderHandoff.java'),str(p/'HandoffCheck.java')],check=True)
 subprocess.run(['java','-cp',directory,'xiao.bu.tv.HandoffCheck'],check=True)
