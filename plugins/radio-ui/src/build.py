"""Build an API14 DEX JAR; Android resources are ordinary JAR entries."""
import argparse,json,pathlib,subprocess,zipfile,hashlib,os
root=pathlib.Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser();parser.add_argument('--version',type=int,default=1)
parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT','D:/android/sdk'))
parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME','C:/Users/Administrator/.jdks/corretto-1.8.0_492'))
args=parser.parse_args()
sdk=pathlib.Path(args.sdk); java=pathlib.Path(args.java_home)/'bin'
out=root/'.codex-tmp/radio-ui/build';out.mkdir(parents=True,exist_ok=True)
classes=out/('classes-'+str(args.version));classes.mkdir(exist_ok=True)
def run(command):subprocess.run([str(x) for x in command],check=True)
api=root/'app/src/main/java/xiao/bu/tv/radio/RadioUi.java'
sources=list((root/'plugins/radio-ui/src/xiao').rglob('*.java'))
run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',sdk/'platforms/android-30/android.jar','-d',classes,api,*sources])
code=out/'code.jar'; contract=out/'api.jar'
with zipfile.ZipFile(code,'w') as a,zipfile.ZipFile(contract,'w') as b:
    for p in classes.rglob('*.class'):
        name=p.relative_to(classes).as_posix();(b if name.startswith('xiao/bu/tv/radio/') else a).write(p,name)
dex=out/('dex-'+str(args.version));dex.mkdir(exist_ok=True)
run([java/'java.exe','-cp',sdk/'build-tools/30.0.3/lib/d8.jar','com.android.tools.r8.D8','--min-api','14','--lib',sdk/'platforms/android-30/android.jar','--classpath',contract,'--output',dex,code])
target=root/'plugins/radio-ui/dist';target.mkdir(exist_ok=True)
jar=target/('radio-ui-v'+str(args.version)+'.jar')
with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex/'classes.dex','classes.dex')
    archive.writestr('radio-ui.json',json.dumps(dict(api=1,version=args.version,minSdk=14,entry='xiao.bu.radio.RadioArtwork')))
    for p in (root/'plugins/radio-ui/assets').rglob('*'):
        if p.is_file():archive.write(p,p.relative_to(root/'plugins/radio-ui/assets').as_posix())
sha=hashlib.sha256(jar.read_bytes()).hexdigest()
jar.with_suffix('.sha256').write_text(sha+'\n',encoding='ascii')
print('Built',jar,'bytes=',jar.stat().st_size,'sha256=',sha)
