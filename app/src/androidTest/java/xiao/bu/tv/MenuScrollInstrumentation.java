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

/** Real ListView touch/DPAD toggles, including the deferred catalog refresh. */
public final class MenuScrollInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private TvUiMenu menu; private File out;
    private final JSONArray results=new JSONArray();
    interface Work { void run() throws Exception; }
    private void main(Work w)throws Exception { Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]); }
    private static Object field(Object o,String n)throws Exception { Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o); }
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private View content()throws Exception{return ((PopupWindow)field(menu,"popup")).getContentView();}
    private ListView list(boolean categories)throws Exception{return (ListView)field(content(),categories?"categories":"options");}
    private void key(int code){if(Build.VERSION.SDK_INT>=18){sendKeyDownUpSync(code);return;}try{main(()->{content().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));content().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));});}catch(Exception e){throw new AssertionError(e);}}
    private void settle(){waitForIdleSync();SystemClock.sleep(100);}
    private void shot(String name)throws Exception{Bitmap b;if(Build.VERSION.SDK_INT>=18)b=getUiAutomation().takeScreenshot();else{final Bitmap[] drawn={null};main(()->{View v=content();drawn[0]=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new android.graphics.Canvas(drawn[0]));});b=drawn[0];}try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private final class Host implements TvUiSettingsMenu.Host {
        final boolean[] visible;int last=-1,actions;
        Host(int count){visible=new boolean[count];java.util.Arrays.fill(visible,true);}
        public Bundle state(){Bundle b=new Bundle();b.putInt("sources",2);return b;}
        public Bundle groups(){Bundle b=new Bundle();String[] ids=new String[visible.length],names=new String[visible.length];for(int i=0;i<ids.length;i++){ids[i]=i==0?"favorites":"group:"+i;names[i]=i==0?"我的收藏":"频道分组 "+i;}b.putStringArray("ids",ids);b.putStringArray("names",names);b.putBooleanArray("visible",visible.clone());return b;}
        public void group(String id,boolean show){last=id.equals("favorites")?0:Integer.parseInt(id.substring(6));visible[last]=show;actions++;new Handler().postDelayed(()->menu.refresh(),160);}
        public void setting(String key,Object value){} public void source(int i){} public void navigate(String s){}
    }
    private int[] position(int target)throws Exception {ListView l=list(false);View first=l.getChildAt(0),row=l.getChildAt(target-l.getFirstVisiblePosition());check(row!=null,"Target not visible: count="+l.getCount()+" first="+l.getFirstVisiblePosition()+" children="+l.getChildCount()+" category="+field(content(),"category"));return new int[]{l.getFirstVisiblePosition(),first.getTop(),row.getTop(),l.getSelectedItemPosition()};}
    private void verify(int[] before,int target,boolean remote,String where)throws Exception {
        int[] after=position(target);check(before[0]==after[0]&&Math.abs(before[1]-after[1])<=1&&Math.abs(before[2]-after[2])<=1,"Menu jumped "+where+" before="+java.util.Arrays.toString(before)+" after="+java.util.Arrays.toString(after));
        if(remote)check(list(false).hasFocus()&&after[3]==target,"Remote focus lost "+where);
    }
    public void onStart(){Bundle status=new Bundle();int code=1;String saved="builtin";String savedScale=getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).getString("ui_scale_mode","standard");getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).edit().putString("ui_scale_mode","standard").commit();
        try{
            Context c=getTargetContext();saved=new JSONObject(TvUiPlugin.status(c)).getString("selected");out=new File(c.getExternalFilesDir(null),"menu-scroll");out.mkdirs();
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));long end=SystemClock.elapsedRealtime()+15000;while(activity==null&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);check(activity!=null,"No activity");SystemClock.sleep(700);menu=(TvUiMenu)field(activity,"tvUiMenu");
            for(String theme:new String[]{"ui2","ui3","ui4","ui5","ui6"}){
                TvUiPlugin.select(c,theme);end=SystemClock.elapsedRealtime()+5000;while((Integer)field(activity,"appliedTvUiRevision")!=TvUiPlugin.revision()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);settle();
                for(int count:new int[]{6,32})for(boolean remote:new boolean[]{false,true}){
                    Host host=new Host(count);TvUiSettingsMenu model=new TvUiSettingsMenu(host,menu);
                    main(()->menu.show((View)field(activity,"root"),"menu",model.initial(),model));settle();main(()->{list(true).setSelection(4);list(true).performItemClick(null,4,4);});settle();
                    int target=count==6?3:15;
                    if(remote){key(KeyEvent.KEYCODE_DPAD_DOWN);settle();setInTouchMode(false);main(()->{ListView l=list(false);l.requestFocus();l.setSelectionFromTop(count==6?0:12,count==6?l.getPaddingTop():-31);});}
                    else{setInTouchMode(true);main(()->list(false).setSelectionFromTop(count==6?0:12,count==6?list(false).getPaddingTop():-31));}
                    settle();if(Build.VERSION.SDK_INT<=15&&count==32){
                        if(remote){main(()->{list(false).requestFocus();list(false).setSelection(0);});settle();for(int i=0;i<target;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);settle();}}
                        else{main(()->list(false).smoothScrollToPosition(target));SystemClock.sleep(600);settle();}
                    }else if(remote){for(int i=0;i<3;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);settle();}}final int[][] before={null};main(()->{before[0]=position(target);});
                    String name=theme+"-"+count+(remote?"-remote":"-touch");if(count==32&&!remote)shot(name+"-before");
                    for(int repeat=0;repeat<2;repeat++){
                        if(remote)key(KeyEvent.KEYCODE_DPAD_CENTER);
                        else{final int[] xy=new int[2];main(()->{ListView l=list(false);View v=l.getChildAt(target-l.getFirstVisiblePosition());v.getLocationOnScreen(xy);xy[0]+=v.getWidth()/2;xy[1]+=v.getHeight()/2;});long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,xy[0],xy[1],0);if(Build.VERSION.SDK_INT<18)main(()->list(false).performItemClick(list(false).getChildAt(target-list(false).getFirstVisiblePosition()),target,target));else{sendPointerSync(down);sendPointerSync(up);}down.recycle();up.recycle();}
                        settle();main(()->verify(before[0],target,remote,name+" immediate"));SystemClock.sleep(230);settle();main(()->verify(before[0],target,remote,name+" deferred"));
                        check(host.actions==repeat+1&&host.last==target&&host.visible[target]==(repeat==1),"Wrong group/checked state "+name+" repeat="+repeat+" actions="+host.actions+" last="+host.last+" visible="+host.visible[target]);main(()->check(list(false).getAdapter().getItem(target).toString().endsWith(host.visible[target]?" ●":" ○"),"Stale toggle"));
                    }
                    if(count==32&&!remote)shot(name+"-after");
                    if(remote){key(KeyEvent.KEYCODE_DPAD_DOWN);settle();main(()->check(list(false).getSelectedItemPosition()==target+1,"DOWN stopped after toggle"));}
                    main(()->{ListView cats=list(true);cats.requestFocus();cats.setSelection(1);cats.performItemClick(null,1,1);});settle();main(()->check(list(false).getCount()==5,"Submenu did not reset"));
                    results.put(new JSONObject().put("theme",theme).put("count",count).put("input",remote?"remote":"touch").put("first",before[0][0]).put("top",before[0][1]).put("targetTop",before[0][2]).put("toggles",2).put("deferredRefresh",true));main(()->menu.dismiss());
                }
            }
            status.putString("stream","PASS: 20 menu cases, five themes, short/scrolling groups, touch/remote double toggle, immediate/deferred refresh, stable viewport/focus and next DPAD/submenu\n");code=-1;
        }catch(Throwable e){status.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(results.toString(2).getBytes("UTF-8"));}if(menu!=null)main(()->menu.dismiss());TvUiPlugin.select(getTargetContext(),saved);if(activity!=null)main(()->activity.finish());}catch(Exception ignored){}}
        getTargetContext().getSharedPreferences(MainActivity.PREFERENCES,0).edit().putString("ui_scale_mode",savedScale).commit();
        finish(code,status);
    }
}
