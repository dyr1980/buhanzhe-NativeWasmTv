from pathlib import Path
import subprocess,os
root=Path(__file__).resolve().parent.parent;out=root/'.codex-tmp/hls-uri-test';out.mkdir(parents=True,exist_ok=True)
s='''package xiao.bu.tv;
import java.net.URI;
public class HlsUriCheck {
 static void check(String base,String ref,String expected){String actual=HlsUri.resolve(URI.create(base),ref).toString();if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
 check("https://live.zohi.tv/video/index.m3u8","20261013/ts/1018/1791857937344@[duration].ts","https://live.zohi.tv/video/20261013/ts/1018/1791857937344@%5Bduration%5D.ts");
 check("https://cdn.test/x/index.m3u8","../a b.ts?token=x%2By%3D&time=1","https://cdn.test/a%20b.ts?token=x%2By%3D&time=1");
 check("https://cdn.test/x/index.m3u8","https://[::1]:8443/a[b].ts?sig=%2F","https://[::1]:8443/a%5Bb%5D.ts?sig=%2F");
 check("https://cdn.test/x/index.m3u8","clip%5Bok%5D.ts?sig=abc%2Fdef","https://cdn.test/x/clip%5Bok%5D.ts?sig=abc%2Fdef");
 HlsSegmentBitrate stats=new HlsSegmentBitrate();stats.register("https://cdn.test/x/index.m3u8",new String[]{"#EXTINF:2,","clip@[duration].ts"});
 if(stats.begin("https://cdn.test/x/clip@%5Bduration%5D.ts")==null)throw new AssertionError("Encoded segment not registered");
 System.out.println("HLS URI checks passed");
 }
}'''
(out/'HlsUriCheck.java').write_text(s)
java=Path(os.environ.get('JAVA_HOME', 'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
subprocess.run([str(java/'javac.exe'),'-d',str(out),*[str(root/'app/src/main/java/xiao/bu/tv'/name) for name in ['HlsUri.java','HlsSegmentBitrate.java','MediaBitrateEstimator.java']],str(out/'HlsUriCheck.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'xiao.bu.tv.HlsUriCheck'],check=True)
