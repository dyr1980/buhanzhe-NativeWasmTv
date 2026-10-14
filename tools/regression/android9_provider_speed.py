"""ARM32 live provider first-frame comparison, isolated from the installed nTv."""
import argparse
import base64
import gzip
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.codex-tmp/android9-v16-speed'
# Call the binary directly: terminating Chocolatey's shim leaves logcat children alive.
ADB = ['D:/android/sdk/platform-tools/adb.exe', '-s', '127.0.0.1:21503']
PACKAGES = {'baseline': 'xiao.bu.tv.speed16', 'current': 'xiao.bu.tv.speed18',
    'fixed': 'xiao.bu.tv.speed18fixed'}
REFS = {'baseline': '648254c', 'current': 'HEAD', 'fixed': 'HEAD'}
RUNNER = 'xiao.bu.tv.ProviderLoadComparisonInstrumentation'


def run(command, **kwargs):
    return subprocess.check_output(command, stderr=subprocess.STDOUT, **kwargs)


def prepare(version):
    directory = OUT / version
    if directory.exists():
        raise RuntimeError('Refusing to overwrite existing snapshot: ' + str(directory))
    archive = OUT / (version + '.zip')
    run(['git', 'archive', REFS[version], '-o', str(archive)], cwd=ROOT)
    with zipfile.ZipFile(archive) as zipped:
        zipped.extractall(directory)
    changes = []
    if version != 'baseline':
        changes = run(['git', 'diff', 'HEAD', '--name-only'], cwd=ROOT).decode().splitlines()
        for name in changes:
            source = ROOT / name
            if source.is_file():
                target = directory / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
    (directory / 'local.properties').write_text('sdk.dir=D:/android/sdk\n', encoding='utf-8')
    # Resolve the same cached AGP/ProGuard toolchain; this does not touch app code.
    directory.joinpath('build.gradle').write_bytes(ROOT.joinpath('build.gradle').read_bytes())
    gradle = directory / 'app/build.gradle'
    gradle.write_text(gradle.read_text(encoding='utf-8') + '''
// Benchmark-only instrumentation configuration; production sources are unchanged.
android {
    defaultConfig { testInstrumentationRunner 'xiao.bu.tv.ProviderLoadComparisonInstrumentation' }
    sourceSets { androidTest { java.srcDirs = ['src/speedTest/java'] } }
}
''', encoding='utf-8')
    fixture = directory / 'app/src/speedTest/java/xiao/bu/tv'
    fixture.mkdir(parents=True)
    fixture.joinpath('ProviderLoadComparisonInstrumentation.java').write_bytes(
        Path(__file__).with_name('ProviderLoadComparisonInstrumentation.java').read_bytes())
    extra_runner = ''
    if version != 'baseline':
        fixture.joinpath('CctvStartupGateInstrumentation.java').write_bytes(ROOT.joinpath(
            'app/src/androidTest/java/xiao/bu/tv/CctvStartupGateInstrumentation.java').read_bytes())
        extra_runner = '<instrumentation android:name="xiao.bu.tv.CctvStartupGateInstrumentation"'
        extra_runner += ' android:targetPackage="' + PACKAGES[version] + '"/>'
    directory.joinpath('app/src/androidTest').mkdir(exist_ok=True)
    directory.joinpath('app/src/androidTest/AndroidManifest.xml').write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'
        + PACKAGES[version] + '.test"><instrumentation android:name="' + RUNNER
        + '" android:targetPackage="' + PACKAGES[version] + '"/>' + extra_runner + '</manifest>', encoding='utf-8')
    identity = run(['git', 'rev-parse', REFS[version]], cwd=ROOT).decode().strip()
    directory.joinpath('snapshot.json').write_text(json.dumps({'commit': identity,
        'workingCopyChanges': changes, 'package': PACKAGES[version]}, indent=2), encoding='utf-8')
    print(version, identity, 'prepared', flush=True)


def build(version):
    directory = OUT / version
    env = dict(os.environ, JAVA_HOME=str(ROOT / '.codex-tmp/jdk8/runtime/jdk8u504-b01'))
    with (OUT / (version + '-build.log')).open('wb') as log:
        subprocess.run(['cmd.exe', '/c', 'gradlew.bat', ':app:assembleArm32Debug',
            ':app:assembleArm32DebugAndroidTest', '-PapplicationIdOverride=' + PACKAGES[version],
            '--offline'], cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT,
            check=True, timeout=300)
    for relative in ['app/build/outputs/apk/arm32/debug/app-arm32-debug.apk',
                     'app/build/outputs/apk/androidTest/arm32/debug/app-arm32-debug-androidTest.apk']:
        apk = directory / relative
        with zipfile.ZipFile(apk) as zipped:
            abis = sorted({n.split('/')[1] for n in zipped.namelist() if n.startswith('lib/')})
            if abis and abis != ['armeabi-v7a']:
                raise RuntimeError('Non-ARM32 native payload: ' + str(abis))
        sdk = int(run(ADB + ['shell', 'getprop', 'ro.build.version.sdk']).strip())
        options = ['--abi', 'armeabi-v7a'] if sdk >= 21 else []
        output = run(ADB + ['install', '-r'] + options + [str(apk)]).decode()
        print(output.strip(), flush=True)
        if 'Success' not in output:
            raise RuntimeError('Installation failed')
    details = run(ADB + ['shell', 'dumpsys', 'package', PACKAGES[version]]).decode()
    (OUT / (version + '-package.txt')).write_text(details, encoding='utf-8')
    if sdk >= 21 and 'primaryCpuAbi=armeabi-v7a' not in details:
        raise RuntimeError('Installed package is not ARM32')
    if sdk < 21 and not run(ADB + ['shell', 'getprop', 'ro.product.cpu.abi']).strip().startswith(b'armeabi'):
        raise RuntimeError('Legacy device is not ARM32')
    print(version, 'ARM32 installed', flush=True)
    directory.joinpath('installed-build.json').write_text(json.dumps({
        'installedAt': datetime.now(timezone.utc).isoformat(),
        'serial': ADB[-1], 'sdk': sdk,
        'nativePlayerSha256': hashlib.sha256(directory.joinpath(
            'app/src/main/libs/armeabi-v7a/libijkplayer.so').read_bytes()).hexdigest(),
        'productionHash': hashlib.sha256(b''.join(directory.joinpath('app/src/main/java/xiao/bu/tv', name)
            .read_bytes() for name in ['MainActivity.java', 'HlsProxyServer.java'])).hexdigest()}), encoding='utf-8')


def measure(version, batch, setup=False, count=6, delay='stable', uncached=False, fixed=False, observe=2,
            expect_fps=False):
    for package in PACKAGES.values():
        run(ADB + ['shell', 'am', 'force-stop', package])
    label = version + ('-setup' if setup else f'-{delay}-batch{batch}'
        + ('-uncached' if uncached else '') + ('-fixed' if fixed else ''))
    logs = OUT / 'logs'
    logs.mkdir(exist_ok=True)
    # Clear only the logging buffer, never application data.
    run(ADB + ['logcat', '-c'])
    device_log = (logs / (label + '-device.log')).open('wb')
    sdk = int(run(ADB + ['shell', 'getprop', 'ro.build.version.sdk']).strip())
    logger = subprocess.Popen(ADB + ['logcat'] + (['-T', '1'] if sdk >= 21 else [])
        + ['-v', 'threadtime', 'MainActivity:D',
        'CjsPlugin:I', 'CjsSiteResolver:D', 'HlsProxyServer:D', 'YangshipinResolver:D',
        'LiveUrlResolver:D', 'IJKMEDIA:I', '*:S'],
        stdout=device_log, stderr=subprocess.STDOUT)
    command = ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'batch', str(batch),
        '-e', 'count', str(count), '-e', 'delayMode', delay, '-e', 'setup', str(setup).lower(),
        '-e', 'uncached', str(uncached).lower(), '-e', 'fixedCctv', str(fixed).lower(),
        '-e', 'observeSeconds', str(observe),
        '-e', 'expectFps', str(expect_fps).lower(),
        PACKAGES[version] + '.test/' + RUNNER]
    if fixed:
        rows = [json.loads(line) for line in (OUT / 'results.jsonl').read_text(encoding='utf-8').splitlines()]
        values = {row['channel']: row['activeStreamUrl'] for row in rows
            if row.get('type') == 'sample' and row['version'] == 'baseline'
            and row['provider'] == '央视网' and not row.get('fixedCctv')
            and row.get('activeStreamUrl')}
        for index, channel in enumerate(['cctv1', 'cctv5', 'cctv13']):
            command[-1:-1] = ['-e', 'fixedUrl' + str(index), values[channel]]
    collected = []
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding='utf-8', errors='replace')
        with (logs / (label + '.log')).open('w', encoding='utf-8') as raw:
            for line in process.stdout:
                raw.write(line)
                raw.flush()
                if line.startswith('INSTRUMENTATION_STATUS: sample='):
                    row = json.loads(line.split('sample=', 1)[1])
                    row.update(version=version, recordedAt=datetime.now(timezone.utc).isoformat(),
                        logFile=str(logs / (label + '-device.log')))
                    row.update(json.loads((OUT / version / 'installed-build.json').read_text(encoding='utf-8')))
                    collected.append(row)
                    with (OUT / 'results.jsonl').open('a', encoding='utf-8') as results:
                        results.write(json.dumps(row, ensure_ascii=False) + '\n')
                    print(json.dumps({k: v for k, v in row.items() if k not in
                        ['fpsSamples', 'mediaState', 'activeStreamUrl', 'url', 'logFile']},
                        ensure_ascii=False), flush=True)
            process.wait(timeout=15)
        text = (logs / (label + '.log')).read_text(encoding='utf-8')
        if process.returncode or 'PASS ' not in text or 'INSTRUMENTATION_CODE: -1' not in text:
            raise RuntimeError('Instrumentation failed: ' + text[-2000:])
        if not setup and len(collected) != count:
            raise RuntimeError('Incomplete measurements')
    finally:
        logger.terminate()
        logger.wait(timeout=5)
        device_log.close()


def copy_url_caches(source, target):
    """Keep paired host builds on identical signed URLs/CDNs, outside measurement."""
    for package in [PACKAGES[source], PACKAGES[target]]:
        run(ADB + ['shell', 'am', 'force-stop', package])
    if int(run(ADB + ['shell', 'getprop', 'ro.build.version.sdk']).strip()) < 21:
        # Samsung KitKat's run-as rejects installed debug packages. Transfer only
        # the isolated packages' URL preferences through their own instrumentation.
        output = run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'cacheTransfer',
            'export', PACKAGES[source] + '.test/' + RUNNER]).decode('utf-8')
        if 'PASS cache export' not in output:
            raise RuntimeError('Cache export failed: ' + output)
        caches = next(json.loads(line.split('sample=', 1)[1])['payload']
            for line in output.splitlines() if line.startswith('INSTRUMENTATION_STATUS: sample='))
        now = int(run(ADB + ['shell', 'date', '+%s']).strip()) * 1000
        for name, entries in caches.items():
            for entry in entries:
                key = entry['key']
                if name == 'yangshipin_resolver' and key.startswith('url_') and key.endswith('_at'):
                    entry['value'] = now
                elif name == 'cjs-result-cache' and key.endswith(':until'):
                    entry['value'] = now + 600000
        (OUT / 'paired-caches.json').write_text(json.dumps(caches, ensure_ascii=False), encoding='utf-8')
        # Legacy adbd caps the entire shell command at 4096 bytes.
        payload = base64.b64encode(gzip.compress(json.dumps(caches).encode())).decode()
        for package in [PACKAGES[source], PACKAGES[target]]:
            output = run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'cacheTransfer',
                'import', '-e', 'cachePayload', payload, package + '.test/' + RUNNER]).decode('utf-8')
            if 'PASS cache import' not in output:
                raise RuntimeError('Cache import failed: ' + output)
        return
    for name in ['yangshipin_resolver', 'cjs-result-cache']:
        payload = run(ADB + ['exec-out', 'run-as', PACKAGES[source], 'cat', 'shared_prefs/' + name + '.xml'])
        # This is a controlled warm-URL experiment, not a cache-expiry test.
        # Keep the identical still-playable URL alive across both test processes.
        prefs = ET.fromstring(payload)
        now = int(run(ADB + ['shell', 'date', '+%s']).strip()) * 1000
        for entry in prefs:
            key = entry.get('name', '')
            if name == 'yangshipin_resolver' and key.startswith('url_') and key.endswith('_at'):
                entry.set('value', str(now))
            elif name == 'cjs-result-cache' and key.endswith(':until'):
                entry.set('value', str(now + 600000))
        payload = ET.tostring(prefs, encoding='utf-8', xml_declaration=True)
        local = OUT / ('paired-' + name + '.xml')
        local.write_bytes(payload)
        remote = '/data/local/tmp/ntv-speed-' + name + '.xml'
        run(ADB + ['push', str(local), remote])
        for package in [PACKAGES[source], PACKAGES[target]]:
            run(ADB + ['shell', 'run-as', package, 'cp', remote, 'shared_prefs/' + name + '.xml'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['prepare', 'build', 'setup', 'measure', 'cleanup'])
    parser.add_argument('version', choices=PACKAGES, nargs='?')
    parser.add_argument('--batch', type=int, default=0)
    parser.add_argument('--count', type=int, default=6)
    parser.add_argument('--delay', default='stable', choices=['stable', 'low', 'balanced', 'auto'])
    parser.add_argument('--uncached', action='store_true')
    parser.add_argument('--fixed', action='store_true')
    parser.add_argument('--observe', type=int, default=2)
    parser.add_argument('--serial', default=ADB[-1])
    parser.add_argument('--sdk', type=int, default=28)
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--package-prefix', default='xiao.bu.tv.speed')
    parser.add_argument('--expect-fps', action='store_true')
    args = parser.parse_args()
    ADB[-1] = args.serial
    OUT = args.output.resolve()
    PACKAGES = dict(baseline=args.package_prefix + '16', current=args.package_prefix + '18',
        fixed=args.package_prefix + '18fixed')
    if run(ADB + ['shell', 'getprop', 'ro.build.version.sdk']).strip() != str(args.sdk).encode():
        raise SystemExit('Expected Android API ' + str(args.sdk) + ' device unavailable')
    OUT.mkdir(parents=True, exist_ok=True)
    if args.phase == 'prepare': prepare(args.version)
    elif args.phase == 'build': build(args.version)
    elif args.phase in ('setup', 'measure'):
        measure(args.version, args.batch, args.phase == 'setup', args.count, args.delay,
            args.uncached, args.fixed, args.observe, args.expect_fps)
    elif args.phase == 'cleanup':
        for package in PACKAGES.values():
            for target in [package + '.test', package]:
                print(run(ADB + ['uninstall', target]).decode().strip())
