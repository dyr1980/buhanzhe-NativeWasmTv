"""Exercise the production channel identity resolver after document redirects."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/xiao/bu/tv/WebSourceView.java').read_text(encoding='utf8')
start = source.index('    String currentChannelUrl() {')
end = source.index('\n    String currentChannelGroup()', start)
method = source[start:end]
harness = r'''class WebIdentityCheck {
 static class WebTabBar {
  static class Tab {String bookmarkUrl="", channelEntryUrl="";}
  Tab selected; Tab active(){return selected;}
 }
 static class Legacy {String value(String key){return "https://station.test/entry";}}
 Legacy legacy; WebTabBar tabBar=new WebTabBar(); WebTabBar.Tab bookmark;
 String document="https://cdn.test/redirected/player";
 WebTabBar.Tab currentBookmarkChannel(){return bookmark;}
 String activePageUrl(){return document;}
 void check(String expected){if(!expected.equals(currentChannelUrl())) throw new AssertionError(currentChannelUrl());}
 public static void main(String[] args){
  WebIdentityCheck view=new WebIdentityCheck();
  view.check(view.document);
  view.tabBar.selected=new WebTabBar.Tab();
  view.tabBar.selected.channelEntryUrl="https://station.test/entry";
  view.check("https://station.test/entry");
  view.document="https://another-cdn.test/play?token=2";
  view.check("https://station.test/entry");
  WebTabBar.Tab other=new WebTabBar.Tab(); other.channelEntryUrl="https://other-station.test/live";
  view.tabBar.selected=other; view.check(other.channelEntryUrl);
  view.tabBar.selected.channelEntryUrl=""; view.check(view.document);
  view.bookmark=new WebTabBar.Tab(); view.bookmark.bookmarkUrl="https://bookmark.test/live";
  view.check(view.bookmark.bookmarkUrl);
  view.legacy=new Legacy(); view.check("https://station.test/entry");
  System.out.println("web channel identity: 7 cases passed");
 }
'''
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'WebIdentityCheck.java'
    path.write_text(harness + method + '\n}', encoding='utf8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(path)], check=True)
    subprocess.run(['java', '-cp', tmp, 'WebIdentityCheck'], check=True)
