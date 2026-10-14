"""Validate cropped SPS dimensions and colour against ffprobe fixtures on ARM."""
import argparse
import json
from pathlib import Path
import subprocess
import media_debug_matrix as matrix

def run(command):
    return subprocess.check_output(list(map(str,command)),stderr=subprocess.STDOUT).decode('utf-8','replace')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);args=parser.parse_args()
    ndk=Path('D:/android/sdk/ndk/android-ndk-r17c')
    gcc=ndk/'toolchains/arm-linux-androideabi-4.9/prebuilt/windows-x86_64/bin/arm-linux-androideabi-gcc.exe'
    out=matrix.ROOT/'.codex-tmp/all-device-rounds/native-headers';out.mkdir(exist_ok=True)
    def adb(*command):return run([matrix.ADB,'-s',args.serial,*command])
    for codec in ['h264','hevc']:
        run([gcc,'--sysroot='+str(ndk/'platforms/android-14/arch-arm'),
             '-isystem',ndk/'sysroot/usr/include','-isystem',ndk/'sysroot/usr/include/arm-linux-androideabi',
             '-D__ANDROID_API__=14','-fPIE','-pie','-std=c99','-I'+str(matrix.ROOT/'tools/ijk'),
             matrix.ROOT/f'tools/ijk/test-{codec}-color.c','-o',out/('ntv-check-'+codec)])
    sources=[]
    for folder in sorted(matrix.MEDIA.iterdir()):
        if not (folder/'sample.mp4').exists():continue
        reference=json.loads((folder/'reference.json').read_text(encoding='utf-8'))
        video=next(s for s in reference['streams'] if s['codec_type']=='video')
        codec=video['codec_name']
        if codec not in ('h264','hevc'):continue
        name=folder.name+'.'+codec;target=out/name
        run(['ffmpeg','-y','-loglevel','error','-i',folder/'sample.mp4','-map','0:v:0','-c','copy',
             '-bsf:v',codec+'_mp4toannexb','-frames:v','1','-f',codec,target])
        pix=video['pix_fmt'];depth=10 if '10' in pix else 8
        fields={'bt709':1,'bt2020':9,'arib-std-b67':18,'smpte2084':16,'bt2020nc':9}
        values=[depth,fields.get(video.get('color_primaries'),2),fields.get(video.get('color_transfer'),2),fields.get(video.get('color_space'),2)]
        scan=2 if video.get('field_order') in ('tt','bb','tb','bt') else 1
        expected=values+([scan] if codec=='h264' else [])+[video['width'],video['height']]
        sources.append((name,codec,expected))
    remote='/data/local/tmp/ntv-sps-regression'
    adb('shell','mkdir','-p',remote)
    for name in ['ntv-check-h264','ntv-check-hevc']+[r[0] for r in sources]:adb('push',out/name,remote+'/'+name)
    results=[]
    try:
        for codec in ['h264','hevc']:adb('shell','chmod','700',remote+'/ntv-check-'+codec)
        for name,codec,expected in sources:
            output=adb('shell',remote+'/ntv-check-'+codec,remote+'/'+name,*expected)
            if 'pass' not in output.lower() or 'fail' in output.lower():raise RuntimeError(name+': '+output)
            results.append(dict(fixture=name,expected=expected,output=output));print(name,'passed',flush=True)
        (out/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    finally:
        # These are exact, test-owned filenames; no recursive directory deletion.
        for name in ['ntv-check-h264','ntv-check-hevc']+[r[0] for r in sources]:adb('shell','rm','-f',remote+'/'+name)
        adb('shell','rmdir',remote)
