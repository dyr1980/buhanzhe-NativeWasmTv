"""Build the standalone Java + bundled WebView channel plugin."""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import zipfile

here = pathlib.Path(__file__).resolve().parent
root = here.parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', 'D:/android/sdk'))
parser.add_argument('--java-home', default='C:/Users/Administrator/.jdks/corretto-1.8.0_492')
args = parser.parse_args()
# Refuse stale website snapshots or modified generated assets.
manifest = json.loads((here/'assets/upstream-manifest.json').read_text(encoding='utf-8'))
if manifest.get('revision') != '851af3478bfa98b68dbd21fbc89120cc11b7ea96':
    raise SystemExit('Run build-web.py to compile the pinned official source first')
if manifest.get('target') != 'chrome61' or manifest.get('minimumWebView') != 61:
    raise SystemExit('Run build-web.py to compile the Chrome 61 compatibility frontend first')
for name, expected in manifest['files'].items():
    if hashlib.sha256((here/'assets/web'/name).read_bytes()).hexdigest() != expected:
        raise SystemExit('Generated frontend changed: '+name)
sdk = pathlib.Path(args.sdk)
java = pathlib.Path(args.java_home) / 'bin'
out = root / '.codex-tmp/folia-full/build'
classes = out / 'classes'
dex = out / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)
api = root / 'app/src/main/java/xiao/bu/tv/jar/ChannelPlugin.java'
subprocess.run([str(java/'javac.exe'), '-source', '8', '-target', '8', '-encoding', 'UTF-8',
                '-cp', str(sdk/'platforms/android-30/android.jar'), '-d', str(classes), str(api),
                *map(str, (here/'src').rglob('*.java'))], check=True)
with zipfile.ZipFile(out/'code.jar', 'w') as code, zipfile.ZipFile(out/'api.jar', 'w') as contract:
    for file in classes.rglob('*.class'):
        name = file.relative_to(classes).as_posix()
        (contract if name.startswith('xiao/bu/tv/jar/') else code).write(file, name)
subprocess.run([str(java/'java.exe'), '-cp', str(sdk/'build-tools/30.0.3/lib/d8.jar'),
                'com.android.tools.r8.D8', '--min-api', '24', '--lib', str(sdk/'platforms/android-30/android.jar'),
                '--classpath', str(out/'api.jar'), '--output', str(dex), str(out/'code.jar')], check=True)
target = here / 'dist'
target.mkdir(exist_ok=True)
jar = target / 'folia-full.jar'
with zipfile.ZipFile(jar, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex/'classes.dex', 'classes.dex')
    archive.writestr('channel-plugin.json', json.dumps(dict(api=1, version=7, minSdk=24, minWebView=61,
                     entry='xiao.bu.channel.FoliaFull', page='web/index.html')))
    for file in (here/'assets').rglob('*'):
        if file.is_file():
            archive.write(file, file.relative_to(here/'assets').as_posix())
    for name in ['NOTICE', 'LICENSE']:
        archive.write(here/name, name)
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
jar.with_suffix('.sha256').write_text(digest+'\n', encoding='ascii')
example = ('#EXTM3U\n#EXTINF:-1 group-title="音乐",Folia 源码版\n'
           'https://raw.githubusercontent.com/buhanzhe/NativeWasmTv/master/plugins/folia-full/dist/folia-full.jar?sha256='+digest+'\n')
(target/'example.m3u').write_text(example, encoding='utf-8')
print(f'Built {jar}, {jar.stat().st_size} bytes, SHA-256 {digest}')
