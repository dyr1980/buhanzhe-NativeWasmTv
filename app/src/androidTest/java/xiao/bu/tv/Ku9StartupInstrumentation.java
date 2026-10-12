package xiao.bu.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.TextView;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Actual script -> manifest -> advancing first video output, with settings restored. */
public final class Ku9StartupInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Bundle args;
    private Throwable uiError;
    private java.net.ServerSocket catalogBootstrap;
    private final java.util.concurrent.CountDownLatch catalogHold=new java.util.concurrent.CountDownLatch(1);
    private final JSONArray rows=new JSONArray();
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void callActivityOnCreate(Activity target,Bundle state) {
        target.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                |WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                |WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        super.callActivityOnCreate(target,state);
        if(target instanceof MainActivity)try {
            Field generation=MainActivity.class.getDeclaredField("catalogLoadGeneration");generation.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger)generation.get(target)).incrementAndGet();
            if(catalogBootstrap!=null) {
                Field remote=MainActivity.class.getDeclaredField("remoteCatalogUrl");remote.setAccessible(true);
                remote.set(target,"http://127.0.0.1:"+catalogBootstrap.getLocalPort());
            }
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(String name)throws Exception{return field(name).get(activity);}
    private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
    private interface Action{void run()throws Exception;}
    private void main(Action work)throws Exception {
        uiError=null;runOnMainSync(()->{try{work.run();}catch(Throwable error){uiError=error;}});
        if(uiError!=null)throw new Exception(uiError);
    }
    private void start(int index)throws Exception {
        main(()->{Method m=MainActivity.class.getDeclaredMethod("startChannel",int.class);m.setAccessible(true);m.invoke(activity,index);});
    }
    private void play(int index,String name)throws Exception {
        long begin=SystemClock.elapsedRealtime();start(index);
        JSONObject row=new JSONObject().put("channel",name).put("requestId",get("playRequestId"));rows.put(row);
        IMediaPlayer seen=null;long position=0;
        long limit=Long.parseLong(args.getString("caseTimeoutMs","45000"));
        while(SystemClock.elapsedRealtime()-begin<limit) {
            if(activity.isFinishing())throw new InterruptedException("Playback audit cancelled by closing the activity");
            IMediaPlayer current=(IMediaPlayer)get("player");
            boolean radio=args.getString("radioPlaylist")!=null;
            if(current!=null&&(Boolean)get("prepared")&&(radio?(Boolean)get("audioOnlyPlayback"):(Boolean)get("videoRenderingStarted"))
                    &&!(Boolean)get("preparingChannelSwitch")&&(Integer)get("currentChannelIndex")==index
                    &&(Long)get("channelLoadDurationMs")>=0) {
                if(seen!=current){seen=current;position=current.getCurrentPosition();}
                if(current.isPlaying()&&(radio||current.getVideoWidth()>0)&&current.getCurrentPosition()-position>=600) {
                    if(!activity.hasWindowFocus())throw new AssertionError("Playback window lost focus");
                    row.put("firstFrameMs",get("channelLoadDurationMs")).put("confirmedMs",SystemClock.elapsedRealtime()-begin)
                            .put("width",current.getVideoWidth()).put("player",current.getClass().getSimpleName()).put("status","passed");
                    if(radio)row.put("audioConfirmedMs",SystemClock.elapsedRealtime()-begin);
                    long watch=Long.parseLong(args.getString("watchMs","0"));
                    if(watch>0) {
                        long from=current.getCurrentPosition();SystemClock.sleep(watch);
                        if(get("player")!=current||!current.isPlaying()||current.getCurrentPosition()-from<watch/2)
                            throw new AssertionError("Playback stopped during stability watch: "+name);
                        row.put("watchMs",watch).put("watchClockAdvanceMs",current.getCurrentPosition()-from);
                    }
                    Bundle update=new Bundle();update.putString("stream",row.toString()+"\n");sendStatus(0,update);return;
                }
            }
            String status=((TextView)get("statusText")).getText().toString();
            if(SystemClock.elapsedRealtime()-begin>2000&&!(Boolean)get("preparingChannelSwitch")
                    &&((Integer)get("currentChannelIndex")!=index||status.contains("暂不可用")
                    ||status.contains("脚本执行失败")||status.contains("IPv6")||status.contains("分片准备失败")
                    ||status.contains("播放错误")||status.contains("频道缓冲超时"))) {
                row.put("status","unavailable").put("message",status).put("elapsedMs",SystemClock.elapsedRealtime()-begin);
                throw new AssertionError("Source unavailable: "+name+" "+status);
            }
            SystemClock.sleep(100);
        }
        row.put("status","unavailable").put("elapsedMs",SystemClock.elapsedRealtime()-begin);
        throw new AssertionError("No advancing video: "+name);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();int code=-1;
        SharedPreferences prefs=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=prefs.getAll();ChannelCatalog.Group[] savedGroups=ChannelCatalog.GROUPS;
        try {
            catalogBootstrap=new java.net.ServerSocket(0,2,java.net.InetAddress.getByName("127.0.0.1"));
            new Thread(()->{try(java.net.Socket socket=catalogBootstrap.accept()) {
                catalogHold.await(15,java.util.concurrent.TimeUnit.SECONDS);
            }catch(Exception ignored){}},"ku9-audit-bootstrap").start();
            // Prevent an unrelated saved 4K channel from allocating a decoder during
            // this audit. The temporary missing local script never opens a player.
            prefs.edit().putString("last_channel_snapshot_v2",new JSONObject()
                    .put("groupTitle","KU9_AUDIT_BOOTSTRAP").put("catalogSource",ChannelCatalog.SOURCE_CUSTOM)
                    .put("number","1").put("name","KU9_AUDIT_BOOTSTRAP")
                    .put("urls",new JSONArray().put("http://A/ku9/js/__ku9_missing_bootstrap_fixture__.js"))
                    .put("sourceIndex",0).toString()).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1500);
            main(()->{
                ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet();
                set("remoteCatalogUrl","");
            });
            catalogHold.countDown();catalogBootstrap.close();
            if(args.getString("cmgNativeTrial")!=null) {
                CjsPluginRuntime.ensureCatalog();
                if(!CjsPluginRuntime.isInstalled("yangshipin.cn"))CjsPluginRuntime.installOrUpdate("yangshipin.cn");
                Field states=CjsPluginRuntime.class.getDeclaredField("states");states.setAccessible(true);
                Object state=((Map)states.get(null)).get("yangshipin.cn");
                Field loaded=state.getClass().getDeclaredField("loaded");loaded.setAccessible(true);
                if(loaded.getBoolean(state))throw new AssertionError("CMG library already loaded before trial");
                File trial=new File(getTargetContext().getFilesDir(),"cmg-native-trial");trial.mkdirs();
                try(java.io.InputStream input=new java.io.FileInputStream(args.getString("cmgNativeTrial"));
                        FileOutputStream output=new FileOutputStream(new File(trial,"yangshipin.so"))) {
                    byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                }
                Field directory=state.getClass().getDeclaredField("directory");directory.setAccessible(true);directory.set(state,trial);
                CjsPluginRuntime.loadNativeLibrary("yangshipin.cn");
                File trace=new File(getTargetContext().getExternalFilesDir(null),"cmg-crypto-"+args.getString("label","test")+".bin");
                if(!CmgTraceBridge.enable(trace.getAbsolutePath()))throw new AssertionError("Unable to open CMG native trace");
            }
            if(Boolean.parseBoolean(args.getString("checkAudioForwarding","false")))
                result.putString("stream",HlsStartupChecks.radioForwarding(activity));
            if(args.getString("fixtureDir")!=null) {
                result.putString("stream",Ku9StartupChecks.run(activity)
                        +HlsStartupChecks.run(activity,new File(args.getString("fixtureDir"))));
            }
            String base=args.getString("base","https://raw.githubusercontent.com/buhanzhe/webSourceM3U8/main/k-web/ku9/js/");
            JSONArray fullManifest=null;
            String[] cases=args.getString("cases","jlntv.js?id=ds,jlntv.js?id=sh,jlntv.js?id=ds").split(",");
            if(args.getString("manifest")!=null) {
                JSONArray manifest=new JSONArray(Ku9HttpClient.readUtf8(new java.io.FileInputStream(args.getString("manifest")),256*1024));
                if(Boolean.parseBoolean(args.getString("fullManifest","false")))fullManifest=manifest;
                cases=new String[manifest.length()];for(int n=0;n<cases.length;n++)cases[n]=fullManifest==null?manifest.getJSONObject(n).getString("url").split("/ku9/js/",2)[1]:manifest.getJSONObject(n).getString("name");
            }
            if(args.getString("fixtureDir")!=null)cases=new String[0];
            Channel[] radios=null;
            if(args.getString("radioPlaylist")!=null) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                try(java.io.InputStream input=getContext().getAssets().open(args.getString("radioPlaylist"))) {
                    byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))>=0)bytes.write(buffer,0,count);
                }
                Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);
                Channel[] all=((ChannelCatalog.Group[])parse.invoke(null,(Object)bytes.toByteArray()))[0].channels;
                String[] indices=args.getString("indices","0,3,0").split(",");
                radios=new Channel[indices.length];cases=new String[indices.length];
                for(int n=0;n<indices.length;n++){radios[n]=all[Integer.parseInt(indices[n])];cases[n]=radios[n].name;}
            }
            final String[] entries=cases;final Channel[] radioEntries=radios;final JSONArray channelManifest=fullManifest;
            main(()->{
                ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet();
                set("autoSwitchSource",false);set("remoteCatalogUrl","");
                Channel[] channels=radioEntries==null?new Channel[entries.length]:radioEntries;
                if(radioEntries==null)for(int n=0;n<entries.length;n++)channels[n]=new Channel(String.valueOf(n+1),entries[n],"",channelManifest==null?base+entries[n]:channelManifest.getJSONObject(n).getString("url"),null,null);
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("KU9_STARTUP_TEST",ChannelCatalog.SOURCE_CUSTOM,channels)};
                set("currentGroupIndex",0);set("currentSourceIndex",0);
            });
            for(int n=0;n<entries.length;n++) {
                if(activity.isFinishing())throw new InterruptedException("Playback audit cancelled");
                try{if(args.getString("cmgNativeTrial")!=null)CmgTraceBridge.mark(entries[n]);play(n,entries[n]);}
                catch(Exception|AssertionError error) {
                    if(!Boolean.parseBoolean(args.getString("continueFailures","false")))throw error;
                    JSONObject row=rows.getJSONObject(rows.length()-1);row.put("error",error.toString());
                    Bundle update=new Bundle();update.putString("stream",row.toString()+"\n");sendStatus(0,update);
                }
                saveReport();
                if(n+1<entries.length)SystemClock.sleep(Long.parseLong(args.getString("settleMs","0")));
            }
            if(args.getString("fixtureDir")==null)result.putString("stream","COMPLETE Ku9 actual playback audit: "+entries.length+" entries; see per-channel report\n");
        }catch(Throwable error){code=0;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            catalogHold.countDown();try{if(catalogBootstrap!=null)catalogBootstrap.close();}catch(Exception ignored){}
            saveReport();
            if(activity!=null)try{main(()->activity.finish());}catch(Exception ignored){}
            if(args.getString("cmgNativeTrial")!=null) {
                try{CmgTraceBridge.stop();}catch(Throwable ignored){}
                File trial=new File(getTargetContext().getFilesDir(),"cmg-native-trial");
                new File(trial,"yangshipin.so").delete();trial.delete();
            }
            ChannelCatalog.GROUPS=savedGroups;
            SharedPreferences.Editor edit=prefs.edit().clear();
            for(Map.Entry<String,?> item:saved.entrySet()) {
                String key=item.getKey();Object value=item.getValue();
                if(value instanceof String)edit.putString(key,(String)value);
                else if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof Long)edit.putLong(key,(Long)value);
                else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                else if(value instanceof Float)edit.putFloat(key,(Float)value);
                else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);
            }
            edit.commit();
        }
        finish(code,result);
    }
    private void saveReport() {
        try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),
                "ku9-startup-"+args.getString("label","test")+".json"))){output.write(rows.toString(2).getBytes("UTF-8"));}catch(Exception ignored){}
    }
}
