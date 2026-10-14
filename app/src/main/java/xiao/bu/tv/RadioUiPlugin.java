package xiao.bu.tv;

import android.content.Context;
import android.os.*;
import dalvik.system.DexClassLoader;
import xiao.bu.tv.radio.RadioUi;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Immutable JAR paths permit replacing a View while its old DEX is still loaded. */
final class RadioUiPlugin {
    static final String DEFAULT_URL = "https://raw.githubusercontent.com/buhanzhe/NativeWasmTv/master/plugins/radio-ui2/dist/radio-ui2.jar";
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final List<WeakReference<AudioArtworkView>> views = new ArrayList<WeakReference<AudioArtworkView>>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile Factory active;
    private static boolean requested;
    private static volatile String failure = "";
    private static final Object INSTALL_LOCK = new Object();
    static final class Factory {
        final ClassLoader loader;
        final Constructor<?> constructor;
        final String sha;
        final int version;
        final boolean supportsOptical;
        Factory(ClassLoader loader, Constructor<?> constructor, String sha, int version, boolean supportsOptical) {
            this.loader=loader; this.constructor=constructor; this.sha=sha; this.version=version;
            this.supportsOptical=supportsOptical;
        }
        RadioUi create(Context context) throws Exception { return (RadioUi) constructor.newInstance(context, loader); }
    }
    private static File root(Context context) { return new File(context.getFilesDir(), "radio-ui"); }
    static synchronized void request(final AudioArtworkView view) {
        boolean known=false;
        for (Iterator<WeakReference<AudioArtworkView>> it=views.iterator();it.hasNext();) {
            AudioArtworkView value=it.next().get(); if(value==null)it.remove(); else if(value==view)known=true;
        }
        if(!known)views.add(new WeakReference<AudioArtworkView>(view));
        if(active!=null){view.replacePlugin(active);return;}
        if(requested)return;
        requested=true;
        final Context context=view.getContext().getApplicationContext();
        worker.execute(new Runnable(){public void run(){try {
            String sha=context.getSharedPreferences("radio_ui",0).getString("active","");
            if(!sha.matches("[a-f0-9]{64}")){installUrl(context, sourceUrl(context));return;}
            Factory cached=load(context,new File(root(context),sha+".jar"),sha);
            synchronized(RadioUiPlugin.class){if(active==null)publish(cached);}
        }catch(Exception error){failure=error.toString();}}});
    }
    private static Factory load(Context context, File jar, String sha) throws Exception {
        if(jar.length()==0 || jar.length()>MAX_BYTES)throw new IOException("JAR 文件大小无效");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(jar)){byte[] block=new byte[8192];int count;while((count=input.read(block))!=-1)digest.update(block,0,count);}
        StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));
        if(!sha.equals(actual.toString()))throw new IOException("界面插件校验失败");
        JSONObject metadata;
        try(ZipFile zip=new ZipFile(jar)) {
            ZipEntry dex=zip.getEntry("classes.dex"), info=zip.getEntry("radio-ui.json");
            if(dex==null || info==null || dex.getSize()>4*1024*1024 || info.getSize()>4096)
                throw new IOException("不是有效的电台界面 JAR");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=zip.getInputStream(info)) { int b;while((b=in.read())!=-1){if(bytes.size()>=4096)throw new IOException("插件配置过大");bytes.write(b);} }
            metadata=new JSONObject(bytes.toString("UTF-8"));
        }
        if(metadata.getInt("api")!=1 || metadata.getInt("version")<1 || metadata.getInt("minSdk")>Build.VERSION.SDK_INT)
            throw new IOException("电台界面版本不兼容");
        File optimized=new File(root(context),"dex"); if(!optimized.isDirectory()&&!optimized.mkdirs())throw new IOException("无法创建 DEX 目录");
        DexClassLoader loader=new DexClassLoader(jar.getAbsolutePath(),optimized.getAbsolutePath(),null,RadioUi.class.getClassLoader());
        Class<?> type=loader.loadClass(metadata.getString("entry"));
        if(!RadioUi.class.isAssignableFrom(type))throw new IOException("插件接口不兼容");
        JSONArray styles=metadata.optJSONArray("styles");boolean optical=false;
        if(styles!=null)for(int i=0;i<styles.length();i++)if("optical".equals(styles.optString(i)))optical=true;
        return new Factory(loader,type.getConstructor(Context.class,ClassLoader.class),sha,metadata.getInt("version"),optical);
    }
    private static synchronized void publish(final Factory factory) {
        active=factory; failure="";
        main.post(new Runnable(){public void run(){synchronized(RadioUiPlugin.class){
            for(Iterator<WeakReference<AudioArtworkView>> it=views.iterator();it.hasNext();) {
                AudioArtworkView view=it.next().get();if(view==null)it.remove();else view.replacePlugin(factory);
            }
        }}});
    }
    /** Called on a control-server worker, never on the UI thread. */
    static String install(final Context context, byte[] bytes) throws Exception {
        synchronized(INSTALL_LOCK){return installLocked(context,bytes);}
    }
    private static String installLocked(final Context context, byte[] bytes) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("插件安装需要后台线程");
        if(bytes.length==0 || bytes.length>MAX_BYTES)throw new IOException("JAR 不能超过 2 MB");
        StringBuilder digest=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(Locale.US,"%02x",b&255));
        String sha=digest.toString();File dir=root(context);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建插件目录");
        File jar=new File(dir,sha+".jar");
        if(!jar.isFile()) {
            File temp=new File(dir,sha+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
            if(!temp.setReadOnly()||!temp.renameTo(jar))throw new IOException("无法保存界面插件");
        }
        // Verify content, API and constructor before switching the persistent active pointer.
        final Factory factory;
        try { factory=load(context,jar,sha); }
        catch(Exception error) {
            if(!sha.equals(context.getSharedPreferences("radio_ui",0).getString("active","")))jar.delete();
            throw error;
        }
        final CountDownLatch done=new CountDownLatch(1);final Throwable[] error={null};
        main.post(new Runnable(){public void run(){try{RadioUi ui=factory.create(context);ui.show("",true,null);ui.dispose();}catch(Throwable e){error[0]=e;}finally{done.countDown();}}});
        if(!done.await(10,TimeUnit.SECONDS))throw new IOException("界面初始化超时");
        if(error[0]!=null)throw new IOException("界面初始化失败",error[0]);
        android.content.SharedPreferences.Editor preferences=context.getSharedPreferences("radio_ui",0).edit().putString("active",sha);
        if(!factory.supportsOptical)preferences.putString("style","classic");
        if(!preferences.commit())throw new IOException("无法保存插件版本");
        publish(factory);
        return new JSONObject().put("ok",true).put("version",factory.version).put("sha256",sha).toString();
    }
    static String sourceUrl(Context context) {
        return context.getSharedPreferences("radio_ui",0).getString("url",DEFAULT_URL);
    }
    static String configure(Context context, JSONObject config) throws Exception {
        synchronized(INSTALL_LOCK){return configureLocked(context,config);}
    }
    static String style(Context context) {
        return "optical".equals(context.getSharedPreferences("radio_ui",0).getString("style","classic")) ? "optical" : "classic";
    }
    private static String configureLocked(Context context, JSONObject config) throws Exception {
        String style=config.optString("style",style(context));
        if(!("classic".equals(style)||"optical".equals(style)))throw new IOException("电台界面样式无效");
        Factory current=active;
        if(config.has("style") && current==null) {
            String sha=context.getSharedPreferences("radio_ui",0).getString("active","");
            if(sha.matches("[a-f0-9]{64}"))current=load(context,new File(root(context),sha+".jar"),sha);
        }
        if(config.has("style")&&"optical".equals(style)&&(current==null||!current.supportsOptical))
            throw new IOException("请先导入支持柔光唱片的电台界面插件");
        String url=config.optString("url",sourceUrl(context)).trim();
        if(url.length()==0)url=DEFAULT_URL;
        java.net.URL parsed=new java.net.URL(url);
        if(!("http".equalsIgnoreCase(parsed.getProtocol()) || "https".equalsIgnoreCase(parsed.getProtocol()))
                || parsed.getHost().length()==0 || parsed.getUserInfo()!=null || url.length()>4096)
            throw new IOException("请输入有效的 HTTP/HTTPS 界面地址");
        android.content.SharedPreferences.Editor editor=context.getSharedPreferences("radio_ui",0).edit().putString("url",url);
        if(config.has("style"))editor.putString("style",style);
        if(!editor.commit())
            throw new IOException("无法保存界面地址");
        if(config.has("style")&&current!=null)publish(current);
        if(config.optBoolean("download",false))return installUrl(context,url);
        return status(context);
    }
    private static String installUrl(Context context,String url) throws Exception {
        java.net.HttpURLConnection connection=NetworkClient.openComponent(new java.net.URL(url),true);
        try {
            connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            int status=connection.getResponseCode();
            if(status<200 || status>=300)throw new IOException("界面下载 HTTP "+status);
            if(connection.getContentLength()>MAX_BYTES)throw new IOException("JAR 不能超过 2 MB");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()) {
                byte[] block=new byte[8192];int count;
                while((count=input.read(block))!=-1) {
                    if(bytes.size()+count>MAX_BYTES)throw new IOException("JAR 不能超过 2 MB");
                    bytes.write(block,0,count);
                }
            }
            return install(context,bytes.toByteArray());
        } catch(Exception error) {failure=error.toString();throw error;}
        finally {connection.disconnect();}
    }
    static String status(Context context) throws Exception {
        Factory installed=active;
        if(installed==null) {
            synchronized(INSTALL_LOCK) {
                installed=active;
                String sha=context.getSharedPreferences("radio_ui",0).getString("active","");
                if(installed==null&&sha.matches("[a-f0-9]{64}")) {
                    try {installed=load(context,new File(root(context),sha+".jar"),sha);publish(installed);}
                    catch(Exception error){failure=error.toString();}
                }
            }
        }
        JSONArray styles=new JSONArray().put("classic");
        if(installed!=null&&installed.supportsOptical)styles.put("optical");
        return new JSONObject().put("ok",true).put("loaded",active!=null)
                .put("style",style(context)).put("styles",styles)
                .put("url",sourceUrl(context)).put("defaultUrl",DEFAULT_URL)
                .put("version",active==null?0:active.version).put("error",failure)
                .put("sha256",context.getSharedPreferences("radio_ui",0).getString("active","")).toString();
    }
}
