package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.Map;

/** Opt-in cold startup probe; temporarily expire URL caches, preserving app data. */
public final class ColdStartupDiagnosisInstrumentation extends Instrumentation {
    private Bundle args;
    private MainActivity activity;
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    private Object field(String name) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    private void restore(SharedPreferences prefs, Map<String,?> values) {
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(Map.Entry<String,?> e:values.entrySet()) {
            String k=e.getKey(); Object v=e.getValue();
            if(v instanceof String)edit.putString(k,(String)v);
            else if(v instanceof Long)edit.putLong(k,(Long)v);
            else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
            else if(v instanceof Float)edit.putFloat(k,(Float)v);
            else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);
        }
        edit.commit();
    }
    @Override public void onStart() {
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder();
        SharedPreferences ysp=getTargetContext().getSharedPreferences("yangshipin_resolver",0);
        SharedPreferences cjs=getTargetContext().getSharedPreferences("cjs-result-cache",0);
        Map<String,?> savedYsp=ysp.getAll(), savedCjs=cjs.getAll();
        boolean uncached="true".equals(args.getString("uncached"));
        try {
            CjsPluginRuntime.initialize(getTargetContext());
            report.append("components=").append(CjsPluginRuntime.statusJson()).append('\n');
            for(String id:new String[]{"tv.cctv.com","yangshipin.cn"}) {
                java.io.File site=new java.io.File(new java.io.File(new java.io.File(
                        getTargetContext().getFilesDir(),"cjs-sites-v5"),id),CjsPluginRuntime.currentProfile());
                java.io.File[] versions=site.listFiles();
                if(versions!=null)for(java.io.File version:versions) {
                    java.io.File[] files=version.listFiles();
                    if(files!=null)for(java.io.File file:files)
                        report.append("component-file ").append(id).append('/').append(version.getName())
                                .append('/').append(file.getName()).append(" bytes=").append(file.length()).append('\n');
                }
            }
            if(uncached) {
                SharedPreferences.Editor e=ysp.edit();
                for(String k:savedYsp.keySet()) if(k.startsWith("url_")&&k.endsWith("_at"))e.putLong(k,0L);
                e.commit(); e=cjs.edit();
                for(String k:savedCjs.keySet()) if(k.endsWith(":until"))e.putLong(k,0L);
                e.commit();
            }
            report.append("URL cache expired=").append(uncached).append('\n');
            report.append("plugins: ysp=").append(CjsPluginRuntime.isInstalled("yangshipin.cn"))
                    .append(", cctv=").append(CjsPluginRuntime.isInstalled("tv.cctv.com")).append('\n');
            long start=SystemClock.elapsedRealtime();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            report.append("Activity launchedMs=").append(SystemClock.elapsedRealtime()-start).append('\n');
            if ("true".equals(args.getString("recreateSurface"))) {
                final boolean[] opened={false};
                while (!opened[0] && SystemClock.elapsedRealtime()-start<60000) {
                    runOnMainSync(()->{try { opened[0]=field("player")!=null; }
                        catch(Exception e){throw new RuntimeException(e);}});
                    SystemClock.sleep(100);
                }
                if(!opened[0]) throw new AssertionError("Decoder did not open");
                // SurfaceView genuinely loses its Surface when detached. Verify recovery,
                // rather than requiring the retained-TextureView identity from the old output.
                final android.view.View[] child={null};
                runOnMainSync(()->{try {
                    DirectVideoView view=(DirectVideoView)field("videoView");
                    child[0]=view.getChildAt(0);view.removeView(child[0]);
                }catch(Exception e){throw new RuntimeException(e);}});
                SystemClock.sleep(300);
                runOnMainSync(()->{try {
                    ((DirectVideoView)field("videoView")).addView(child[0]);
                }catch(Exception e){throw new RuntimeException(e);}});
                report.append("SurfaceView detached and reattached; waiting for resumed first frame\n");
            }
            final boolean[] ready={false}; final String[] text={""};
            while(SystemClock.elapsedRealtime()-start<90000) {
                SystemClock.sleep(100);
                runOnMainSync(()->{try {
                    ready[0]=Boolean.TRUE.equals(field("videoRenderingStarted"));
                    text[0]=((android.widget.TextView)field("statusText")).getText().toString();
                }catch(Exception e){throw new RuntimeException(e);}});
                if(ready[0])break;
                if(text[0].contains("解析失败")||text[0].contains("解析超时"))break;
            }
            report.append("ready=").append(ready[0]).append(" observedTotalMs=")
                    .append(SystemClock.elapsedRealtime()-start).append(" status=").append(text[0]).append('\n');
        }catch(Throwable e){report.append(android.util.Log.getStackTraceString(e));}
        finally {
            if(uncached){restore(ysp,savedYsp);restore(cjs,savedCjs);}
            if(activity!=null)runOnMainSync(()->activity.finish());
        }
        result.putString("stream",report.toString());finish(-1,result);
    }
}
