"""Asynchronous seek coalescing, optimistic UI position and bounded recovery."""
import os
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
test = '''package xiao.bu.tv;
public class SeekCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  PlaybackSeekState s=new PlaybackSeekState();
  check(s.request(251000,100)==251000);
  check(s.position(121000,200)==251000);
  check(s.request(261000,201)==-1);
  check(s.request(271000,202)==-1);
  check(s.request(241000,203)==-1);
  check(s.complete()==241000);
  check(s.position(251000,300)==241000);
  check(s.complete()==-1);
  check(s.position(121000,400)==241000);
  check(s.position(241100,500)==241000);
  check(s.position(240000,600)==241000);
  check(s.position(241400,1000)==241400);
  check(s.position(242100,1500)==242100);
  s.request(0,1600);s.complete();check(s.position(0,1700)==0);
  s.request(9000,2000);s.reset();check(s.complete()==-1);
  check(s.position(1000,2100)==1000);
  s.request(9000,3000);check(s.position(1000,13000)==1000);
  check(s.request(12000,13001)==-1);
  check(s.complete()==12000); // A slow native seek still owns the in-flight slot.
  s.reset();s.request(10000,14000);
  for(int i=0;i<5;i++)s.request(s.position(1000,14001+i)+1000,14001+i);
  check(s.complete()==15000);s.complete();
  check(s.position(1000,15000)==15000);
  s.reset();s.request(2100,16000);s.complete();
  check(s.position(4000,16500)==4000); // HLS may align to the next keyframe.
  PlaybackSeekFeedback f=new PlaybackSeekFeedback();
  f.request(60000,10000,100);check(f.loading(100,false));check(f.status(100,false).contains("正在加载"));
  f.complete();f.observe(10100,false,true,200);f.observe(10400,false,true,500);check(f.pending());
  f.observe(60000,false,true,1000);f.observe(60300,false,true,1300);check(!f.pending()&&f.status(1300,false).isEmpty());
  f.request(10000,80000,2000);f.complete();f.observe(80100,false,true,2200);f.observe(80500,false,true,2700);check(f.pending());
  check(f.status(11000,false).contains("等待较久"));check(f.status(28000,false).contains("可能"));check(!f.loading(28000,false));
  f.request(30000,80000,29000);f.failed("下载失败（HTTP 503）");check(!f.loading(30000,false)&&f.status(30000,false).contains("503"));
  f.complete();f.observe(30000,false,true,30100);f.observe(30300,false,true,30500);check(f.status(30500,false).isEmpty());
  f.request(20000,40000,31000);f.complete();f.observe(24000,false,false,31200);check(!f.pending()); // GOP-aligned paused seek
  f.reset();f.observe(0,true,true,32000);check(f.loading(33000,true));check(f.status(41000,true).contains("缓冲较慢"));f.observe(1000,false,true,42000);check(f.status(42000,false).isEmpty());
  System.out.println("PASS seek loading, stale completion, recovery, timeout, HTTP error and paused GOP seek; rapid forward/backward seeks, last-target wins, stale clocks, zero, reset and timeout");
 }
}'''
with tempfile.TemporaryDirectory() as folder:
    out=Path(folder)
    (out/'PlaybackSeekState.java').write_text((root/'app/src/main/java/xiao/bu/tv/PlaybackSeekState.java').read_text(encoding='utf-8'),encoding='utf-8')
    (out/'SeekCheck.java').write_text(test,encoding='utf-8')
    (out/'PlaybackSeekFeedback.java').write_text((root/'app/src/main/java/xiao/bu/tv/PlaybackSeekFeedback.java').read_text(encoding='utf-8'),encoding='utf-8')
    jdk=Path(os.environ['JAVA_HOME'])/'bin'
    subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',folder,str(out/'PlaybackSeekState.java'),str(out/'PlaybackSeekFeedback.java'),str(out/'SeekCheck.java')],check=True)
    subprocess.run([str(jdk/'java.exe'),'-cp',folder,'xiao.bu.tv.SeekCheck'],check=True)
