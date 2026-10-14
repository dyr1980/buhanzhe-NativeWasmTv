"""Generate ffprobe-verified media and replay it through the real debug HUD.

Requires local ffmpeg/ffprobe, the cached Gradle toolchain and an ADB device.
Uses an isolated application ID; never changes the user's channel preferences.
"""
import argparse
import concurrent.futures
import contextlib
import functools
from fractions import Fraction
import http.server
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import threading
import time
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.codex-tmp/hls-debug-matrix'
MEDIA = OUT / 'media'
ADB = 'D:/android/sdk/platform-tools/adb.exe'
PACKAGE = 'xiao.bu.tv.hlsmatrix'
RUNNER = 'xiao.bu.tv.PlaylistPlaybackInstrumentation'
PORT = 39981


class MediaHandler(http.server.SimpleHTTPRequestHandler):
    """Serve real single byte ranges, so MP4 seeking/reconnect is testable."""
    def log_message(self,*args):pass

    def send_head(self):
        self.remaining=None
        header=self.headers.get('Range')
        if not header:return super().send_head()
        path=Path(self.translate_path(self.path))
        if not path.is_file():return super().send_head()
        match=re.fullmatch(r'bytes=(\d*)-(\d*)',header.strip())
        size=path.stat().st_size
        if match and size:
            start=int(match[1]) if match[1] else max(0,size-int(match[2] or '0'))
            end=min(size-1,int(match[2])) if match[1] and match[2] else size-1
            if 0<=start<=end<size:
                file=path.open('rb');file.seek(start);self.remaining=end-start+1
                self.send_response(206)
                self.send_header('Content-Type',self.guess_type(str(path)))
                self.send_header('Accept-Ranges','bytes')
                self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
                self.send_header('Content-Length',str(self.remaining));self.end_headers()
                return file
        self.send_response(416);self.send_header('Content-Range',f'bytes */{size}')
        self.send_header('Content-Length','0');self.end_headers();return None

    def copyfile(self,source,outputfile):
        try:
            if self.remaining is None:return super().copyfile(source,outputfile)
            while self.remaining>0:
                data=source.read(min(65536,self.remaining))
                if not data:break
                outputfile.write(data);self.remaining-=len(data)
        except (BrokenPipeError,ConnectionResetError):
            # A channel switch deliberately cancels an in-flight download.
            pass

def legacy_capture_handler(serial, directory):
    """API 14-17 lacks UiAutomation; the authorized ADB shell captures the screen."""
    class Handler(MediaHandler):
        def do_GET(self):
            request=urlsplit(self.path)
            if request.path!='/__capture':return super().do_GET()
            try:
                index=int(parse_qs(request.query).get('index',['0'])[0])
                if not 1<=index<=1000:raise ValueError('Invalid capture index')
                folder=directory/'shell-captures';folder.mkdir(exist_ok=True)
                target=folder/f'{index:04d}.png'
                remote=f'/sdcard/Android/data/{PACKAGE}/files/shell-capture.png'
                run([ADB,'-s',serial,'shell','screencap','-p',remote],timeout=20)
                run([ADB,'-s',serial,'pull',remote,target],timeout=20)
                body=target.read_bytes()
                self.send_response(200);self.send_header('Content-Type','image/png')
                self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
            except Exception as error:self.send_error(500,str(error))
    return Handler


def run(command, **kwargs):
    return subprocess.check_output(list(map(str, command)), stderr=subprocess.STDOUT, **kwargs)


def probe(path, packets=False):
    args = ['ffprobe', '-v', 'error', '-show_streams', '-show_format']
    if packets:
        args += ['-show_packets', '-show_entries',
                 'packet=stream_index,pts_time,duration_time,size:stream:format']
    return json.loads(run(args + ['-of', 'json', path]))


def case(name, width, height, fps, bitrate, video='h264', audio='aac',
         channels=2, audio_bitrate=128000, container='mpegts', depth=8,
         transfer='bt709', interlaced=False, sample_rate=48000):
    return dict(name=name, width=width, height=height, fps=fps, bitrate=bitrate,
                video=video, audio=audio, channels=channels, audio_bitrate=audio_bitrate,
                container=container, depth=depth, transfer=transfer,
                interlaced=interlaced, sample_rate=sample_rate)


CASES = [
    case('avc-360p25-aac-mono',640,360,25,300000,channels=1,audio_bitrate=48000),
    case('avc-480p30-aac-44100',854,480,30,800000,audio_bitrate=96000,sample_rate=44100),
    case('avc-576p25-mp2',1024,576,25,1500000,audio='mp2',audio_bitrate=192000),
    case('avc-720p25-aac',1280,720,25,2000000),
    case('avc-720p50-aac',1280,720,50,3000000,audio_bitrate=192000),
    case('avc-1080p25-aac',1920,1080,25,5000000,audio_bitrate=192000),
    case('avc-1080p30-ac3',1920,1080,30,6000000,audio='ac3',audio_bitrate=192000),
    case('avc-1080p2997-aac51',1920,1080,'30000/1001',5000000,channels=6,audio_bitrate=384000),
    case('avc-1080p60-fmp4',1920,1080,60,8000000,container='fmp4',audio_bitrate=192000),
    case('avc-1080i50-ac351',1920,1080,25,6000000,audio='ac3',channels=6,
         audio_bitrate=384000,interlaced=True),
    case('avc-480p25-mp3',854,480,25,1000000,audio='mp3'),
    case('avc-720p25-ac351',1280,720,25,2000000,audio='ac3',channels=6,audio_bitrate=384000),
    case('avc-720p25-eac351',1280,720,25,2000000,audio='eac3',channels=6,audio_bitrate=640000),
    case('avc-720p25-eac3-fmp4',1280,720,25,2000000,audio='eac3',audio_bitrate=192000,container='fmp4'),
    case('avc-720p25-noaudio',1280,720,25,1500000,audio=None),
    case('hevc-480p25-aac',854,480,25,600000,video='hevc'),
    case('hevc-720p25-aac',1280,720,25,1500000,video='hevc'),
    case('hevc-1080p25-aac',1920,1080,25,3000000,video='hevc'),
    case('hevc-1080p50-ac351',1920,1080,50,5000000,video='hevc',audio='ac3',channels=6,audio_bitrate=384000),
    case('hevc-1080p30-main10-sdr',1920,1080,30,3000000,video='hevc',depth=10,
         audio='eac3',channels=6,audio_bitrate=640000,container='fmp4'),
    case('hevc-2160p25-aac',3840,2160,25,8000000,video='hevc',audio_bitrate=192000),
    case('hevc-1080p25-hlg10',1920,1080,25,3000000,video='hevc',depth=10,transfer='arib-std-b67'),
    case('hevc-1080p25-pq10-eac3',1920,1080,25,3000000,video='hevc',depth=10,
         transfer='smpte2084',audio='eac3',channels=6,audio_bitrate=640000,container='fmp4'),
    case('hevc-2160p50-aac',3840,2160,50,12000000,video='hevc',audio_bitrate=192000),
    case('avc-720p25-low512k',1280,720,25,512000),
    case('avc-720p25-high8m',1280,720,25,8000000),
]


def encode(spec):
    directory = MEDIA / spec['name']; directory.mkdir(parents=True, exist_ok=True)
    target = directory / 'sample.mp4'
    if not target.exists():
        fps = float(spec['fps']) if '/' not in str(spec['fps']) else 30000/1001
        input_rate = fps*2 if spec['interlaced'] else spec['fps']
        args = ['ffmpeg','-hide_banner','-loglevel','error','-f','lavfi','-i',
                f"testsrc2=size={spec['width']}x{spec['height']}:rate={input_rate}"]
        if spec['audio']:
            layout = '5.1' if spec['channels']==6 else 'mono' if spec['channels']==1 else 'stereo'
            args += ['-f','lavfi','-i',f"anoisesrc=color=pink:amplitude=0.05:sample_rate={spec['sample_rate']}",
                     '-map','0:v','-map','1:a','-ac',spec['channels'],'-channel_layout',layout,
                     '-c:a','libmp3lame' if spec['audio']=='mp3' else spec['audio'],
                     '-b:a',spec['audio_bitrate'],'-ar',spec['sample_rate']]
        else:
            args += ['-an']
        args += ['-t','20','-c:v','libx264' if spec['video']=='h264' else 'libx265',
                 '-preset','ultrafast','-threads','2','-pix_fmt','yuv420p10le' if spec['depth']==10 else 'yuv420p',
                 '-b:v',spec['bitrate'],'-maxrate',spec['bitrate'],'-bufsize',spec['bitrate']*2,
                 '-g',round(fps*2),'-keyint_min',round(fps*2),'-sc_threshold','0']
        if spec['interlaced']:
            args += ['-vf','interlace=scan=tff:lowpass=linear','-flags','+ilme+ildct','-x264-params','tff=1:nal-hrd=cbr']
        elif spec['video']=='h264':
            args += ['-x264-params','nal-hrd=cbr:force-cfr=1']
        else:
            args += ['-x265-params','pools=2:frame-threads=2:log-level=error:strict-cbr=1:hrd=1','-tag:v','hvc1']
        args += ['-color_primaries','bt2020' if spec['transfer']!='bt709' else 'bt709',
                 '-color_trc',spec['transfer'],'-colorspace','bt2020nc' if spec['transfer']!='bt709' else 'bt709',
                 '-color_range','tv','-movflags','+faststart','-y',target]
        try:
            run(args, timeout=300)
        except Exception:
            target.unlink(missing_ok=True)
            raise
    # Check the encoded VUI, rather than trusting FFmpeg output options. Some
    # encoder builds omit these flags unless explicitly passed to the encoder.
    streams=probe(target)['streams']
    video=next(s for s in streams if s['codec_type']=='video')
    if video.get('color_transfer')!=spec['transfer']:
        values='colour_primaries=9:transfer_characteristics=%d:matrix_coefficients=9' % (18 if spec['transfer']=='arib-std-b67' else 16) if spec['transfer']!='bt709' else 'colour_primaries=1:transfer_characteristics=1:matrix_coefficients=1'
        tagged=directory/'tagged.mp4'
        run(['ffmpeg','-v','error','-i',target,'-map','0','-c','copy','-bsf:v',
             ('h264_metadata=' if spec['video']=='h264' else 'hevc_metadata=')+values,
             '-movflags','+faststart','-y',tagged],timeout=60)
        tagged.replace(target)
        for old in directory.iterdir():
            if old.name in ('index.m3u8','init.mp4') or old.name.startswith('segment-'):
                old.unlink()
    package_hls(directory, spec['container'])
    reference = probe(target, True)
    (directory/'reference.json').write_text(json.dumps(reference,indent=2),encoding='utf-8')
    print('generated',spec['name'],flush=True)
    return spec


def package_hls(directory, container):
    if (directory/'index.m3u8').exists() and (container!='fmp4' or (directory/'init.mp4').exists()): return
    extension = 'm4s' if container=='fmp4' else 'ts'
    run(['ffmpeg','-hide_banner','-loglevel','error','-i',directory/'sample.mp4',
         '-map','0','-c','copy','-f','hls','-hls_time','2','-hls_playlist_type','vod',
         '-hls_segment_type',container,'-hls_segment_filename',directory/f'segment-%03d.{extension}',
         '-y',directory/'index.m3u8'],timeout=60,cwd=directory)


def generate():
    MEDIA.mkdir(parents=True,exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        specs=list(pool.map(encode,CASES))
    source=OUT/'atmos-source.mp4'
    if not source.exists():
        url='https://ott.dolby.com/OnDelKits/DDP/Dolby_Digital_Plus_Online_Delivery_Kit_v1.4.1/Test_Signals/muxed_streams/MP4/Example/ChID_voices_1920x1080p_25fps_h265_6ch_640kbps_ddp_joc.mp4'
        run(['ffmpeg','-v','error','-i',url,'-t','20','-map','0','-c','copy','-y',source],timeout=120)
    if not any('Atmos' in stream.get('profile','') for stream in probe(source)['streams']):
        raise RuntimeError('Source is not verified E-AC-3 JOC/Atmos')
    for video,container in [('hevc','fmp4'),('h264','mpegts')]:
        spec=case(f'atmos-joc-{video}',1920 if video=='hevc' else 1280,
                  1080 if video=='hevc' else 720,25,3000000,video=video,
                  audio='eac3',channels=6,audio_bitrate=640000,container=container,
                  depth=10 if video=='hevc' else 8)
        directory=MEDIA/spec['name'];directory.mkdir(exist_ok=True)
        if not (directory/'sample.mp4').exists():
            args=['ffmpeg','-v','error','-i',source,'-map','0:v','-map','0:a','-c:a','copy']
            if video=='hevc': args+=['-c:v','copy']
            else: args+=['-vf','scale=1280:720','-c:v','libx264','-preset','ultrafast','-threads','2',
                         '-pix_fmt','yuv420p','-b:v','3000000','-g','50','-color_primaries','bt709',
                         '-color_trc','bt709','-colorspace','bt709']
            run(args+['-movflags','+faststart','-y',directory/'sample.mp4'],timeout=120)
        package_hls(directory,container)
        reference=probe(directory/'sample.mp4',True)
        assert any('Atmos' in s.get('profile','') for s in reference['streams'])
        (directory/'reference.json').write_text(json.dumps(reference,indent=2),encoding='utf-8')
        specs.append(spec)
    (OUT/'specs.json').write_text(json.dumps(specs,indent=2),encoding='utf-8')


def manifest(base_url, formats, names=None):
    result=[]
    for spec in json.loads((OUT/'specs.json').read_text(encoding='utf-8')):
        if names and spec['name'] not in names:continue
        ref=json.loads((MEDIA/spec['name']/'reference.json').read_text(encoding='utf-8'))
        video=next(s for s in ref['streams'] if s['codec_type']=='video')
        audio=next((s for s in ref['streams'] if s['codec_type']=='audio'),None)
        rates={}
        for stream in ref['streams']:
            packets=[p for p in ref['packets'] if p['stream_index']==stream['index'] and 'pts_time' in p]
            start=min(float(p['pts_time']) for p in packets)
            end=max(float(p['pts_time'])+float(p.get('duration_time',0)) for p in packets)
            rates[stream['codec_type']]=sum(int(p['size']) for p in packets)*8/(end-start)
        for fmt in formats:
            live=fmt=='hls' and os.environ.get('NTV_LIVE_FIXTURES')=='1'
            if live:
                playlist=(MEDIA/spec['name']/'index.m3u8').read_text(encoding='utf-8')
                (MEDIA/spec['name']/'index-live.m3u8').write_text(
                    playlist.replace('#EXT-X-ENDLIST\n','').replace('#EXT-X-PLAYLIST-TYPE:VOD\n',''),encoding='utf-8')
            row=dict(index=len(result)+1,name=spec['name']+'-'+fmt,
                     fixture=base_url+'/media/'+spec['name']+('/index-live.m3u8' if live else '/index.m3u8' if fmt=='hls' else '/sample.mp4'),
                     checkDebugInfo=True,width=video['width'],height=video['height'],
                     expectedVideoCodec='H.264' if video['codec_name']=='h264' else 'H.265',
                     expectedAudioCodec='MP2' if fmt=='hls' and spec['audio']=='mp2' else {'aac':'AAC','mp2':'MP2','mp3':'MP3','ac3':'AC-3','eac3':'E-AC-3'}.get(audio['codec_name'],'--') if audio else '--',
                     expectedSourceFps=float(Fraction(video['r_frame_rate'])),
                     expectedScanType='interlaced' if spec['interlaced'] else 'progressive',
                     expectedBitDepth=spec['depth'],sampleMs=1500 if live else 5500,decodeMode='software',
                     expectedVideoBitrate=round(rates['video']),bitrateTolerance=0.4,hasAudio=bool(audio))
            if os.environ.get('NTV_VERBOSE_PLAYER')=='1':row['verbosePlayer']=True
            if not result and os.environ.get('NTV_TEST_SWITCH_UI')=='1':row['checkUiResponsiveness']=True
            if audio:
                row['expectedAudioBitrate']=round(rates['audio'])
                row['expectedSampleRate']=int(audio['sample_rate'])
                row['expectedAudioChannels']=int(audio['channels'])
            transfer={'bt709':'BT.709','arib-std-b67':'HLG','smpte2084':'PQ'}.get(video.get('color_transfer'))
            primaries={'bt709':'BT.709','bt2020':'BT.2020'}.get(video.get('color_primaries'))
            if transfer:row['expectedTransfer']=transfer
            if primaries:row['expectedPrimaries']=primaries
            if spec['transfer'] in ('arib-std-b67','smpte2084'):
                row['expectedTransfer']='HLG' if spec['transfer']=='arib-std-b67' else 'PQ'
            result.append(row)
    return result


def build(abi):
    env=dict(os.environ,JAVA_HOME=str(ROOT/'.codex-tmp/jdk8/runtime/jdk8u504-b01'))
    flavor='Arm64' if abi=='arm64' else 'Arm32'
    with (OUT/f'build-{abi}.log').open('w',encoding='utf-8') as log:
        subprocess.run([str(ROOT/'gradlew.bat'),f':app:assemble{flavor}Debug',
                        f':app:assemble{flavor}DebugAndroidTest',f'-PapplicationIdOverride={PACKAGE}',
                        f'-PtestRunner={RUNNER}','--offline'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)


def lan_ip():
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        sock.connect(('8.8.8.8',80));return sock.getsockname()[0]

def replay_legacy_cases(serial, command, rows, report_name, directory, log, adb):
    """Keep one wedged API 14-17 native decoder from swallowing the whole matrix."""
    results=[]
    for item in rows:
        index=item['index'];name=f'{report_name[:-5]}-{index}.json'
        case=command.copy();case[case.index('-e') : case.index('-e')]=['-e','onlyIndices',str(index)]
        case[case.index('reportName')+1]=name
        adb('shell','am','force-stop',PACKAGE)
        process=subprocess.Popen(case,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
        expired=threading.Event()
        def abort():
            if process.poll() is None:
                expired.set()
                try:run([ADB,'-s',serial,'shell','am','force-stop',PACKAGE],timeout=10)
                finally:process.terminate()
        budget=45 if item.get('category') in ('local','supplement') and '.mp4' in item['fixture'].lower() else 90
        watchdog=threading.Timer(budget,abort);watchdog.start()
        try:
            for line in process.stdout:
                log.write(line);log.flush();print(serial,line.rstrip(),flush=True)
            process.wait(timeout=10)
        finally:
            watchdog.cancel()
            if process.poll() is None:process.kill();process.wait(timeout=10)
        path=directory/f'case-{index:04d}.json'
        try:
            adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/{name}',path)
            observed=json.loads(path.read_text(encoding='utf-8'))
            row=observed[0] if len(observed)==1 else dict(index=index,name=item['name'],status='failed',error='Missing case result')
        except Exception as error:row=dict(index=index,name=item['name'],status='failed',error='No case report: '+str(error))
        if expired.is_set() or row.get('status')=='running':
            previous=row.get('error','')
            row.update(status='failed',hostTimeout=True,hostTimeoutSeconds=budget,
                       error=(previous+'\n' if previous else '')+f'Native/UI test exceeded {budget}-second case deadline')
        results.append(row)
        (directory/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
        print(serial,'CASE COMPLETE',index,row['status'],flush=True)


def replay(serial, abi, formats, names=None, run_id='full', scripts=None, apk_dir=None):
    directory=OUT/serial.replace(':','_')/run_id;directory.mkdir(parents=True,exist_ok=True)
    def adb(*args):return run([ADB,'-s',serial,*args],timeout=180).decode('utf-8','replace')
    sdk=int(adb('shell','getprop','ro.build.version.sdk').strip())
    packages=adb('shell','pm','list','packages',PACKAGE)
    if PACKAGE in packages:raise RuntimeError('Refusing to reuse an existing test app')
    handler=functools.partial(legacy_capture_handler(serial,directory) if sdk<18 else MediaHandler,directory=str(OUT))
    server=http.server.ThreadingHTTPServer(('0.0.0.0',PORT),handler)
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    base=f'http://127.0.0.1:{PORT}' if sdk>=21 else f'http://{lan_ip()}:{PORT}'
    rows=manifest(base,formats,names)
    path=directory/'manifest.json';path.write_text(json.dumps(rows,indent=2),encoding='utf-8')
    installed=[];trace=None;trace_file=None
    try:
        apk_dir=Path(apk_dir) if apk_dir else ROOT/'app/build/outputs/apk'
        for suffix,apk in [('',apk_dir/f'{abi}/debug/app-{abi}-debug.apk'),
                           ('.test',apk_dir/f'androidTest/{abi}/debug/app-{abi}-debug-androidTest.apk')]:
            args=['install','-r']
            if sdk>=21 and not suffix:args+=['--abi','arm64-v8a' if abi=='arm64' else 'armeabi-v7a']
            answer=adb(*args,apk)
            if 'Success' not in answer:raise RuntimeError(answer)
            installed.append(PACKAGE+suffix)
        if sdk>=23:
            for permission in ['READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE']:
                adb('shell','pm','grant',PACKAGE,'android.permission.'+permission)
        for script in scripts or []:
            script=Path(script)
            if script.suffix.lower()!='.js':raise ValueError('Script fixture must be a .js file')
            target=f'/sdcard/Android/data/{PACKAGE}/files/js'
            adb('shell','mkdir','-p',target)
            adb('push',script,target+'/'+script.name)
        if sdk>=21:adb('reverse',f'tcp:{PORT}',f'tcp:{PORT}')
        adb('push',path,'/data/local/tmp/ntv-media-matrix.json')
        report_name='matrix-'+str(int(time.time()))+'.json'
        command=[ADB,'-s',serial,'shell','am','instrument','-w','-e','routeThroughResolver',os.environ.get('NTV_RESOLVER','true'),
                 '-e','manifest','/data/local/tmp/ntv-media-matrix.json','-e','reportName',report_name,
                 PACKAGE+'.test/'+RUNNER]
        if sdk<18:command[-1:-1]=['-e','captureEndpoint',base+'/__capture']
        trace_file=(directory/'player.log').open('w',encoding='utf-8')
        log_args=[ADB,'-s',serial,'logcat','-v','threadtime']
        if sdk>=21:log_args+=['-T','1']
        trace=subprocess.Popen(log_args+['-s','IJKMEDIA:V' if os.environ.get('NTV_VERBOSE_PLAYER')=='1' else 'IJKMEDIA:I','MainActivity:I','nTvHdr:I','HlsProxyServer:I'],stdout=trace_file,stderr=subprocess.STDOUT)
        with (directory/'instrumentation.txt').open('w',encoding='utf-8') as log:
            if sdk<18:
                replay_legacy_cases(serial,command,rows,report_name,directory,log,adb)
            else:
                process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
                if os.environ.get('NTV_TEST_FOREGROUND')=='1':
                    # MIUI can leave startActivitySync waiting behind its background
                    # launch policy. A normal explicit shell launch presents the app.
                    time.sleep(1)
                    adb('shell','am','start','-n',PACKAGE+'/xiao.bu.tv.MainActivity')
                for line in process.stdout:
                    log.write(line);log.flush();print(serial,line.rstrip(),flush=True)
                process.wait(timeout=30)
        trace.terminate();trace.wait(timeout=10);trace_file.close()
        trace=None;trace_file=None
        if sdk>=18:adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/{report_name}',directory/'results.json')
        with contextlib.suppress(subprocess.CalledProcessError):
            adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/codec-capabilities.json',directory)
        with contextlib.suppress(subprocess.CalledProcessError):
            adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/http2-fallback-checks.json',directory)
        with contextlib.suppress(subprocess.CalledProcessError):
            adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/probe-manifest-reuse-checks.json',directory)
        with contextlib.suppress(subprocess.CalledProcessError):
            adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/live-delay-preferences-checks.json',directory)
        results=json.loads((directory/'results.json').read_text(encoding='utf-8'))
        for row in results:
            filename=f"failure-{row['index']:04d}-threads.txt"
            if row.get('failureThreads')==filename:
                with contextlib.suppress(subprocess.CalledProcessError):
                    adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/{filename}',directory/filename)
        for row in results:
            with contextlib.suppress(subprocess.CalledProcessError):
                adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/playlist-{row["index"]:04d}.png',directory)
        (directory/'device.json').write_text(json.dumps(dict(serial=serial,sdk=sdk,abi=abi,model=adb('shell','getprop','ro.product.model').strip()),indent=2),encoding='utf-8')
        if len(results)!=len(rows) or any(row.get('status')=='running' for row in results):
            raise RuntimeError(f'Incomplete instrumentation: {len(results)}/{len(rows)} rows; see instrumentation.txt')
    finally:
        if trace is not None:
            with contextlib.suppress(Exception):trace.terminate();trace.wait(timeout=10)
        if trace_file is not None:trace_file.close()
        # A native crash can end instrumentation before its final pull. Preserve
        # the last completed rows before removing this disposable test package.
        if installed and 'report_name' in locals() and not (directory/'results.json').exists():
            with contextlib.suppress(Exception):
                adb('pull',f'/sdcard/Android/data/{PACKAGE}/files/{report_name}',directory/'results.json')
        for package in reversed(installed):
            with contextlib.suppress(Exception):adb('uninstall',package)
        if sdk>=21:
            with contextlib.suppress(Exception):adb('reverse','--remove',f'tcp:{PORT}')
        with contextlib.suppress(Exception):adb('shell','rm','-f','/data/local/tmp/ntv-media-matrix.json')
        server.shutdown();server.server_close()


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['generate','build','run'])
    parser.add_argument('--serial',default='127.0.0.1:21503');parser.add_argument('--abi',choices=['arm32','arm64'],default='arm64')
    parser.add_argument('--formats',default='hls,mp4');parser.add_argument('--names',default='')
    parser.add_argument('--run-id',default='full')
    parser.add_argument('--manifest',type=Path,help='Replay a JSON manifest of external live URLs instead of generated fixtures')
    args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True)
    if args.phase=='generate':generate()
    elif args.phase=='build':build(args.abi)
    else:
        if args.manifest:
            external_rows=json.loads(args.manifest.read_text(encoding='utf-8'))
            manifest=lambda *unused:external_rows
        replay(args.serial,args.abi,args.formats.split(','),args.names.split(',') if args.names else None,args.run_id)
