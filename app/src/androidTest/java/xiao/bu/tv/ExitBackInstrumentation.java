package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;

/** Real system BACK events must confirm exit instead of dismissing the popup. */
public final class ExitBackInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void set(Object value,String name,Object data)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,data);}
    private static void invoke(Object value,String name)throws Exception{Method m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(value);}
    private interface Task{void run()throws Exception;}
    private void main(Task task)throws Exception{final Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void key(int code){long now=SystemClock.uptimeMillis();check(getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true),"Key down injection failed");check(getUiAutomation().injectInputEvent(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,code,0),true),"Key up injection failed");waitForIdleSync();}
    private void back(){key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(150);}
    @Override public void onStart(){Bundle result=new Bundle();JSONArray rows=new JSONArray();int code=1;
        Context c=getTargetContext();String saved=c.getSharedPreferences("tv_ui",0).getString("selected","builtin");
        try{
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6","ui3"}){
                boolean bridge=rows.length()==6;activity=null;
                // MIUI may block background Activity starts between completed exit cases.
                if(Build.VERSION.SDK_INT>=21){try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -W -n "+c.getPackageName()+"/xiao.bu.tv.MainActivity");FileInputStream input=new FileInputStream(fd.getFileDescriptor())){byte[] bytes=new byte[1024];while(input.read(bytes)>=0){}}}
                else c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                long until=SystemClock.elapsedRealtime()+15000;while((activity==null||!activity.hasWindowFocus())&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
                final MainActivity a=activity;check(a!=null&&a.hasWindowFocus(),"Activity did not obtain input focus");
                TvUiPlugin.select(c,id);waitForIdleSync();SystemClock.sleep(250);
                main(()->{invoke(a,"closeChannelList");invoke(a,"closeManagementPanel");invoke(a,"closeWebSource");
                    VideoControlsOverlay controls=(VideoControlsOverlay)field(a,"videoControlsOverlay");if(controls!=null)controls.hide();
                    ((View)field(a,"backPrompt")).setVisibility(View.GONE);set(a,"lastBackPressedAt",0L);set(a,"catchupProgram",null);((View)field(a,"root")).requestFocus();
                });
                back();TvUiMenu menu=(TvUiMenu)field(a,"tvUiMenu");
                check(!a.isFinishing(),"First BACK exited "+id);
                check("builtin".equals(id)?((View)field(a,"backPrompt")).getVisibility()==View.VISIBLE:menu.visible(),"First BACK did not show exit confirmation "+id);
                if(!"builtin".equals(id)&&!bridge){
                    key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
                    check(menu.visible()&&field(menu,"exitEvents")==null&&!a.isFinishing(),"Exit DPAD settings action failed "+id);
                    back();check(!menu.visible()&&!a.isFinishing(),"Regular menu BACK exited Activity "+id);
                    back();check(menu.visible()&&!a.isFinishing(),"Could not reopen exit confirmation "+id);
                }
                main(()->a.dispatchKeyEvent(new KeyEvent(0,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,3)));
                check(!a.isFinishing(),"Held BACK repeat confirmed exit "+id);
                if(bridge)main(a::onBackPressed);else back();
                until=SystemClock.elapsedRealtime()+8000;while(!a.isDestroyed()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);
                check(a.isFinishing()&&a.isDestroyed(),"Second BACK failed to exit "+id);
                check(field(a,"player")==null,"Player retained after exit "+id);
                rows.put(new JSONObject().put("theme",id).put("bridgeBack",bridge).put("firstBackShowsConfirmation",true).put("heldBackIgnored",true).put("secondBackExits",true).put("playerReleased",true));
            }
            result.putString("stream","PASS: six themes, real first/second BACK, held-key repeat, DPAD settings, ordinary menu BACK and web-control BACK\n");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{
            try{File out=new File(c.getExternalFilesDir(null),"exit-back-results.json");try(FileOutputStream stream=new FileOutputStream(out)){stream.write(rows.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            if(activity!=null&&!activity.isFinishing())try{main(()->activity.finish());}catch(Exception ignored){}
            try{TvUiPlugin.select(c,saved);}catch(Exception ignored){}
        }
        finish(code,result);
    }
}
