"""Real-provider hard-decoder recovery, including optional root stack evidence."""
import argparse
import json
import re
import subprocess
import time
import threading
import all_device_providers as providers
import all_device_media as devices
import media_debug_matrix as matrix

def main(args):
    if args.serial == '0123456789ABCDEF':
        raise ValueError('KT107 adaptation stopped at user request')
    channels=args.channels.split(',')
    if not channels or len(set(channels))!=len(channels) or any(c not in ('0','1','2','3','4') for c in channels):
        raise ValueError('Channels must be distinct positions 0 to 4')
    if args.observe<=0 or args.detach<0:
        raise ValueError('Observe must be positive; detach must not be negative')
    out = providers.OUT / 'diagnostics' / args.name
    out.mkdir()
    adb = [matrix.ADB, '-s', args.serial]
    def run(*parameters, timeout=30):
        return subprocess.run(adb+list(map(str,parameters)), capture_output=True, text=True,
                              encoding='utf-8', errors='replace', timeout=timeout)
    info = json.loads((providers.OUT/'builds'/args.build_id/'manifest.json').read_text(encoding='utf-8'))
    (out/'build-manifest.json').write_text(json.dumps(info, indent=2), encoding='utf-8')
    run('shell','am','force-stop','xiao.bu.tv')
    device = devices.device_info(args.serial)
    abi = 'arm64' if device['abi']=='arm64-v8a' else 'arm32'
    if args.serial=='ff0316c7':devices.install_confirmation(args.serial)
    for relative in (f'{abi}/debug/app-{abi}-debug.apk', f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk'):
        options=['--abi','arm64-v8a' if abi=='arm64' else 'armeabi-v7a'] if device['sdk']>=21 else []
        installed=matrix.run(adb+['install','-r',*options,str(providers.OUT/'builds'/args.build_id/'apks'/relative)]).decode('utf-8','replace')
        if 'Success' not in installed:raise RuntimeError(installed)
    run('shell','am','force-stop',providers.PACKAGE)
    if device['sdk']>=23:
        for permission in ('READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE'):
            run('shell','pm','grant',providers.PACKAGE,'android.permission.'+permission)
    for remote, name in (('/system/etc/media_codecs_google_video.xml','media-codecs.xml'),('/proc/cpuinfo','cpuinfo.txt')):
        (out/name).write_text(run('shell','cat',remote).stdout,encoding='utf-8')
    root = 'uid=0(' in run('shell','id').stdout
    system_pids = []
    for line in run('shell','ps','-A').stdout.splitlines():
        if 'media.codec' in line or 'media.omx' in line:
            pid = line.split()[1];system_pids.append(pid)
            (out/f'media-maps-{pid}.txt').write_text(run('shell','cat',f'/proc/{pid}/maps').stdout,encoding='utf-8')
    command = adb+['shell','am','instrument','-w','-r','-e','round','2','-e','quality','high',
        '-e','allowRecovery','true','-e','auditFrozenPixels','false' if args.detach else 'true','-e','channels',args.channels,
        '-e','providerOnly','1','-e','observeSeconds',str(args.observe),providers.PACKAGE+'.test/'+providers.RUNNER]
    if args.detach:
        command[-1:-1] = ['-e','detachVideoAfterMs',str(args.detach)]
    if args.check_exhaustion:
        command[-1:-1] = ['-e','checkRecoveryExhaustion','true']
    tags=['MainActivity:I','IJKMEDIA:I','HlsProxyServer:I','MediaCodec:V','ACodec:V','OMXNodeInstance:V',
          'BufferQueueProducer:W','ActivityManager:I','WindowManager:I','PowerManagerService:I','libc:F','AndroidRuntime:E','*:S']
    deadline = time.monotonic()+max(300, args.observe*4+120)
    with (out/'player.log').open('w',encoding='utf-8') as log,(out/'instrumentation.txt').open('w',encoding='utf-8') as output:
        trace = subprocess.Popen(adb+['logcat',*(['-T','1'] if device['sdk']>=21 else []),'-v','threadtime']+tags,stdout=log,stderr=subprocess.STDOUT)
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        def abort():
            if process.poll() is None:
                try:run('shell','am','force-stop',providers.PACKAGE)
                finally:process.terminate()
        watchdog = threading.Timer(max(300, args.observe*4+120), abort)
        watchdog.start()
        foreground=None
        try:
            for line in process.stdout:
                output.write(line);output.flush()
                if 'stream=' in line:print(line.strip(),flush=True)
                if 'PLUGINS_READY' in line and args.serial=='ff0316c7':
                    foreground=threading.Timer(1,lambda:run('shell','am','start','-n',providers.PACKAGE+'/xiao.bu.tv.MainActivity'))
                    foreground.start()
                stopped = re.search(r'VIDEO_OUTPUT_STOPPED pid=(\d+) index=(\d+)',line)
                if stopped and root:
                    pid, index = stopped.groups()
                    # Non-terminating debuggerd backtraces; never kill the decoder.
                    for target in [pid]+system_pids:
                        result=run('shell','debuggerd','-b',target,timeout=15)
                        (out/f'stack-{index}-{target}.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
                    (out/f'codec-state-{index}.txt').write_text(run('shell','dumpsys','media.codec').stdout,encoding='utf-8')
                if time.monotonic()>deadline:raise TimeoutError('Decoder audit exceeded deadline')
            process.wait(timeout=20)
        finally:
            watchdog.cancel()
            if foreground is not None:foreground.join(timeout=5)
            try:providers.stop_process(process)
            finally:providers.stop_process(trace)
    for name in ('provider-live.json','ui-preflight.json','setup.json'):
        result=run('pull',f'/sdcard/Android/data/{providers.PACKAGE}/files/{name}',out/name)
        if result.returncode:raise RuntimeError(result.stderr)
    rows=json.loads((out/'provider-live.json').read_text(encoding='utf-8'))
    errors=providers.validate_results(rows,len(channels),
        json.loads((out/'ui-preflight.json').read_text(encoding='utf-8')),
        (out/'instrumentation.txt').read_text(encoding='utf-8'),process.returncode)
    for row in rows:
        images=[f"provider-{row['index']:02d}.png"]+[r['image'] for r in row.get('frozenPixelAudits',[])]
        for name in images:run('pull',f'/sdcard/Android/data/{providers.PACKAGE}/files/{name}',out/name)
    print([(r['channel'],r['status'],r.get('recoveries'),r.get('decoder'),r.get('outputFps')) for r in rows])
    (out/'validation.json').write_text(json.dumps({'expected':len(channels),'errors':errors},indent=2),encoding='utf-8')
    for error in errors:print(error,flush=True)
    return int(bool(errors))

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',default='127.0.0.1:21503')
    parser.add_argument('--build-id',required=True);parser.add_argument('--name',required=True)
    parser.add_argument('--channels',default='3');parser.add_argument('--observe',type=int,default=120)
    parser.add_argument('--detach',type=int,default=0)
    parser.add_argument('--check-exhaustion',action='store_true',help='Policy boundary check, not sustained playback')
    raise SystemExit(main(parser.parse_args()))
