"""Fault-inject real proxy handlers: no second HTTP response inside a broken video."""
import os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=(root/'app/src/main/java/xiao/bu/tv/HlsProxyServer.java').read_text(encoding='utf-8')
def method(start,end):return src[src.index(start):src.index(end,src.index(start))]
handle=method('    private void handle(Socket socket)', '    private static boolean isPlayerDisconnect(')
stream=method('    private void streamUpstream(', '    private static long contentLength(')
java=r'''import java.io.*;import java.net.*;import java.util.regex.*;import java.nio.charset.*;import java.util.concurrent.atomic.*;
public class HlsStreamFailureCheck {
 static class Build {static class VERSION {static final int SDK_INT=19;}}
 static class android {static class os {static class Build {static class VERSION {static final int SDK_INT=19;}}}}
 boolean sourceVideoProbeEnabled;byte[] screenshotSegment;long lastMediaSegmentServedAt;
 boolean isTransportStream(String u,String t){return false;}void sampleSourceVideo(byte[] b){}
 static final Charset UTF_8=StandardCharsets.UTF_8;
 static final int UPSTREAM_MAX_ATTEMPTS=3,UPSTREAM_CONNECT_TIMEOUT_MS=3500,UPSTREAM_READ_TIMEOUT_MS=5500,UPSTREAM_RETRY_DELAY_MS=250;
 static final String TAG="test";boolean running=true,failHeaders,forbidden,failWritingHeaders,resumable,fullBody,badRange,badEntity,ignoredRange,resumeFails,noEntity;int opens;String resumeHeader,ifRange;
 PlaybackHttpError.Attempt playbackHttpError=new PlaybackHttpError.Attempt();
 AtomicLong upstreamDownloadedBytes=new AtomicLong(),streamedResponseBytes=new AtomicLong(),streamedResponseCount=new AtomicLong();
 ThreadLocal<byte[]> streamCopyBuffer=new ThreadLocal<byte[]>(){protected byte[] initialValue(){return new byte[65536];}};
 static class Log{static void e(String t,String m,Exception e){}static void i(String t,String m){}}
 static class SystemClock{static long elapsedRealtime(){return 0;}static void sleep(long t){}}
 static class Base64{static final int URL_SAFE=0;static byte[] decode(String s,int flags){return "https://example.test/segment.mp4".getBytes(UTF_8);}}
 static class ProxyResponse{String contentType;byte[] body;}
 static class HlsSegmentBitrate{static class Sample{void add(byte[] b,int o,int c){}}Sample begin(String u){return null;}void complete(Sample s,long t){}}
 HlsSegmentBitrate segmentBitrate=new HlsSegmentBitrate();
 HlsVodDiskCache vodDiskCache;
 boolean needsCjsTransform(String u){return false;}boolean canStreamWithoutRewrite(String u){return true;}
 boolean hasAesSegmentKey(String u){return false;}boolean hasGenericSegmentTask(String u){return false;}
 boolean isPlayerDisconnect(Exception e){return false;}boolean isRetryableUpstreamError(IOException e,String u){return false;}
 String readAsciiLine(InputStream i){return "GET /proxy/token HTTP/1.1";}String readRangeHeader(InputStream i){return null;}
 ProxyResponse fetch(String u){throw new AssertionError("unexpected buffered fetch");}
 void writeOk(OutputStream o,String t,byte[] b){}
 void writeError(OutputStream o,int code,String m)throws IOException{o.write(("HTTP/1.1 "+code+"\r\n\r\n"+m).getBytes(UTF_8));o.flush();}
 String sanitizeHeaderValue(String s){return s;}void applyRequestHeaders(HttpURLConnection c,String u){}
 long contentLength(HttpURLConnection c){String length=c.getHeaderField("Content-Length");return length==null?131072:Long.parseLong(length);}
 void writeStreamingHeaders(OutputStream o,int s,String t,long l,String r)throws IOException{o.write(("HTTP/1.1 "+s+"\r\nContent-Length: "+l+"\r\n\r\n").getBytes(UTF_8));o.flush();if(failWritingHeaders)throw new IOException("Injected header write failure");}
 HttpURLConnection openUpstreamConnection(String u)throws IOException{final int number=++opens;return new HttpURLConnection(new URL(u)){
  public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  public int getResponseCode(){return forbidden?403:failHeaders?500:resumable&&!(fullBody&&number==1)&&!(ignoredRange&&number>1)?206:200;}
  public String getContentType(){return "video/mp4";}public void setRequestProperty(String key,String value){if(number>1){if(key.equals("Range"))resumeHeader=value;if(key.equals("If-Range"))ifRange=value;}}
  public String getHeaderField(String s){
   if(!resumable)return null;
   if(s.equals("Content-Length"))return number==1?"131072":"65536";
   if(s.equals("ETag"))return noEntity?null:badEntity&&number>1?"\"v2\"":"\"v1\"";
   if(s.equals("Content-Range"))return fullBody?(number==1?null:badRange?"bytes 65537-131072/131072":"bytes 65536-131071/131072"):
     number==1?"bytes 1000-132071/200000":badRange?"bytes 66537-132072/200000":"bytes 66536-132071/200000";
   return null;
  }
  public InputStream getInputStream(){return new InputStream(){boolean emitted;
   public int read(){return -1;}
   public int read(byte[] b)throws IOException{if(resumeFails&&number>1)throw new SocketTimeoutException("repeated failure");if(emitted){if(resumable&&number>1)return -1;throw new SocketTimeoutException("Injected segment read timeout");}emitted=true;java.util.Arrays.fill(b,number==1?(byte)'V':(byte)'W');return b.length;}
  };}
 };}
 static class MemorySocket extends Socket {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();boolean closed;
  public void setSoTimeout(int x){}public void setTcpNoDelay(boolean b){}public void setSendBufferSize(int x){}
  public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
  public OutputStream getOutputStream(){return bytes;}public void close(){closed=true;}
 }
 HANDLE
 STREAM
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  HlsStreamFailureCheck p=new HlsStreamFailureCheck();MemorySocket s=new MemorySocket();p.handle(s);
  String body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 200"),"Expected initial media response");
  check(body.indexOf("HTTP/1.1",1)<0&&!body.contains("Upstream failed"),"Error response corrupted partial media");
  check(body.length()>65536&&s.closed,"Close truncated media for player reconnect");
  p.failWritingHeaders=true;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.indexOf("HTTP/1.1",1)<0&&!body.contains("Upstream failed"),"Partial headers must not get a second response");
  p.failWritingHeaders=false;p.failHeaders=true;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 502")&&s.closed,"Pre-header failures must still return HTTP error");
  p=new HlsStreamFailureCheck();p.forbidden=true;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 403")&&p.playbackHttpError.isForbidden(),"Upstream 403 must reach the player and diagnosis");
  p=new HlsStreamFailureCheck();p.resumable=true;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(p.opens==2&&"bytes=66536-132071".equals(p.resumeHeader)&&"\"v1\"".equals(p.ifRange),"Resume wrong byte offset or missing If-Range");
  check(body.indexOf('W')-body.indexOf('V')==65536&&body.endsWith("WWWW"),"Resumed media duplicated or lost bytes");
  check(p.streamedResponseCount.get()==1&&p.streamedResponseBytes.get()==131072,"Completed range should count exact payload");
  p=new HlsStreamFailureCheck();p.resumable=true;p.resumeFails=true;s=new MemorySocket();p.handle(s);
  check(p.opens==3&&p.streamedResponseCount.get()==0&&s.closed,"Body retry count must be bounded at two");
  for(int bad=0;bad<3;bad++){
   p=new HlsStreamFailureCheck();p.resumable=true;p.badRange=bad==0;p.badEntity=bad==1;p.ignoredRange=bad==2;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
   check(!body.contains("WWWW")&&body.indexOf("HTTP/1.1",1)<0,"Unsafe range/entity change appended bytes");
   check(p.streamedResponseCount.get()==0,"Unsafe range reported complete");
  }
  p=new HlsStreamFailureCheck();p.resumable=true;p.fullBody=true;s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 200")&&p.opens==2&&"bytes=65536-131071".equals(p.resumeHeader),"Full fragment must resume at received offset");
  check(body.indexOf('W')-body.indexOf('V')==65536&&body.endsWith("WWWW")&&p.streamedResponseBytes.get()==131072,"Full fragment resume changed media bytes");
  for(int bad=0;bad<4;bad++){
   p=new HlsStreamFailureCheck();p.resumable=true;p.fullBody=true;p.badRange=bad==0;p.badEntity=bad==1;p.ignoredRange=bad==2;p.noEntity=bad==3;
   s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
   check(!body.contains("WWWW")&&body.indexOf("HTTP/1.1",1)<0&&p.streamedResponseCount.get()==0,"Unsafe full fragment resume appended bytes");
   if(p.noEntity)check(p.opens==1,"Missing entity tag must not retry by range");
  }
  p=new HlsStreamFailureCheck();
  p.vodDiskCache=new HlsVodDiskCache(new File(args[0]),2,url -> new HttpURLConnection(new URL(url)) {
   public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
   public int getResponseCode(){return 200;}public int getContentLength(){return 1024;}public String getContentType(){return "video/mp4";}
   public InputStream getInputStream(){byte[] b=new byte[1024];java.util.Arrays.fill(b,(byte)'K');return new ByteArrayInputStream(b);}
  });
  p.vodDiskCache.register("https://example.test/list.m3u8",new String[]{"#EXTM3U","#EXTINF:20,","segment.mp4","#EXT-X-ENDLIST"});
  s=new MemorySocket();p.handle(s);body=new String(s.bytes.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 200")&&body.endsWith("KKKK")&&p.opens==0,"Cached media must avoid upstream streaming");
  ByteArrayOutputStream range=new ByteArrayOutputStream();p.serveVodCache("https://example.test/segment.mp4","bytes=10-99",range);
  body=new String(range.toByteArray(),UTF_8);
  check(body.startsWith("HTTP/1.1 206")&&body.length()-body.indexOf("\r\n\r\n")-4==90,"Cached range must copy exact requested bytes");
  p.vodDiskCache.close();
  System.out.println("PASS byte-exact HTTP 200 fragment and 206 range resume; changed entity/range/ignored range/missing ETag rejected; retries bounded; no HTTP errors injected into media");
 }
}'''.replace('\n HANDLE\n',handle).replace('\n STREAM\n',stream)
java += (root/'app/src/main/java/xiao/bu/tv/PlaybackHttpError.java').read_text(encoding='utf-8').replace('package xiao.bu.tv;', '').replace('import java.util.regex.Pattern;', '')
out=root/'.codex-tmp/hls-stream-failure-test';out.mkdir(parents=True,exist_ok=True)
f=out/'HlsStreamFailureCheck.java';f.write_text(java,encoding='utf-8');b=Path(os.environ['JAVA_HOME'])/'bin'
cache=out/'HlsVodDiskCache.java';cache.write_text((root/'app/src/main/java/xiao/bu/tv/HlsVodDiskCache.java').read_text(encoding='utf-8').replace('package xiao.bu.tv;',''),encoding='utf-8')
subprocess.run([str(b/'javac.exe'),'-encoding','UTF-8',str(f),str(cache)],check=True)
subprocess.run([str(b/'java.exe'),'-cp',str(out),'HlsStreamFailureCheck',str(out/'cache')],check=True,timeout=30)
