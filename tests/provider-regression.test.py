"""Reject incomplete device runs and tolerate an advancing HLS clock after rebasing."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root/'tools/regression'))
from all_device_providers import validate_results

rows = [{'provider': '央视网', 'channel': 'CCTV-1', 'status': 'passed'},
        {'provider': '央视网', 'channel': 'CCTV-2', 'status': 'passed'}]
output = 'INSTRUMENTATION_RESULT: stream=COMPLETE cases=2 failures=0\nINSTRUMENTATION_CODE: -1\n'
preflight = {'status': 'passed'}
assert not validate_results(rows, 2, preflight, output)
assert validate_results([], 2, preflight, output)
assert validate_results(rows[:1], 2, preflight, output)
assert validate_results([rows[0], rows[0]], 2, preflight, output)
assert validate_results([rows[0], dict(rows[1], status='running')], 2, preflight, output)
assert validate_results([rows[0], dict(rows[1], status='failed')], 2, preflight, output)
assert validate_results(rows, 2, {}, output)
assert validate_results(rows, 2, preflight, output.replace('failures=0', 'failures=1'))
assert validate_results(rows, 2, preflight, output.replace('cases=2', 'cases=1'))
assert validate_results(rows, 2, preflight, 'INSTRUMENTATION_CODE: -1\n')
assert validate_results(rows, 2, preflight, output.replace('CODE: -1', 'CODE: 0'))
assert validate_results(rows, 2, preflight, output, adb_exit=1)

source = (root/'app/src/androidTest/java/xiao/bu/tv/ProviderLiveInstrumentation.java').read_text(encoding='utf-8')
start = source.index('    static final class Observation {')
end = source.index('    interface Action', start)
test = '''class ProviderObservationCheck {
OBSERVATION
static void check(boolean ok){if(!ok)throw new AssertionError();}
public static void main(String[] args){
Observation o=new Observation(0);o.sampleClock(0,90000);
o.sampleClock(250,90250);o.sampleClock(500,0);o.sampleClock(750,250);
check(o.advancedMs==500&&o.lastProgressAt==750&&o.maxNoProgressMs==0);
o.sampleClock(3750,250);check(o.maxNoProgressMs==3000); // A genuinely frozen clock still fails.
o.sampleOutput(0,100);o.sampleOutput(250,106);o.sampleOutput(3250,106);
check(o.outputFrames-o.firstOutputFrames==6&&o.maxNoVideoFramesMs==3000);
o.reset(4000);check(o.advancedMs==0&&o.position==-1&&o.firstOutputFrames==-1);
o.sampleClock(4000,0);o.sampleClock(4250,250);check(o.advancedMs==250);
o.sampleOutput(4250,5);o.sampleOutput(4500,11);check(o.outputFrames-o.firstOutputFrames==6);
check(o.maxNoVideoFramesMs==3000); // Keep the original failure gap in diagnostic evidence.
Observation baseline=new Observation(0);baseline.sampleClock(100,0);
check(baseline.advancedMs==0&&baseline.lastProgressAt==0); // Startup baseline is not progress.
baseline.sampleClock(200,-1);check(baseline.position==0&&baseline.advancedMs==0);
}}
'''.replace('OBSERVATION', source[start:end])
jdk = Path(os.environ.get('JAVA_HOME', 'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
with tempfile.TemporaryDirectory(prefix='ntv-provider-regression-') as directory:
    file = Path(directory)/'ProviderObservationCheck.java'
    file.write_text(test, encoding='utf-8')
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', str(file)], check=True)
    subprocess.run([str(jdk/'java.exe'), '-cp', directory, 'ProviderObservationCheck'], check=True)
print('PASS complete-run validation, HLS clock rebase, real stalls and recovery observation baselines')
