package xiao.bu.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.*;
import xiao.bu.tv.skin.TvUi;

/** Built-in theme selection. No theme files, dynamic DEX loading or network access. */
final class TvUiPlugin {
    interface Listener { void onTvUiChanged(); }
    static final String[] IDS={"builtin","ui2","ui3","ui4","ui5","ui6"};
    static final String[] NAMES={"原版界面","青色圆角","蓝色卡片","简洁侧栏","清透极简","银雾玻璃"};
    private static final Object lock=new Object();
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,TvUiStyle> cached=new HashMap<>();
    private static WeakReference<Listener> listener=new WeakReference<>(null);
    private static volatile TvUiStyle active;
    static TvUi provider(){TvUiStyle s=active;return s==null?null:s.provider;}
    private static volatile String error="";
    private static volatile int revision;
    private TvUiPlugin(){}
    static TvUiStyle current(){return active;}
    static int revision(){return revision;}
    static String failure(){return error;}
    private static boolean valid(String id){return "builtin".equals(id)||"ui2".equals(id)||"ui3".equals(id)||"ui4".equals(id)||"ui5".equals(id)||"ui6".equals(id);}
    static void attach(Context c,Listener value){
        listener=new WeakReference<>(value);
        final Context app=c.getApplicationContext();
        String id=app.getSharedPreferences("tv_ui",0).getString("selected","builtin");
        if(!valid(id)){id="builtin";app.getSharedPreferences("tv_ui",0).edit().putString("selected",id).apply();}
        if("builtin".equals(id)&&active==null)return;
        if(active!=null&&active.id.equals(id)){notifyChanged();return;}
        selectAsync(app,id);
    }
    static void rejectRenderer(Context c,Throwable cause){
        active=null;error="界面运行失败，已恢复原版："+cause.getClass().getSimpleName();revision++;
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
        synchronized(lock){
            if(!valid(id))throw new IOException("未知电视界面");
            TvUiStyle next=null;
            if(!"builtin".equals(id)) {
                next=cached.get(id);
                if(next==null){next=new TvUiStyle(id,new BuiltinTheme(id.charAt(2)-'0'));cached.put(id,next);}
            }
            if(!c.getSharedPreferences("tv_ui",0).edit().putString("selected",id).commit())
                throw new IOException("无法保存界面选择");
            active=next;error="";revision++;notifyChanged();return status(c);
        }
    }
    private static final class BuiltinTheme extends xiao.bu.tv.skin.SharedTvUi {
        private final int number;
        BuiltinTheme(int number){this.number=number;}
        protected int theme(){return number;}
    }
    static String manage(Context c,byte[] body)throws Exception {
        if(body==null)return status(c);
        return select(c,new JSONObject(new String(body,"UTF-8")).getString("id"));
    }
    private static String message(Exception e){return e.getMessage()==null?"界面加载失败":e.getMessage();}
    static String status(Context c)throws JSONException {
        JSONArray choices=new JSONArray();for(int i=0;i<IDS.length;i++)choices.put(new JSONObject().put("id",IDS[i]).put("name",NAMES[i]));
        return new JSONObject().put("ok",true).put("selected",active==null?"builtin":active.id)
                .put("choices",choices).put("error",error).toString();
    }
}
