"""Release asset names stay compatible; case variants never cross ABI boundaries."""
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = (root / 'app/src/main/java/xiao/bu/tv/AutoUpdater.java').read_text('utf-8')
method = source[source.index('    static String validatedApkFilename('):source.index('    private static boolean isArm32Build()')]
check = '''package xiao.bu.tv;
import java.io.*; import java.net.*; import org.json.JSONException;
class AssetCheck {
METHOD
 static void verify(String file,String expected,String display,boolean valid)throws Exception {
  try {String actual=validatedApkFilename("https://github.com/a/b/releases/latest/download/"+file,expected,display);
   if(!valid||!actual.equals(URLDecoder.decode(file,"UTF-8")))throw new AssertionError(file);
  }catch(JSONException e){if(valid)throw new AssertionError(file,e);}
 }
 public static void main(String[] args)throws Exception {
  verify("nTv.apk","nTv.apk","网页电视32位.apk",true);
  verify("ntv.apk","nTv.apk","网页电视32位.apk",true);
  verify("ntv64.apk","nTv64.apk","网页电视64位.apk",true);
  verify("nTvX86.apk","nTvX86.apk","网页电视x86.apk",true);
  verify("%E7%BD%91%E9%A1%B5%E7%94%B5%E8%A7%8632%E4%BD%8D.apk","nTv.apk","网页电视32位.apk",true);
  verify("nTv64.apk","nTv.apk","网页电视32位.apk",false);
  verify("ntv.apk","nTv64.apk","网页电视64位.apk",false);
  verify("nTvX86.apk","nTv64.apk","网页电视64位.apk",false);
  verify("nTv.apk.exe","nTv.apk","网页电视32位.apk",false);
  verify("%zz","nTv.apk","网页电视32位.apk",false);
 }
}'''.replace('METHOD', method)
with tempfile.TemporaryDirectory() as folder:
    tmp = Path(folder)
    (tmp/'AssetCheck.java').write_text(check, encoding='utf-8')
    (tmp/'JSONException.java').write_text('package org.json; public class JSONException extends Exception {public JSONException(String message){super(message);}}')
    jdk = Path(os.environ['JAVA_HOME'])/'bin'
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', '-d', folder, str(tmp/'AssetCheck.java'), str(tmp/'JSONException.java')], check=True)
    subprocess.run([str(jdk/'java.exe'), '-cp', folder, 'xiao.bu.tv.AssetCheck'], check=True)

names = ['nTv.apk', 'nTv64.apk', 'nTvX86.apk']
script = (root/'scripts/build-release.ps1').read_text('utf-8-sig')
gradle = (root/'app/build.gradle').read_text('utf-8')
assert re.findall(r"Name = '(.*\.apk)'", script) == names
for name in names:
    assert "= '" + name + "'" in gradle
manifest = json.loads((root/'version-lite.json').read_text('utf-8'))
assert [manifest[key].rsplit('/',1)[-1] for key in ['apk32Url','apk64Url','apkX86Url']] == names
if os.name == 'nt':
    # Execute the production copy/rename steps over existing lowercase files.
    # Get-Item echoes requested casing; enumerate real directory entries instead.
    start = script.index('    $destination = Join-Path $OutputDirectory $artifact.Name')
    end = script.index('    & $apksigner verify', start)
    copy_steps = script[start:end]
    with tempfile.TemporaryDirectory(prefix='ntv-assets-') as folder:
        output=Path(folder)/'out';output.mkdir();apk=Path(folder)/'source.apk';apk.write_bytes(b'new apk')
        quote=lambda value: str(value).replace("'", "''")
        for name in names:
            (output/name.lower()).write_bytes(b'old apk')
            command=("$ErrorActionPreference='Stop';$OutputDirectory='"+quote(output)+"';"
                     "$artifact=[pscustomobject]@{Name='"+name+"';Source='"+quote(apk)+"'};\n"+copy_steps)
            subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',command],check=True)
            assert name in [p.name for p in output.iterdir()], name
            assert (output/name).read_bytes()==b'new apk'
print('PASS release script, manifest and updater asset names; case aliases and ABI rejection')
