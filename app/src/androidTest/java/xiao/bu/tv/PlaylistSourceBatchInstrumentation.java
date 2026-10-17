package xiao.bu.tv;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Actual list downloads/parsing on the device, with isolated caches and bounded concurrency. */
public final class PlaylistSourceBatchInstrumentation extends PlaylistRefreshInstrumentation {
    private Bundle batchArgs;
    private final List<JSONObject> completed = new ArrayList<>();
    private File report;
    @Override public void onCreate(Bundle value) { batchArgs=value;super.onCreate(value); }
    private static String read(File file) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=new FileInputStream(file)) {
            byte[] block=new byte[8192];int count;
            while((count=input.read(block))!=-1)bytes.write(block,0,count);
        }
        return bytes.toString("UTF-8");
    }
    private synchronized void save(JSONObject row) throws Exception {
        completed.add(row);
        Collections.sort(completed,(a,b)->Integer.compare(a.optInt("index"),b.optInt("index")));
        try(OutputStream output=new FileOutputStream(report)) {
            output.write(new JSONArray(completed).toString(2).getBytes("UTF-8"));
        }
        Bundle progress=new Bundle();progress.putString("source",row.optInt("index")+" "+row.optString("name")
                +" "+row.optString("status")+" channels="+row.optInt("channels")+"\n");sendStatus(0,progress);
    }
    private void check(JSONObject item,int index) throws Exception {
        index=item.optInt("index",index);
        Context context=isolated();JSONObject row=new JSONObject().put("index",index)
                .put("name",item.getString("name")).put("url",item.getString("url"));
        try {
            PlaylistManager manager=new PlaylistManager(context);
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            JSONArray rounds=new JSONArray();row.put("rounds",rounds);
            for(int round=1;round<=2;round++) {
                JSONObject result=new JSONObject().put("round",round);rounds.put(result);
                long began=SystemClock.elapsedRealtime();
                try {
                    byte[] bytes=manager.readForMobile(item.getString("url"));
                    result.put("downloadMs",SystemClock.elapsedRealtime()-began).put("bytes",bytes.length);
                    long parseBegan=SystemClock.elapsedRealtime();
                    ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,bytes,item.getString("url"));
                    result.put("parseMs",SystemClock.elapsedRealtime()-parseBegan);
                    int count=0;List<Channel> candidates=new ArrayList<>();Set<String> urls=new HashSet<>();
                    for(ChannelCatalog.Group group:groups)for(Channel channel:group.channels) {
                        count++;String url=channel.sourceUrl(0);
                        if(url!=null&&(url.startsWith("http://")||url.startsWith("https://"))
                                && !channel.name.contains("未抓取到") && !channel.name.contains("官网")
                                &&urls.add(url))candidates.add(channel);
                    }
                    if(count==0)throw new IOException("No channels parsed from response");
                    result.put("status","loaded").put("channels",count).put("groups",groups.length);
                    row.put("channels",count).put("status","loaded");
                    // Prefer a public news channel where present; keep remaining order deterministic.
                    Collections.sort(candidates,(a,b)->Integer.compare(rank(a.name),rank(b.name)));
                    JSONArray samples=new JSONArray();
                    for(int n=0;n<Math.min(5,candidates.size());n++) {
                        Channel channel=candidates.get(n);samples.put(new JSONObject().put("name",channel.name)
                                .put("url",channel.sourceUrl(0)));
                    }
                    row.put("samples",samples);
                    try(OutputStream output=new FileOutputStream(new File(report.getParentFile(),"source-"+index+".m3u"))) {output.write(bytes);}
                } catch(Exception error) {
                    Throwable cause=error instanceof java.lang.reflect.InvocationTargetException?error.getCause():error;
                    result.put("status","failed").put("elapsedMs",SystemClock.elapsedRealtime()-began).put("error",cause.toString());
                    if(round==1) {row.put("status","failed");break;}
                    row.put("repeatFailed",true);
                }
            }
        } finally {
            context.getSharedPreferences("management",0).edit().clear().commit();
            File[] files=context.getFilesDir().listFiles();if(files!=null)for(File file:files)file.delete();
            context.getFilesDir().delete();
        }
        save(row);
    }
    private void transport(JSONObject item,int index) throws Exception {
        JSONObject row=new JSONObject().put("index",item.optInt("index",index))
                .put("name",item.getString("name")).put("url",item.getString("url"));
        long began=SystemClock.elapsedRealtime();java.net.HttpURLConnection connection=null;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try {
            connection=NetworkClient.openHttp1(new java.net.URL(item.getString("url")),25000);
            connection.setInstanceFollowRedirects(true);connection.setRequestProperty("User-Agent","nTv/1.5");
            int status=connection.getResponseCode();row.put("headersMs",SystemClock.elapsedRealtime()-began).put("httpStatus",status);
            if(status<200||status>=300)throw new IOException("HTTP "+status);
            try(InputStream input=connection.getInputStream()) {
                byte[] block=new byte[8192];int count;
                while((count=input.read(block))!=-1) {
                    if(bytes.size()+count>8*1024*1024)throw new IOException("Profile body exceeds 8 MiB");
                    bytes.write(block,0,count);
                }
            }
            long downloaded=SystemClock.elapsedRealtime();row.put("downloadMs",downloaded-began);
            byte[] body=bytes.toByteArray();
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,body,item.getString("url"));
            int channels=0;JSONArray samples=new JSONArray();
            for(ChannelCatalog.Group group:groups)for(Channel channel:group.channels) {
                channels++;String url=channel.sourceUrl(0);
                if(samples.length()<5&&url!=null&&(url.startsWith("http://")||url.startsWith("https://")))
                    samples.put(new JSONObject().put("name",channel.name).put("url",url));
            }
            row.put("channels",channels).put("samples",samples).put("parseMs",SystemClock.elapsedRealtime()-downloaded);
            if(channels==0)throw new IOException("No channels parsed");
            row.put("status","loaded");
        }catch(Exception error){row.put("status","failed").put("error",error.toString());}
        finally {
            row.put("elapsedMs",SystemClock.elapsedRealtime()-began).put("bytesReceived",bytes.size()).put("transport","http1");
            if(connection!=null)connection.disconnect();
        }
        save(row);
    }
    private static int rank(String name) {
        String value=name.toUpperCase(Locale.US).replace("-","").replace(" ","");
        return value.contains("CCTV13")?0:value.equals("CCTV1")||value.contains("新闻")?1:2;
    }
    private void streamTransport(JSONObject item,int index) throws Exception {
        JSONObject row=new JSONObject().put("index",index).put("name",item.getString("name"));
        long began=SystemClock.elapsedRealtime(),received=0;java.net.HttpURLConnection connection=null;
        try {
            String target;
            if(item.optBoolean("redirectOnly")) {
                java.net.URL gateway=new java.net.URL(item.getString("url"));
                java.net.HttpURLConnection redirect=NetworkClient.openBounded(gateway,12000);
                try {
                    redirect.setInstanceFollowRedirects(false);redirect.setRequestProperty("User-Agent",item.getString("userAgent"));
                    redirect.setConnectTimeout(7000);redirect.setReadTimeout(7000);
                    redirect.setRequestProperty("Accept","application/vnd.apple.mpegurl,application/x-mpegURL,video/*,audio/*,*/*;q=0.8");
                    redirect.setRequestProperty("Cache-Control","no-cache");redirect.setRequestProperty("Pragma","no-cache");
                    int status=redirect.getResponseCode();String location=redirect.getHeaderField("Location");
                    row.put("gatewayStatus",status);
                    if(status<300||status>308||location==null)throw new IOException("Gateway is not a redirect: "+status);
                    target=new java.net.URL(gateway,location).toString();
                }finally {redirect.disconnect();}
            }else target=HttpStreamResolver.resolve(item.getString("url")).url;
            row.put("resolveMs",SystemClock.elapsedRealtime()-began).put("resolvedUrl",target);
            java.net.URL url=new java.net.URL(target);
            connection=item.optBoolean("http1")?NetworkClient.openHttp1(url,25000):NetworkClient.openBounded(url,25000);
            connection.setRequestProperty("User-Agent",item.getString("userAgent"));
            connection.setRequestProperty("Accept-Encoding","identity");
            if(item.has("referer"))connection.setRequestProperty("Referer",item.getString("referer"));
            int status=connection.getResponseCode();row.put("httpStatus",status).put("playbackHeadersMs",SystemClock.elapsedRealtime()-began);
            if(status!=200)throw new IOException("HTTP "+status);
            row.put("contentLength",connection.getHeaderField("Content-Length"));
            long readStart=SystemClock.elapsedRealtime();boolean eof=false;
            try(InputStream input=connection.getInputStream()) {
                byte[] block=new byte[16384];int count;
                while(SystemClock.elapsedRealtime()-readStart<10000) {
                    count=input.read(block);if(count<0){eof=true;break;}
                    if(received==0 && count>0)row.put("firstDataMs",SystemClock.elapsedRealtime()-began);
                    received+=count;
                }
            }
            row.put("readMs",SystemClock.elapsedRealtime()-readStart).put("eof",eof).put("status",eof?"ended":"streaming");
        }catch(Exception error){row.put("status","failed").put("error",error.toString());}
        finally {if(connection!=null)connection.disconnect();row.put("bytesReceived",received).put("elapsedMs",SystemClock.elapsedRealtime()-began);}
        save(row);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();ExecutorService pool=Executors.newFixedThreadPool(batchArgs.containsKey("streamProbe")?1:3);int code=-1;
        boolean enabled=GithubProxy.isEnabled();String base=GithubProxy.baseUrl();
        try {
            NetworkClient.initialize(getTargetContext());TlsCompat.install();
            GithubProxy.setEnabled(true);GithubProxy.setBaseUrl(GithubProxy.DEFAULT_BASE_URL);
            report=new File(getTargetContext().getExternalFilesDir(null),batchArgs.containsKey("streamProbe")?"stream-transport-results.json":batchArgs.containsKey("http1")
                    ?"source-http1-results.json":"source-batch-results.json");
            JSONArray input=new JSONArray(read(new File(batchArgs.getString("manifest"))));
            List<Future<?>> tasks=new ArrayList<>();
            for(int n=0;n<input.length();n++) {
                final int index=n+1;final JSONObject item=input.getJSONObject(n);
                tasks.add(pool.submit(()->{try{if(batchArgs.containsKey("streamProbe"))streamTransport(item,index);
                        else if(batchArgs.containsKey("http1"))transport(item,index);
                        else check(item,index);}catch(Exception error){throw new RuntimeException(error);}}));
            }
            for(Future<?> task:tasks)task.get(5,TimeUnit.MINUTES);
            result.putString("report",report.toString());result.putInt("sources",completed.size());
        }catch(Throwable error){code=1;result.putString("error",error.toString());}
        finally {pool.shutdownNow();GithubProxy.setBaseUrl(base);GithubProxy.setEnabled(enabled);}
        finish(code,result);
    }
}
