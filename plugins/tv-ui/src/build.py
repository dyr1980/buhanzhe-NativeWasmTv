"""Five API14 entry JARs. Contract and shared renderer belong only to the host APK."""
import argparse, hashlib, json, os, pathlib, shutil, subprocess, tempfile, zipfile
root=pathlib.Path(__file__).resolve().parents[3]
plugin=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT','D:/android/sdk'))
p.add_argument('--java-home',default=os.environ.get('JAVA_HOME',str(root/'.codex-tmp/jdk8/runtime/jdk8u504-b01')))
a=p.parse_args();sdk=pathlib.Path(a.sdk);java=pathlib.Path(a.java_home)/'bin'
out=root/'.codex-tmp/tv-ui-build';out.mkdir(parents=True,exist_ok=True)
styles=['ui2','ui3','ui4','ui5']
source=plugin/'src/xiao/bu/plugins/tvui';source.mkdir(parents=True,exist_ok=True)
for id in styles:
 name='Theme'+id[-1]
 (source/(name+'.java')).write_text('package xiao.bu.plugins.tvui;\npublic final class '+name+' extends xiao.bu.tv.skin.SharedTvUi {protected int theme(){return '+id[-1]+';}}\n',encoding='utf-8')

def run(cmd):subprocess.run([str(x) for x in cmd],check=True)
with tempfile.TemporaryDirectory(dir=out) as temp:
 temp=pathlib.Path(temp);classes=temp/'classes';classes.mkdir()
 api=root/'app/src/main/java/xiao/bu/tv/skin/TvUi.java'
 run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',sdk/'platforms/android-30/android.jar','-d',classes,api,root/'app/src/main/java/xiao/bu/tv/skin/SharedTvUi.java',*source.glob('*.java')])
 contract=temp/'api.jar'
 with zipfile.ZipFile(contract,'w') as z:
  for f in (classes/'xiao/bu/tv/skin').glob('*.class'):z.write(f,f.relative_to(classes).as_posix())
 for id in ['builtin',*styles]:
  name='BuiltinUi' if id=='builtin' else 'Theme'+id[-1];code=temp/(id+'.jar');dex=temp/id;dex.mkdir()
  with zipfile.ZipFile(code,'w') as z:
   for f in (classes/'xiao/bu/plugins/tvui').glob('*.class'):
    if f.stem==name or f.stem.startswith(name+'$'):z.write(f,f.relative_to(classes).as_posix())
  run([java/'java.exe','-cp',sdk/'build-tools/30.0.3/lib/d8.jar','com.android.tools.r8.D8','--min-api','14','--lib',sdk/'platforms/android-30/android.jar','--classpath',contract,'--output',dex,code])
  dist=plugin/'dist';dist.mkdir(exist_ok=True);target=dist/(id+'.jar')
  with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED) as z:
   for entry,data in [('classes.dex',(dex/'classes.dex').read_bytes()),('tv-ui.json',json.dumps(dict(api=2,version=3,sharedRenderer=0 if id=='builtin' else 1,minSdk=14,id=id,entry='xiao.bu.plugins.tvui.'+name)).encode())]:
    info=zipfile.ZipInfo(entry,(2026,10,16,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
  assets=root/'app/src/main/assets/tv-ui';assets.mkdir(exist_ok=True);shutil.copyfile(target,assets/target.name)
  target.with_suffix('.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'\n')
  print(id,target.stat().st_size,'bytes')
