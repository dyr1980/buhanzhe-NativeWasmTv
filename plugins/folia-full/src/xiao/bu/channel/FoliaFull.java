package xiao.bu.channel;

import android.content.Context;
import android.os.Build;
import android.webkit.*;
import android.view.View;
import org.json.JSONObject;
import xiao.bu.tv.jar.ChannelPlugin;

/** The actual upstream Folia application, loaded from this JAR's static assets. */
public final class FoliaFull implements ChannelPlugin {
    private WebView browser;
    private FoliaServer server;
    private FoliaLocalPicker localPicker;
    private volatile boolean disposed;
    public View create(Context context,ClassLoader resources,JSONObject config,Host host)throws Exception {
        if(Build.VERSION.SDK_INT<24)throw new java.io.IOException("完整版需要 Android 7.0 及以上与较新的系统 WebView");
        server=new FoliaServer(context,resources,config.optString("serviceOrigin","https://music.aikun-bili.top"));
        browser=new WebView(context);
        browser.setTag("ntv-channel-media");
        browser.setBackgroundColor(0xff09090b);
        browser.setHorizontalScrollBarEnabled(false);browser.setVerticalScrollBarEnabled(false);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings=browser.getSettings();
        java.util.regex.Matcher engine=java.util.regex.Pattern.compile("Chrome/([0-9]+)").matcher(settings.getUserAgentString());
        if(!engine.find()||Integer.parseInt(engine.group(1))<61)
            throw new java.io.IOException("Folia 需要 WebView 61 或更新版本，请更新系统 WebView");
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,true);
        browser.addJavascriptInterface(new Object(){
            @JavascriptInterface public String save(String name,String base64,String token){
                if(disposed||server==null||!server.exportToken(token))return "导出页面已关闭";
                if(base64==null||base64.length()>32*1024*1024)return "导出文件超过 24 MiB";
                try {
                    java.io.File dir=context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if(dir==null)throw new java.io.IOException("下载目录不可用");
                    if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("无法创建下载目录");
                    String safe=(name==null?"folia-export":name).replaceAll("[\\\\/\\p{Cntrl}]","_");
                    if(safe.length()>160)safe=safe.substring(safe.length()-160);
                    if(safe.isEmpty()||safe.equals(".")||safe.equals(".."))safe="folia-export";
                    java.io.File file=new java.io.File(dir,safe);
                    if(file.exists())file=new java.io.File(dir,System.currentTimeMillis()+"-"+safe);
                    byte[] bytes=android.util.Base64.decode(base64,android.util.Base64.DEFAULT);
                    try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(bytes);}
                    return "已保存："+file.getAbsolutePath();
                }catch(Exception e){return "导出失败："+e.getMessage();}
            }
        },"NTVFoliaExport");
        browser.setDownloadListener((url,ua,disposition,mime,length)->{
            if(!(url.startsWith("https://")||url.startsWith("http://"))){host.status("请使用页面中的导出按钮保存文件");return;}
            try {
                android.app.DownloadManager manager=(android.app.DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
                android.app.DownloadManager.Request request=new android.app.DownloadManager.Request(android.net.Uri.parse(url));
                String filename=URLUtil.guessFileName(url,disposition,mime);
                request.setDestinationInExternalFilesDir(context,android.os.Environment.DIRECTORY_DOWNLOADS,filename);
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.addRequestHeader("User-Agent",ua);
                String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)request.addRequestHeader("Cookie",cookies);
                manager.enqueue(request);host.status("已开始下载："+filename);
            }catch(Exception e){host.status("下载失败："+e.getMessage());}
        });
        browser.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,String url){
                if(url.startsWith(server.origin()+"/"))return false;
                host.status("外部链接："+url);return true;
            }
            public void onPageFinished(WebView view,String url){host.configureBrowser(view);}
            public void onReceivedError(WebView view,int code,String message,String url){if(url.equals(server.origin()+"/"))host.status("Folia 页面加载失败："+message);}
        });
        browser.setWebChromeClient(new WebChromeClient(){
            public boolean onShowFileChooser(WebView view,ValueCallback<android.net.Uri[]> callback,FileChooserParams params){
                String[] types=params.getAcceptTypes();
                ValueCallback<android.net.Uri[]> result=files->{
                    callback.onReceiveValue(files);
                    if(!disposed&&browser==view&&(files==null||files.length==0))
                        view.evaluateJavascript("window.__NTV_FOLIA_FILES__&&window.__NTV_FOLIA_FILES__.cancel()",null);
                };
                String mime=types.length==1&&types[0].matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.*+-]+")?types[0]:"*/*";
                if(!"application/x-ntv-folia-directory".equals(mime)&&host instanceof ChannelPlugin.BrowserHost)
                    ((ChannelPlugin.BrowserHost)host).chooseFiles(result,mime,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                else if(context instanceof android.app.Activity){
                    if(localPicker==null)localPicker=new FoliaLocalPicker((android.app.Activity)context,host);
                    localPicker.open(result,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                }else result.onReceiveValue(null);
                return true;
            }
            public boolean onConsoleMessage(ConsoleMessage message){android.util.Log.d("FoliaFull",message.message()+" @"+message.sourceId()+":"+message.lineNumber());return true;}
        });
        host.configureBrowser(browser);
        browser.loadUrl(server.origin()+"/");
        return browser;
    }
    public void update(long position,boolean playing){/* Upstream Web Audio owns the complete playback clock. */}
    public void dispose(){disposed=true;if(localPicker!=null){localPicker.cancel();localPicker=null;}if(browser!=null){browser.removeJavascriptInterface("NTVFoliaExport");browser.stopLoading();browser.loadUrl("about:blank");browser.destroy();browser=null;}if(server!=null){server.close();server=null;}}
}
