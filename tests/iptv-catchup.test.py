"""Execute catchup URL and metadata regressions with the production Java classes."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java=root/'.codex-tmp/jdk8/runtime/jdk8u504-b01/bin'
source=r'''package xiao.bu.tv;
import java.util.*;import java.io.*;
public class CatchupCheck {
 static int count;
 static void eq(Object a,Object b){count++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 static void bad(IptvCatchup c,String url,long start,long end,long now){count++;try{c.url(url,start,end,now,"");throw new AssertionError("Accepted invalid request");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
 TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
 long start=1609459200000L,end=start+3600000,now=start+7200000;
 String live="http://example.test/live/index.m3u8?token=A%2BB";
 IptvCatchup c=new IptvCatchup("default","http://example.test/archive/{utc}-${end}.m3u8?d={duration:60}&t={utc:Y-m-d:H-M-S}&n=${now}",7,0);
 eq(c.url(live,start,end,now,""),"http://example.test/archive/1609459200-1609462800.m3u8?d=60&t=2021-01-01:08-00-00&n=1609466400");
 c=new IptvCatchup("append","?playseek={Y}{m}{d}{H}{M}{S}-${end:YmdHMS}&offset={offset:60}",7,0);
 eq(c.url(live,start,end,now,""),live+"&playseek=20210101080000-20210101090000&offset=120");
 c=IptvCatchup.from("#EXTINF:-1 catchup=\"append\" catchup-days=\"7\" catchup-source=\"?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}\",CCTV-1",Collections.emptyMap(),"http://localhost:8767/all.m3u");
 eq(c.url(live,start,end,now,""),live+"&playseek=20210101080000-20210101090000");
 eq(c.url(live,start,now+3600000,now,""),live+"&playseek=20210101080000-20210101100000");
 eq(new IptvCatchup("append","?s=${(b)yyyy-MM-dd HH:mm:ss}&e=${(e)yyyy-MM-dd HH:mm:ss}",7,3600).url(live,start,end,now,""),live+"&s=2021-01-01%2007:00:00&e=2021-01-01%2008:00:00");
 eq(new IptvCatchup("append","?s=${(b)}&e=${(e)}",7,0).url(live,start,end,now,""),live+"&s=1609459200&e=1609462800");
 Map<String,IptvCatchup> formatted=new HashMap<>();formatted.put(live,c);
 eq(IptvCatchup.decode(IptvCatchup.encode(formatted)).get(live).url(live,start,end,now,""),live+"&playseek=20210101080000-20210101090000");
 bad(new IptvCatchup("append","?s=${(b)q}",7,0),live,start,end,now);
 bad(new IptvCatchup("append","?s=${(b)"+String.join("",Collections.nCopies(81,"y"))+"}",7,0),live,start,end,now);
 c=new IptvCatchup("shift","",7,0);eq(c.url(live,start,end,now,""),live+"&utc=1609459200&lutc=1609466400");
 eq(c.url("http://example.test/a#view",start,end,now,""),"http://example.test/a?utc=1609459200&lutc=1609466400#view");
 eq(new IptvCatchup("fs","",7,0).url(live,start,end,now,""),"http://example.test/live/index-1609459200-3600.m3u8?token=A%2BB");
 eq(new IptvCatchup("xc","",7,0).url("https://example.test/live/user/pass/123.ts?token=x",start,end,now,""),"https://example.test/timeshift/user/pass/60/2021-01-01:08-00/123.ts?token=x");
 eq(new IptvCatchup("shift","?start={utc}",7,3600).url(live,start,end,now,""),live+"&start=1609455600");
 eq(new IptvCatchup("default","https://example.test/{catchup-id}?end={utcend}",7,0).url(live,start,now+3600000,now,"abc"),"https://example.test/abc?end=1609466400");
 Map<String,String> defaults=IptvCatchup.attributes("#EXTM3U catchup='shift' catchup-days='3' catchup-correction='1.5'");
 c=IptvCatchup.from("#EXTINF:-1 catchup-source='?s={utc}',Example",defaults,"http://example.test/lists/all.m3u");eq(c.mode,"shift");eq(c.days,3.0);eq(c.correctionSeconds,5400L);
 eq(IptvCatchup.from("#EXTINF:-1 catchup='none',X",defaults,"").enabled(),false);
 eq(IptvCatchup.from("#EXTINF:-1 timeshift='2',X",Collections.emptyMap(),"").days,2.0);
 eq(IptvCatchup.from("#EXTINF:-1 catchup='default' catchup-source='../archive/{utc}.m3u8',X",Collections.emptyMap(),"http://example.test/lists/a.m3u").source,"http://example.test/archive/{utc}.m3u8");
 eq(new IptvCatchup("default","",7,0).enabled(),false);
 bad(new IptvCatchup("shift","",7,0),live,start,end,now+8*86400000L);bad(new IptvCatchup("shift","",7,0),live,now+1,now+1000,now);
 for(String value:new String[]{"https://x/{duration:0}","https://x/{unknown}","file:///{utc}","https://x/{catchup-id}","https://x/\r\n"})bad(new IptvCatchup("default",value,7,0),live,start,end,now);
 Channel channel=new Channel("1","Test","",live,null,null).withCatchup(live,c);
 String second="https://example.test/other.m3u8";
 channel=channel.withAdditionalUrl(second).withCatchup(second,new IptvCatchup("shift","",1,0));
 channel=channel.withLogo("https://x/icon.png").withRadio(true).withSubtitles("https://x/a.srt").withCatalogSource(3).asFavorite("key",3);
 eq(channel.catchupFor(0).correctionSeconds,5400L);eq(channel.catchupFor(1).days,1.0);
 Channel restored=new Channel("1","Test","",channel.urls,null,null,null,null).withCatchups(channel.catchupText());
 eq(restored.catchupText(),channel.catchupText());eq(restored.catchupDays(),3.0);
 Channel plain=new Channel("2","Plain","",live,null,null);
 eq(plain.catchupDays(),7.0);eq(plain.automaticCatchupOnly(),true);eq(plain.catchupText(),"");
 eq(plain.catchupFor(0).url(live,start,end,now,""),live+"&playseek=20210101080000-20210101090000");
 eq(plain.catchupFor(0)==plain.catchupFor(0),true);eq(plain.canCatchup(start,end,now),true);
 eq(plain.canCatchup(start,end,now+8*86400000L),false);
 String signed="https://example.test/live/index.m3u8?token=A%2BB&PLAYSEEK=old&x=1&play%73eek=old2#video";
 eq(IptvCatchup.automatic(signed,null).url(signed,start,end,now,""),"https://example.test/live/index.m3u8?token=A%2BB&x=1&playseek=20210101080000-20210101090000#video");
 String operator="http://example.test/PLTV2/2780/index?icpid=abc";
 eq(IptvCatchup.automatic(operator,null).url(operator,start,end,now,""),operator+"&playseek=20210101080000-20210101090000");
 String xc="https://example.test/live/User/PASS/123.ts?auth=A%2BB";
 eq(IptvCatchup.automatic(xc,null).url(xc,start,end,now,""),"https://example.test/timeshift/User/PASS/60/2021-01-01:08-00/123.ts?auth=A%2BB");
 IptvCatchup incomplete=IptvCatchup.from("#EXTINF:-1 catchup='append' catchup-days='2' catchup-correction='1',X",Collections.emptyMap(),"");
 Channel shortWindow=plain.withCatchup(live,incomplete);
 eq(shortWindow.catchupDays(),2.0);eq(shortWindow.catchupFor(0).url(live,start,end,now,""),live+"&playseek=20210101070000-20210101080000");
 eq(plain.withCatchup(live,new IptvCatchup("shift","",3,0)).automaticCatchupOnly(),false);
 for(String disabled:new String[]{"none","off","disabled","unknown"}){
  Channel off=plain.withCatchup(live,new IptvCatchup(disabled,"",7,0));eq(off.catchupDays(),0.0);eq(off.canCatchup(start,end,now),false);
  Channel reloaded=new Channel("2","Plain","",live,null,null).withCatchups(off.catchupText());eq(reloaded.catchupDays(),0.0);
 }
 eq(plain.withCatchup(live,new IptvCatchup("append","",0,0)).catchupDays(),0.0);
 eq(plain.withRadio(true).catchupDays(),0.0);
 for(String skipped:new String[]{"rtp://239.0.0.1:1234","udp://@239.0.0.1:1234","webview://https://example.test/live.m3u8","https://example.test/resolver.php?id=1","https://example.test/video.mp4","https://example.test/live.flv","https://example.test/page","file:///video.ts"})eq(IptvCatchup.automatic(skipped,null)==null,true);
 Channel mixed=plain.withAdditionalUrl(second).withCatchup(live,new IptvCatchup("none","",7,0));eq(mixed.catchupFor(0)==null,true);eq(mixed.catchupFor(1).inferred(),true);
 Channel round=new Channel("2","Plain","",new String[]{live,second},null,null,null,null).withCatchups(mixed.catchupText());eq(round.catchupFor(0)==null,true);eq(round.catchupFor(1).inferred(),true);
 System.out.println("PASS "+count+" catchup URL, expiry, templates, header inheritance, metadata copying and persistence checks");
 }
}'''
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp);(p/'CatchupCheck.java').write_text(source,encoding='utf-8')
 subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(p),str(root/'app/src/main/java/xiao/bu/tv/IptvCatchup.java'),str(root/'app/src/main/java/xiao/bu/tv/Channel.java'),str(p/'CatchupCheck.java')],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(p),'xiao.bu.tv.CatchupCheck'],check=True)
