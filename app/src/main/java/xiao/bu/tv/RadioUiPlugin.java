package xiao.bu.tv;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.*;
import dalvik.system.DexClassLoader;
import xiao.bu.tv.radio.RadioUi;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Cached, immutable DEX JARs; selecting artwork never replaces the audio player. */
final class RadioUiPlugin {
    static final int MAX_BYTES=2*1024*1024;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static void execute(Runnable task){worker.execute(task);}
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final List<WeakReference<AudioArtworkView>> views=new ArrayList<>();
    private static final Map<String,Factory> factories=new HashMap<>();
    private static final Object INSTALL_LOCK=new Object();
    private static volatile Factory active;
    private static boolean requested;
    private static volatile long retryAfter;
    private static volatile String failure="";
    static final class Factory {
        final ClassLoader loader; final Constructor<?> constructor; final String sha; final int version;
        final Set<String> styles; final Set<String> validatedStyles=new HashSet<>();
        Factory(ClassLoader loader,Constructor<?> constructor,String sha,int version,Set<String> styles){
            this.loader=loader;this.constructor=constructor;this.sha=sha;this.version=version;this.styles=styles;
        }
        RadioUi create(Context c)throws Exception{return (RadioUi)constructor.newInstance(c,loader);}
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("radio_ui",0);}
    private static File root(Context c){return new File(c.getFilesDir(),"radio-ui");}
    static String style(Context c){return prefs(c).getString("style","classic");}
    private static String selected(Context c){return prefs(c).getString("selected",style(c));}
    static synchronized void request(AudioArtworkView view){
        boolean known=false;
        for(Iterator<WeakReference<AudioArtworkView>> it=views.iterator();it.hasNext();){AudioArtworkView v=it.next().get();if(v==null)it.remove();else if(v==view)known=true;}
        if(!known)views.add(new WeakReference<>(view));
        if(active!=null){view.replacePlugin(active);return;}
        if(requested||SystemClock.elapsedRealtime()<retryAfter)return;
        requested=true;final Context c=view.getContext().getApplicationContext();
        worker.execute(()->{try{synchronized(INSTALL_LOCK){if(active==null){
            RadioUiCatalog.Entry saved=savedEntry(c);
            // A catalog refresh must not turn a working cached selection into a mandatory update.
            selectLocked(c,saved!=null&&new File(root(c),saved.sha+".jar").isFile()?saved:entry(c,selected(c)));
        }}}
            catch(Exception e){failure=message(e);retryAfter=SystemClock.elapsedRealtime()+60000;}
            finally{synchronized(RadioUiPlugin.class){requested=false;}}});
    }
    private static void publish(final Factory factory){
        active=factory;failure="";retryAfter=0L;
        main.post(()->{synchronized(RadioUiPlugin.class){
            if(active!=factory)return;
            for(Iterator<WeakReference<AudioArtworkView>> it=views.iterator();it.hasNext();){AudioArtworkView v=it.next().get();if(v==null)it.remove();else v.replacePlugin(factory);}
        }});
    }
    static String configure(Context c,JSONObject config)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("请在后台切换电台界面");
        if(config.has("url")||config.has("download"))throw new IOException("请直接选择电台界面，无需填写地址");
        if(config.optBoolean("refresh",false))RadioUiCatalog.refresh(c,true);
        if(config.has("id")||config.has("style")){
            synchronized(INSTALL_LOCK){try{selectLocked(c,config.optString("id",config.optString("style")));}catch(Exception e){failure=message(e);throw e;}}
        }
        return status(c);
    }
    private static RadioUiCatalog.Entry savedEntry(Context c){
        try{RadioUiCatalog.Entry e=new RadioUiCatalog.Entry(new JSONObject(prefs(c).getString("selected_entry","")));return selected(c).equals(e.id)?e:null;}catch(Exception ignored){return null;}
    }
    private static RadioUiCatalog.Entry entry(Context c,String id)throws Exception {
        RadioUiCatalog.Entry e=RadioUiCatalog.find(c,id);
        if(e==null&&id.equals(selected(c)))e=savedEntry(c);
        if(e==null)throw new IOException("此界面不在可选列表中，请刷新列表");return e;
    }
    private static void selectLocked(Context c,String id)throws Exception {
        selectLocked(c,entry(c,id));
    }
    private static void selectLocked(Context c,RadioUiCatalog.Entry e)throws Exception {
        Factory factory=ensure(c,e);
        if(!factory.styles.contains(e.style))throw new IOException("插件不支持所选电台样式");
        if(!factory.validatedStyles.contains(e.style)){validate(c,factory,e.style);factory.validatedStyles.add(e.style);}
        if(!prefs(c).edit().putString("selected",e.id).putString("selected_entry",e.json().toString())
                .putString("style",e.style).putString("active",factory.sha).commit())throw new IOException("无法保存电台界面");
        publish(factory);
    }
    private static Factory ensure(Context c,RadioUiCatalog.Entry e)throws Exception {
        Factory cached=factories.get(e.sha);if(cached!=null)return cached;
        File dir=root(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建界面目录");
        File jar=new File(dir,e.sha+".jar");
        if(jar.isFile()){
            try{Factory f=load(c,jar,e.sha);factories.put(e.sha,f);return f;}
            catch(Exception invalid){if(!jar.delete())throw new IOException("无法修复损坏的界面缓存",invalid);}
        }
        byte[] bytes=RadioUiCatalog.download(e.url,MAX_BYTES,25000);
        if(!e.sha.equals(sha(bytes)))throw new IOException("界面文件校验失败，已保留原界面");
        File temp=File.createTempFile("install-",".tmp",dir);
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
            if(!temp.setReadOnly()||!temp.renameTo(jar))throw new IOException("无法保存电台界面");
        }finally{temp.delete();}
        final Factory f;
        try{f=load(c,jar,e.sha);}catch(Exception invalid){jar.delete();throw invalid;}
        if(factories.size()>=6)factories.clear();factories.put(e.sha,f);return f;
    }
    private static Factory load(Context c,File jar,String sha)throws Exception {
        if(jar.length()==0||jar.length()>MAX_BYTES)throw new IOException("界面文件大小无效");
        try(InputStream in=new FileInputStream(jar)){if(!sha.equals(sha(RadioUiCatalog.read(in,MAX_BYTES))))throw new IOException("界面缓存已损坏");}
        JSONObject metadata;
        try(ZipFile zip=new ZipFile(jar)){
            Set<String> names=new HashSet<>();long total=0;
            for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();){ZipEntry e=it.nextElement();if(!names.add(e.getName())||e.getSize()<0||e.getSize()>4*1024*1024||(total+=e.getSize())>8*1024*1024)throw new IOException("界面文件内容超限");}
            ZipEntry dex=zip.getEntry("classes.dex"),info=zip.getEntry("radio-ui.json");if(dex==null||dex.getSize()==0||info==null)throw new IOException("不是有效的电台界面");
            try(InputStream in=zip.getInputStream(info)){metadata=new JSONObject(new String(RadioUiCatalog.read(in,4096),"UTF-8"));}
        }
        String name=metadata.getString("entry");
        if(metadata.getInt("api")!=1||metadata.getInt("version")<1||metadata.getInt("minSdk")<14||metadata.getInt("minSdk")>Build.VERSION.SDK_INT||!name.startsWith("xiao.bu.radio."))throw new IOException("电台界面版本不兼容");
        if(!jar.setReadOnly())throw new IOException("界面文件必须只读");
        File optimized=new File(root(c),"dex");if(!optimized.isDirectory()&&!optimized.mkdirs())throw new IOException("无法创建界面缓存");
        try{
            ClassLoader loader=new DexClassLoader(jar.getAbsolutePath(),optimized.getAbsolutePath(),null,RadioUi.class.getClassLoader());Class<?> type=loader.loadClass(name);
            if(!RadioUi.class.isAssignableFrom(type))throw new IOException("电台界面接口不兼容");
            Set<String> styles=new HashSet<>();JSONArray list=metadata.optJSONArray("styles");
            if(list==null)styles.add("classic");else for(int i=0;i<list.length();i++)styles.add(list.getString(i));
            return new Factory(loader,type.getConstructor(Context.class,ClassLoader.class),sha,metadata.getInt("version"),styles);
        }catch(LinkageError error){throw new IOException("电台界面无法加载",error);}
    }
    private static void validate(Context c,Factory factory,String style)throws Exception {
        // Probe the requested variant without changing the persisted style beneath the visible UI.
        final SharedPreferences original=prefs(c);
        final SharedPreferences overlay=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if("getString".equals(method.getName())&&"style".equals(args[0]))return style;
            return method.invoke(original,args);
        });
        final Context context=new ContextWrapper(c){@Override public SharedPreferences getSharedPreferences(String name,int mode){return "radio_ui".equals(name)?overlay:super.getSharedPreferences(name,mode);}};
        final CountDownLatch done=new CountDownLatch(1);final Throwable[] failure={null};
        main.post(()->{RadioUi ui=null;try{ui=factory.create(context);ui.show("",true,null);}catch(Throwable e){failure[0]=e;}finally{try{if(ui!=null)ui.dispose();}catch(Throwable e){if(failure[0]==null)failure[0]=e;}finally{done.countDown();}}});
        if(!done.await(10,TimeUnit.SECONDS))throw new IOException("电台界面初始化超时");
        if(failure[0]!=null)throw new IOException("电台界面初始化失败",failure[0]);
    }
    private static String sha(byte[] bytes)throws Exception {StringBuilder value=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format(Locale.US,"%02x",b&255));return value.toString();}
    private static String message(Exception e){return e.getMessage()==null?"电台界面切换失败":e.getMessage();}
    static String status(Context c)throws Exception {
        RadioUiCatalog.refresh(c,false);
        JSONArray choices=new JSONArray(),styles=new JSONArray();boolean found=false;String selected=selected(c);
        for(RadioUiCatalog.Entry e:RadioUiCatalog.choices(c)){choices.put(new JSONObject().put("id",e.id).put("name",e.name));styles.put(e.id);if(e.id.equals(selected))found=true;}
        if(!found){try{RadioUiCatalog.Entry e=entry(c,selected);choices.put(new JSONObject().put("id",e.id).put("name",e.name));styles.put(e.id);}catch(Exception ignored){}}
        Factory current=active;
        return new JSONObject().put("ok",true).put("selected",selected).put("style",style(c)).put("choices",choices).put("styles",styles)
                .put("loaded",current!=null).put("version",current==null?0:current.version).put("sha256",current==null?"":current.sha)
                .put("error",failure).put("catalogRefreshing",RadioUiCatalog.refreshing()).put("catalogError",RadioUiCatalog.failure()).toString();
    }
}
