"""Exercise real display telemetry with old/new SDKs and a faulting public API."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
jdk=Path(os.environ.get('JAVA_HOME',root/'.codex-tmp/jdk8/runtime/jdk8u504-b01'))/'bin'
files={
 'android/os/Build.java':'package android.os; public class Build { public static class VERSION { public static int SDK_INT=34; } }',
 'android/os/SystemClock.java':'package android.os; public class SystemClock { public static long now=1000; public static long elapsedRealtime(){return now;} }',
 'android/view/View.java':'package android.view; public class View {public Display display;public int calls;public Display getDisplay(){calls++;return display;}}',
 'android/view/Display.java':'''package android.view;public class Display {public boolean valid=true,available=true,fail;public float ratio=2;public int reads,availableReads;public boolean isValid(){return valid;}public boolean isHdrSdrRatioAvailable(){availableReads++;if(fail)throw new IllegalStateException("vendor failure");return available;}public float getHdrSdrRatio(){reads++;return ratio;}}''',
 'xiao/bu/tv/DisplayHdrStatus.java':(root/'app/src/main/java/xiao/bu/tv/DisplayHdrStatus.java').read_text(encoding='utf-8'),
 'xiao/bu/tv/DisplayHdrStatusTest.java':'''package xiao.bu.tv;
import android.os.*;import android.view.*;import java.util.Locale;
public class DisplayHdrStatusTest {
 static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  Locale.setDefault(Locale.GERMANY);DisplayHdrStatus status=new DisplayHdrStatus();View view=new View();Display d=new Display();view.display=d;
  for(int sdk:new int[]{14,17,19,25,30,33}){Build.VERSION.SDK_INT=sdk;ok(status.read(view)==DisplayHdrStatus.UNKNOWN,"Legacy SDK must stay unknown");}
  ok(view.calls==0&&d.reads==0,"Old SDK must not query display or load new API");Build.VERSION.SDK_INT=34;
  ok(status.read(view).label().equals("屏幕HDR余量 2.00×"),"Read current ratio with stable decimal separator");
  ok(d.reads==1&&d.availableReads==1,"One query");
  for(int i=0;i<20;i++)ok(status.read(view).ratio==2,"Cached value");ok(d.reads==1,"No polling above 1 Hz");
  d.ratio=1;SystemClock.now+=1000;ok(status.read(view).label().equals("屏幕HDR余量 1.00×"),"1x headroom is not proof HDR is off");
  d.available=false;SystemClock.now+=1000;int reads=d.reads;ok(status.read(view)==DisplayHdrStatus.UNKNOWN&&d.reads==reads,"Unsupported ratio must not consume default 1x as telemetry");
  d.available=true;d.fail=true;SystemClock.now+=1000;ok(status.read(view)==DisplayHdrStatus.UNKNOWN,"Vendor exception must clear previous ratio");
  d.fail=false;d.valid=false;ok(status.read(view)==DisplayHdrStatus.UNKNOWN,"Disconnected display must not report stale state");
  d.valid=true;d.ratio=3;ok(status.read(view).ratio==3,"Reconnected display refreshes immediately");
  Display other=new Display();other.ratio=4;view.display=other;ok(status.read(view).ratio==4,"External/fold display change invalidates cache immediately");
  view.display=null;ok(status.read(view)==DisplayHdrStatus.UNKNOWN,"Detached view is unknown");
  for(float f:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1,0,.9f})ok(DisplayHdrStatus.ratio(true,f)==DisplayHdrStatus.UNKNOWN,"Invalid ratio accepted");
  ok(DisplayHdrStatus.ratio(false,2)==DisplayHdrStatus.UNKNOWN,"Availability is authoritative");
  ok(DisplayHdrStatus.UNKNOWN.label().equals("屏幕HDR状态未知"),"Never equate HDR content/capability with active panel mode");
  System.out.println("PASS legacy no-query, public API availability, 1 Hz cache, live changes, display replacement, invalid data and vendor failure; no false HDR-active claims");
 }
}'''}
with tempfile.TemporaryDirectory(prefix='ntv-display-hdr-') as directory:
 out=Path(directory)
 for name,text in files.items():
  file=out/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text,encoding='utf-8')
 subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',str(out),*[str(out/name) for name in files]],check=True)
 subprocess.run([str(jdk/'java.exe'),'-cp',str(out),'xiao.bu.tv.DisplayHdrStatusTest'],check=True)
