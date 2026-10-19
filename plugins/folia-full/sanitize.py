"""Do not redistribute the reference deployment's embedded service credentials."""
import hashlib,json,pathlib,re
here=pathlib.Path(__file__).resolve().parent
manifest=here/'assets/upstream-manifest.json'
data=json.loads(manifest.read_text('utf-8'))
changed=0
for path,info in data['files'].items():
    file=here/'assets/web'/path
    original=file.read_bytes()
    if file.suffix=='.js':
        text=original.decode('utf-8')
        text,count=re.subn(r'(VITE_[A-Z_]*(?:TOKEN|SECRET|KEY)[A-Z_]*\s*:\s*)([`\'"])[^`\'"\r\n]+\2',r'\1""',text)
        if count:
            file.write_text(text,encoding='utf-8')
            info.setdefault('upstreamSha256',info['sha256'])
            changed+=count
    info['sha256']=hashlib.sha256(file.read_bytes()).hexdigest()
    info['bytes']=file.stat().st_size
manifest.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Removed {changed} embedded service credential values; private sync/AI require user configuration.')
