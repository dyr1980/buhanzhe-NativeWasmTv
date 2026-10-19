package xiao.bu.channel;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import org.json.JSONObject;
import xiao.bu.tv.jar.ChannelPlugin;
import java.io.*;
import java.util.concurrent.*;

/** Compact standalone Folia integration. No Node, Electron or QuickJS runtime. */
public final class FoliaChannel implements ChannelPlugin {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WebView web;
    private volatile boolean disposed;
    private boolean ready;
    private JSONObject data;
    private int selection;
    public View create(Context context,ClassLoader resources,JSONObject config,Host host)throws Exception {
        String audio=config.optString("audio");
        if(audio.length()>0)requireHttp(audio,"audio");
        String html;
        try(InputStream in=resources.getResourceAsStream("folia/index.html")) {
            if(in==null)throw new IOException("缺少 Folia 页面");
            html=new String(MetadataService.read(in,256*1024),"UTF-8");
        }
        web=new WebView(context);web.setBackgroundColor(0xff101522);
        web.setHorizontalScrollBarEnabled(false);web.setVerticalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        if(android.os.Build.VERSION.SDK_INT>=16) {
            web.getSettings().setAllowFileAccessFromFileURLs(false);
            web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        }
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
                Uri uri=Uri.parse(url);
                if("ntv-folia".equals(uri.getScheme())) {
                    if("search".equals(uri.getHost()))search(uri.getQueryParameter("q"));
                    else if("song".equals(uri.getHost())) {
                        try {
                            JSONObject song=new JSONObject().put("songId",uri.getQueryParameter("id"))
                                    .put("title",uri.getQueryParameter("title")).put("artist",uri.getQueryParameter("artist"));
                            String id=song.optString("songId");
                            if(id.matches("[0-9]+")) {
                                host.play("https://music.163.com/song/media/outer/url?id="+id+".mp3");
                                load(context,song);
                            }
                        }catch(Exception e){host.status(e.getMessage());}
                    }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view,String url){ready=true;host.configureBrowser(view);render();}
        });
        data=new JSONObject().put("title",config.optString("title")).put("artist",config.optString("artist"))
                .put("status",audio.length()>0?"正在获取封面与歌词":"搜索歌曲开始播放，或通过 M3U 指定音频").put("lrc","");
        web.loadDataWithBaseURL("https://folia.invalid/",html,"text/html","UTF-8",null);
        // Post so the host can attach the returned view before playback starts.
        host.configureBrowser(web);
        main.post(()->{if(!disposed&&audio.length()>0)host.play(audio);});
        if(audio.length()>0)load(context,config);
        return web;
    }
    private void load(Context context,JSONObject config) {
        final int request=++selection;
        worker.execute(()->{
            JSONObject result=MetadataService.load(context,config);
            main.post(()->{if(!disposed&&request==selection){data=result;render();}});
        });
    }
    private void search(String query) {
        if(query==null||query.trim().length()==0||query.length()>200)return;
        script("window.folia.searchStatus('正在搜索…')");
        final int request=++selection;
        worker.execute(()->{
            try {
                org.json.JSONArray songs=MetadataService.search(query);
                String json=songs.toString();
                main.post(()->{if(!disposed&&request==selection)script("window.folia.results("+JSONObject.quote(json)+")");});
            }catch(Exception e){main.post(()->{if(!disposed&&request==selection)script("window.folia.searchStatus("+JSONObject.quote("搜索失败: "+e.getMessage())+")");});}
        });
    }
    static void requireHttp(String value,String field)throws IOException {
        Uri uri=Uri.parse(value);
        if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null)
            throw new IOException("请配置有效的 "+field+" HTTP(S) 地址");
    }
    private void render(){if(ready&&data!=null)script("window.folia.set("+JSONObject.quote(data.toString())+")");}
    private void script(String code){
        if(disposed||web==null)return;
        if(android.os.Build.VERSION.SDK_INT>=19)web.evaluateJavascript(code,null);
        else web.loadUrl("javascript:"+code);
    }
    public void update(long position,boolean playing){if(ready)script("window.folia.clock("+Math.max(0,position)+","+playing+")");}
    public void dispose(){disposed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}}
}
