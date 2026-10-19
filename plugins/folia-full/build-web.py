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
config = source / 'vite.ntv.config.ts'
config.write_text("""import upstream from './vite.config.ts';
import {mergeConfig} from 'vite';
import {traditionalLocalePlugin} from './dev/i18n/traditionalLocalePlugin.mjs';
import {commandPinyinPlugin} from './dev/pinyin/commandPinyinPlugin.mjs';
import {icuFoliaDataPlugin} from './dev/icuFolia/icuFoliaDataPlugin.mjs';
export default async (env:any)=>{const base=await upstream(env);base.plugins=base.plugins.flat(Infinity).filter((p:any)=>p&&!String(p.name).includes('pwa'));return mergeConfig(base,{
 build:{target:'chrome100',cssTarget:'chrome100'},
 worker:{plugins:()=>[traditionalLocalePlugin(),commandPinyinPlugin(),icuFoliaDataPlugin()]}
});};
""", encoding='utf-8')
env = {k:v for k,v in os.environ.items() if not k.startswith(('VITE_', 'GEMINI_', 'OPENAI_'))}
env.pop('GITHUB_ACTIONS', None)
env.update(FOLIA_BUILD_REPO='chthollyphile/folia-major', VERCEL_GIT_COMMIT_SHA=REV, VERCEL_GIT_COMMIT_REF='main', VITE_NETEASE_API_BASE='/ncm-api', VITE_KUGOU_API_BASE='/kugou-api', VITE_QQ_API_BASE='/qq-api')
subprocess.run([npm, 'exec', '--', 'vite', 'build', '--config', config.name], cwd=source, env=env, check=True)
web = HERE / 'assets/web'
# Only replace this plugin's generated static directory after a successful source build.
if web.resolve() != (ROOT/'plugins/folia-full/assets/web').resolve(): raise SystemExit('Invalid output directory')
if web.exists(): shutil.rmtree(web)
shutil.copytree(source / 'dist', web)
files = {f.relative_to(web).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in web.rglob('*') if f.is_file()}
manifest = dict(repository='https://github.com/chthollyphile/folia-major', revision=REV,
    archive=url, archiveSha256=hashlib.sha256(archive.read_bytes()).hexdigest(),
    build='npm ci --ignore-scripts; vite build --config vite.ntv.config.ts', target='chrome100', files=files)
(HERE/'assets/upstream-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
shutil.copyfile(source/'LICENSE', HERE/'LICENSE')
print(f'Compiled official Folia {REV}: {len(files)} static files')
