"""Real EPG name/ID lookup and source merging, including CCTV4 regional schedules."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / 'app/src/main/java/xiao/bu/tv/EpgManager.java').read_text(encoding='utf8')

def method(start, end):
    return src[src.index(start):src.index(end, src.index(start))]

methods = method('    private static String channelIdFor(', '    private boolean restoringCache;')
methods += method('    private static void mergeGuide(', '    private static void notifyListener(')
methods += method('    static long dayStart(', '    private void writeCache(')
methods += src[src.index('    private static Guide parse('):src.rfind('\n}')]

java = r'''import java.util.*;import java.io.*;import java.text.*;import java.util.zip.*;import org.xmlpull.v1.XmlPullParser;
public class EpgMatchingCheck {
 static final long KEEP_PAST_MS=14L*86400000,KEEP_FUTURE_MS=36L*3600000;
 static final int MAX_DOWNLOAD_BYTES=8*1024*1024,MAX_XML_BYTES=64*1024*1024;
 static class Xml {static XmlPullParser newPullParser(){return new org.kxml2.io.KXmlParser();}}
 static class Channel {String name,epgId;Channel(String name,String id){this.name=name;epgId=id;}}
 static class Program {
  long startMillis,stopMillis;String title,catchupId;
  Program(){this(0,1,"","");}
  Program(long start,long stop,String title,String id){startMillis=start;stopMillis=stop;this.title=title;catchupId=id;}
 }
 static class Guide {
  Map<String,String> channelByAlias=new HashMap<>(),logosByChannel=new HashMap<>();
  Map<String,List<Program>> programsByChannel=new HashMap<>();
 }
 METHODS
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static void name(String raw,String expected){check(expected.equals(normalize(raw)),raw+" -> "+normalize(raw));}
 static void add(Guide g,String id,String display){
  g.programsByChannel.put(id,Collections.singletonList(new Program()));
  g.channelByAlias.put(normalize(id),id);g.channelByAlias.put(normalize(display),id);
 }
 static void lookup(Guide g,String raw,String id,String expected){
  String found=channelIdFor(g,new Channel(raw,id));check(Objects.equals(expected,found),raw+" / "+id+" -> "+found+", expected "+expected);
 }
 public static void main(String[] args)throws Exception{
  for(String raw:new String[]{"CCTV4","CCTV-4 中文国际","CCTV4中文国际（亚洲）","CCTV4亚洲HD","CCTV4ASIA","中文国际","国际亚洲"})name(raw,"CCTV4");
  for(String raw:new String[]{"CCTV4欧洲","CCTV-4 中文国际（欧洲）","CCTV4EUO","CCTV4EUROPE","CCTV4EU","国际欧洲","中文国际欧洲频道"})name(raw,"CCTV4欧洲");
  for(String raw:new String[]{"CCTV4美洲","CCTV-4 中文国际（美洲）","CCTV4AME","CCTV4AMERICA","CCTV4AMERICAS","国际美洲","中文国际美洲HD"})name(raw,"CCTV4美洲");
  for(String raw:new String[]{"CCTV4K","CCTV-4K 超高清","CCTV4K UHD","CCTV4K超高清频道"})name(raw,"CCTV4K");
  for(String raw:new String[]{"CCTV8K","CCTV-8K 超高清"})name(raw,"CCTV8K");
  name("CCTV-5+ 体育赛事","CCTV5+");name("CCTV16-4K","CCTV16");name("CCTV1综合HD","CCTV1");
  name("CCTV40","CCTV40");name("东方卫视4K","东方卫视");name("CGTN西班牙语","CGTN西语");name(null,"");
  Guide g=new Guide();add(g,"asia.xmltv","CCTV-4 亚洲");add(g,"europe.xmltv","CCTV4EUO");
  add(g,"america.xmltv","CCTV4AME");add(g,"uhd.xmltv","CCTV4K超高清");
  lookup(g,"CCTV4中文国际",null,"asia.xmltv");lookup(g,"国际欧洲",null,"europe.xmltv");
  lookup(g,"CCTV4国际美洲",null,"america.xmltv");lookup(g,"CCTV4K超高清",null,"uhd.xmltv");
  lookup(g,"CCTV4欧洲","missing","europe.xmltv");lookup(g,"CCTV4中文国际","europe.xmltv","europe.xmltv");
  lookup(g,"CCTV4K","asia.xmltv","asia.xmltv");
  g.channelByAlias.put(normalize("Case.ID"),"uhd.xmltv");
  g.programsByChannel.put("Case.ID",Collections.singletonList(new Program()));
  lookup(g,"CCTV4K","Case.ID","Case.ID");
  Guide only4k=new Guide();add(only4k,"uhd.xmltv","CCTV4K超高清");
  lookup(only4k,"CCTV4",null,null);lookup(only4k,"CCTV4欧洲",null,null);lookup(only4k,"国际美洲",null,null);
  Guide onlyAsia=new Guide();add(onlyAsia,"asia.xmltv","CCTV4");lookup(onlyAsia,"国际欧洲",null,null);
  Guide headersMissing=new Guide();headersMissing.programsByChannel.put("CCTV4",Collections.singletonList(new Program()));
  lookup(headersMissing,"CCTV-4 中文国际",null,"CCTV4");lookup(headersMissing,"other","CCTV-4","CCTV4");
  Guide target=new Guide();mergeGuide(target,g);
  check(target.programsByChannel.size()==5,"Regional or UHD guide dropped while merging");
  Guide fallback=new Guide();add(fallback,"otherAsia","CCTV4中文国际");add(fallback,"otherUHD","CCTV4K超高清");
  mergeGuide(target,fallback);check(target.programsByChannel.size()==5,"Fallback source replaced or duplicated preferred schedules");
  lookup(target,"CCTV4",null,"asia.xmltv");lookup(target,"CCTV4欧洲",null,"europe.xmltv");
  lookup(target,"CCTV4美洲",null,"america.xmltv");lookup(target,"CCTV4K",null,"uhd.xmltv");
  String[] ids={"CCTV4","CCTV4-EU","CCTV4-AME","CCTV4K Ultra","CCTV4CaseA","CCTV4CaseB"};
  String[] labels={"CCTV4中文国际","CCTV4EUO","CCTV4AME","CCTV4K超高清","Opaque A","Opaque B"};
  StringBuilder xml=new StringBuilder("<tv>");long now=System.currentTimeMillis();
  SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
  for(int i=0;i<ids.length;i++)xml.append("<channel id=\"").append(ids[i]).append("\"><display-name>").append(labels[i]).append("</display-name></channel>");
  for(int i=0;i<ids.length;i++)xml.append("<programme channel=\"").append(ids[i]).append("\" start=\"").append(date.format(new Date(now-60000)))
   .append("\" stop=\"").append(date.format(new Date(now+3600000))).append("\"><title>schedule ").append(i).append("</title></programme>");
  byte[] bytes=xml.append("</tv>").toString().getBytes("UTF-8");
  ByteArrayOutputStream zipped=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(zipped)){zip.write(bytes);}
  for(byte[] content:new byte[][]{bytes,zipped.toByteArray()}){
   Guide parsed=parse(content);check(parsed.programsByChannel.size()==6,"XML parser collapsed opaque IDs");
   for(int i=0;i<4;i++){
    lookup(parsed,labels[i],null,ids[i]);
    check(parsed.programsByChannel.get(channelIdFor(parsed,new Channel(labels[i],null))).get(0).title.equals("schedule "+i),"Wrong XML programme title");
   }
   lookup(parsed,"CCTV4中文国际",null,"CCTV4");
   lookup(parsed,"other","CCTV4CaseA","CCTV4CaseA");lookup(parsed,"other","CCTV4CaseB","CCTV4CaseB");
  }
  // A near-limit disk backup must parse without allocating a full XML byte array.
  File cache=File.createTempFile("epg-stream-",".xml");
  try {
   byte[] padding=new byte[16384];Arrays.fill(padding,(byte)' ');
   try(OutputStream file=new FileOutputStream(cache)){
    file.write("<tv><channel id=\"stream\"><display-name>Streaming</display-name></channel>".getBytes("UTF-8"));
    for(int i=0;i<448;i++){file.write("<ignored>".getBytes("UTF-8"));file.write(padding);file.write("</ignored>".getBytes("UTF-8"));}
    file.write("</tv>".getBytes("UTF-8"));
   }
   check(cache.length()>7*1024*1024,"Streaming fixture too small");
   Guide streamed=parse(new FileInputStream(cache));
   check("stream".equals(streamed.channelByAlias.get("STREAMING")),"Streaming cache lost aliases");
   LimitedGuideInputStream exact=new LimitedGuideInputStream(new ByteArrayInputStream(new byte[8]),8);
   check(exact.read(new byte[16])==8&&exact.read()==-1,"Exact stream limit rejected EOF");
   try{new LimitedGuideInputStream(new ByteArrayInputStream(new byte[9]),8).read(new byte[16]);throw new AssertionError("Oversized stream accepted");}catch(IOException expected){}
   final boolean[] closed={false};
   try{parse(new ByteArrayInputStream(new byte[]{31,(byte)139,0}){public void close(){closed[0]=true;}});throw new AssertionError("Broken gzip accepted");}catch(IOException expected){}
   check(closed[0],"Broken gzip leaked its source");
   try(OutputStream file=new GZIPOutputStream(new FileOutputStream(cache))){
    file.write("<tv>".getBytes("UTF-8"));for(int i=0;i<MAX_XML_BYTES/padding.length+1;i++){file.write("<ignored>".getBytes("UTF-8"));file.write(padding);file.write("</ignored>".getBytes("UTF-8"));}file.write("</tv>".getBytes("UTF-8"));
   }
   check(cache.length()<MAX_DOWNLOAD_BYTES,"Gzip fixture exceeds download limit");
   try{parse(new FileInputStream(cache));throw new AssertionError("Unbounded gzip expansion accepted");}catch(Exception expected){check(expected.toString().contains("上限"),"Unexpected gzip failure: "+expected);}
  }finally{cache.delete();}
  System.out.println("PASS "+checks+" EPG identity checks: CCTV4 Asia/Europe/Americas, 4K/8K suffixes, exact opaque IDs, missing schedules and source priority");
  System.out.println("PASS streaming XML cache under a 16 MiB heap, exact/over-limit streams, gzip expansion limit and failure cleanup");
 }
}'''.replace('METHODS', methods)

with tempfile.TemporaryDirectory() as folder:
    source = Path(folder) / 'EpgMatchingCheck.java'
    source.write_text(java, encoding='utf8')
    jdk = Path(os.environ.get('JAVA_HOME', root / '.codex-tmp/jdk8/runtime/jdk8u504-b01')) / 'bin'
    kxml = Path(os.environ.get('ANDROID_HOME', 'D:/android/sdk')) / 'tools/lib/kxml2-2.3.0.jar'
    subprocess.run([str(jdk / 'javac.exe'), '-encoding', 'UTF-8', '-cp', str(kxml), '-d', folder, str(source)], check=True)
    subprocess.run([str(jdk / 'java.exe'), '-Xmx16m', '-cp', folder+os.pathsep+str(kxml), 'EpgMatchingCheck'], check=True)
