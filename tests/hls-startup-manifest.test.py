"""Manifest-only native eligibility must never download or rewrite TS packets."""
import os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parent.parent
out=root/'.codex-tmp/hls-startup-manifest';out.mkdir(parents=True,exist_ok=True)
source=r'''package xiao.bu.tv;
import java.io.*;import java.util.*;
class ManifestCheck {
 static String media="#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-DISCONTINUITY-SEQUENCE:0\n#EXTINF:2,\nsegment\n";
 static class Transport implements HlsStartupManifest.Transport {
  String content=media,master="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchild\n";List<String> calls=new ArrayList<>();
  public String playlist(String url){calls.add(url);return url.equals("master")?master:content;}
  public String origin(String url){return url;}
 }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  Transport t=new Transport();HlsStartupManifest.Result r=HlsStartupManifest.prepare(t,"master");
  check(r.mediaUrl.equals("child")&&r.segmentUrls.size()==1&&r.segmentUrls.get(0).equals("segment"));
  check(t.calls.equals(Arrays.asList("master","child")));
  for(String tag:new String[]{"#EXT-X-KEY:METHOD=AES-128", "#EXT-X-MAP:URI=init", "#EXT-X-ENDLIST", "#EXT-X-BYTERANGE:188", "#EXT-X-DISCONTINUITY", "#EXT-X-I-FRAMES-ONLY"}) {
   t=new Transport();t.content=media+tag+"\n";check(HlsStartupManifest.prepare(t,"child")==null);
  }
  t=new Transport();t.master="#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchild\n";
  check(HlsStartupManifest.prepare(t,"master")==null);
  t=new Transport();t.content=media.replace("#EXTINF:2,","#EXTINF:0,");
  try{HlsStartupManifest.prepare(t,"child");throw new AssertionError();}catch(IOException expected){}
  t=new Transport();t.master="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmaster\n";
  try{HlsStartupManifest.prepare(t,"master");throw new AssertionError();}catch(IOException expected){}
  String origin="http://127.0.0.1:39962/live/index.m3u8?session=abc";
  HlsStartupManifest.NativeUrlPolicy http=url->url.startsWith("http://");
  String forwarded=HlsStartupManifest.directMediaPlaylist(origin,media,http);
  check(forwarded!=null&&forwarded.contains("http://127.0.0.1:39962/live/segment"));
  check(HlsStartupManifest.directMediaPlaylist(origin,media,url->false)==null);
  check(HlsStartupManifest.directMediaPlaylist(origin,media.replace("segment", "https://other.test/segment"),http)==null);
  for(String tag:new String[]{"#EXT-X-KEY:METHOD=AES-128", "#EXT-X-MAP:URI=init", "#EXT-X-ENDLIST", "#EXT-X-BYTERANGE:188", "#EXT-X-DISCONTINUITY", "#EXT-X-I-FRAMES-ONLY"})
   check(HlsStartupManifest.directMediaPlaylist(origin,media+tag+"\n",http)==null);
  check(HlsStartupManifest.directMediaPlaylist(origin,media.replace("#EXTINF:2,", "#EXTINF:0,"),http)==null);
  check(HlsStartupManifest.directMediaPlaylist(origin,"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchild\n",http)==null);
  System.out.println("PASS manifest-only selection and player-request direct segments; crypto/TLS/init/VOD/ranges/discontinuity retain compatibility");
 }
}'''
test=out/'ManifestCheck.java';test.write_text(source,encoding='utf-8')
bin=Path(os.environ.get('JAVA_HOME',r'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
subprocess.run([str(bin/'javac.exe'),'-encoding','UTF-8','-d',str(out),str(root/'app/src/main/java/xiao/bu/tv/HlsStartupManifest.java'),str(root/'app/src/main/java/xiao/bu/tv/HlsUri.java'),str(test)],check=True)
subprocess.run([str(bin/'java.exe'),'-cp',str(out),'xiao.bu.tv.ManifestCheck'],check=True)
