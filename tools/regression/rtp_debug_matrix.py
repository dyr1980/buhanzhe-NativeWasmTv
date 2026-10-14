"""Verify RTP/TS, bare dynamic RTP and SDP through the existing player entry point."""
import argparse
import contextlib
import functools
import http.server
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import threading
import time
import media_debug_matrix as matrix

ROOT=matrix.ROOT;OUT=matrix.OUT/'rtp';OUT.mkdir(exist_ok=True)
PORT=39982;ADB=matrix.ADB;PACKAGE=matrix.PACKAGE


def capture(name,codec,muxer):
    target=OUT/(name+'.rtpbin')
    if target.exists():
        # A 20-second fixture can end while a slow device is still probing and
        # preparing. Require a live sender throughout the playback sample.
        last=0
        with target.open('rb') as existing:
            while header:=existing.read(12):
                if len(header)!=12:raise RuntimeError('Truncated RTP fixture')
                last,length=struct.unpack('<QI',header);existing.seek(length,1)
        if last>=44000000:return
    source=matrix.MEDIA/('avc-720p25-aac' if codec=='h264' else 'hevc-720p25-aac')/'sample.mp4'
    sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);sock.bind(('127.0.0.1',0));sock.settimeout(.25)
    port=sock.getsockname()[1]
    cmd=['ffmpeg','-v','error','-re','-stream_loop','-1','-i',str(source),'-t','45','-map','0:v']
    if muxer=='rtp_mpegts':cmd+=['-map','0:a']
    cmd+=['-c','copy','-f',muxer]
    if muxer=='rtp':cmd+=['-sdp_file',str(OUT/(name+'.sdp'))]
    cmd+=[f'rtp://127.0.0.1:{port}?pkt_size=1316']
    process=subprocess.Popen(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    first=None;packets=0
    try:
        with target.open('wb') as file:
            while process.poll() is None:
                try:data,_=sock.recvfrom(65536)
                except socket.timeout:continue
                now=time.monotonic_ns()//1000
                if first is None:first=now
                assert data[0]>>6==2 and data[1]&127==(33 if muxer=='rtp_mpegts' else 96)
                file.write(struct.pack('<QI',now-first,len(data)));file.write(data);packets+=1
        error=process.communicate(timeout=10)[1].decode('utf-8','replace')
        if process.returncode or not packets:raise RuntimeError(error)
    except Exception:
        target.unlink(missing_ok=True);raise
    finally:
        if process.poll() is None:process.terminate();process.wait(timeout=10)
        sock.close()
    print('captured',name,'packets',packets,flush=True)


def prepare():
    ndk=Path('D:/android/sdk/ndk/android-ndk-r17c')
    gcc=ndk/'toolchains/arm-linux-androideabi-4.9/prebuilt/windows-x86_64/bin/arm-linux-androideabi-gcc.exe'
    matrix.run([gcc,'-std=c99','-D_POSIX_C_SOURCE=200809L',
                '--sysroot='+str(ndk/'platforms/android-14/arch-arm'),
                '-isystem',ndk/'sysroot/usr/include','-isystem',ndk/'sysroot/usr/include/arm-linux-androideabi',
                '-fPIE','-pie',ROOT/'tools/regression/rtp_fixture_sender.c','-o',OUT/'sender'])
    for arch,platform in [('x86','android-14'),('x86_64','android-21')]:
        prefix='i686-linux-android' if arch=='x86' else 'x86_64-linux-android'
        gcc=ndk/f'toolchains/{arch}-4.9/prebuilt/windows-x86_64/bin/{prefix}-gcc.exe'
        matrix.run([gcc,'-std=c99','-D_POSIX_C_SOURCE=200809L',
                    '--sysroot='+str(ndk/f'platforms/{platform}/arch-{arch}'),
                    '-isystem',ndk/'sysroot/usr/include','-isystem',ndk/f'sysroot/usr/include/{prefix}',
                    '-fPIE','-pie',ROOT/'tools/regression/rtp_fixture_sender.c','-o',OUT/('sender-'+arch)])
    for name,codec,muxer in [('avc-ts','h264','rtp_mpegts'),('hevc-ts','hevc','rtp_mpegts'),('avc-dynamic','h264','rtp')]:
        capture(name,codec,muxer)


def replay(serial,abi='arm32',decode_mode='software',cases=None,run_id=None,apk_dir=None):
    directory=OUT/serial.replace(':','_')
    if run_id:directory=directory/run_id
    directory.mkdir(parents=True,exist_ok=True)
    def adb(*args):return matrix.run([ADB,'-s',serial,*args],timeout=120).decode('utf-8','replace')
    sdk=int(adb('shell','getprop','ro.build.version.sdk').strip())
    if PACKAGE in adb('shell','pm','list','packages',PACKAGE):raise RuntimeError('Test app already exists')
    installed=[]
    handler=functools.partial(matrix.legacy_capture_handler(serial,directory) if sdk<18
                             else http.server.SimpleHTTPRequestHandler,directory=str(matrix.OUT))
    server=http.server.ThreadingHTTPServer(('0.0.0.0',PORT),handler)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        apk_dir=Path(apk_dir) if apk_dir else ROOT/'app/build/outputs/apk'
        for suffix,apk in [('',apk_dir/f'{abi}/debug/app-{abi}-debug.apk'),
                           ('.test',apk_dir/f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk')]:
            result=adb('install','-r',apk)
            if 'Success' not in result:raise RuntimeError(result)
            installed.append(PACKAGE+suffix)
        if sdk>=23:
            for permission in ['READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE']:
                adb('shell','pm','grant',PACKAGE,'android.permission.'+permission)
        if sdk>=21:adb('reverse',f'tcp:{PORT}',f'tcp:{PORT}')
        host_abi=adb('shell','getprop','ro.product.cpu.abi').strip()
        sender_binary=OUT/('sender-'+host_abi if host_abi in ('x86','x86_64') else 'sender')
        adb('push',sender_binary,'/data/local/tmp/ntv-rtp-sender')
        adb('shell','chmod','755','/data/local/tmp/ntv-rtp-sender')
        results=[]
        for index,(name,codec,mode) in enumerate([('avc-ts','h264','rtp'),('hevc-ts','hevc','rtp'),
                                                ('avc-dynamic','h264','rtp'),('avc-dynamic','h264','sdp')]):
            if cases and index+1 not in cases:continue
            if sdk<18:adb('shell','am','force-stop',PACKAGE)
            port=40120+index*2
            filename=directory/f'{index}-manifest.json'
            url=f'rtp://127.0.0.1:{port}'
            if mode=='sdp':
                sdp=(OUT/(name+'.sdp')).read_text(encoding='utf-8')
                import re
                sdp=re.sub(r'm=video \d+',f'm=video {port}',sdp)
                (directory/'video.sdp').write_text(sdp,encoding='utf-8')
                url=(f'http://127.0.0.1:{PORT}/' if sdk>=21 else f'http://{matrix.lan_ip()}:{PORT}/')+directory.relative_to(matrix.OUT).as_posix()+'/video.sdp'
            item=dict(index=index+1,name=name+'-'+mode,fixture=url,checkDebugInfo=True,checkResourceProbe=True,
                      width=1280,height=720,expectedSourceFps=25,expectedScanType='progressive',
                      expectedVideoCodec='H.264' if codec=='h264' else 'H.265',
                      expectedAudioCodec='AAC' if name.endswith('-ts') else '--',decodeMode=decode_mode,sampleMs=8000)
            filename.write_text(json.dumps([item]),encoding='utf-8')
            adb('push',filename,'/data/local/tmp/ntv-media-matrix.json')
            adb('push',OUT/(name+'.rtpbin'),'/data/local/tmp/ntv-rtp-packets')
            with (directory/f'{index}-logcat.txt').open('w',encoding='utf-8') as tracefile,\
                 (directory/f'{index}-instrumentation.txt').open('w',encoding='utf-8') as log:
                traceargs=[ADB,'-s',serial,'logcat','-v','threadtime']+(['-T','1'] if sdk>=21 else [])
                trace=subprocess.Popen(traceargs+['-s','IJKMEDIA:I','MainActivity:I','HlsProxyServer:I'],stdout=tracefile,stderr=subprocess.STDOUT)
                command=[ADB,'-s',serial,'shell','am','instrument','-w','-e','routeThroughResolver','true',
                    '-e','manifest','/data/local/tmp/ntv-media-matrix.json','-e','reportName',f'rtp-{index}.json',
                    PACKAGE+'.test/'+matrix.RUNNER]
                if sdk<18:command[-1:-1]=['-e','captureEndpoint',f'http://{matrix.lan_ip()}:{PORT}/__capture']
                process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
                if os.environ.get('NTV_TEST_FOREGROUND')=='1':
                    time.sleep(1);adb('shell','am','start','-n',PACKAGE+'/xiao.bu.tv.MainActivity')
                time.sleep(4)
                sender=subprocess.Popen([ADB,'-s',serial,'shell','/data/local/tmp/ntv-rtp-sender',
                                         '/data/local/tmp/ntv-rtp-packets',str(port)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
                timed_out=False
                try:process.wait(timeout=90 if sdk<18 else 70)
                except subprocess.TimeoutExpired:
                    timed_out=True
                    adb('shell','am','force-stop',PACKAGE)
                    process.terminate();process.wait(timeout=10)
                sender_result=sender.communicate(timeout=55)[0].decode('utf-8','replace')
                if sender.returncode or 'sent=' not in sender_result:
                    raise RuntimeError('RTP fixture sender did not run: '+sender_result)
                trace.terminate();trace.wait(timeout=10)
            report=directory/f'{index}-result.json'
            try:
                adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/rtp-{index}.json',report)
                row=json.loads(report.read_text(encoding='utf-8'))[0]
            except Exception:
                if not timed_out:raise
                row=dict(index=index+1,name=item['name'])
            if timed_out:row.update(status='failed',hostTimeout=True,error='RTP case exceeded bounded test deadline')
            row['sender']=sender_result
            if row.get('captureVisible'):
                adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/playlist-{index+1:04d}.png',
                    directory/f'{index+1:04d}.png')
            results.append(row);print(serial,name,mode,row['status'],row.get('fpsLabel'),flush=True)
            (directory/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
        (directory/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    finally:
        for package in reversed(installed):
            with contextlib.suppress(Exception):adb('uninstall',package)
        if sdk>=21:
            with contextlib.suppress(Exception):adb('reverse','--remove',f'tcp:{PORT}')
        for path in ['/data/local/tmp/ntv-rtp-sender','/data/local/tmp/ntv-rtp-packets','/data/local/tmp/ntv-media-matrix.json']:
            with contextlib.suppress(Exception):adb('shell','rm','-f',path)
        server.shutdown();server.server_close()


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['prepare','run'])
    parser.add_argument('--serial',default='0123456789ABCDEF')
    parser.add_argument('--abi',choices=['arm32','arm64'],default='arm32')
    parser.add_argument('--decode-mode',choices=['software','hardware'],default='software')
    args=parser.parse_args()
    if args.phase=='prepare':prepare()
    else:replay(args.serial,args.abi,args.decode_mode)
