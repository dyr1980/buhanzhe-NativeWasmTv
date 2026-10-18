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

/** Menu layout plus the actual HDR shortcut, persistence and player restart. */
public final class MenuCenterHdrInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;private final JSONArray results=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] types,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(activity,values);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(25);check(c.get(),s);}
    private void settle(){waitForIdleSync();SystemClock.sleep(55);}
    private TvUiMenu menu()throws Exception{return (TvUiMenu)get("tvUiMenu");}
    private View content()throws Exception{return ((PopupWindow)field(menu(),"popup")).getContentView();}
    private ListView list(boolean cats)throws Exception{return (ListView)field(content(),cats?"categories":"options");}
    private void category(int n)throws Exception{main(()->{ListView l=list(true);l.setSelection(n);l.performItemClick(null,n,n);});settle();}
    private void open(int n)throws Exception{main(()->call("openTvUiMenu",new Class[]{}));settle();category(n);}
    private JSONObject centered(ListView l)throws Exception{
        int count=l.getCount();check(count>0&&l.getChildCount()>0,"Missing menu rows");int pitch=l.getChildAt(0).getHeight();boolean fits=count*pitch<=l.getHeight();
        JSONObject r=new JSONObject().put("count",count).put("fits",fits);
        if(fits){check(l.getFirstVisiblePosition()==0&&l.getChildCount()==count,"Fitting list scrolled/clipped");int top=l.getChildAt(0).getTop(),bottom=l.getHeight()-l.getChildAt(count-1).getBottom();check(Math.abs(top-bottom)<=1,"Menu not centered: "+top+" / "+bottom);r.put("top",top).put("bottom",bottom);}
        else check(l.getPaddingTop()==0&&l.getPaddingBottom()==0,"Scrollable menu has artificial whitespace");
        return r;
    }
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    public void onStart(){Bundle status=new Bundle();int code=1;String theme="builtin",size="auto",hdr="hardware";ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;
        try{
            Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),"menu-center-hdr");out.mkdirs();theme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(800);size=(String)get("uiScaleMode");hdr=(String)get("hdrMode");
            String base=args.getString("base"),m3u="#EXTM3U\n#EXTINF:-1 group-title=\"验证\",验证视频\n"+base+"/vod.mp4?line=1\n#EXTINF:-1 group-title=\"验证\",验证视频\n"+base+"/vod.mp4?line=2\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] fixture=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=fixture;set("currentGroupIndex",0);set("autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No frame");
            for(String preset:new String[]{"auto","small","large"}){
                call("handleWebSettings",new Class[]{JSONObject.class},new JSONObject().put("uiScaleMode",preset));settle();
                for(String id:new String[]{"ui2","ui3","ui4","ui5","ui6"}){
                    TvUiPlugin.select(c,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme not applied");open(0);
                    for(int n=0;n<8;n++){
                        category(n);final int cat=n;main(()->results.put(new JSONObject().put("theme",id).put("preset",preset).put("category",cat).put("categories",centered(list(true))).put("options",centered(list(false)))));
                        main(()->list(false).setSelection(list(false).getCount()-1));settle();main(()->centered(list(false)));
                        if(preset.equals("auto")&&(n==2||n==5))shot(id+"-"+(n==2?"decode":"display"));
                    }
                    category(5);main(()->list(false).performItemClick(null,4,4));settle();
                    main(()->{check(list(true).getCount()==1&&list(true).getAdapter().getItem(0).equals("HDR 模式"),"HDR shortcut opened wrong menu");check(list(false).getCount()==4,"HDR modes missing");centered(list(true));centered(list(false));});
                    if(preset.equals("auto"))shot(id+"-hdr");
                    if(id.equals("ui3")&&preset.equals("auto")){
                        String[] modes={"mapping","hardware_sdr","bt709","hardware"};int[] positions={1,2,3,0};
                        for(int i=0;i<modes.length;i++){final String mode=modes[i];final int index=positions[i];Object player=get("player");main(()->list(false).performItemClick(null,index,index));
                            until(()->mode.equals(get("hdrMode"))&&get("player")!=player&&(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"HDR mode did not restart/render: "+mode);settle();
                            main(()->{check(c.getSharedPreferences(MainActivity.PREFERENCES,0).getString("hdr_mode","").equals(mode),"HDR mode not saved");check((Integer)field(list(false).getAdapter(),"checked")==index,"HDR radio state stale");centered(list(false));});
                            results.put(new JSONObject().put("hdrMode",mode).put("persisted",true).put("rendered",true));
                        }
                        Object player=get("player");main(()->list(false).performItemClick(null,0,0));SystemClock.sleep(200);check(get("player")==player,"Reselecting active HDR mode restarted player");
                    }
                    main(()->menu().dismiss());
                }
            }
            status.putString("stream","PASS: all eight menus x five themes x three presets vertically centered; scrollable rows retained; HDR shortcut/choices centered; four actual HDR changes saved and rendered, same-mode selection retained player\n");code=-1;
        }catch(Throwable error){status.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(results.toString(2).getBytes("UTF-8"));}if(activity!=null){main(()->menu().dismiss());call("handleWebSettings",new Class[]{JSONObject.class},new JSONObject().put("uiScaleMode",size).put("hdrMode",hdr));main(()->activity.finish());}TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;}
        finish(code,status);
    }
}
