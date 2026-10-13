"""Verify playlist diagnostics against valid, malformed and encrypted fixtures."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/xiao/bu/tv/HlsProbeFailure.java'
harness = r'''package xiao.bu.tv;
class HlsProbeFailureCheck {
 static int checks;
 static void check(String manifest,String reason) {
  String actual=HlsProbeFailure.manifest(manifest);
  if(reason.isEmpty()? !actual.isEmpty():!actual.contains(reason)) throw new AssertionError(actual);
  checks++;
 }
 public static void main(String[] args) {
  String live="#EXTM3U\n#EXTINF:8,\nsegment.ts\n";
  check(live,""); check(live+"#EXT-X-ENDLIST\n","");
  check("\uFEFF"+live,"");
  check("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvideo.m3u8","");
  check("","空内容"); check(" \n","空内容");
  check("<!DOCTYPE html><html>Login</html>","登录或验证");
  check("Not a playlist","缺少 #EXTM3U");
  check("#EXTM3U\n#EXT-X-TARGETDURATION:8","没有可播放");
  check("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000","没有可用");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:8,\na.ts","");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128\n#EXTINF:8,\na.ts","缺少密钥地址");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.widevine\"","Widevine DRM");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"","FairPlay DRM");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.microsoft.playready\"","PlayReady DRM");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key.bin\"","不支持的加密方式");
  check("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,KEYFORMAT=\"vendor.drm\"","DRM 密钥格式");
  if(!HlsProbeFailure.nativeError(-10000,0).contains("尚未确认")) throw new AssertionError();
  System.out.println("HLS probe diagnostics: "+(checks+1)+" cases passed");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'HlsProbeFailureCheck.java'
    path.write_text(harness, encoding='utf8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp, str(source), str(path)], check=True)
    subprocess.run(['java', '-cp', tmp, 'xiao.bu.tv.HlsProbeFailureCheck'], check=True)
