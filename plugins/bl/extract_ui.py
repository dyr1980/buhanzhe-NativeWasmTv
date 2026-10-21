"""Re-extract the user-supplied BBLL UI after `apktool d -s -f ...`.

Requires Node and sharp for old-Android PNG conversion. Does not copy APK DEX code.
"""
import argparse,json,pathlib,shutil,subprocess,xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('decoded',type=pathlib.Path,help='apktool decoded BBLL1.5.2 directory')
p.add_argument('--sharp',default='sharp',help='Node module name or absolute module directory')
a=p.parse_args();here=pathlib.Path(__file__).resolve().parent
info=json.loads((here/'reference/source.json').read_text(encoding='utf-8'))
res=a.decoded/'res';ns='{http://schemas.android.com/apk/res/android}'
for name in info['layouts']:shutil.copyfile(res/'layout'/(name+'.xml'),here/'reference'/(name+'.xml'))
for name in info['icons']:
    vector=ET.parse(res/'drawable'/(name+'.xml')).getroot()
    svg=ET.Element('svg',xmlns='http://www.w3.org/2000/svg',width='64',height='64',viewBox='0 0 '+vector.attrib[ns+'viewportWidth']+' '+vector.attrib[ns+'viewportHeight'])
    for path in vector.findall('path'):ET.SubElement(svg,'path',fill='white',d=path.attrib[ns+'pathData'])
    ET.ElementTree(svg).write(here/'reference'/(name+'.svg'),encoding='utf-8')
for name in ['default_avatar.png','default_video.png','background_image.webp']:
    source=next(res.rglob(name));shutil.copyfile(source,here/'assets'/name)
script='''const fs=require('fs'),p=require('path'),sharp=require(process.argv[1]),root=process.argv[2];
(async()=>{for(const n of fs.readdirSync(p.join(root,'reference')).filter(n=>n.endsWith('.svg')))
await sharp(p.join(root,'reference',n)).png().toFile(p.join(root,'assets',n.replace('.svg','.png')));
await sharp(p.join(root,'assets/background_image.webp')).resize(1280,720,{fit:'cover'}).png().toFile(p.join(root,'assets/background.png'));})();'''
subprocess.run(['node','-e',script,a.sharp,str(here)],check=True)
print('Extracted BBLL layouts, vector references and PNG assets; no Activity or DEX imported.')
