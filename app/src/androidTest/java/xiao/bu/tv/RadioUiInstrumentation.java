package xiao.bu.tv;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.FrameLayout;
import android.os.Bundle;
import android.graphics.*;
import android.view.View;
import android.media.MediaPlayer;
import java.io.*;
import java.lang.reflect.Field;
import org.json.JSONObject;
import java.util.Map;

/** Loads actual API14 DEX JARs, swaps identical class names, and rejects invalid candidates. */
public final class RadioUiInstrumentation extends Instrumentation {
    private Bundle args;
    private AudioArtworkView view;
    private AudioArtworkView cold;
    private Activity activity;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    private static byte[] read(File file)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        return bytes.toByteArray();
    }
    private Object delegate()throws Exception{Field f=AudioArtworkView.class.getDeclaredField("ui");f.setAccessible(true);return f.get(view);}
    private void waitVersion(int version)throws Exception {
        for(int i=0;i<100;i++) {
            waitForIdleSync();
            Field field=AudioArtworkView.class.getDeclaredField("factory");field.setAccessible(true);
            RadioUiPlugin.Factory current=(RadioUiPlugin.Factory)field.get(view);
            if(current!=null&&current.version==version&&delegate()!=null)return;
            Thread.sleep(30);
        }
        throw new AssertionError("Version not active: "+version);
    }
    @Override public void onStart(){Bundle result=new Bundle();int code=1;MediaPlayer audio=null;
        String previous=getTargetContext().getSharedPreferences("radio_ui",0).getString("active","");
        SharedPreferences preferences=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0);
        Map<String,?> saved=new java.util.HashMap<String,Object>(preferences.getAll());
        try {
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Bitmap cover=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);cover.eraseColor(0xff42bb66);
            runOnMainSync(new Runnable(){public void run(){
                try {java.lang.reflect.Method stop=MainActivity.class.getDeclaredMethod("releasePlayer");stop.setAccessible(true);stop.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}
                view=new AudioArtworkView(activity,null);
                ((FrameLayout)activity.getWindow().getDecorView()).addView(view,new FrameLayout.LayoutParams(-1,-1));
                view.show("电台插件测试",true,cover);view.setPlaying(true);
            }});
            String dir=args.getString("fixtures");
            audio=new MediaPlayer();audio.setVolume(0,0);audio.setDataSource(dir+"/audio.wav");audio.setLooping(true);audio.prepare();audio.start();Thread.sleep(200);
            RadioUiPlugin.install(getTargetContext(),read(new File(dir,"radio-ui-v1.jar")));waitVersion(1);
            final Object old=delegate();
            int before=audio.getCurrentPosition();
            runOnMainSync(new Runnable(){public void run(){view.beginChannelSwitch(1);view.showPending("热替换测试", "",cover);}});
            RadioUiPlugin.install(getTargetContext(),read(new File(dir,"radio-ui-v2.jar")));waitVersion(2);
            Object next=delegate();
            if(old==next || old.getClass().getClassLoader()==next.getClass().getClassLoader())throw new AssertionError("ClassLoader was reused");
            Thread.sleep(150);
            if(!audio.isPlaying()||audio.getCurrentPosition()<=before)throw new AssertionError("Audio stopped during UI replacement");
            if(view.cover()!=cover)throw new AssertionError("Cover state lost");
            runOnMainSync(new Runnable(){public void run(){
                view.setVisibility(View.GONE);
                try{if(((xiao.bu.tv.radio.RadioUi)delegate()).view().getVisibility()!=View.GONE)throw new AssertionError("Host visibility not forwarded");}catch(Exception e){throw new RuntimeException(e);}
                view.setVisibility(View.VISIBLE);
            }});
            runOnMainSync(new Runnable(){public void run(){
                view.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));
                view.layout(0,0,960,540);
                Bitmap image=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
                if(image.getPixel(480,225)==0)throw new AssertionError("Plugin did not render");
                try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"radio-ui.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}catch(Exception e){throw new RuntimeException(e);}
            }});
            String active=new JSONObject(RadioUiPlugin.status(getTargetContext())).getString("sha256");
            try{RadioUiPlugin.install(getTargetContext(),new byte[]{1,2,3});throw new AssertionError("Invalid JAR accepted");}catch(IOException expected){}
            if(!active.equals(new JSONObject(RadioUiPlugin.status(getTargetContext())).getString("sha256")) || delegate()!=next)
                throw new AssertionError("Failed install replaced working UI");
            // Forget the process cache, as on a cold start; only the on-disk JAR/pointer remain.
            Field cached=RadioUiPlugin.class.getDeclaredField("active");cached.setAccessible(true);cached.set(null,null);
            Field requested=RadioUiPlugin.class.getDeclaredField("requested");requested.setAccessible(true);requested.setBoolean(null,false);
            runOnMainSync(new Runnable(){public void run(){cold=new AudioArtworkView(activity,null);cold.show("离线恢复",true,cover);}});
            Field content=AudioArtworkView.class.getDeclaredField("ui");content.setAccessible(true);
            for(int i=0;i<100&&content.get(cold)==null;i++){Thread.sleep(30);waitForIdleSync();}
            if(content.get(cold)==null)throw new AssertionError("Cold cached JAR did not load");
            result.putString("stream","PASS: DEX load, v1->v2 hot replacement, cover preservation, rendering, advancing audio, invalid-JAR rollback and offline cold reload\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally {
            if(audio!=null)audio.release();
            if(view!=null)runOnMainSync(new Runnable(){public void run(){view.setActive(false);view.clear();}});
            if(cold!=null)runOnMainSync(new Runnable(){public void run(){cold.setActive(false);cold.clear();}});
            if(activity!=null)runOnMainSync(new Runnable(){public void run(){activity.finish();}});
            waitForIdleSync();
            SharedPreferences.Editor editor=preferences.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()) {
                String k=entry.getKey();Object v=entry.getValue();
                if(v instanceof String)editor.putString(k,(String)v);
                else if(v instanceof Integer)editor.putInt(k,(Integer)v);
                else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
                else if(v instanceof Long)editor.putLong(k,(Long)v);
                else if(v instanceof Float)editor.putFloat(k,(Float)v);
                else if(v instanceof java.util.Set)editor.putStringSet(k,(java.util.Set<String>)v);
            }
            editor.commit();
            getTargetContext().getSharedPreferences("radio_ui",0).edit().putString("active",previous).commit();
        }
        finish(code,result);
    }
}
