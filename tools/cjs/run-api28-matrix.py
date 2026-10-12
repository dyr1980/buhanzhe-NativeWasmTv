"""Run isolated real-stream native benchmarks on a multi-ABI Android emulator."""
import argparse
import json
import pathlib
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=pathlib.Path)
parser.add_argument('--device', default='127.0.0.1:21503')
args = parser.parse_args()
p = args.directory.resolve()
sdk = pathlib.Path('D:/android/sdk')
java = pathlib.Path('C:/Users/Administrator/.jdks/corretto-1.8.0_492/bin')
adb = [str(sdk/'platform-tools/adb.exe'), '-s', args.device]

def run(command):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(result.stdout.decode('utf-8', 'replace'))
    return result.stdout.decode('utf-8', 'replace')

(p/'classes').mkdir(exist_ok=True)
run([str(java/'javac.exe'), '-source','7','-target','7','-encoding','UTF-8','-cp',str(sdk/'platforms/android-30/android.jar'),'-d',str(p/'classes'),
     'tools/cjs/CmgReplayTrial.java','tools/cjs/CctvCryptoTrial.java',str(p/'armeabi-v7a/cmg_base/CmgReplayBridge.java')])
run([str(java/'java.exe'),'-jar',str(sdk/'build-tools/27.0.3/lib/dx.jar'),'--dex','--output='+str(p/'classes.dex'),str(p/'classes')])
(p/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="xiao.bu.cryptotrial" android:versionCode="1" android:versionName="trial"><uses-sdk android:minSdkVersion="21" android:targetSdkVersion="25"/><application android:debuggable="true"/><instrumentation android:name="xiao.bu.cryptotrial.CmgReplayTrial" android:targetPackage="xiao.bu.cryptotrial"/><instrumentation android:name="xiao.bu.cryptotrial.CctvCryptoTrial" android:targetPackage="xiao.bu.cryptotrial"/></manifest>''', encoding='utf-8')
for abi in ['armeabi-v7a','arm64-v8a','x86']:
    unsigned = p/abi/'unsigned.apk'
    run([str(sdk/'build-tools/30.0.3/aapt.exe'),'package','-f','-M',str(p/'AndroidManifest.xml'),'-I',str(sdk/'platforms/android-30/android.jar'),'-F',str(unsigned)])
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(p/'classes.dex','classes.dex')
        for variant in ['cctv_base','cctv_pool','cmg_base','cmg_combined']:
            native = 'yangshipin.so' if variant.startswith('cmg_') else variant+'.so'
            archive.write(p/abi/variant/'libs'/abi/native, 'lib/'+abi+'/lib'+variant+'.so')
    apk = p/abi/'trial.apk'
    run([str(java/'java.exe'),'-jar',str(sdk/'build-tools/30.0.3/lib/apksigner.jar'),'sign','--ks','C:/Users/Administrator/.android/debug.keystore','--ks-pass','pass:android','--out',str(apk),str(unsigned)])
    print(run(adb+['install','-r','--abi',abi,str(apk)]),flush=True)
    external = '/sdcard/Android/data/xiao.bu.cryptotrial/files/'
    run(adb+['shell','mkdir','-p',external])
    for local, remote in [('cmg-speed/trace.bin','trace.bin'),('cmg-speed/baseline/methods.json','methods.json'),('crypto-speed/cctv-0.ts','cctv-0.ts')]:
        run(adb+['push',str(pathlib.Path('.codex-tmp')/local),external+remote])
    for variant in ['cctv_base','cctv_pool','cmg_base','cmg_combined']:
        if variant.startswith('cctv_'):
            extra = ['-e','count','1','-e','threads','1,2','-e','fixtures',external]
            runner = 'CctvCryptoTrial'
        else:
            extra = ['-e','trace',external+'trace.bin','-e','metadata',external+'methods.json']
            runner = 'CmgReplayTrial'
        output = run(adb+['shell','am','instrument','-w','-e','variant',variant,'-e','repeats','3']+extra+['xiao.bu.cryptotrial/xiao.bu.cryptotrial.'+runner])
        (p/abi/(variant+'.log')).write_text(output,encoding='utf-8')
        print(abi,variant,output,flush=True)
        if 'PASS' not in output: raise RuntimeError('Native benchmark failed: '+abi+'/'+variant)
        run(adb+['pull',external+variant+'.json',str(p/abi/(variant+'.json'))])
    # Each APK contains only one ABI. Reinstalling another forces a fresh process
    # and a different actual package ABI; never load ARM and x86 in one VM.
    print(run(adb+['shell','dumpsys','package','xiao.bu.cryptotrial']).split('primaryCpuAbi=')[1].splitlines()[0],flush=True)
