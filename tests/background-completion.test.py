"""Completed/paused foreground restoration must retain the replay state once."""
from pathlib import Path
import os,shutil,subprocess,tempfile

root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/xiao/bu/tv/MainActivity.java').read_text(encoding='utf-8')
start=source.index('    private void restoreForegroundPlayback(')
end=source.index('\n    private int maxSoftwareVideoPixels',start)
method=source[start:end]
java=r'''class RestoreCheck {
 interface IMediaPlayer {void seekTo(long value);void pause();}
 static class Player implements IMediaPlayer {long seek=-1;int pauses;public void seekTo(long p){seek=p;}public void pause(){pauses++;}}
 int playRequestId=9,foregroundResumeRequest=9,completions;long foregroundResumePosition=3750;
 boolean foregroundResumePaused,foregroundResumeCompleted;
 void completeVideoPlayback(){check(foregroundResumeRequest==-1&&!foregroundResumeCompleted,"completion was not consumed before callbacks");completions++;}
 METHOD
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  for(boolean systemSeek:new boolean[]{true,false}){
   RestoreCheck c=new RestoreCheck();Player p=new Player();c.foregroundResumeCompleted=true;
   c.restoreForegroundPlayback(p,systemSeek);
   check(c.completions==1&&p.pauses==1,"completed video restarted / lost replay controls");
   check(p.seek==(systemSeek?3750:-1),"IJK seek-at-start was repeated after prepare");
   check(c.foregroundResumePosition==0&&!c.foregroundResumePaused,"resume state retained");
   c.restoreForegroundPlayback(p,systemSeek);check(c.completions==1&&p.pauses==1,"duplicate prepared callback restored twice");
  }
  RestoreCheck c=new RestoreCheck();Player p=new Player();c.foregroundResumePaused=true;c.restoreForegroundPlayback(p,true);
  check(c.completions==0&&p.pauses==1&&p.seek==3750,"paused video changed state");
  c=new RestoreCheck();p=new Player();c.restoreForegroundPlayback(p,false);check(c.completions==0&&p.pauses==0,"playing video was paused");
  c=new RestoreCheck();p=new Player();c.foregroundResumeCompleted=true;c.foregroundResumeRequest=8;c.restoreForegroundPlayback(p,true);
  check(c.completions==0&&p.pauses==0&&p.seek==-1,"stale channel callback consumed another resume");
  System.out.println("PASS completed/paused/playing foreground states, IJK seek-at-start, duplicate and stale callbacks");
 }
}'''.replace(' METHOD',method)
with tempfile.TemporaryDirectory() as folder:
    file=Path(folder)/'RestoreCheck.java';file.write_text(java,encoding='utf-8')
    suffix='.exe' if os.name=='nt' else ''
    bundled_bin=root/'.codex-tmp/jdk8/runtime/jdk8u504-b01/bin'
    java_bin=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else bundled_bin
    def java_tool(name):
        candidate=java_bin/(name+suffix)
        return str(candidate) if candidate.is_file() else shutil.which(name) or name
    subprocess.run([java_tool('javac'),'-encoding','UTF-8',str(file)],check=True)
    subprocess.run([java_tool('java'),'-cp',folder,'RestoreCheck'],check=True)
