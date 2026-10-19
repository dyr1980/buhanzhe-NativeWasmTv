"""Pin the AGPL Folia deployment's complete static module graph into this JAR.
Only static same-origin files are fetched; API and user/account data are excluded.
"""
import argparse
import concurrent.futures
import hashlib
import json
import pathlib
import re
import urllib.parse
import urllib.request

HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE/'assets/web'
ORIGIN='https://music.aikun-bili.top/'
ROOT.mkdir(parents=True,exist_ok=True)
parser=argparse.ArgumentParser()
parser.add_argument('--refresh',action='store_true',help='Refresh the pinned deployment snapshot instead of reusing cached assets')
args=parser.parse_args()
seen=set()
pending={'index.html','runtime-config.js','icon.svg','folia-cover-sw.js'}
records={}
pattern=re.compile(r'''["'`(]((?:\.?\.?/|/)?(?:assets/)?[A-Za-z0-9_./@-]+\.(?:js|css|wasm|json|svg|png|webp|jpg|woff2?|ttf|bin))(?:["'`)?])''')
def fetch(path):
    file=ROOT/path
    if file.exists() and not args.refresh:data=file.read_bytes()
    else:
        request=urllib.request.Request(urllib.parse.urljoin(ORIGIN,'' if path=='index.html' else path),headers={'User-Agent':'Mozilla/5.0'})
        with urllib.request.urlopen(request,timeout=40) as response:data=response.read(24*1024*1024+1)
        if len(data)>24*1024*1024:raise ValueError('Asset too large: '+path)
        file.parent.mkdir(parents=True,exist_ok=True)
        file.write_bytes(data)
    return path,data
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
    while pending:
        batch=sorted(pending-seen)
        pending=set()
        if not batch:break
        seen.update(batch)
        for path,data in pool.map(fetch,batch):
            if not path.endswith('.html') and data.lstrip().lower().startswith(b'<!doctype html'):
                (ROOT/path).unlink()
                continue
            records[path]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
            if path.endswith(('.html','.js','.css')):
                text=data.decode('utf-8')
                for item in pattern.findall(text):
                    if item.startswith('assets/') or item.startswith('/'):target=item.lstrip('/')
                    else:target=urllib.parse.urljoin(path,item)
                    if target.startswith(('assets/','icons/')) and '..' not in target.split('/') and target not in seen:pending.add(target)
        print(f'{len(records)} assets, {sum(x["bytes"] for x in records.values())/1048576:.1f} MiB',flush=True)
(HERE/'assets/upstream-manifest.json').write_text(json.dumps({'origin':ORIGIN,'files':records},ensure_ascii=False,indent=2),encoding='utf-8')
if args.refresh:
    for file in ROOT.rglob('*'):
        if file.is_file() and file.relative_to(ROOT).as_posix() not in records:file.unlink()
