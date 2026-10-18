"""Exercise production resolver handoff and fetch dispatch: one use, rewrite, expiry."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / '.codex-tmp/hls-resolved-handoff'
out.mkdir(parents=True, exist_ok=True)
proxy = (root / 'app/src/main/java/xiao/bu/tv/HlsProxyServer.java').read_text(encoding='utf-8')
ahead = proxy[proxy.index('    // One upcoming manifest'):proxy.index('    void rememberResolvedPlaylist(')]
seed = proxy[proxy.index('    void rememberResolvedPlaylist('):proxy.index('    HlsStartupManifest.Result prepareManifest(')]
fetch = proxy[proxy.index('    private ProxyResponse fetch('):proxy.index('        if (metadataProbeMode) synchronized (probeManifests)')]
fetch += '        return upstream();\n    }\n'
source = r'''package xiao.bu.tv;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.nio.charset.*;
class SystemClock {static long now=10000;static long elapsedRealtime(){return now;}}
class HttpStreamResolver {static boolean isExplicitHlsUrl(String u){return u.endsWith(".m3u8");}static class Result {
 String url;byte[] playlist;long playlistReceivedNanos=System.nanoTime();
 Result(String url,byte[] playlist){this.url=url;this.playlist=playlist;}
}}
class HlsProxyServer {
 static final Charset UTF_8=StandardCharsets.UTF_8;
 volatile boolean running=true,playbackSuspended;volatile int requests;int rewrites;CountDownLatch gate,started;
 final HlsStartupPlaylistCache startupPlaylists=new HlsStartupPlaylistCache(),resolvedPlaylists=new HlsStartupPlaylistCache();
 final Map<String,Boolean> knownHlsPlaylists=new HashMap<>();
 boolean needsSpecialDecrypt(String url){return url.contains("encrypted");}
 boolean needsCjsTransform(String url){return url.contains("plugin");}
 static class ProxyResponse {byte[] body;ProxyResponse(String type,byte[] body){this.body=body;}}
 String rewritePlaylist(String policy,String response,String text){rewrites++;return "rewritten:"+text;}
 ProxyResponse upstream()throws IOException{requests++;if(started!=null)started.countDown();if(gate!=null)try{gate.await();}catch(InterruptedException e){throw new IOException(e);}return new ProxyResponse("application/vnd.apple.mpegurl",new byte[0]);}
 AHEAD
 SEED
 FETCH
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  byte[] body="#EXTM3U\n#EXTINF:2,\npart.ts\n#EXT-X-ENDLIST".getBytes(UTF_8);
  String url="http://fixture.test/huya.php?id=123";
  HlsProxyServer p=new HlsProxyServer();
  p.rememberResolvedPlaylist(new HttpStreamResolver.Result(url,body));
  check(p.knownHlsPlaylists.containsKey(url),"Suffixless HLS classification lost");
  p.fetch(url+"4");check(p.requests==1,"Handoff leaked to a different room");
  ProxyResponse response=p.fetch(url);
  check(p.requests==1&&p.rewrites==1&&new String(response.body,UTF_8).startsWith("rewritten:#EXTM3U"),"Handoff must be rewritten and avoid another gateway request");
  p.fetch(url);check(p.requests==2,"Live reload reused a stale handoff");
  p.rememberResolvedPlaylist(new HttpStreamResolver.Result(url,body));SystemClock.now+=1001;
  p.fetch(url);check(p.requests==3,"Queued snapshot must expire");
  HttpStreamResolver.Result old=new HttpStreamResolver.Result(url,body);old.playlistReceivedNanos-=TimeUnit.SECONDS.toNanos(2);
  p.rememberResolvedPlaylist(old);p.fetch(url);check(p.requests==4,"Old resolver completion reset freshness");
  for(String excluded:new String[]{"http://fixture.test/encrypted","http://fixture.test/plugin"}) {
   p.rememberResolvedPlaylist(new HttpStreamResolver.Result(excluded,body));
   check(!p.knownHlsPlaylists.containsKey(excluded),"Special decrypt path inherited a plain response");
  }
  HlsProxyServer q=new HlsProxyServer();q.gate=new CountDownLatch(1);q.started=new CountDownLatch(1);
  q.rememberResolvedPlaylist(new HttpStreamResolver.Result(url,"#EXTM3U\n#EXTINF:2,\npart.ts".getBytes(UTF_8)));
  q.fetch(url);check(q.started.await(1,TimeUnit.SECONDS),"Next manifest was not overlapped with playback");
  ProxyResponse[] joined=new ProxyResponse[2];Throwable[] errors=new Throwable[2];
  Thread a=new Thread(()->{try{joined[0]=q.takeManifestAhead(url);}catch(Throwable e){errors[0]=e;}});
  Thread b=new Thread(()->{try{joined[1]=q.takeManifestAhead(url);}catch(Throwable e){errors[1]=e;}});
  a.start();b.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while((a.getState()!=Thread.State.WAITING||b.getState()!=Thread.State.WAITING)&&System.nanoTime()<deadline)Thread.yield();
  q.gate.countDown();a.join(2000);b.join(2000);q.manifestAheadWorker.shutdownNow();
  check(!a.isAlive()&&!b.isAlive()&&errors[0]==null&&errors[1]==null,"Reload join did not finish");
  check(joined[0]!=null&&joined[0]==joined[1]&&q.requests==1,"Concurrent reloads duplicated the gateway request");
  p.playbackSuspended=true;int before=p.requests;
  try{p.fetch(url);throw new AssertionError("Background fetch reached the network");}
  catch(InterruptedIOException expected){}
  check(p.requests==before,"Suspended handoff opened another request");
  System.out.println("PASS dynamic live reload overlaps playback and joins one GET");
  System.out.println("PASS exact-URL one-shot manifest handoff, rewriting, reload, expiry and encrypted/plugin isolation");
 }
}'''.replace(' AHEAD', ahead).replace(' SEED', seed).replace(' FETCH', fetch)
target = out / 'HlsProxyServer.java'
target.write_text(source, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java/'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(target),
                str(root/'app/src/main/java/xiao/bu/tv/HlsStartupPlaylistCache.java')], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'xiao.bu.tv.HlsProxyServer'], check=True)
