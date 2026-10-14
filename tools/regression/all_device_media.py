"""Run isolated playback/probe/HUD rounds on every connected local Android device.

Each device has its own process, HTTP ports and evidence directory. APK snapshots
keep one round on the same build, even when subsequent fixes are built locally.
"""
import argparse
import contextlib
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import media_debug_matrix as matrix

OUT=matrix.ROOT/'.codex-tmp/all-device-rounds'

def adb(serial,*args):
    return subprocess.check_output([matrix.ADB,'-s',serial,*map(str,args)],stderr=subprocess.STDOUT,timeout=120).decode('utf-8','replace')

def device_info(serial):
    return dict(serial=serial,model=adb(serial,'shell','getprop','ro.product.model').strip(),
                sdk=int(adb(serial,'shell','getprop','ro.build.version.sdk').strip()),
                release=adb(serial,'shell','getprop','ro.build.version.release').strip(),
                abi=adb(serial,'shell','getprop','ro.product.cpu.abi').strip(),
                abilist=adb(serial,'shell','getprop','ro.product.cpu.abilist').strip())

def install_confirmation(serial):
    original=matrix.run
    def run(command,**kwargs):
        args=list(map(str,command))
        if len(args)>3 and args[:4]==[matrix.ADB,'-s',serial,'install']:
            args.insert(4,'--no-streaming')
            process=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+40
            while process.poll() is None and time.monotonic()<deadline:
                try:
                    result=subprocess.run([matrix.ADB,'-s',serial,'shell','uiautomator','dump','--compressed','/sdcard/ntv-matrix-install.xml'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5)
                    if result.returncode:continue
                    raw=adb(serial,'shell','cat','/sdcard/ntv-matrix-install.xml')
                    for node in ET.fromstring(raw).iter('node'):
                        if node.get('package')=='com.miui.securitycenter' and node.get('text')=='继续安装' and node.get('enabled')=='true':
                            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
                            adb(serial,'shell','input','tap',(x1+x2)//2,(y1+y2)//2)
                            print('Accepted visible MIUI install confirmation',flush=True)
                            break
                except (subprocess.TimeoutExpired,ET.ParseError):pass
                time.sleep(.3)
            output=process.communicate(timeout=15)[0]
            if process.returncode:raise RuntimeError(output.decode('utf-8','replace'))
            return output
        return original(command,**kwargs)
    matrix.run=run
    os.environ['NTV_TEST_FOREGROUND']='1'

def fixtures(base,*unused):
    rows=original_manifest(base,['hls','mp4'])
    for row in rows:
        row.update(decodeMode='hardware',checkResourceProbe=True,category='local')
    def add(name,fmt,decode='software',hdr='hardware'):
        row=original_manifest(base,[fmt],[name])[0]
        row.update(name=row['name']+'-'+decode+'-'+hdr,decodeMode=decode,hdrMode=hdr,
                   checkResourceProbe=True,category='supplement')
        rows.append(row)
    for fmt in ['hls','mp4']:
        add('avc-1080i50-ac351',fmt)
        add('atmos-joc-h264',fmt)
    for name in ['hevc-1080p25-hlg10','hevc-1080p25-pq10-eac3','atmos-joc-hevc']:
        for hdr in ['hardware_sdr','bt709']:add(name,'mp4','hardware',hdr)
    external=json.loads((matrix.OUT/'mi6-external-manifest.json').read_text(encoding='utf-8'))[:5]
    for row in external:
        row.update(sampleMs=10000,checkResourceProbe=True,category='online')
        if row['name'].startswith('洛阳'):row['expectedMinProbeBitrate']=100000
        rows.append(row)
    for name,channel in [('凤凰资讯','fhzx'),('凤凰中文','fhzw'),('凤凰香港','fhhk')]:
        rows.append(dict(name=name,fixture='http://A/ku9/js/fhshow.js?id='+channel+'$凤凰秀JS',
                         checkDebugInfo=True,decodeMode='hardware',sampleMs=10000,
                         expectedVideoCodec='H.264',expectedSourceFps=25,expectedAudioCodec='AAC',category='script'))
    for index,row in enumerate(rows,1):row['index']=index
    return rows

original_manifest=matrix.manifest

def validation_failures(datasets):
    return [dict(kind=kind,index=row.get('index'),name=row.get('name'),
                 status=row.get('status'),probeStatus=row.get('probeStatus'))
            for kind,rows in datasets for row in rows
            if row.get('status') not in ('played','capabilityLimited') or row.get('probeStatus')=='failed']

def worker(args):
    info=device_info(args.serial)
    directory=OUT/f'round{args.round}'/args.serial.replace(':','_');directory.mkdir(parents=True,exist_ok=True)
    status=dict(device=info,started=datetime.now().astimezone().isoformat(),round=args.round,errors=[])
    def save():
        (directory/'status.json').write_text(json.dumps(status,ensure_ascii=False,indent=2),encoding='utf-8')
    save()
    if args.serial=='ff0316c7':install_confirmation(args.serial)
    # A user's original player can keep decoding in the background. Stop only
    # nTv while its isolated test app owns the device; channel data is retained.
    with contextlib.suppress(Exception):adb(args.serial,'shell','am','force-stop','xiao.bu.tv')
    matrix.PORT=args.port
    matrix.manifest=fixtures
    apk_dir=OUT/f'round{args.round}'/'apks'
    try:
        status['phase']='HLS/MP4/HDR/online/scripts';save()
        matrix.replay(args.serial,args.abi,['hls','mp4'],run_id=f'all-round{args.round}',
                      scripts=['E:/Downloads/fhshow.js'],apk_dir=apk_dir)
    except Exception as error:
        status['errors'].append(dict(phase=status['phase'],error=str(error)));save()
    try:
        import rtp_debug_matrix as rtp
        rtp.PORT=args.port+1
        status['phase']='RTP/SDP';save()
        rtp.replay(args.serial,args.abi,'hardware',cases=[1,2,4],run_id=f'all-round{args.round}',apk_dir=apk_dir)
    except Exception as error:
        status['errors'].append(dict(phase=status['phase'],error=str(error)));save()
    finally:
        with contextlib.suppress(Exception):adb(args.serial,'shell','rm','-f','/sdcard/ntv-matrix-install.xml')
    safe=args.serial.replace(':','_')
    sources=[('media',matrix.OUT/safe/f'all-round{args.round}'/'results.json'),
             ('rtp',matrix.OUT/'rtp'/safe/f'all-round{args.round}'/'results.json')]
    status['validationFailures']=validation_failures([(kind,json.loads(path.read_text(encoding='utf-8')))
                                                    for kind,path in sources if path.exists()])
    status['phase']='finished';status['finished']=datetime.now().astimezone().isoformat();save()
    # Completion is not success: preserve all evidence, but fail CI for a bad
    # picture/metadata assertion, unavailable source or resource-probe failure.
    return 1 if status['errors'] or status['validationFailures'] else 0

def dispatch(args):
    devices=[line.split()[0] for line in subprocess.check_output([matrix.ADB,'devices'],text=True).splitlines()[1:] if '\tdevice' in line]
    if args.only:devices=[serial for serial in devices if serial in args.only]
    if not devices:raise RuntimeError('No connected devices')
    round_dir=OUT/f'round{args.round}'
    if round_dir.exists():raise RuntimeError('Round exists; preserve prior evidence instead of overwriting it: '+str(round_dir))
    round_dir.mkdir(parents=True)
    manifest=dict(started=datetime.now().astimezone().isoformat(),devices=[],apks={})
    for abi in ['arm32','arm64']:
        for suffix,path in [('',f'{abi}/debug/app-{abi}-debug.apk'),('.test',f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk')]:
            source=matrix.ROOT/'app/build/outputs/apk'/path;target=round_dir/'apks'/path
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
            manifest['apks'][path]=hashlib.sha256(target.read_bytes()).hexdigest()
    jobs=[]
    for index,serial in enumerate(devices):
        info=device_info(serial)
        abi='arm64' if info['abi']=='arm64-v8a' else 'arm32'
        info['testAbi']=abi;manifest['devices'].append(info)
        directory=round_dir/serial.replace(':','_');directory.mkdir(exist_ok=True)
        log=(directory/'worker.log').open('w',encoding='utf-8')
        command=[sys.executable,str(Path(__file__).resolve()),'--worker','--round',str(args.round),
                 '--serial',serial,'--abi',abi,'--port',str(39990+index*2)]
        jobs.append((serial,subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,
                    env=dict(os.environ,PYTHONIOENCODING='utf-8')),log))
    (round_dir/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest['devices'],ensure_ascii=True),flush=True)
    start=time.monotonic()
    while any(process.poll() is None for _,process,_ in jobs):
        for serial,process,_ in jobs:
            directory=round_dir/serial.replace(':','_')
            state=json.loads((directory/'status.json').read_text(encoding='utf-8')) if (directory/'status.json').exists() else {}
            progress=matrix.OUT/serial.replace(':','_')/f'all-round{args.round}'/'instrumentation.txt'
            last=progress.read_text(encoding='utf-8').strip().splitlines()[-1:] if progress.exists() else []
            print(json.dumps(dict(serial=serial,phase=state.get('phase','starting'),last=last,exit=process.poll()),ensure_ascii=True),flush=True)
        if time.monotonic()-start>2400:
            for serial,process,_ in jobs:
                if process.poll() is None:
                    with contextlib.suppress(Exception):adb(serial,'shell','am','force-stop',matrix.PACKAGE)
                    process.terminate()
            raise TimeoutError('Round exceeded 40 minutes; test apps stopped')
        time.sleep(30)
    for serial,process,log in jobs:
        log.close();print(serial,'worker exit',process.returncode,flush=True)
    return int(any(process.returncode for _,process,_ in jobs))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--round',type=int,required=True)
    parser.add_argument('--worker',action='store_true')
    parser.add_argument('--serial');parser.add_argument('--abi',choices=['arm32','arm64'])
    parser.add_argument('--port',type=int,default=39990)
    parser.add_argument('--only',nargs='*')
    args=parser.parse_args()
    sys.exit(worker(args) if args.worker else dispatch(args))
