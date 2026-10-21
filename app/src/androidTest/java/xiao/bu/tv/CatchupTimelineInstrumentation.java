package xiao.bu.tv;
import android.app.Instrumentation;import android.os.Bundle;import java.net.*;import java.io.*;import java.text.*;import java.util.*;
public final class CatchupTimelineInstrumentation extends Instrumentation {
 ServerSocket server;volatile boolean running;long start=System.currentTimeMillis()-7200000,stop=start+1800000;int count;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 String stamp(long t){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(t));}
 String manifest(String path){if(path.startsWith("/master"))return "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nmatch.m3u8\n";String time=path.startsWith("/live")?stamp(System.currentTimeMillis()):stamp(start);return "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-START:TIME-OFFSET=-6\n"+(path.startsWith("/unknown")?"":"#EXT-X-PROGRAM-DATE-TIME:"+time+"\n")+"#EXTINF:6,\nsegment.ts\n";}
 void check(String path,boolean strict,boolean reject,int expected)throws Exception{
 HlsProxyServer p=new HlsProxyServer(getTargetContext(),false,false,false,0,false,"high",0,0,0);p.start();int[] state={CatchupTimeline.UNKNOWN};String[] reason={""};
 try{p.setCatchupWindow(start,stop,strict,(s,t,e)->{state[0]=s;reason[0]=e;});boolean failed=false;
 try{p.prepareManifest("http://127.0.0.1:"+server.getLocalPort()+path+"?playseek="+start+"-"+stop);}catch(IOException error){failed=true;}
 if(failed!=reject||state[0]!=expected)throw new AssertionError(path+" failed="+failed+" state="+state[0]+" reason="+reason[0]);
 if(!reject&&!p.finiteMedia())throw new AssertionError("Archive kept live-edge behavior");
 if(!reject&&p.canUseDirectHttpHlsUrl("http://example.test/live.m3u8"))throw new AssertionError("Validation bypass enabled");
 if(path.startsWith("/live")&&!reason[0].contains("返回"))throw new AssertionError("Missing actual returned time");
 count++;
 }finally{p.close();}}
 public void onStart(){Bundle result=new Bundle();try{
 NetworkClient.initialize(getTargetContext());server=new ServerSocket(0);running=true;
 Thread fixture=new Thread(()->{while(running)try(Socket socket=server.accept()){
 BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String first=reader.readLine();if(first==null)continue;String header;while((header=reader.readLine())!=null&&!header.isEmpty()){}
 String path=first.split(" ")[1];byte[] body=manifest(path).getBytes("UTF-8");OutputStream output=socket.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));output.write(body);output.flush();
 }catch(IOException error){if(running)android.util.Log.e("CatchupTimeCheck","fixture",error);}},"catchup-time-fixture");fixture.setDaemon(true);fixture.start();
 check("/live.m3u8",true,true,CatchupTimeline.MISMATCH);
 check("/unknown.m3u8",true,true,CatchupTimeline.MISMATCH);
 check("/unknown.m3u8",false,false,CatchupTimeline.UNKNOWN);
 check("/match.m3u8",true,false,CatchupTimeline.VERIFIED);
 check("/master.m3u8",true,false,CatchupTimeline.VERIFIED);
 result.putString("stream","PASS "+count+" Android "+android.os.Build.VERSION.RELEASE+" HTTP200 response checks: live rejected, inferred unknown rejected, explicit unknown unverified, matching time and master validated; finite playback and no direct bypass");finish(-1,result);
 }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}finally{running=false;try{if(server!=null)server.close();}catch(IOException ignored){}}}
}
