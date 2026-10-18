"""Run the production install callback with a cancelled request and a moved cursor."""
from pathlib import Path
import os, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/xiao/bu/tv/MainActivity.java').read_text(encoding='utf8')
begin = source.index('    private void installCjsPluginAndStart(')
method = source[begin:source.index('    private static boolean isPluginDownloadNetworkError(', begin)]
test = '''import java.util.concurrent.*;
class MainActivity {
static final String TAG="test";
boolean cjsPluginInstallInProgress,startupMaintenanceDestroyed,multimediaSuspended,finishing,network; int cjsPluginInstallRetryCount,pendingCjsChannelIndex=-1,currentChannelIndex,restarts,errors; Runnable pendingCjsInstallRetry;
static class Root {Runnable task;long delay;void postDelayed(Runnable r,long d){task=r;delay=d;}} Root root=new Root(); boolean isFinishing(){return finishing;}
long pendingPluginTimingMs,pendingPluginTimingStart; String pendingPluginTimingTarget="A";
CountDownLatch done=new CountDownLatch(1);
static class SystemClock {static long elapsedRealtime(){return 100;}}
static class Log {static void i(String a,String b){}static void e(String a,String b,Throwable c){}}
static class TextUtils {static boolean equals(String a,String b){return java.util.Objects.equals(a,b);}}
static class Channel {String name="channel";} Channel currentChannel(){return new Channel();}
static class Toast {static final int LENGTH_LONG=1;static Toast makeText(Object a,String b,int c){return new Toast();}void show(){}}
static class CjsSource {static boolean isSource(String s){return s.endsWith(".cjs");}}
static class CjsPluginRuntime {
 static CountDownLatch entered,release; static boolean cached;
 static void prepareSource(String s) throws Exception {ensureInstalled(s);}
 static void ensureCatalog() throws Exception {ensureInstalled("catalog");}
 static String ensureInstalled(String s)throws Exception {
  entered.countDown();release.await();throw new java.io.IOException("stream was reset: CANCEL");
 }
 static boolean isInstalled(String s){return cached;} static boolean hasCatalog(){return cached;}
}
void runOnUiThread(Runnable r){try{r.run();}finally{done.countDown();}}
void showLoading(String a,String b){} void showChannelBar(String a,String b){if(b.contains("失败"))errors++;}
void abortChannelSwitchAnimation(){} void hideLoading(){}
boolean isDeviceNetworkDisconnected(){return false;} boolean isPluginDownloadNetworkError(Throwable t){return network;}
void startChannel(int index){if(index!=currentChannelIndex)throw new AssertionError("stale channel");restarts++;}
METHOD
static void check(boolean valid){if(!valid)throw new AssertionError();}
public static void main(String[] args)throws Exception {
 for(int scenario=0;scenario<11;scenario++) {
  MainActivity a=new MainActivity();
  CjsPluginRuntime.cached=false;
  CjsPluginRuntime.entered=new CountDownLatch(1);CjsPluginRuntime.release=new CountDownLatch(1);
  a.network=scenario>=4&&scenario<=8;if(scenario==8)a.cjsPluginInstallRetryCount=2;
  a.installCjsPluginAndStart(0,"CCTV-1",scenario==9?"":"tv.cctv.com");
  check(CjsPluginRuntime.entered.await(3,TimeUnit.SECONDS));
  if(scenario==0){a.pendingPluginTimingTarget="B";a.currentChannelIndex=1;a.pendingCjsChannelIndex=1;}
  if(scenario==1)CjsPluginRuntime.cached=true;
  if(scenario==3)a.pendingCjsChannelIndex=-1;
  if(scenario==9)CjsPluginRuntime.cached=true;if(scenario==10)a.finishing=true;
  CjsPluginRuntime.release.countDown();check(a.done.await(3,TimeUnit.SECONDS));
  check(!a.cjsPluginInstallInProgress);
  check(a.restarts==(scenario<2||scenario==9?1:0));check(a.errors==(scenario==2?1:0));
  if(scenario>=4&&scenario<=7){check(a.root.task!=null&&a.root.delay==1000&&a.cjsPluginInstallRetryCount==1);
    if(scenario==5)a.pendingPluginTimingTarget="B";if(scenario==6)a.pendingCjsChannelIndex=-1;if(scenario==7)a.finishing=true;
    a.root.task.run();check(a.restarts==(scenario==4?1:0));
  }else check(a.root.task==null);
 }
}
}
'''.replace('METHOD', method)
with tempfile.TemporaryDirectory(prefix='ntv-cjs-cancel-') as folder:
    java = Path(folder) / 'MainActivity.java'
    java.write_text(test, encoding='utf8')
    jdk = Path(os.environ['JAVA_HOME']) / 'bin'
    subprocess.run([str(jdk/'javac.exe'), '-encoding', 'UTF-8', '-d', folder, str(java)], check=True)
    subprocess.run([str(jdk/'java.exe'), '-cp', folder, 'MainActivity'], check=True)
print('PASS 11 install callback cases: stale/cached/catalog-ready/error/abandoned, scheduled retry, changed target/cancel/destroy and retry limit')
