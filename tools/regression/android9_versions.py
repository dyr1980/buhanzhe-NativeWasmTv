"""Build isolated version snapshots and run real Android instrumentation suites.

The multi-runner manifest is generated only in the exported snapshots. Production
package data is never cleared. Each suite starts in a fresh target process and
the raw output, exit/result code and duration are retained, including failures.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.codex-tmp/android9-v17'
ADB = ['D:/android/sdk/platform-tools/adb.exe', '-s', '127.0.0.1:21503']
PACKAGES = {'baseline': 'xiao.bu.tv.regression17', 'current': 'xiao.bu.tv.regression18'}
REFS = {'baseline': '1b747a4', 'current': 'HEAD'}


def run(command, cwd=ROOT, timeout=180):
    return subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout)


def prepare(version):
    directory = OUT / version
    if directory.exists():
        raise RuntimeError('Snapshot already exists; refusing to overwrite ' + str(directory))
    archive = OUT / (version + '.zip')
    subprocess.run(['git', 'archive', REFS[version], '-o', str(archive)], cwd=ROOT, check=True)
    with zipfile.ZipFile(archive) as source:
        source.extractall(directory)
    # Current means the reviewable working copy, including fixes found by this run.
    if version == 'current':
        changed=subprocess.check_output(['git','diff','HEAD','--name-only'],cwd=ROOT,text=True).splitlines()
        for name in changed:
            path=ROOT/name
            if path.is_file():
                target=directory/name
                target.parent.mkdir(parents=True,exist_ok=True)
                target.write_bytes(path.read_bytes())
    (directory / 'local.properties').write_text('sdk.dir=D:/android/sdk\n', encoding='utf-8')
    source = directory / 'app/src/androidTest/java/xiao/bu/tv'
    shared = ROOT / 'app/src/androidTest/java/xiao/bu/tv/VersionBehaviorInstrumentation.java'
    if shared.exists():
        (source / shared.name).write_bytes(shared.read_bytes())
    classes = sorted(p.stem for p in source.glob('*Instrumentation.java'))
    declarations = ['<manifest xmlns:android="http://schemas.android.com/apk/res/android"'
                    ' package="' + PACKAGES[version] + '.test">']
    # AGP rewrites the first instrumentation node to testInstrumentationRunner.
    declarations.append('<instrumentation android:name="xiao.bu.tv.QuickJsInstrumentation"'
                        ' android:targetPackage="' + PACKAGES[version] + '"/>')
    for name in classes:
        if name != 'QuickJsInstrumentation':
            declarations.append('<instrumentation android:name="xiao.bu.tv.' + name
                                + '" android:targetPackage="' + PACKAGES[version] + '"/>')
    declarations.append('</manifest>')
    (directory / 'app/src/androidTest/AndroidManifest.xml').write_text('\n'.join(declarations), encoding='utf-8')
    identity = subprocess.check_output(['git', 'rev-parse', REFS[version]], cwd=ROOT, text=True).strip()
    (directory / 'snapshot.json').write_text(json.dumps({'ref': REFS[version], 'commit': identity,
             'package': PACKAGES[version], 'classes': classes}, indent=2), encoding='utf-8')
    from android9_adapt_fixtures import adapt
    adapt(directory)
    print(version, identity, 'prepared', len(classes), 'runners', flush=True)


def build(version, abi='x86'):
    directory = OUT / version
    env = os.environ.copy()
    env['JAVA_HOME'] = str(ROOT / '.codex-tmp/jdk8/runtime/jdk8u504-b01')
    flavor=abi[0].upper()+abi[1:]
    command = ['cmd.exe', '/c', 'gradlew.bat', ':app:assemble'+flavor+'Debug',
               ':app:assemble'+flavor+'DebugAndroidTest', '-PapplicationIdOverride=' + PACKAGES[version],
               '-PtestRunner=xiao.bu.tv.QuickJsInstrumentation', '--offline']
    with (OUT / (version + '-'+abi+'-build.log')).open('wb') as log:
        result = subprocess.run(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=300)
    if result.returncode:
        raise RuntimeError(version + ' build failed; see build log')
    for apk in [directory / ('app/build/outputs/apk/'+abi+'/debug/app-'+abi+'-debug.apk'),
                directory / ('app/build/outputs/apk/androidTest/'+abi+'/debug/app-'+abi+'-debug-androidTest.apk')]:
        result = run(ADB + ['install', '-r', str(apk)])
        print(result.stdout.decode('utf-8', 'replace'), flush=True)
        if result.returncode or b'Success' not in result.stdout:
            raise RuntimeError('Install failed: ' + str(apk))
    print(version, 'built and installed', flush=True)
    (directory/'installed-build.json').write_text(json.dumps({'abi':abi,
        'installedAt':datetime.now(timezone.utc).isoformat()}),encoding='utf-8')


def suite(version, name, extras, timeout):
    package = PACKAGES[version]
    for target in PACKAGES.values():
        run(ADB + ['shell', 'am', 'force-stop', target])
    command = ADB + ['shell', 'am', 'instrument', '-w', '-r']
    for key, value in extras.items():
        command.extend(['-e', key, str(value)])
    command.append(package + '.test/xiao.bu.tv.' + name + 'Instrumentation')
    start = time.monotonic()
    logs = OUT / 'logs'
    logs.mkdir(exist_ok=True)
    label = name + ('-' + '-'.join(k + '=' + str(v) for k, v in extras.items()) if extras else '')
    prefix=version + '-' + re.sub(r'[^A-Za-z0-9_.=-]', '_', label)
    unique=prefix+'-'+str(time.time_ns())
    recording=(logs / (unique+'-device.log')).open('wb')
    logger=subprocess.Popen(ADB+['logcat','MainActivity:D','IJKMEDIA:E','FaviconRegression:I','*:S'],
                            stdout=recording,stderr=subprocess.STDOUT)
    timed_out = False
    try:
        result = run(command, timeout=timeout)
        output, exit_code = result.stdout.decode('utf-8', 'replace'), result.returncode
    except subprocess.TimeoutExpired as error:
        timed_out = True
        output, exit_code = (error.stdout or b'').decode('utf-8', 'replace'), -1
        run(ADB + ['shell', 'am', 'force-stop', package])
    finally:
        logger.terminate()
        logger.wait(timeout=5)
        recording.close()
    codes = re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)', output, re.M)
    success_code = codes == ['-1'] or (name == 'Ku9Compatibility' and codes == ['0']
                                     and 'PASS minified release Ku9' in output)
    ok = exit_code == 0 and success_code and not re.search(r'\bFAIL\b|AssertionError|FATAL EXCEPTION', output)
    label = name + ('-' + '-'.join(k + '=' + str(v) for k, v in extras.items()) if extras else '')
    logs = OUT / 'logs'
    logs.mkdir(exist_ok=True)
    (logs / (version + '-' + re.sub(r'[^A-Za-z0-9_.=-]', '_', label) + '.log')).write_text(output, encoding='utf-8')
    (logs / (unique+'.log')).write_text(output,encoding='utf-8')
    row = {'version': version, 'suite': name, 'arguments': extras, 'passed': ok,
           'exitCode': exit_code, 'instrumentationCode': codes,
           'timeout': timed_out, 'durationSeconds': round(time.monotonic() - start, 2), 'output': output}
    row['recordedAt']=datetime.now(timezone.utc).isoformat()
    row['logFile']=str(logs/(unique+'.log'))
    installed=OUT/version/'installed-build.json'
    row['abi']=json.loads(installed.read_text(encoding='utf-8'))['abi'] if installed.exists() else extras.get('abi','unknown')
    with (OUT / 'results.jsonl').open('a', encoding='utf-8') as target:
        target.write(json.dumps(row, ensure_ascii=False) + '\n')
    print(version, label, 'PASS' if ok else 'FAIL', row['durationSeconds'], flush=True)
    if not ok:
        print(output[-1600:], flush=True)
    return ok


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['prepare', 'build', 'suite', 'batch'])
    parser.add_argument('version', choices=list(PACKAGES))
    parser.add_argument('--suite')
    parser.add_argument('--arguments', default='{}')
    parser.add_argument('--timeout', type=int, default=90)
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--abi', choices=['x86','arm32'], default='x86')
    args = parser.parse_args()
    status=run(ADB+['shell','getprop','ro.build.version.sdk'],timeout=15)
    if status.returncode or status.stdout.strip()!=b'28':
        raise SystemExit('Android 9/API 28 device is unavailable; refusing to record application failures.')
    OUT.mkdir(parents=True, exist_ok=True)
    if args.phase == 'prepare':
        prepare(args.version)
    elif args.phase == 'build':
        build(args.version,args.abi)
    elif args.phase == 'suite':
        raise SystemExit(0 if suite(args.version, args.suite, json.loads(args.arguments), args.timeout) else 1)
    else:
        classes = json.loads((OUT / args.version / 'snapshot.json').read_text(encoding='utf-8'))['classes']
        passed=True
        for test in json.loads(args.plan.read_text(encoding='utf-8')):
            if test['suite'] + 'Instrumentation' not in classes:
                print(args.version, test['suite'], 'NOT PRESENT', flush=True)
                continue
            passed=suite(args.version, test['suite'], test.get('arguments', {}), test.get('timeout', args.timeout)) and passed
        raise SystemExit(0 if passed else 1)
