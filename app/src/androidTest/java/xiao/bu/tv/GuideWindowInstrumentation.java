package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

/** Actual guide-window bounds, edge coverage and dismissal through settings. */
public final class GuideWindowInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private File out;private View backdrop;private final JSONArray rows=new JSONArray();
    interface Work{void run()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void settle(){waitForIdleSync();SystemClock.sleep(75);}
    private TvUiMenu menu()throws Exception{return (TvUiMenu)get("tvUiMenu");}
    private View content()throws Exception{return ((PopupWindow)field(menu(),"popup")).getContentView();}
    private void open()throws Exception{
        main(()->{Method m=MainActivity.class.getDeclaredMethod("openTvUiMenu");m.setAccessible(true);m.invoke(activity);});settle();
        main(()->{ListView cats=(ListView)field(content(),"categories");cats.setSelection(5);cats.performItemClick(null,5,5);});settle();
        main(()->((ListView)field(content(),"options")).performItemClick(null,5,5));settle();
    }
    public void onStart(){Bundle status=new Bundle();int code=1;String saved="builtin";
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"guide-fullscreen");out.mkdirs();saved=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));long end=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);check(activity!=null,"No Activity");SystemClock.sleep(800);
            main(()->{backdrop=new View(activity);backdrop.setBackgroundColor(0xffff4040);((FrameLayout)get("root")).addView(backdrop,new FrameLayout.LayoutParams(-1,-1));});
            for(String theme:new String[]{"ui2","ui3","ui4","ui5","ui6"}){
                TvUiPlugin.select(c,theme);end=SystemClock.elapsedRealtime()+5000;while((Integer)get("appliedTvUiRevision")!=TvUiPlugin.revision()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);settle();
                for(int close=0;close<3;close++){
                    open();JSONObject row=new JSONObject().put("theme",theme).put("dismiss",new String[]{"back","ok","touch"}[close]).put("sdk",Build.VERSION.SDK_INT);
                    main(()->{View guide=content(),window=activity.getWindow().getDecorView();int[] g=new int[2],w=new int[2];guide.getLocationOnScreen(g);window.getLocationOnScreen(w);
                        check(g[0]==w[0]&&g[1]==w[1]&&guide.getWidth()==window.getWidth()&&guide.getHeight()==window.getHeight(),"Guide does not cover host bounds "+theme+" guide="+g[0]+","+g[1]+","+guide.getWidth()+","+guide.getHeight()+" host="+w[0]+","+w[1]+","+window.getWidth()+","+window.getHeight());
                        row.put("x",g[0]).put("y",g[1]).put("width",guide.getWidth()).put("height",guide.getHeight());
                        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=(WindowManager.LayoutParams)guide.getRootView().getLayoutParams();check(p.layoutInDisplayCutoutMode==activity.getWindow().getAttributes().layoutInDisplayCutoutMode,"Cutout policy not inherited");row.put("cutoutMode",p.layoutInDisplayCutoutMode);}
                    });
                    Bitmap shot=getUiAutomation().takeScreenshot();int left=shot.getPixel(0,shot.getHeight()/2),right=shot.getPixel(shot.getWidth()-1,shot.getHeight()/2);check(left==right&&left!=0xffff4040,"Uncovered guide edge "+theme+" "+Integer.toHexString(left)+" / "+Integer.toHexString(right));row.put("edgeColor",Integer.toHexString(left));
                    if(close==0)try(FileOutputStream f=new FileOutputStream(new File(out,theme+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,f);}finally{shot.recycle();}else shot.recycle();
                    if(close==0)sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);else if(close==1)sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);else{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,300,300,0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,300,300,0);sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();}
                    settle();check(!menu().visible(),"Guide did not dismiss "+theme+" "+close);rows.put(row.put("passed",true));
                }
            }
            status.putString("stream","PASS: 15 guide-window cases, five themes x BACK/OK/touch; full host bounds, both edge pixels covered\n");code=-1;
        }catch(Throwable error){status.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(rows.toString(2).getBytes("UTF-8"));}if(activity!=null)main(()->{menu().dismiss();if(backdrop!=null&&backdrop.getParent()!=null)((ViewGroup)backdrop.getParent()).removeView(backdrop);activity.finish();});TvUiPlugin.select(getTargetContext(),saved);}catch(Exception ignored){}}
        finish(code,status);
    }
}
