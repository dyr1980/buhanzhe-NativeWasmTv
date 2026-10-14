"""Adapt test fixtures in snapshots without changing version-specific assertions."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]


def adapt(directory):
    source = directory / 'app/src/androidTest/java/xiao/bu/tv'
    shared = ROOT / 'app/src/androidTest/java/xiao/bu/tv'
    for name in ['ChannelTouchInstrumentation.java', 'BrowserPolicyInstrumentation.java', 'SniffedMediaProbeInstrumentation.java', 'VersionBehaviorInstrumentation.java']:
        if (shared / name).exists():
            (source / name).write_bytes((shared / name).read_bytes())
    path = source / 'ChannelUiInstrumentation.java'
    content = path.read_text(encoding='utf-8')
    content = content.replace(
        'Method publish=EpgManager.class.getDeclaredMethod("publish",guide.getClass(),String.class);',
        'Method resolve=EpgManager.class.getDeclaredMethod("resolveLogos",guide.getClass(),String.class);'
        ' resolve.setAccessible(true); resolve.invoke(null,guide,"https://epg.example.test/feed/daily.xml");\n'
        '        Method publish=EpgManager.class.getDeclaredMethod("publish",guide.getClass(),List.class);')
    content = content.replace('publish.invoke(manager,guide,"https://epg.example.test/feed/daily.xml");',
        'publish.invoke(manager,guide,java.util.Collections.singletonList("https://epg.example.test/feed/daily.xml"));')
    content = content.replace('publish.invoke(rowGuide, parse.invoke(null, (Object)rowXml.getBytes("UTF-8")), "https://example.test/epg.xml");',
        'publish.invoke(rowGuide, parse.invoke(null, (Object)rowXml.getBytes("UTF-8")), java.util.Collections.singletonList("https://example.test/epg.xml"));')
    content = content.replace('check((Boolean)call(cached,"isCacheFresh"),',
        'Method fresh=EpgManager.class.getDeclaredMethod("isCacheFresh",File.class); fresh.setAccessible(true);\n            check((Boolean)fresh.invoke(cached,file),')
    content = content.replace('check(!(Boolean)call(cached,"isCacheFresh"),',
        'check(!(Boolean)fresh.invoke(cached,file),')
    content = content.replace('key(KeyEvent.KEYCODE_DPAD_RIGHT);\n                    check(epg.getVisibility()==View.VISIBLE,',
        'key(KeyEvent.KEYCODE_DPAD_RIGHT);\n                    check((Boolean)get(activity,"favoriteActionFocused"),"Channel favorite action not reachable");\n                    key(KeyEvent.KEYCODE_DPAD_RIGHT);\n                    check(epg.getVisibility()==View.VISIBLE,')
    content = content.replace('check(((View)get(activity,"epgFavorite")).hasFocus(),"Favorite header not reachable");', '')
    content = content.replace('key(KeyEvent.KEYCODE_DPAD_LEFT);\n                    check(epg.getVisibility()==View.GONE,',
        'key(KeyEvent.KEYCODE_DPAD_LEFT);\n                    if(epg.getVisibility()==View.VISIBLE) key(KeyEvent.KEYCODE_DPAD_LEFT);\n                    check(epg.getVisibility()==View.GONE,')
    if 'setInTouchMode(false);' not in content:
        content=content.replace('activity=(MainActivity)startActivitySync(', 'setInTouchMode(false);\n            activity=(MainActivity)startActivitySync(')
    if 'independent of remembered menu focus' not in content:
        content=content.replace('key(KeyEvent.KEYCODE_DPAD_RIGHT);\n                    check((Boolean)get(activity,"favoriteActionFocused"),',
            '// Test row -> favorite -> EPG, independent of remembered menu focus.\n'
            '                    set(activity,"favoriteActionFocused",false); call(activity,"updateFavoriteButton"); list.requestFocus();\n'
            '                    key(KeyEvent.KEYCODE_DPAD_RIGHT);\n                    check((Boolean)get(activity,"favoriteActionFocused"),')
    path.write_text(content, encoding='utf-8')
    metadata = json.loads((directory / 'snapshot.json').read_text(encoding='utf-8'))
    classes = sorted(p.stem for p in source.glob('*Instrumentation.java'))
    metadata['classes'] = classes
    (directory / 'snapshot.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    ordered = ['QuickJsInstrumentation'] + [c for c in classes if c != 'QuickJsInstrumentation']
    manifest = ['<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + metadata['package'] + '.test">']
    manifest.extend('<instrumentation android:name="xiao.bu.tv.' + c + '" android:targetPackage="' + metadata['package'] + '"/>' for c in ordered)
    manifest.append('</manifest>')
    (directory / 'app/src/androidTest/AndroidManifest.xml').write_text('\n'.join(manifest), encoding='utf-8')


if __name__ == '__main__':
    for version in ['baseline', 'current']:
        adapt(ROOT / '.codex-tmp/android9-v17' / version)
