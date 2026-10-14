"""Exercise the production video-output guard independently of the audio clock."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root/'app/src/main/java/xiao/bu/tv/MainActivity.java').read_text(encoding='utf-8')
start = source.index('            if (outputFps > 0.1f || !player.isPlaying()')
end = source.index('            long playbackPosition = player.getCurrentPosition();', start)
guard = source[start:end]
start = source.index('            boolean retrySoftwareDecode = activeSoftwareDecode')
end = source.index(';', start)
fallback = source[start:end] + '; return retrySoftwareDecode;'
start = source.index('    private static String playbackRecoveryMessage(')
end = source.index('\n    private ', start+8)
message = source[start:end]
test = '''class VideoOutputRecoveryCheck {
static class Build {static class VERSION {static int SDK_INT=28;}static class VERSION_CODES {static int N_MR1=25;}}
static class Player {boolean playing=true;boolean isPlaying(){return playing;}}
static class IjkMediaPlayer extends Player {float source=25;}
static class IjkStreamMetrics {static int sourceQueries;static float sourceFrameRate(IjkMediaPlayer p){sourceQueries++;return p.source;}}
static class Proxy {boolean finite;boolean finiteMedia(){return finite;}}
Player player=new IjkMediaPlayer();Proxy proxy=new Proxy();
boolean videoRenderingStarted=true,buffering,audioOnlyPlayback,activeSoftwareDecode;
String activePlayerStreamUrl="live",decodeMode="auto";
static final String DECODE_MODE_AUTO="auto";
static final long PLAYBACK_STALL_RECOVERY_MS=10000,NTV_CAST_STALL_RECOVERY_MS=5000;
long lastVideoOutputAt=100;int playRequestId=7,calls;boolean software;
boolean isNtVCastSource(String u){return "cast".equals(u);}
boolean isHttpHlsSource(String u){return "live".equals(u);}
void recoverStalledPlayback(int request,Player p,String reason){calls++;software=false;}
void recoverStalledPlayback(int request,Player p,String reason,boolean stopped){calls++;software=choose(p,stopped);}
boolean choose(Player watchedPlayer,boolean videoOutputStopped){FALLBACK}
MESSAGE
void sample(long now,float outputFps){GUARD}
static void check(boolean ok){if(!ok)throw new AssertionError();}
public static void main(String[] args){
VideoOutputRecoveryCheck s=new VideoOutputRecoveryCheck();s.sample(10099,0);check(s.calls==0);
check(IjkStreamMetrics.sourceQueries==0); // Do not cross JNI before the stall deadline.
s.sample(10100,0);check(s.calls==1&&!s.software); // Restart hardware; audio must not hide a video freeze.
check(IjkStreamMetrics.sourceQueries==1);
s=new VideoOutputRecoveryCheck();s.sample(20000,2);check(s.calls==0&&s.lastVideoOutputAt==20000);
s=new VideoOutputRecoveryCheck();s.player.playing=false;s.sample(20000,0);check(s.calls==0);
s.player.playing=true;s.sample(20001,0);check(s.calls==0);s.sample(30000,0);check(s.calls==1);
s=new VideoOutputRecoveryCheck();s.buffering=true;s.sample(20000,0);s.buffering=false;s.sample(20001,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();s.audioOnlyPlayback=true;s.sample(20000,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();s.proxy.finite=true;s.sample(20000,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();s.videoRenderingStarted=false;s.sample(20000,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();((IjkMediaPlayer)s.player).source=0;s.sample(20000,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();s.player=new Player();s.sample(20000,0);check(s.calls==0);
s=new VideoOutputRecoveryCheck();s.decodeMode="hardware";s.sample(20000,0);check(s.calls==1&&!s.software);
s.decodeMode="auto";check(!s.choose(s.player,false));check(!s.choose(s.player,true));
check(s.decodeMode.equals("auto")&&!s.activeSoftwareDecode); // Choice does not alter saved/global mode.
s.activeSoftwareDecode=true;check(s.choose(s.player,true)); // Explicit software playback stays software.
check(playbackRecoveryMessage(true,false,false,false,1,5).contains("恢复硬解"));
check(!playbackRecoveryMessage(true,false,false,false,1,5).contains("崩溃"));
check(playbackRecoveryMessage(false,false,true,false,1,5).contains("系统解码器崩溃，正在恢复"));
check(playbackRecoveryMessage(true,false,false,true,1,5).contains("网络已断开"));
check(!playbackRecoveryMessage(true,true,false,false,1,5).contains("系统解码器"));
s.activePlayerStreamUrl="cast";s.calls=0;s.sample(20000,0);check(s.calls==1&&!s.software);
}}
'''.replace('GUARD', guard).replace('FALLBACK', fallback).replace('MESSAGE', message)
jdk = Path(os.environ.get('JAVA_HOME', 'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
with tempfile.TemporaryDirectory(prefix='ntv-output-recovery-') as directory:
    file = Path(directory)/'VideoOutputRecoveryCheck.java'
    file.write_text(test, encoding='utf-8')
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', str(file)], check=True)
    subprocess.run([str(jdk/'java.exe'), '-cp', directory, 'VideoOutputRecoveryCheck'], check=True)
print('PASS video-only freeze, sparse output, pause/resume, buffering, audio, VOD and decode-mode guards')
