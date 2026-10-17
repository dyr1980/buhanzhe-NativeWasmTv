"""Validate explicit-HLS classification without touching the network."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/xiao/bu/tv/HttpStreamResolver.java').read_text(encoding='utf-8')
method=source[source.index('    static boolean isExplicitHlsUrl('):source.index('    static boolean shouldResolve(')]
java="""import java.net.*;import java.util.*;
class DirectHlsCheck {
METHOD
 static void check(String url,boolean expected){if(isExplicitHlsUrl(url)!=expected)throw new AssertionError(url);}
 public static void main(String[] args){
  for(String url:new String[]{"http://192.168.1.2/live.m3u8","http://tv.test/LIVE.M3U8?token=123","https://tv.test/live.m3u8","http://tv.test/live?format=m3u8","http://tv.test/play?type=m3u8&token=abc"})check(url,true);
  for(String url:new String[]{null,"","rtp://239.1.1.1:1234","file:///a.m3u8","http://tv.test/live.php","http://tv.test/page?url=http://other/live.m3u8","http://tv.test/live.m3u8?bad=hello world","http://user:pass@tv.test/live.m3u8","http:///live.m3u8","http://tv.test/live.m3u8.ts"})check(url,false);
  System.out.println("PASS explicit HLS URLs, query hints, dynamic endpoints, credentials, invalid URLs and nested media links");
 }
}""".replace('METHOD',method)
with tempfile.TemporaryDirectory() as tmp:
 target=Path(tmp)/'DirectHlsCheck.java';target.write_text(java,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8',str(target)],check=True)
 subprocess.run(['java','-cp',tmp,'DirectHlsCheck'],check=True)
