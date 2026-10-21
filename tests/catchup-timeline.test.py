from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java=r'''package xiao.bu.tv;
public class CatchupTimelineCheck {
 static int checks;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError("case "+checks);}
 static String list(String time){return "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-PROGRAM-DATE-TIME:"+time+"\n#EXTINF:6,\nsegment.ts\n";}
 public static void main(String[] args){
 long start=CatchupTimeline.parseTime("2026-10-20T01:00:00Z"),stop=start+3600000;
 ok(start>0);ok(start==CatchupTimeline.parseTime("2026-10-20T09:00:00+08:00"));ok(start==CatchupTimeline.parseTime("2026-10-19T21:00:00-0400"));
 ok(CatchupTimeline.parseTime("2026-10-20T01:00:00.12Z")==start+120);
 ok(CatchupTimeline.parseTime("2026-10-20T01:00:00.123456Z")==start+123);
 for(String invalid:new String[]{"2026-02-30T01:00:00Z","2026-10-20T01:00:00","bad","2026-10-20T01:00:00Zignored"})ok(CatchupTimeline.parseTime(invalid)<0);
 ok(CatchupTimeline.inspect(list("2026-10-20T09:00:00+08:00"),start,stop,true).state==CatchupTimeline.VERIFIED);
 ok(CatchupTimeline.inspect(list("2026-10-20T11:30:00+08:00"),start,stop,true).state==CatchupTimeline.MISMATCH);
 ok(CatchupTimeline.inspect(list("2026-10-20T09:05:00+08:00"),start,stop,true).state==CatchupTimeline.MISMATCH);
 ok(CatchupTimeline.inspect(list("2026-10-20T09:05:00+08:00"),start,stop,false).state==CatchupTimeline.VERIFIED);
 ok(CatchupTimeline.inspect(list("2026-10-20T10:00:00+08:00"),start,stop,false).state==CatchupTimeline.MISMATCH);
 ok(CatchupTimeline.inspect("#EXTM3U\n#EXTINF:6,\narchive.ts?start="+start,start,stop,true).state==CatchupTimeline.UNKNOWN);
 ok(CatchupTimeline.inspect("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\nchild.m3u8",start,stop,true).state==CatchupTimeline.UNKNOWN);
 String late="#EXTM3U\n#EXTINF:6,\nfirst.ts\n#EXT-X-PROGRAM-DATE-TIME:2026-10-20T01:00:06Z\n#EXTINF:6,\nsecond.ts\n";
 ok(CatchupTimeline.inspect(late,start,stop,true).actual==start);
 ok(CatchupTimeline.inspect(late.replace("#EXT-X-PROGRAM", "#EXT-X-DISCONTINUITY\n#EXT-X-PROGRAM"),start,stop,true).state==CatchupTimeline.UNKNOWN);
 System.out.println("PASS "+checks+" returned-time checks: false-live, missing time, timezone/fractions, start alignment, window end and discontinuity");
 }}'''
with tempfile.TemporaryDirectory() as folder:
 out=Path(folder)/'xiao/bu/tv';out.mkdir(parents=True)
 source=out/'CatchupTimeline.java';source.write_text((root/'app/src/main/java/xiao/bu/tv/CatchupTimeline.java').read_text(encoding='utf8'),encoding='utf8')
 check=out/'CatchupTimelineCheck.java';check.write_text(java,encoding='utf8')
 jdk=Path(os.environ.get('JAVA_HOME',root/'.codex-tmp/jdk8/runtime/jdk8u504-b01'))/'bin'
 subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',folder,str(source),str(check)],check=True)
 subprocess.run([str(jdk/'java.exe'),'-cp',folder,'xiao.bu.tv.CatchupTimelineCheck'],check=True)
