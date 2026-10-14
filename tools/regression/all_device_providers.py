"""Two real-provider rounds, keeping setup separate from remote-OK latency."""
import argparse
from collections import Counter
from datetime import datetime
import contextlib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time
import media_debug_matrix as matrix
import all_device_media as devices

ROOT=matrix.ROOT
OUT=ROOT/'.codex-tmp/provider-live-rounds'
PACKAGE='xiao.bu.tv.providerregression'
RUNNER='xiao.bu.tv.ProviderLiveInstrumentation'

def validate_results(rows, expected, preflight, instrumentation, adb_exit=0):
    """ADB exit zero alone does not prove that the Android runner completed."""
    errors=[]
    if adb_exit:
        errors.append('ADB instrumentation exit '+str(adb_exit))
    if len(rows)!=expected or any(row.get('status') not in ('passed','failed') for row in rows):
        errors.append(f'Incomplete provider test: {len(rows)}/{expected}')
    if len({(row.get('provider'),row.get('channel')) for row in rows})!=len(rows):
        errors.append('Duplicate provider/channel results')
    if any(row.get('status')!='passed' for row in rows):
        errors.append('Provider playback checks failed')
    if preflight.get('status')!='passed':
        errors.append('Channel-list UI preflight did not pass')
    complete=re.search(r'COMPLETE cases=(\d+) failures=(\d+)',instrumentation)
    if (not re.search(r'^INSTRUMENTATION_CODE: -1\s*$',instrumentation,re.MULTILINE)
            or complete is None or tuple(map(int,complete.groups()))!=(expected,0)):
        errors.append('Instrumentation result bundle did not pass')
    return errors

def stop_process(process):
    """Reap local ADB processes even when a device stops responding."""
    if process is None:return
    if process.poll() is None:process.terminate()
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill();process.wait(timeout=10)

def build(build_id, trace_native=False):
    folder=OUT/'builds'/build_id
    if folder.exists():raise RuntimeError('Preserve existing build snapshot: '+str(folder))
    folder.mkdir(parents=True)
    env=dict(os.environ,JAVA_HOME=str(ROOT/'.codex-tmp/jdk8/runtime/jdk8u504-b01'))
    with (folder/'build.log').open('w',encoding='utf-8') as log:
        subprocess.run([str(ROOT/'gradlew.bat'),':app:assembleArm32Debug',':app:assembleArm32DebugAndroidTest',
            ':app:assembleArm64Debug',':app:assembleArm64DebugAndroidTest',
            '-PapplicationIdOverride='+PACKAGE,'-PtestRunner='+RUNNER,
            '-PcastLatencyTrace='+str(trace_native).lower(),'--offline'],cwd=ROOT,env=env,
            stdout=log,stderr=subprocess.STDOUT,check=True,timeout=300)
    manifest={'build':build_id,'traceNative':trace_native,'apks':{},'sources':{}}
    for abi in ['arm32','arm64']:
        for relative in [f'{abi}/debug/app-{abi}-debug.apk',f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk']:
            source=ROOT/'app/build/outputs/apk'/relative;target=folder/'apks'/relative
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
            manifest['apks'][relative]=hashlib.sha256(target.read_bytes()).hexdigest()
    for relative in ['app/src/main/java/xiao/bu/tv/MainActivity.java','app/src/main/java/xiao/bu/tv/HlsProxyServer.java',
                     'app/src/androidTest/java/xiao/bu/tv/ProviderLiveInstrumentation.java']:
        source=ROOT/relative;target=folder/'sources'/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        manifest['sources'][relative]=hashlib.sha256(source.read_bytes()).hexdigest()
    (folder/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('build snapshot ready',build_id,flush=True)

def replay(args):
    serial=args.serial;info=devices.device_info(serial);abi='arm64' if info['abi']=='arm64-v8a' else 'arm32'
    info['testAbi']=abi
    directory=OUT/f'round{args.round}'/serial.replace(':','_');directory.mkdir(parents=True,exist_ok=True)
    state=dict(device=info,round=args.round,build=args.build_id,phase='install',started=datetime.now().astimezone().isoformat(),errors=[])
    def save(): (directory/'status.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
    def adb(*parameters):return devices.adb(serial,*parameters)
    save();trace=None;process=None;timer=None
    if serial=='ff0316c7':devices.install_confirmation(serial)
    try:
        adb('shell','am','force-stop','xiao.bu.tv')
        adb('shell','am','force-stop',PACKAGE)
        for relative in [f'{abi}/debug/app-{abi}-debug.apk',f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk']:
            options=['--abi','arm64-v8a' if abi=='arm64' else 'armeabi-v7a'] if info['sdk']>=21 else []
            output=matrix.run([matrix.ADB,'-s',serial,'install','-r',*options,str(OUT/'builds'/args.build_id/'apks'/relative)]).decode('utf-8','replace')
            if 'Success' not in output:raise RuntimeError(output)
        if info['sdk']>=23:
            for permission in ['READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE']:
                adb('shell','pm','grant',PACKAGE,'android.permission.'+permission)
        command=[matrix.ADB,'-s',serial,'shell','am','instrument','-w','-r','-e','round',str(args.round),
                 '-e','observeSeconds',str(args.observe),PACKAGE+'.test/'+RUNNER]
        log_args=[matrix.ADB,'-s',serial,'logcat','-v','threadtime']
        if info['sdk']>=21:log_args+=['-T','1']
        with (directory/'player.log').open('w',encoding='utf-8') as native,(directory/'instrumentation.txt').open('w',encoding='utf-8') as log:
            trace=subprocess.Popen(log_args+['MainActivity:I','IJKMEDIA:I','HlsProxyServer:I','CjsPlugin:I','CjsSiteResolver:I',
                'YangshipinResolver:I','AndroidRuntime:E','libc:F','DEBUG:F','*:S'],stdout=native,stderr=subprocess.STDOUT)
            process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
            def abort():
                if process.poll() is None:
                    state['errors'].append('Instrumentation exceeded 20 minutes')
                    with contextlib.suppress(Exception):adb('shell','am','force-stop',PACKAGE)
                    process.terminate()
            watchdog=threading.Timer(1200,abort);watchdog.start()
            try:
                state['phase']='plugins / remote OK / provider playback';save()
                for line in process.stdout:
                    log.write(line);log.flush()
                    if 'PLUGINS_READY' in line and serial=='ff0316c7':
                        timer=threading.Timer(1,lambda:adb('shell','am','start','-n',PACKAGE+'/xiao.bu.tv.MainActivity'));timer.start()
                    if 'stream=' in line:
                        state['progress']=line.strip().split('stream=',1)[1];save();print(serial,state['progress'],flush=True)
                process.wait(timeout=20)
            finally:
                watchdog.cancel()
                if timer is not None:timer.join(timeout=5)
                stop_process(process)
            stop_process(trace);trace=None
        for name in ['provider-live.json','ui-preflight.json','setup.json']+[f'provider-{n:02d}.png' for n in range(1,11)]:
            with contextlib.suppress(subprocess.CalledProcessError):
                adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/{name}',directory/name)
        rows=json.loads((directory/'provider-live.json').read_text(encoding='utf-8')) if (directory/'provider-live.json').exists() else []
        state['cases']=len(rows);state['counts']=dict(Counter(row['status'] for row in rows))
        state['complete']=len(rows)==10 and all(row.get('status') in ('passed','failed') for row in rows)
        state['validationFailures']=[{'provider':r['provider'],'channel':r['channel'],'error':r.get('error')} for r in rows if r['status']!='passed']
        preflight=directory/'ui-preflight.json'
        state['errors'].extend(validate_results(rows,10,
            json.loads(preflight.read_text(encoding='utf-8')) if preflight.exists() else {},
            (directory/'instrumentation.txt').read_text(encoding='utf-8'),process.returncode))
    except Exception as error:state['errors'].append(str(error))
    finally:
        for child in (process,trace):
            with contextlib.suppress(Exception):stop_process(child)
        state['phase']='finished';state['finished']=datetime.now().astimezone().isoformat();save()
    return int(bool(state['errors'] or state.get('validationFailures')))

def dispatch(args):
    folder=OUT/f'round{args.round}'
    existing=folder.exists()
    if existing and not args.only:raise RuntimeError('Preserve prior round: '+str(folder))
    connected=[line.split()[0] for line in subprocess.check_output([matrix.ADB,'devices'],text=True).splitlines()[1:] if '\tdevice' in line]
    if args.only:connected=[serial for serial in connected if serial in args.only]
    if not connected:raise RuntimeError('No local devices')
    folder.mkdir(parents=True,exist_ok=existing)
    manifest=dict(round=args.round,build=args.build_id,observeSeconds=args.observe,devices=[devices.device_info(s) for s in connected],
                  channels=['CCTV-1','CCTV-2','CCTV-5','CCTV-8','CCTV-13'],providers=['央视频','央视网'],expected=len(connected)*10)
    if not existing:(folder/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    jobs=[]
    for serial in connected:
        directory=folder/serial.replace(':','_');directory.mkdir()
        log=(directory/'worker.log').open('w',encoding='utf-8')
        command=[sys.executable,str(Path(__file__).resolve()),'run','--worker','--serial',serial,
                 '--round',str(args.round),'--build-id',args.build_id,'--observe',str(args.observe)]
        jobs.append((serial,subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,env=dict(os.environ,PYTHONIOENCODING='utf-8')),log))
    while any(p.poll() is None for _,p,_ in jobs):
        for serial,process,_ in jobs:
            source=folder/serial.replace(':','_')/'status.json'
            state=json.loads(source.read_text(encoding='utf-8')) if source.exists() else {}
            print(serial,state.get('phase','starting'),state.get('progress',''),process.poll(),flush=True)
        time.sleep(30)
    for serial,process,log in jobs:log.close();print(serial,'exit',process.returncode,flush=True)
    return int(any(p.returncode for _,p,_ in jobs))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['build','run'])
    parser.add_argument('--build-id',default='candidate');parser.add_argument('--round',type=int,default=1)
    parser.add_argument('--observe',type=int,default=30);parser.add_argument('--worker',action='store_true');parser.add_argument('--serial')
    parser.add_argument('--only',nargs='+')
    parser.add_argument('--trace-native',action='store_true',help='Diagnostic DEBUG builds: packet/decode/display timestamps')
    args=parser.parse_args()
    sys.exit(build(args.build_id,args.trace_native) if args.phase=='build' else replay(args) if args.worker else dispatch(args))
