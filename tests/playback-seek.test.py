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
  System.out.println("PASS rapid forward/backward seeks, last-target wins, stale clocks, zero, reset and timeout");
 }
}'''
with tempfile.TemporaryDirectory() as folder:
    out=Path(folder)
    (out/'PlaybackSeekState.java').write_text((root/'app/src/main/java/xiao/bu/tv/PlaybackSeekState.java').read_text(encoding='utf-8'),encoding='utf-8')
    (out/'SeekCheck.java').write_text(test,encoding='utf-8')
    jdk=Path(os.environ['JAVA_HOME'])/'bin'
    subprocess.run([str(jdk/'javac.exe'),'-d',folder,str(out/'PlaybackSeekState.java'),str(out/'SeekCheck.java')],check=True)
    subprocess.run([str(jdk/'java.exe'),'-cp',folder,'xiao.bu.tv.SeekCheck'],check=True)
