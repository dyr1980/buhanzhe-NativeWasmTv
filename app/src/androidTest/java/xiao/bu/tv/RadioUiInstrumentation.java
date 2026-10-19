package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.FrameLayout;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Exercise the real audio player, downloaded DEX, catalog and offline repair on a device. */
public final class RadioUiInstrumentation extends Instrumentation {
    private Bundle args;private volatile MainActivity activity;private AudioArtworkView view,cold;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void callActivityOnResume(Activity resumed){super.callActivityOnResume(resumed);if(resumed instanceof MainActivity)activity=(MainActivity)resumed;}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void setStatic(Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private Object delegate(AudioArtworkView v)throws Exception{return field(v,"ui");}
    private void choose(String id)throws Exception {RadioUiPlugin.configure(getTargetContext(),new JSONObject().put("id",id));waitForIdleSync();}
    private int downloads()throws Exception {
        java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL(new java.net.URL(args.getString("audio")),"/cache-stats").openConnection();
        connection.setConnectTimeout(3000);connection.setReadTimeout(3000);
        try(InputStream in=connection.getInputStream()){return new JSONObject(new String(RadioUiCatalog.read(in,4096),"UTF-8")).getInt("downloads");}finally{connection.disconnect();}
    }
    private void waitStyle(AudioArtworkView v,boolean optical)throws Exception {
        long until=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<until){waitForIdleSync();Object ui=delegate(v);if(ui!=null&&(Boolean)field(ui,"optical")==optical)return;SystemClock.sleep(30);}
        throw new AssertionError("Selected artwork did not become active: "+optical+" "+RadioUiPlugin.status(getTargetContext()));
    }
    private void screenshot(String name)throws Exception {
        runOnMainSync(()->{try{for(String overlay:new String[]{"channelBar","debugInfoOverlay","networkSpeedOverlay","backPrompt"})((View)field(activity,overlay)).setVisibility(View.GONE);}catch(Exception e){throw new RuntimeException(e);}});
        SystemClock.sleep(150);Bitmap image=getUiAutomation().takeScreenshot();
        if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private void startRadio(String url)throws Exception {
        startFixture(url,true);
    }
    private void startFixture(String url,boolean radio)throws Exception {
        runOnMainSync(()->{try{
            Channel channel=new Channel("1","断流画面测试","",url,null,null).withRadio(radio);
            ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("电台",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
            Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class);start.setAccessible(true);start.invoke(activity,0);
        }catch(Exception error){throw new RuntimeException(error);}});
    }
    private void stoppedArtwork(String message)throws Exception {
        waitForIdleSync();SystemClock.sleep(250);
        check(view.isShown()&&delegate(view)!=null&&((RadioUiPlugin.Factory)field(view,"factory"))!=null,message+": artwork hidden");
        check(!(Boolean)field(view,"playing")&&!(Boolean)field(delegate(view),"playing"),message+": disc still rotating");
        check(view.cover()==null,message+": default disc was not restored");
    }
    private void radioFailures()throws Exception {
        String missing=new java.net.URL(new java.net.URL(args.getString("audio")),"/missing.mp3").toString();
        for(String style:new String[]{"classic","optical"}){
            choose(style);startRadio(missing);
            long until=SystemClock.elapsedRealtime()+12000;
            while((Boolean)field(activity,"loadingActive")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
            check(!(Boolean)field(activity,"loadingActive"),"Unavailable radio did not finish loading");
            stoppedArtwork(style+" unavailable stream");screenshot("radio-missing-"+style);
            audioAvailable(true);startRadio(args.getString("audio")+"?failure="+style);until=SystemClock.elapsedRealtime()+15000;
            while((!(Boolean)field(activity,"prepared")||!(Boolean)field(activity,"audioOnlyPlayback"))&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
            check((Boolean)field(activity,"prepared")&&(Boolean)field(activity,"audioOnlyPlayback"),"Radio did not resume after missing stream");
            check((Boolean)field(view,"playing"),"Recovered radio did not rotate");
            audioAvailable(false);
            runOnMainSync(()->{try{
                IMediaPlayer current=(IMediaPlayer)field(activity,"player");current.pause();
                Method error=tv.danmaku.ijk.media.player.AbstractMediaPlayer.class.getDeclaredMethod("notifyOnError",int.class,int.class);error.setAccessible(true);error.invoke(current,1,-1004);
            }catch(Exception error){throw new RuntimeException(error);}});
            stoppedArtwork(style+" interrupted stream");screenshot("radio-interrupted-"+style);
        }
        audioAvailable(true);
        startFixture(new java.net.URL(new java.net.URL(args.getString("audio")),"/gateway.mp3").toString(),false);
        long fallbackUntil=SystemClock.elapsedRealtime()+15000;
        while((!(Boolean)field(activity,"prepared")||!(Boolean)field(activity,"audioOnlyPlayback"))&&SystemClock.elapsedRealtime()<fallbackUntil)SystemClock.sleep(40);
        check((Boolean)field(activity,"prepared")&&(Boolean)field(activity,"audioOnlyPlayback"),"Audio-looking URL did not recover through normal classification");
        check(field(activity,"directAudioFallbackRequestId").equals(field(activity,"playRequestId")),"Audio gateway did not exercise one-shot fallback");
        startFixture(new java.net.URL(new java.net.URL(args.getString("audio")),"/video.mp4").toString(),false);
        long until=SystemClock.elapsedRealtime()+15000;
        while((!(Boolean)field(activity,"prepared")||!(Boolean)field(activity,"videoRenderingStarted"))&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
        check((Boolean)field(activity,"prepared")&&(Boolean)field(activity,"videoRenderingStarted"),"Video did not start after failed radio");
        check(view.getVisibility()==View.GONE,"Failed radio artwork covered the next video");
    }
    private void audioAvailable(boolean available)throws Exception {
        java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL(new java.net.URL(args.getString("audio")),available?"/unblock-audio":"/block-audio").openConnection();
        try(InputStream in=connection.getInputStream()){check(connection.getResponseCode()==200,"Audio fixture toggle failed");}finally{connection.disconnect();}
    }
    private void startupTimings()throws Exception {
        JSONArray samples=new JSONArray();
        for(boolean radio:new boolean[]{false,true})for(int i=0;i<3;i++){
            String url=args.getString("audio")+"?startup="+SystemClock.elapsedRealtime();
            long started=SystemClock.elapsedRealtime();startFixture(url,radio);
            long until=started+15000;boolean audible=false;
            while(SystemClock.elapsedRealtime()<until){
                IMediaPlayer current=(IMediaPlayer)field(activity,"player");
                if((Boolean)field(activity,"prepared")&&current!=null&&current.isPlaying()&&current.getCurrentPosition()>0){audible=true;break;}
                SystemClock.sleep(10);
            }
            check(audible,"Radio startup timed out");
            samples.put(new JSONObject().put("radio",radio).put("elapsedMs",SystemClock.elapsedRealtime()-started));
        }
        resultTimings=samples.toString();
    }
    private String resultTimings;
    private static void restore(SharedPreferences prefs,Map<String,?> saved){
        SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> v:saved.entrySet()){
            Object o=v.getValue();String k=v.getKey();if(o instanceof String)e.putString(k,(String)o);else if(o instanceof Integer)e.putInt(k,(Integer)o);else if(o instanceof Long)e.putLong(k,(Long)o);else if(o instanceof Boolean)e.putBoolean(k,(Boolean)o);else if(o instanceof Float)e.putFloat(k,(Float)o);else if(o instanceof Set)e.putStringSet(k,(Set<String>)o);
        }e.commit();
    }
    @Override public void onStart(){Bundle result=new Bundle();int code=1;Context c=getTargetContext();
        SharedPreferences prefs=c.getSharedPreferences("radio_ui",0),settings=c.getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=new HashMap<>(prefs.getAll()),savedSettings=new HashMap<>(settings.getAll());ChannelCatalog.Group[] catalog=ChannelCatalog.GROUPS;
        try{
            prefs.edit().clear().putString("url","not-a-valid-legacy-url").putString("style","classic").putLong("catalog_checked_at",System.currentTimeMillis()).commit();
            settings.edit().putBoolean("auto_update_channel_list",false).putBoolean(GithubProxy.ENABLED_PREFERENCE,true).putString(GithubProxy.PREFERENCE,"https://127.0.0.1:39470/").commit();
            NetworkClient.initialize(c);installTestTransport();
            File cache=new File(c.getFilesDir(),"radio-ui");File[] files=cache.listFiles();if(files!=null)for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.jar")){file.setWritable(true);check(file.delete(),"Could not reset download fixture");}
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long launched=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<launched)SystemClock.sleep(30);
            check(activity!=null,"Activity did not resume");
            SystemClock.sleep(2000); // Let the startup catalog finish before injecting the radio fixture.
            runOnMainSync(()->{try{
                Channel channel=new Channel("1","电台界面测试","",args.getString("audio"),null,null);
                ChannelCatalog.GROUPS=new ChannelCatalog.Group[]{new ChannelCatalog.Group("电台",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
                for(String name:new String[]{"currentGroupIndex","currentChannelIndex","currentSourceIndex"}){Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setInt(activity,0);}
                Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class);start.setAccessible(true);start.invoke(activity,0);
            }catch(Exception e){throw new RuntimeException(e);}});
            long until=SystemClock.elapsedRealtime()+15000;
            while(SystemClock.elapsedRealtime()<until&&(!(Boolean)field(activity,"prepared")||!(Boolean)field(activity,"audioOnlyPlayback")))SystemClock.sleep(50);
            check((Boolean)field(activity,"prepared")&&(Boolean)field(activity,"audioOnlyPlayback"),"Audio fixture did not start");
            view=(AudioArtworkView)field(activity,"audioArtwork");IMediaPlayer audio=(IMediaPlayer)field(activity,"player");waitStyle(view,false);
            if("true".equals(args.getString("startupOnly"))){
                startupTimings();result.putString("stream","PASS radio startup samples: "+resultTimings+"\n");
            }else{
            JSONObject nativeMenus=new JSONObject();TvUiChecks.radioPreferences(this,activity,nativeMenus);result.putString("nativeMenus",nativeMenus.toString());
            Bitmap cover=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);cover.eraseColor(0xff42bb66);runOnMainSync(()->{try{((AlbumArtLoader)field(activity,"albumArtLoader")).clear();}catch(Exception e){throw new RuntimeException(e);}view.setCover(cover);});
            SystemClock.sleep(4000); // Let any startup toast expire before the artwork screenshots.
            long before=audio.getCurrentPosition();screenshot("radio-select-classic");
            choose("optical");waitStyle(view,true);check(view.cover()==cover,"Selection lost cover");screenshot("radio-select-optical");
            check(field(activity,"player")==audio&&audio.isPlaying(),"Selection replaced or stopped player");
            Object optical=delegate(view);try{choose("unknown");throw new AssertionError("Unknown choice accepted");}catch(IOException expected){}
            check(delegate(view)==optical&&"optical".equals(RadioUiPlugin.style(c)),"Failed choice replaced artwork");
            runOnMainSync(()->view.previewSlide(-45,true,"neighbor",cover));choose("classic");check(delegate(view)==optical,"Replaced during gesture");
            choose("optical");runOnMainSync(()->view.restoreSlide());waitStyle(view,true);
            choose("classic");waitStyle(view,false);choose("optical");waitStyle(view,true);
            // A future catalog ID is a choice, not a hardcoded host enum. Reuse cached bytes for offline verification.
            GithubProxy.setBaseUrl("https://127.0.0.1:39470/");GithubProxy.setEnabled(false);GithubProxy.setEnabled(true);
            RadioUiCatalog.refresh(c,true);until=SystemClock.elapsedRealtime()+10000;while(RadioUiCatalog.refreshing()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);
            check(!RadioUiCatalog.refreshing()&&RadioUiCatalog.failure().isEmpty(),"Remote catalog refresh failed: "+RadioUiCatalog.failure()+" routes="+Arrays.toString(GithubProxy.candidates(RadioUiCatalog.URL)));
            JSONObject remote=RadioUiCatalog.find(c,"remote-optical").json();
            check(new JSONObject(RadioUiPlugin.status(c)).getJSONArray("choices").length()==3,"Remote choice missing");
            choose("remote-optical");waitStyle(view,true);check("remote-optical".equals(new JSONObject(RadioUiPlugin.status(c)).getString("selected")),"Remote choice not selected");
            List<RadioUiCatalog.Entry> valid=new ArrayList<>(RadioUiCatalog.choices(c)),bad=new ArrayList<>(valid);
            String zeros="0000000000000000000000000000000000000000000000000000000000000000";
            bad.add(new RadioUiCatalog.Entry(new JSONObject(remote.toString()).put("id","bad-download").put("url","https://127.0.0.1:39470/fail.jar").put("sha256",zeros)));
            bad.add(new RadioUiCatalog.Entry(new JSONObject(remote.toString()).put("id","bad-hash").put("url","https://127.0.0.1:39470/radio-ui2.jar").put("sha256",zeros)));
            setStatic(RadioUiCatalog.class,"entries",bad);Object retained=delegate(view);
            for(String id:new String[]{"bad-download","bad-hash"}){try{choose(id);throw new AssertionError("Invalid download accepted");}catch(IOException expected){check(expected.getMessage().contains(id.equals("bad-download")?"503":"校验"),"Unexpected download failure: "+expected);}check(delegate(view)==retained&&"remote-optical".equals(prefs.getString("selected","")),"Failed download changed selection");}
            // A changed remote descriptor must not force a JAR download on cold startup.
            List<RadioUiCatalog.Entry> changed=new ArrayList<>(valid);
            for(int i=0;i<changed.size();i++)if(changed.get(i).id.equals("remote-optical"))changed.set(i,new RadioUiCatalog.Entry(new JSONObject(remote.toString()).put("sha256",zeros).put("url","https://127.0.0.1:39470/fail.jar")));
            setStatic(RadioUiCatalog.class,"entries",changed);setStatic(RadioUiPlugin.class,"active",null);((Map<?,?>)staticField(RadioUiPlugin.class,"factories")).clear();
            runOnMainSync(()->RadioUiPlugin.request(view));until=SystemClock.elapsedRealtime()+6000;
            while(staticField(RadioUiPlugin.class,"active")==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);
            check(staticField(RadioUiPlugin.class,"active")!=null&&remote.getString("sha256").equals(prefs.getString("active","")),"Catalog update replaced working cached selection");
            int cachedDownloads=downloads();
            choose("classic");waitStyle(view,false);
            // Forget loaded classes as on a fresh process; the changed catalogue
            // points at a failing URL, so the per-style disk record is required.
            setStatic(RadioUiPlugin.class,"active",null);((Map<?,?>)staticField(RadioUiPlugin.class,"factories")).clear();
            choose("remote-optical");waitStyle(view,true);
            choose("classic");waitStyle(view,false);choose("remote-optical");waitStyle(view,true);
            check(downloads()==cachedDownloads,"Cached style switches made plugin download requests");
            setStatic(RadioUiCatalog.class,"entries",valid);

            // Missing/invalid init is retryable, rather than leaving requested=true forever.
            setStatic(RadioUiPlugin.class,"active",null);setStatic(RadioUiPlugin.class,"requested",false);prefs.edit().putString("selected","missing").remove("selected_entry").commit();
            runOnMainSync(()->{cold=new AudioArtworkView(activity,null);((FrameLayout)activity.getWindow().getDecorView()).addView(cold,new FrameLayout.LayoutParams(-1,-1));cold.show("初始化失败恢复测试",true,cover);});
            until=SystemClock.elapsedRealtime()+3000;while((Boolean)staticField(RadioUiPlugin.class,"requested")&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);
            check(!(Boolean)staticField(RadioUiPlugin.class,"requested"),"Initialization failure stuck loading flag");choose("optical");waitStyle(cold,true);
            // Corrupt the cached file and forget class loaders, as after a cold process start.
            String sha=prefs.getString("active","");File jar=new File(new File(c.getFilesDir(),"radio-ui"),sha+".jar");jar.setWritable(true);try(FileOutputStream out=new FileOutputStream(jar)){out.write(new byte[]{1,2,3});}jar.setReadOnly();
            ((Map<?,?>)staticField(RadioUiPlugin.class,"factories")).clear();setStatic(RadioUiPlugin.class,"active",null);
            GithubProxy.setBaseUrl("https://127.0.0.1:39470/");GithubProxy.setEnabled(false);GithubProxy.setEnabled(true);
            choose("optical");waitStyle(view,true);check(jar.length()>100000,"Download cache repair did not run");
            choose("classic");waitStyle(view,false);
            setStatic(RadioUiPlugin.class,"active",null);((Map<?,?>)staticField(RadioUiPlugin.class,"factories")).clear();choose("optical");waitStyle(view,true);waitStyle(cold,true);
            check(audio.isPlaying()&&audio.getCurrentPosition()>before&&field(activity,"player")==audio,"Audio interrupted by switching or recovery");
            runOnMainSync(()->{cold.setActive(false);cold.clear();((android.view.ViewGroup)cold.getParent()).removeView(cold);});
            radioFailures();
            for(String json:new String[]{"{\"schema\":2,\"styles\":[]}",new JSONObject().put("schema",1).put("styles",new JSONArray().put(remote).put(remote)).toString()})try{RadioUiCatalog.parse(json);throw new AssertionError("Invalid catalog accepted");}catch(IOException expected){}
            result.putString("stream","PASS: real audio player retained; downloaded classic/optical; legacy URL ignored; gesture-deferred and latest selection; invalid choice rollback; future catalog ID; HTTP failure and hash mismatch rollback; cached selection survives catalog update; retryable init; corrupt cache repair; offline reload; cover and audio preserved\n");
            }code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            if(activity!=null)runOnMainSync(()->{if(cold!=null){cold.setActive(false);cold.clear();}if(view!=null)view.setActive(false);activity.finish();});waitForIdleSync();
            ChannelCatalog.GROUPS=catalog;restore(prefs,saved);restore(settings,savedSettings);
        }finish(code,result);
    }
    private void installTestTransport()throws Exception {
        java.security.cert.Certificate certificate;try(InputStream in=new FileInputStream(args.getString("cert"))){certificate=java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        java.security.KeyStore store=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());store.load(null);store.setCertificateEntry("fixture",certificate);
        javax.net.ssl.TrustManagerFactory managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());managers.init(store);
        javax.net.ssl.X509TrustManager trust=(javax.net.ssl.X509TrustManager)managers.getTrustManagers()[0];javax.net.ssl.SSLContext tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,new javax.net.ssl.TrustManager[]{trust},null);
        okhttp3.OkHttpClient.Builder builder=NetworkClient.sharedClient().newBuilder().sslSocketFactory(tls.getSocketFactory(),trust);
        okhttp3.OkHttpClient client=builder.build();
        setStatic(NetworkClient.class,"client",client);setStatic(NetworkClient.class,"factory",new okhttp3.OkUrlFactory(client));GithubProxy.setBaseUrl("https://127.0.0.1:39470/");GithubProxy.setEnabled(true);
    }
    private static Object staticField(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
