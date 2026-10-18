package xiao.bu.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.zip.*;
import org.json.*;
import xiao.bu.tv.skin.TvUi;

/** Optional offline DEX JARs. No default-UI downloads, player work or frame callbacks. */
final class TvUiPlugin {
    interface Listener { void onTvUiChanged(); }
    static final int MAX_BYTES=512*1024;
    static final String[] IDS={"builtin","ui2","ui3","ui4","ui5","ui6"};
    static final String[] NAMES={"原版界面","青色圆角","蓝色卡片","简洁侧栏","清透极简","影院红黑"};
    private static final Object lock=new Object();
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,TvUiStyle> cached=new HashMap<>();
    private static WeakReference<Listener> listener=new WeakReference<>(null);
    private static volatile TvUiStyle active;
    private static volatile TvUi builtin;
    static TvUi provider(){TvUiStyle s=active;return s==null?builtin:s.provider;}
    private static volatile String error="";
    private static volatile int revision;
    private static String lastRequestedId;
    private TvUiPlugin(){}
    static TvUiStyle current(){return active;}
    static int revision(){return revision;}
    static String failure(){return error;}
    private static boolean valid(String id){return "builtin".equals(id)||"ui2".equals(id)||"ui3".equals(id)||"ui4".equals(id)||"ui5".equals(id)||"ui6".equals(id);}
    private static File root(Context c){return new File(c.getFilesDir(),"tv-ui");}
    static void attach(Context c,Listener value){
        listener=new WeakReference<>(value);
        final Context app=c.getApplicationContext();
        String id=app.getSharedPreferences("tv_ui",0).getString("selected","builtin");
        if("builtin".equals(id)&&active==null&&builtin!=null)return;
        if(active!=null&&active.id.equals(id)){notifyChanged();return;}
        if(id.equals(lastRequestedId))return;
        lastRequestedId=id;
        selectAsync(app,id);
    }
    static void rejectRenderer(Context c,Throwable cause){
        if(active==null)builtin=null;
        active=null;error="界面插件运行失败，已恢复原版："+cause.getClass().getSimpleName();revision++;
        c.getSharedPreferences("tv_ui",0).edit().putString("selected","builtin").apply();notifyChanged();
    }
    static void detach(Listener value){if(listener.get()==value)listener.clear();}
    private static void notifyChanged(){main.post(()->{Listener value=listener.get();if(value!=null)value.onTvUiChanged();});}
    static void execute(Runnable action){worker.execute(action);}
    static void selectAsync(Context c,String id){
        final Context app=c.getApplicationContext();
        worker.execute(()->{try{select(app,id);}catch(Exception e){error=message(e);notifyChanged();}});
    }
    static String select(Context c,String id)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("请在后台加载界面");
        synchronized(lock){
            if(!valid(id))throw new IOException("未知电视界面");
            TvUiStyle next=null;
            if(valid(id)){
                android.content.SharedPreferences prefs=c.getSharedPreferences("tv_ui",0);
                String sha=prefs.getString("api2_"+id,"");
                long stamp=new File(c.getApplicationInfo().sourceDir).lastModified();
                if(!sha.matches("[a-f0-9]{64}")||!prefs.getBoolean("imported_"+id,false)&&prefs.getLong("bundled_"+id,-1)!=stamp){
                    try(InputStream in=c.getAssets().open("tv-ui/"+id+".jar")){sha=save(c,read(in,MAX_BYTES));}
                }
                next=load(c,sha,id);
                if(!c.getSharedPreferences("tv_ui",0).edit().putString("api2_"+id,sha).putLong("bundled_"+id,stamp).commit())throw new IOException("无法保存界面插件");
            }
            if(!c.getSharedPreferences("tv_ui",0).edit().putString("selected",id).commit())throw new IOException("无法保存界面选择");
            if("builtin".equals(id)){builtin=next.provider;next=null;}
            active=next;error="";revision++;notifyChanged();return status(c);
        }
    }
    static String manage(Context c,byte[] body)throws Exception {
        if(body==null)return status(c);
        String savedSha=null;
        try {
            if(body.length>0&&body[0]=='{')return select(c,new JSONObject(new String(body,"UTF-8")).getString("id"));
            if(body.length==0||body.length>MAX_BYTES)throw new IOException("界面 JAR 不能超过 512 KB");
            synchronized(lock){
                String sha=save(c,body);savedSha=sha;
                TvUiStyle style=load(c,sha,null);
                if(!c.getSharedPreferences("tv_ui",0).edit().putString("api2_"+style.id,sha).putBoolean("imported_"+style.id,true).commit())throw new IOException("无法保存界面插件");
                if("builtin".equals(style.id)&&active==null){builtin=style.provider;revision++;notifyChanged();}
                else if(active!=null&&active.id.equals(style.id)){active=style;revision++;notifyChanged();}
                return status(c);
            }
        }catch(Exception e){
            if(savedSha!=null){
                synchronized(lock){
                    boolean used=false;for(String id:IDS)if(savedSha.equals(c.getSharedPreferences("tv_ui",0).getString("api2_"+id,"")))used=true;
                    if(!used){cached.remove(savedSha);new File(root(c),savedSha+".jar").delete();}
                }
            }
            error=message(e);throw e;
        }
    }
    private static String save(Context c,byte[] bytes)throws Exception {
        String sha=sha(bytes);File dir=root(c);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建界面目录");
        File jar=new File(dir,sha+".jar");
        if(jar.exists())return sha;
        File temp=File.createTempFile("install-",".tmp",dir);
        try {
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
            if(!temp.setReadOnly()||!temp.renameTo(jar))throw new IOException("无法保存界面文件");
        }finally{temp.delete();}
        return sha;
    }
    private static TvUiStyle load(Context c,String sha,String expected)throws Exception {
        TvUiStyle old=cached.get(sha);
        if(old!=null){if(expected!=null&&!expected.equals(old.id))throw new IOException("界面编号不匹配");return old;}
        File jar=new File(root(c),sha+".jar");
        if(jar.length()==0||jar.length()>MAX_BYTES)throw new IOException("界面插件文件无效");
        try(InputStream in=new FileInputStream(jar)){if(!sha.equals(sha(read(in,MAX_BYTES))))throw new IOException("界面插件已损坏");}
        JSONObject meta;
        try(ZipFile zip=new ZipFile(jar)){
            long total=0;Set<String> names=new HashSet<>();
            for(Enumeration<? extends ZipEntry> entries=zip.entries();entries.hasMoreElements();){
                ZipEntry e=entries.nextElement();
                if(!names.add(e.getName())||e.getSize()<0||e.getSize()>2*1024*1024||(total+=e.getSize())>2*1024*1024)
                    throw new IOException("界面插件内容超限");
            }
            ZipEntry info=zip.getEntry("tv-ui.json"),dex=zip.getEntry("classes.dex");
            if(info==null||dex==null||dex.getSize()==0)throw new IOException("不是电视界面 JAR");
            try(InputStream in=zip.getInputStream(info)){meta=new JSONObject(new String(read(in,4096),"UTF-8"));}
        }
        String id=meta.getString("id"),entry=meta.getString("entry");
        if(meta.optInt("sharedRenderer",0)>xiao.bu.tv.skin.SharedTvUi.RENDERER_API||meta.getInt("api")!=2||meta.getInt("version")<1||meta.getInt("minSdk")<14||meta.getInt("minSdk")>android.os.Build.VERSION.SDK_INT
                ||!valid(id)||expected!=null&&!expected.equals(id)||!entry.startsWith("xiao.bu.plugins.tvui."))
            throw new IOException("电视界面版本不兼容");
        File dex=new File(root(c),"dex");if(!dex.isDirectory()&&!dex.mkdirs())throw new IOException("无法创建界面缓存");
        if(!jar.setReadOnly())throw new IOException("界面文件必须只读");
        try {
            ClassLoader loader=new DexClassLoader(jar.getAbsolutePath(),dex.getAbsolutePath(),null,TvUi.class.getClassLoader());
            Class<?> type=loader.loadClass(entry);
            if(!TvUi.class.isAssignableFrom(type))throw new IOException("界面接口不兼容");
            if(java.lang.reflect.Modifier.isAbstract(type.getMethod("create",Context.class,String.class,android.os.Bundle.class,android.view.View[].class,TvUi.Events.class).getModifiers())
                    ||java.lang.reflect.Modifier.isAbstract(type.getMethod("update",android.view.View.class,android.os.Bundle.class).getModifiers()))throw new IOException("界面没有实现 View 接口");
            TvUi provider=(TvUi)type.getConstructor().newInstance();
            TvUiStyle style=new TvUiStyle(id,provider);
            if(cached.size()>=6)cached.clear();cached.put(sha,style);return style;
        }catch(LinkageError e){throw new IOException("界面插件加载失败",e);}
    }
    private static byte[] read(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        while((n=in.read(block))!=-1){if(out.size()+n>limit)throw new IOException("界面文件过大");out.write(block,0,n);}return out.toByteArray();
    }
    private static String sha(byte[] bytes)throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.US,"%02x",b&255));return out.toString();
    }
    private static String message(Exception e){return e.getMessage()==null?"界面加载失败":e.getMessage();}
    static String status(Context c)throws JSONException {
        JSONArray choices=new JSONArray();for(int i=0;i<IDS.length;i++)choices.put(new JSONObject().put("id",IDS[i]).put("name",NAMES[i]));
        return new JSONObject().put("ok",true).put("selected",active==null?"builtin":active.id)
                .put("choices",choices).put("error",error).toString();
    }
}
