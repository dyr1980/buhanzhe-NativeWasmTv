"""Compile pinned official Folia source; never fetch a deployed website's assets."""
import argparse, hashlib, json, os, pathlib, shutil, subprocess, urllib.request, zipfile
HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
REV = '851af3478bfa98b68dbd21fbc89120cc11b7ea96'
p = argparse.ArgumentParser()
p.add_argument('--source', type=pathlib.Path, help='Existing checkout of the pinned revision')
p.add_argument('--skip-install', action='store_true')
a = p.parse_args()
work = ROOT / '.codex-tmp/folia-source-build'
work.mkdir(parents=True, exist_ok=True)
url = f'https://codeload.github.com/chthollyphile/folia-major/zip/{REV}'
archive = work / f'{REV}.zip'
if not archive.exists():
    urllib.request.urlretrieve(url, archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != '5203a1871458e778dcd5bd6461e26bebc97def958753963db74ca2d793c85957':
    raise SystemExit('Official source archive SHA-256 mismatch')
with zipfile.ZipFile(archive) as z:
    prefix = z.namelist()[0]
    if a.source:
        source = a.source.resolve()
        # Verify the supplied source against the pinned official archive before building.
        for item in z.infolist():
            relative = item.filename[len(prefix):]
            if not relative or item.is_dir(): continue
            local = source / relative
            expected = z.read(item)
            actual = local.read_bytes()
            if relative in ('package.json', 'package-lock.json'):
                actual = actual.replace(b'https://codeload.github.com/MakcRe/KuGouMusicApi/tar.gz/refs/tags/v1.6.0', b'https://github.com/MakcRe/KuGouMusicApi/archive/refs/tags/v1.6.0.tar.gz')
                # JSON line endings may have been normalized by the adapter.
                if json.loads(actual) == json.loads(expected): continue
            if actual != expected: raise SystemExit(f'Source differs from pinned revision: {relative}')
    else:
        source = work / prefix.rstrip('/')
        if not source.exists(): z.extractall(work)
# Use GitHub's direct archive endpoint for the SAME locked dependency version.
for name in ('package.json', 'package-lock.json'):
    file = source / name
    file.write_text(file.read_text(encoding='utf-8').replace(
        'https://github.com/MakcRe/KuGouMusicApi/archive/refs/tags/v1.6.0.tar.gz',
        'https://codeload.github.com/MakcRe/KuGouMusicApi/tar.gz/refs/tags/v1.6.0'), encoding='utf-8')
if any(f.is_file() and f.name != '.env.example' for f in source.glob('.env*')):
    raise SystemExit('Use a clean source directory without private .env files')
npm = shutil.which('npm.cmd') or shutil.which('npm')
if not a.skip_install:
    subprocess.run([npm, 'ci', '--ignore-scripts', '--no-audit', '--no-fund'], cwd=source, check=True)
if not a.skip_install:
    subprocess.run([npm, 'ci', '--ignore-scripts', '--no-audit', '--no-fund'], cwd=HERE, check=True)
config = source / 'vite.ntv.config.ts'
config.write_text("""import upstream from './vite.config.ts';
import {mergeConfig} from 'vite';
import {Features} from 'lightningcss';
import {traditionalLocalePlugin} from './dev/i18n/traditionalLocalePlugin.mjs';
import {commandPinyinPlugin} from './dev/pinyin/commandPinyinPlugin.mjs';
import {icuFoliaDataPlugin} from './dev/icuFolia/icuFoliaDataPlugin.mjs';
import {ntvCompat} from __NTV_COMPAT_PLUGIN__;
const compatEntry=__NTV_COMPAT_ENTRY__;
export default async (env:any)=>{const base=await upstream(env);base.plugins=base.plugins.flat(Infinity).filter((p:any)=>p&&!String(p.name).includes('pwa'));return mergeConfig(base,{
 build:{target:'chrome61',cssTarget:'chrome61',cssMinify:'lightningcss'},
 plugins:[ntvCompat(compatEntry)],
 css:{lightningcss:{targets:{chrome:61<<16},include:Features.HexAlphaColors|Features.SpaceSeparatedColorNotation}},
 worker:{format:'iife',plugins:()=>[ntvCompat(compatEntry),traditionalLocalePlugin(),commandPinyinPlugin(),icuFoliaDataPlugin()]}
});};
""".replace("__NTV_COMPAT_ENTRY__", json.dumps((HERE/"compat-entry.js").as_posix())).replace("__NTV_COMPAT_PLUGIN__", json.dumps((HERE/"vite-compat.mjs").as_posix())), encoding='utf-8')
env = {k:v for k,v in os.environ.items() if not k.startswith(('VITE_', 'GEMINI_', 'OPENAI_'))}
env.pop('GITHUB_ACTIONS', None)
env.update(FOLIA_BUILD_REPO='chthollyphile/folia-major', VERCEL_GIT_COMMIT_SHA=REV, VERCEL_GIT_COMMIT_REF='main', VITE_NETEASE_API_BASE='/ncm-api', VITE_KUGOU_API_BASE='/kugou-api', VITE_QQ_API_BASE='/qq-api')
subprocess.run([npm, 'exec', '--', 'vite', 'build', '--config', config.name], cwd=source, env=env, check=True)
web = HERE / 'assets/web'
# Only replace this plugin's generated static directory after a successful source build.
if web.resolve() != (ROOT/'plugins/folia-full/assets/web').resolve(): raise SystemExit('Invalid output directory')
if web.exists(): shutil.rmtree(web)
shutil.copytree(source / 'dist', web)
subprocess.run([shutil.which('node'), str(HERE/'compat-css.mjs'), str(web)], check=True)
subprocess.run([shutil.which('node'), str(HERE/'compat-assets.mjs'), str(web)], check=True)
# Chromium 61 has static modules but cannot parse dynamic import()/import.meta.
# Preserve split chunks with SystemJS rather than loading the whole application in one bundle.
shutil.copyfile(HERE/'node_modules/systemjs/dist/s.min.js', web/'ntv-system.js')
# Vite expects the synchronous native import.meta.resolve contract. SystemJS 6's
# default context returns a Promise, which breaks its CSS dependency preloader.
with (web/'ntv-system.js').open('a', encoding='utf-8') as loader:
    loader.write('\nSystem.constructor.prototype.createContext=function(url){var loader=this;return {url:url,resolve:function(id,parent){return loader.resolve(id,parent||url);}};};\n')
import re
def system_entry(match):
    entry = match.group(1)
    return '<script src="./ntv-system.js"></script><script data-ntv-entry="'+entry+'">document.addEventListener("DOMContentLoaded",function(){System.import('+json.dumps(entry)+').catch(function(e){window.__NTV_FOLIA_STARTUP_ERROR__=String(e.stack||e);console.error("Folia startup: "+window.__NTV_FOLIA_STARTUP_ERROR__);});});</script>'
for html_file in web.glob('*.html'):
    html = html_file.read_text(encoding='utf-8')
    html, entry_count = re.subn(r'<script\s+type="module"\s+crossorigin\s+src="([^"]+)"\s*></script>', system_entry, html)
    if entry_count != 1: raise SystemExit('Frontend module entry contract changed: '+html_file.name)
    html_file.write_text(html, encoding='utf-8')
# Include the notices of the locked compatibility libraries and their dependencies.
compat_licenses = HERE/'assets/licenses/compat'
compat_licenses.mkdir(parents=True, exist_ok=True)
lock = json.loads((HERE/'package-lock.json').read_text(encoding='utf-8'))
notices = {}
for package_path, package in lock['packages'].items():
    if not package_path.startswith('node_modules/'): continue
    package_dir = HERE/package_path
    name = package_path.removeprefix('node_modules/').replace('/', '__')
    license_files = [f for f in package_dir.glob('*') if f.is_file() and f.name.lower().startswith(('license', 'licence', 'copying'))]
    if not license_files: continue
    notices[name] = dict(version=package.get('version'), license=package.get('license'))
    for file in license_files:
        shutil.copyfile(file, compat_licenses/(name+'-'+file.name))
(compat_licenses/'packages.json').write_text(json.dumps(notices, indent=2)+'\n', encoding='utf-8')
files = {f.relative_to(web).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in web.rglob('*') if f.is_file()}
manifest = dict(repository='https://github.com/chthollyphile/folia-major', revision=REV,
    archive=url, archiveSha256=hashlib.sha256(archive.read_bytes()).hexdigest(),
    build='npm ci --ignore-scripts; vite build --config vite.ntv.config.ts', target='chrome61', minimumWebView=61,
    runtimeCompatibility='core-js; FormatJS Intl.Locale/Segmenter; Unicode literal/constant regex lowering; generator-based async/await; SystemJS with synchronous resolve; classic workers; JSBI metadata arithmetic; Blob/ResizeObserver/AbortController/toggleAttribute/focus-visible bridges',
    cssCompatibility='Lightning CSS + PostCSS Preset Env: shared cross-chunk cascade; runtime shader CSS; legacy colors/selectors; Tailwind property defaults; viewport bounds; square cover fallback; AST-lowered inline CSS strings', files=files)
(HERE/'assets/upstream-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
shutil.copyfile(source/'LICENSE', HERE/'LICENSE')
print(f'Compiled official Folia {REV}: {len(files)} static files')
