"""Real debug FPS formatter: source metadata must not erase measured output."""
import os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parent.parent
source=(root/'app/src/main/java/xiao/bu/tv/MainActivity.java').read_text(encoding='utf-8')
a=source.index('    private static String formatDebugFrameRate(')
b=source.index('    private static String formatDebugVideoCodec(',a)
assert 'if (!IjkStreamMetrics.extendedMetricsAvailable(ijkPlayer)) stats.frameRate = 0f;' not in source
java='import java.util.Locale; public class DebugFpsCheck {\n static class Description {String scanType="progressive",dynamicRange="SDR";boolean dolbyVision;}\n static class PlaybackDebugStats {int width,height;String videoCodec="H.264";boolean sourceFrameRate;float frameRate,nominalFrameRate;Description videoDescription=new Description();}\n METHOD\n static void check(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}\n public static void main(String[] args) {\n  PlaybackDebugStats s=new PlaybackDebugStats();s.frameRate=24.7f;s.nominalFrameRate=25;\n  check(formatDebugFrameRate(s),"25/25 fps");\n  if(!formatChannelVideoInfo(s).contains("25/25 fps"))throw new AssertionError("Card must show measured and source FPS");\n  s.videoDescription.scanType="interlaced";s.frameRate=49.8f;\n  check(formatDebugFrameRate(s),"50/50 fps");\n  s.frameRate=24.8f;check(formatDebugFrameRate(s),"25/50 fps");\n  if(!formatChannelVideoInfo(s).contains("25/50 fps"))throw new AssertionError("Card hid interlaced frame loss");\n  s.nominalFrameRate=0;s.videoDescription.scanType="unknown";s.frameRate=25;\n  check(formatDebugFrameRate(s),"25/-- fps");\n  s.sourceFrameRate=true;s.nominalFrameRate=25;\n  check(formatDebugFrameRate(s),"--/25 fps");\n  s.nominalFrameRate=0;check(formatDebugFrameRate(s),"--/-- fps");\n  s.sourceFrameRate=false;s.nominalFrameRate=25;s.frameRate=0;check(formatDebugFrameRate(s),"0/25 fps");\n  System.out.println("PASS progressive, 50i, absent source property and metadata-only FPS");\n }\n}'.replace('METHOD',source[a:b])
out=root/'.codex-tmp/pre-release-audit/fps';out.mkdir(parents=True,exist_ok=True)
(out/'DebugFpsCheck.java').write_text(java,encoding='utf-8')
bin=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(bin/'javac.exe'),str(out/'DebugFpsCheck.java')],check=True)
subprocess.run([str(bin/'java.exe'),'-cp',str(out),'DebugFpsCheck'],check=True)
