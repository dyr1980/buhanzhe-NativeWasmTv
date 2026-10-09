"""Exercise cache cursors, seeks and bounded free-space polling using production Java.

Optional --baseline PATH compares the pre-cleanup metadata hot path. Timing is
informational; correctness and disk-query counts are deterministic assertions.
"""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / '.codex-tmp/compare-95a7839/cache-performance'
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
SOURCE = r'''package xiao.bu.tv;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;
import java.lang.reflect.*;import java.util.concurrent.atomic.*;
class CachePerformanceCheck {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static final AtomicInteger calls=new AtomicInteger();
 static final List<String> requests=Collections.synchronizedList(new ArrayList<>());
 static class Disk extends File {
  long available;int polls;
  Disk(File path,long available){super(path.toString());this.available=available;}
  public long getUsableSpace(){polls++;return available;}
 }
 static Object field(Object target,String name)throws Exception {
  Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
 }
 static void set(Object target,String name,Object value)throws Exception {
  Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
 }
 static HttpURLConnection connect(String url,int size)throws IOException {
  requests.add(url);calls.incrementAndGet();
  return new HttpURLConnection(new URL(url)) {
   public void connect(){} public boolean usingProxy(){return false;}public void disconnect(){}
   public int getResponseCode(){return 200;}public int getContentLength(){return size;}
   public InputStream getInputStream(){return new InputStream(){
    int remaining=size;
    public int read(){return remaining-- > 0 ? 73 : -1;}
    public int read(byte[] b,int off,int len){if(remaining==0)return -1;int n=Math.min(len,remaining);
     Arrays.fill(b,off,off+n,(byte)73);remaining-=n;return n;}
   };}
  };
 }
 static String[] playlist(int count){List<String> lines=new ArrayList<>();lines.add("#EXTM3U");
  for(int i=0;i<count;i++){lines.add("#EXTINF:5,");lines.add(i+".ts");}lines.add("#EXT-X-ENDLIST");
  return lines.toArray(new String[0]);}
 static void awaitBytes(HlsVodDiskCache cache,long size)throws Exception {
  long deadline=System.nanoTime()+5000000000L;
  while(cache.bytes()<size&&System.nanoTime()<deadline)Thread.sleep(5);
  check(cache.bytes()>=size,"Prefetch finishes");
 }
 static long benchmark(File root,int count,int iterations)throws Exception {
  HlsVodDiskCache cache=new HlsVodDiskCache(root,2,u->{throw new AssertionError("Metadata-only benchmark");});
  Class<?> entryClass=Class.forName("xiao.bu.tv.HlsVodDiskCache$Entry"),trackClass=Class.forName("xiao.bu.tv.HlsVodDiskCache$Track");
  Constructor<?> entryCtor=entryClass.getDeclaredConstructor(),trackCtor=trackClass.getDeclaredConstructor();
  entryCtor.setAccessible(true);trackCtor.setAccessible(true);Object track=trackCtor.newInstance();
  Map entries=(Map)field(cache,"entries");List segments=(List)field(track,"segments");
  Map positions=null;try{positions=(Map)field(track,"positions");}catch(NoSuchFieldException baseline){}
  for(int i=0;i<count;i++){Object e=entryCtor.newInstance();String url="http://fixture.test/"+i+".ts";
   set(e,"url",url);set(e,"seconds",5.0);entries.put(url,e);segments.add(e);if(positions!=null)positions.put(e,i);}
  ((Map)field(cache,"tracks")).put("list",track);
  try{((List)field(cache,"trackOrder")).add(track);double[] durations=new double[count+1];
   for(int i=0;i<=count;i++)durations[i]=i*5.0;set(track,"durations",durations);
  }catch(NoSuchFieldException baseline){}
  // Two active prefetch jobs: measure metadata work while workers are occupied.
  set(cache,"jobs",2);String url="http://fixture.test/"+(count-1)+".ts";
  for(int i=0;i<1000;i++)cache.get(url);
  long start=System.nanoTime();for(int i=0;i<iterations;i++)cache.get(url);long ns=System.nanoTime()-start;
  cache.close();return ns;
 }
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);root.mkdirs();
  if(args.length>1){for(int count:new int[]{256,4096})
   System.out.println("BENCH fragments="+count+" calls=20000 elapsedMs="+benchmark(root,count,20000)/1e6);return;}
  HlsVodDiskCache cache=new HlsVodDiskCache(root,2,u->connect(u,2048));
  cache.register("http://fixture.test/list.m3u8",playlist(12));awaitBytes(cache,12*2048);
  check(cache.get("http://fixture.test/10.ts")!=null,"Forward seek stays cached");
  check(cache.bytes()<=5*2048,"Old prefix evicted once");
  check(cache.get("http://fixture.test/1.ts")!=null,"Backward seek redownloads evicted fragment");
  check(cache.get("http://fixture.test/10.ts")!=null,"Forward seek after backward seek");
  byte[] data=Files.readAllBytes(cache.get("http://fixture.test/10.ts").toPath());
  check(data.length==2048&&data[0]==73,"Seek preserves exact media bytes");cache.close();Thread.sleep(50);
  requests.clear();cache=new HlsVodDiskCache(root,2,u->connect(u,2048));
  cache.register("http://fixture.test/duplicate.m3u8",new String[]{"#EXTM3U","#EXTINF:5,","0.ts","#EXTINF:5,","1.ts","#EXTINF:5,","0.ts","#EXTINF:5,","2.ts","#EXT-X-ENDLIST"});
  awaitBytes(cache,3*2048);check(cache.get("http://fixture.test/0.ts")!=null,"Repeated segment URL works");
  check(requests.size()==3,"Shared URL downloads once");cache.close();Thread.sleep(50);
  requests.clear();cache=new HlsVodDiskCache(root,2,u->connect(u,4*1024*1024));
  Disk disk=new Disk((File)field(cache,"directory"),1024L*1024*1024);set(cache,"directory",disk);
  cache.register("http://fixture.test/disk.m3u8",playlist(1));
  check(cache.get("http://fixture.test/0.ts")!=null,"Large fragment committed");
  check(disk.polls<=8,"Disk space is not queried per 64KiB: "+disk.polls);
  System.out.println("PASS 4MiB fragment disk-space queries="+disk.polls+" (formerly at least 64)");
  disk.available=31L*1024*1024;set(cache,"spaceCheckTime",0L);int before=calls.get();
  cache.register("http://fixture.test/no-space.m3u8",new String[]{"#EXTM3U","#EXTINF:5,","new.ts","#EXT-X-ENDLIST"});
  check(calls.get()==before&&cache.get("http://fixture.test/new.ts")==null,"Low space bypasses reserve without starting a download");
  cache.close();Thread.sleep(50);check(root.list().length==0,"Cache files cleaned");
  System.out.println("PASS forward/backward seeks, eviction, duplicate URL, low space and cleanup");
 }
}'''

def run(source, name, benchmark=False):
    work = WORK / name
    work.mkdir(parents=True, exist_ok=True)
    (work / 'HlsVodDiskCache.java').write_text(source.read_text(encoding='utf-8'), encoding='utf-8')
    (work / 'CachePerformanceCheck.java').write_text(SOURCE, encoding='utf-8')
    subprocess.run([str(JAVA / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(work),
                    str(work / 'HlsVodDiskCache.java'), str(work / 'CachePerformanceCheck.java')], check=True)
    command = [str(JAVA / 'java.exe'), '-cp', str(work), 'xiao.bu.tv.CachePerformanceCheck', str(work / 'cache')]
    subprocess.run(command + (['benchmark'] if benchmark else []), check=True, timeout=30)

parser = argparse.ArgumentParser()
parser.add_argument('--baseline', type=Path)
args = parser.parse_args()
current = ROOT / 'app/src/main/java/xiao/bu/tv/HlsVodDiskCache.java'
run(current, 'current')
if args.baseline:
    print('Before cleanup:', flush=True)
    run(args.baseline, 'before', True)
    print('After cleanup:', flush=True)
    run(current, 'current', True)
