"""Exercise the production freshness and partial-telemetry rules without Android."""
from pathlib import Path
import re, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/xiao/bu/tv/MainActivity.java').read_text(encoding='utf8')
methods = []
for name in ['freshCastMetric', 'estimatedCastDelayMs']:
    methods.append(re.search(r'    private static long ' + name + r'\(.*?\n    }', source, re.S).group())
java = '''class CastDelayCheck {
static final long TAKEOVER_SESSION_TIMEOUT_MS = 3000L;
static class PlaybackDebugStats {
long networkDelayMs=-1, encodeDelayMs=-1, videoQueueDelayMs=-1;
long videoSendDelayMs=-1, receiverDelayMs=-1;
}
static void equal(long expected, long actual) {
if(expected != actual) throw new AssertionError(expected + " != " + actual);
}
public static void main(String[] args) {
equal(80, freshCastMetric(10000, 9999, 80));
equal(-1, freshCastMetric(10000, 7000, 80));
equal(-1, freshCastMetric(10000, 10001, 80));
equal(-1, freshCastMetric(10000, 0, 0));
equal(-1, freshCastMetric(10000, 9999, -1));
equal(-1, freshCastMetric(10000, 9999, 60001));
PlaybackDebugStats s = new PlaybackDebugStats();
equal(-1, estimatedCastDelayMs(s));
s.networkDelayMs=11;s.encodeDelayMs=15;s.videoQueueDelayMs=3;s.videoSendDelayMs=2;
equal(-1, estimatedCastDelayMs(s));
s.receiverDelayMs=80;equal(106, estimatedCastDelayMs(s));
s.receiverDelayMs=0;equal(26, estimatedCastDelayMs(s));
s.networkDelayMs=-1;equal(-1, estimatedCastDelayMs(s));
}
''' + '\n'.join(methods) + '\n}\n'
jdk = Path('C:/Users/Administrator/.jdks/corretto-1.8.0_492/bin')
with tempfile.TemporaryDirectory(prefix='ntv-delay-') as directory:
    file = Path(directory) / 'CastDelayCheck.java'
    file.write_text(java, encoding='utf8')
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', str(file)], check=True)
    subprocess.run([str(jdk/'java.exe'), '-cp', directory, 'CastDelayCheck'], check=True)
print('PASS fresh/stale/missing telemetry and total delay calculation')
