package xiao.bu.channel.bl;

import android.os.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.InflaterInputStream;
import okhttp3.*;
import okio.ByteString;
import org.json.*;

/** Shared-host OkHttp WebSocket, zlib protocol, bounded batches and foreground-only heartbeats. */
final class BlLiveDanmaku {
    private final BlApi api;private final BlDanmaku view;private final long room;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<BlDanmakuData.Line> queued=new ArrayList<>();
    private volatile int generation;private volatile boolean closed;private boolean active=true;private WebSocket socket;private int failures;
    private final LinkedHashSet<String> seen=new LinkedHashSet<>();
    volatile String status="正在连接直播弹幕…";volatile long received;
    private final Runnable flush=new Runnable(){public void run(){if(closed||!active)return;List<BlDanmakuData.Line> batch; synchronized(queued){batch=new ArrayList<>(queued);queued.clear();}if(!batch.isEmpty())view.addLive(batch);main.postDelayed(this,100);}};
    private final Runnable heartbeat=new Runnable(){public void run(){if(!closed&&active&&socket!=null){socket.send(ByteString.of(packet(2,"[object Object]")));main.postDelayed(this,30000);}}};
    private final Runnable reconnect=new Runnable(){public void run(){connect();}};
    BlLiveDanmaku(BlApi api,BlDanmaku view,long room){this.api=api;this.view=view;this.room=room;main.post(flush);connect();}
    static byte[] packet(int operation,String body){try{byte[] bytes=body.getBytes("UTF-8");return ByteBuffer.allocate(16+bytes.length).order(ByteOrder.BIG_ENDIAN).putInt(16+bytes.length).putShort((short)16).putShort((short)1).putInt(operation).putInt(1).put(bytes).array();}catch(Exception e){throw new IllegalArgumentException(e);}}
    private void connect(){if(closed||!active)return;final int token=++generation;if(Build.VERSION.SDK_INT<21){status="直播弹幕兼容模式 · 每3秒刷新";pollHistory(token);return;}status="正在连接直播弹幕…";worker.execute(new Runnable(){public void run(){try{
        final JSONObject info=BlApi.data(api.get(BlApi.LIVE,"/xlive/web-room/v1/index/getDanmuInfo",BlApi.map("id",""+room,"type","0","web_location","444.8"),true));JSONArray hosts=BlApi.array(info,"host_list");JSONObject server=null;for(int i=0;i<hosts.length();i++){JSONObject h=hosts.optJSONObject(i);if(h!=null&&h.optInt("wss_port")>0){server=h;break;}}if(server==null)throw new IOException("直播弹幕地址不可用");
        final String url="wss://"+server.getString("host")+":"+server.getInt("wss_port")+"/sub";
        main.post(new Runnable(){public void run(){if(closed||!active||token!=generation)return;Request request=new Request.Builder().url(url).header("User-Agent",BlApi.UA).header("Referer","https://live.bilibili.com/").header("Origin","https://www.bilibili.com").build();socket=api.client.newWebSocket(request,new WebSocketListener(){
            public void onOpen(WebSocket ws,Response r){try{JSONObject auth=new JSONObject().put("uid",api.uid()).put("roomid",room).put("protover",2).put("platform","web").put("type",2).put("key",info.getString("token"));if(closed||token!=generation){ws.cancel();return;}ws.send(ByteString.of(packet(7,auth.toString())));main.postDelayed(new Runnable(){public void run(){if(token==generation&&status.equals("正在连接直播弹幕…"))failed(token);}},6000);}catch(Exception e){if(!closed&&active&&token==generation)api.diagnostics.record("直播弹幕连接/解包",e,"room="+room);failed(token);}}
            public void onMessage(WebSocket ws,ByteString bytes){if(closed||token!=generation||bytes.size()>2*1024*1024)return;try{decode(bytes.toByteArray(),0,token);}catch(Exception e){if(!closed&&active&&token==generation)api.diagnostics.record("直播弹幕连接/解包",e,"room="+room);failed(token);}}
            public void onClosing(WebSocket ws,int code,String reason){ws.close(1000,null);}
            public void onClosed(WebSocket ws,int code,String reason){failed(token);}
            public void onFailure(WebSocket ws,Throwable t,Response r){if(!closed&&active&&token==generation)api.diagnostics.record("直播弹幕 WebSocket",t,"room="+room+(r==null?"":"; HTTP="+r.code()));failed(token);}
        });}});
    }catch(Exception e){if(!closed&&active&&token==generation)api.diagnostics.record("直播弹幕连接/解包",e,"room="+room);failed(token);}}});}
    // The host's pre-Lollipop TLS engine serializes reads/writes; a WebSocket writer can
    // wait behind its blocking reader. Keep the shared client and use the Web history API.
    private void pollHistory(final int token){if(closed||!active||token!=generation)return;worker.execute(new Runnable(){public void run(){JSONArray rows;String error="";try{rows=BlApi.array(BlApi.data(api.get(BlApi.LIVE,"/xlive/web-room/v1/dM/gethistory",BlApi.map("roomid",""+room),false)),"room");}catch(Exception e){rows=new JSONArray();if(!closed&&active&&token==generation){BlDiagnostics.Entry problem=api.diagnostics.record("直播弹幕兼容轮询",e,"room="+room);error="直播弹幕暂不可用："+problem.summary;}else error="弹幕请求已结束";}final JSONArray values=rows;final String problem=error;main.post(new Runnable(){public void run(){if(closed||!active||token!=generation)return;ArrayList<BlDanmakuData.Line> batch=new ArrayList<>();for(int i=0;i<Math.min(100,values.length());i++){JSONObject item=values.optJSONObject(i);if(item==null)continue;String text=item.optString("text"),key=item.optString("timeline")+":"+item.optLong("uid")+":"+text;if(text.isEmpty()||!seen.add(key))continue;BlDanmakuData.Line line=new BlDanmakuData.Line();line.text=text.length()>120?text.substring(0,120):text;batch.add(line);}while(seen.size()>150)seen.remove(seen.iterator().next());if(!batch.isEmpty()){received+=batch.size();view.addLive(batch);}status=problem.isEmpty()?"直播弹幕兼容模式 · 每3秒刷新":problem;main.postDelayed(new Runnable(){public void run(){pollHistory(token);}},problem.isEmpty()?3000:5000);}});}});}
    private void failed(final int token){main.post(new Runnable(){public void run(){if(closed||!active||token!=generation)return;generation++;main.removeCallbacks(heartbeat);if(socket!=null)socket.cancel();socket=null;status="直播弹幕重连中";main.removeCallbacks(reconnect);main.postDelayed(reconnect,Math.min(30000,2000L<<Math.min(4,failures++)));}});}
    private void decode(byte[] bytes,int depth,final int token)throws Exception{if(depth>2)throw new IOException("直播包嵌套过深");ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);int count=0;while(data.remaining()>=16&&count++<2048){int start=data.position(),length=data.getInt(),header=data.getShort()&65535,version=data.getShort()&65535,op=data.getInt();data.getInt();if(header<16||length<header||length>2*1024*1024||length>bytes.length-start)throw new IOException("直播包长度错误");byte[] body=Arrays.copyOfRange(bytes,start+header,start+length);data.position(start+length);
        if(op==8){if(new JSONObject(new String(body,"UTF-8")).optInt("code",-1)!=0)throw new IOException("直播弹幕认证失败");main.post(new Runnable(){public void run(){if(closed||token!=generation)return;failures=0;status="直播弹幕已连接";main.removeCallbacks(heartbeat);main.post(heartbeat);}});}
        else if(op==5&&version==2){decode(BlApi.read(new InflaterInputStream(new ByteArrayInputStream(body)),2*1024*1024),depth+1,token);}
        else if(op==5&&(version==0||version==1)){JSONObject event=new JSONObject(new String(body,"UTF-8"));if(!event.optString("cmd").startsWith("DANMU_MSG"))continue;JSONArray info=event.optJSONArray("info");if(info==null||info.length()<2)continue;BlDanmakuData.Line line=new BlDanmakuData.Line();line.text=info.optString(1);if(line.text.isEmpty())continue;if(line.text.length()>120)line.text=line.text.substring(0,120);JSONArray style=info.optJSONArray(0);if(style!=null){line.mode=style.optInt(1,1);line.size=style.optInt(2,25);line.color=0xff000000|(style.optInt(3,0xffffff)&0xffffff);}if(line.mode<1||line.mode>6)line.mode=1;synchronized(queued){if(closed||token!=generation)return;if(queued.size()<200){queued.add(line);received++;}}}
        else if(op==5)throw new IOException("直播弹幕协议不可用");
    }}
    void active(boolean value){active=value;generation++;main.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();socket=null;synchronized(queued){queued.clear();}if(value&&!closed){main.post(flush);connect();}}
    void close(){closed=true;active(false);worker.shutdownNow();}
}
