package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Record actual Canvas text draws, not the requested sizes before layout. */
public final class ThemeTypographyInstrumentation extends Instrumentation {
    private volatile MainActivity activity;private Bundle args;private File out;private boolean baseline;
    private final JSONArray results=new JSONArray();
    interface Work{void run()throws Exception;}interface Condition{boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private static Object field(Object o,String name)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private Object get(String n)throws Exception{return field(activity,n);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] types,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(activity,values);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void until(Condition c,long ms,String s)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(c.get(),s);}
    private TvUiMenu menu()throws Exception{return (TvUiMenu)get("tvUiMenu");}
    private View content()throws Exception{return ((PopupWindow)field(menu(),"popup")).getContentView();}
    private ListView list(boolean category)throws Exception{return (ListView)field(content(),category?"categories":"options");}
    private void setting(String mode)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("handleWebSettings",JSONObject.class);m.setAccessible(true);m.invoke(activity,new JSONObject().put("uiScaleMode",mode));waitForIdleSync();SystemClock.sleep(100);
    }
    private void select(String id)throws Exception{TvUiPlugin.select(activity,id);until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme callback missing");waitForIdleSync();}
    private static final class TextDraw {
        final String text;final float size,center;final boolean bold,normalFace;
        TextDraw(String t,float x,float y,Paint p,Matrix m){text=t;float[] values=new float[9];m.getValues(values);size=p.getTextSize()*Math.abs(values[Matrix.MSCALE_Y]);float[] point={x,y+(p.ascent()+p.descent())/2};m.mapPoints(point);center=point[1];bold=p.isFakeBoldText()||p.getTypeface()!=null&&p.getTypeface().isBold();normalFace=p.getTypeface()==Typeface.DEFAULT;}
    }
    private static final class TextCanvas extends Canvas {
        final java.util.List<TextDraw> texts=new java.util.ArrayList<>();
        TextCanvas(Bitmap b){super(b);}
        @Override public void drawText(String text,float x,float y,Paint p){texts.add(new TextDraw(text,x,y,p,getMatrix()));super.drawText(text,x,y,p);}
    }
    private static java.util.List<TextDraw> draw(View v){
        check(v.getWidth()>0&&v.getHeight()>0,"Unmeasured view");Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);
        try{TextCanvas c=new TextCanvas(b);v.draw(c);return c.texts;}finally{b.recycle();}
    }
    private TextDraw menuRow(View v)throws Exception{java.util.List<TextDraw> text=draw(v);check(text.size()==1,"Unexpected row text draws "+text.size());return text.get(0);}
    private void auditMenu(String id,String preset,int category)throws Exception{
        main(()->{
            ListView cats=list(true),options=list(false);float common=-1;JSONArray samples=new JSONArray();boolean equal=true,centered=true;
            for(ListView l:new ListView[]{cats,options})for(int i=0;i<l.getChildCount();i++){
                View v=l.getChildAt(i);TextDraw t=menuRow(v);if(common<0)common=t.size;
                equal&=Math.abs(t.size-common)<.08f&&!t.bold&&t.normalFace;centered&=Math.abs(t.center-v.getHeight()/2f)<.75f;
                if(!baseline&&l==options&&category==7&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("网页电视"))
                    check(t.text.endsWith(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName),"Version number clipped "+t.text);
                samples.put(new JSONObject().put("text",t.text).put("px",t.size).put("bold",t.bold).put("centerError",t.center-v.getHeight()/2f));
            }
            results.put(new JSONObject().put("kind","menu").put("theme",id).put("preset",preset).put("category",category).put("sameFont",equal).put("sameBaseline",centered).put("rows",samples));
            if(!baseline){check(equal,"Menu font mismatch "+id+"/"+preset+"/"+category+" "+samples);check(centered,"Menu baseline mismatch "+id+"/"+preset+"/"+category+" "+samples);}
        });
    }
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"No screenshot");try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}finally{b.recycle();}}
    private View measure(String part,Bundle state,int width,int height){
        View v=TvUiPlugin.provider().create(activity,part,state,null,null);
        v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);return v;
    }
    private void auditOther(String id,String preset,float menuSize)throws Exception{
        main(()->{
            float d=activity.themeScale();int row=Math.round((id.equals("ui2")?70:id.equals("ui3")?65:id.equals("ui4")?60:id.equals("ui5")?46:48)*d);
            for(String part:new String[]{"channel-row","group-row","epg-row"}){
                float size=-1;boolean equal=true;
                for(boolean selected:new boolean[]{false,true}){
                    Bundle b=new Bundle();b.putBoolean("group",part.equals("group-row"));b.putBoolean("selected",selected);b.putBoolean("focused",selected);b.putBoolean("playing",selected);b.putString("title","同级标题");b.putString("program","节目内容");b.putString("number","001");b.putString("time","10:00–11:00");
                    for(TextDraw t:draw(measure(part,b,Math.round(280*d),row)))if(t.text.equals("同级标题")){if(size<0)size=t.size;equal&=Math.abs(size-t.size)<.08f&&!t.bold&&t.normalFace;}
                }
                check(size>0,"No row title");results.put(new JSONObject().put("kind",part).put("theme",id).put("preset",preset).put("sameFont",equal));if(!baseline)check(equal,"Row selection font changes "+part+"/"+id);
            }
            if(!baseline){
                TextView clock=(TextView)TvUiPlugin.provider().create(activity,"clock",new Bundle(),null,null),traffic=(TextView)TvUiPlugin.provider().create(activity,"traffic",new Bundle(),null,null);
                boolean same=clock.getTextSize()==traffic.getTextSize()&&clock.getTypeface()==traffic.getTypeface();check(same,"HUD peer typography differs");
                results.put(new JSONObject().put("kind","hud").put("theme",id).put("preset",preset).put("sameFont",same));
                View root=(View)get("root");float size=-1;int count=0;
                for(TextDraw t:draw(measure("guide",new Bundle(),root.getWidth(),root.getHeight())))
                    if(t.text.equals("上下键")||t.text.equals("左右键")||t.text.equals("OK键")||t.text.equals("菜单键")){
                        if(size<0)size=t.size;check(Math.abs(size-t.size)<.08f&&t.bold,"Guide peer typography differs");count++;
                    }
                check(count==4,"Guide labels missing");results.put(new JSONObject().put("kind","guide").put("theme",id).put("preset",preset).put("sameFont",true));
            }
            Bundle b=new Bundle();b.putString("title","频道名称");b.putString("number","1");b.putString("epg","10:00–11:00 当前节目\n11:00–12:00 下一节目");
            java.util.List<TextDraw> text=draw(measure("card",b,Math.round(720*d),Math.round(120*d)));float current=-1,next=-1;
            for(TextDraw t:text){if(t.text.contains("当前节目"))current=t.size;if(t.text.contains("下一节目"))next=t.size;}
            check(current>0&&next>0,"Card programmes missing");boolean same=Math.abs(current-next)<.08f;
            results.put(new JSONObject().put("kind","card").put("theme",id).put("preset",preset).put("sameFont",same).put("currentPx",current).put("nextPx",next));if(!baseline)check(same,"Peer programme font changes "+id);
            menu().show((View)get("root"),"exit",new Bundle(),new xiao.bu.tv.skin.TvUi.Events(){public Bundle options(int c){return new Bundle();}public void action(int c,int i){}public void dismiss(){}});
        });
        waitForIdleSync();SystemClock.sleep(50);
        main(()->{float first=-1;boolean same=true;int buttons=0;
            for(TextDraw t:draw(content()))if(t.text.equals("功能设置")||t.text.equals("确认退出")){buttons++;if(first<0)first=t.size;same&=Math.abs(first-t.size)<.08f&&Math.abs(menuSize-t.size)<.15f&&!t.bold;}
            check(buttons==2,"Exit buttons missing");results.put(new JSONObject().put("kind","exit").put("theme",id).put("preset",preset).put("sameFont",same).put("menuPx",menuSize).put("exitPx",first));if(!baseline)check(same,"Exit/menu font differs "+id+"/"+preset+" "+first+"/"+menuSize);menu().dismiss();
        });
    }
    public void onStart(){Bundle result=new Bundle();int code=1;String savedTheme="builtin",savedSize="auto";ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;
        try{
            baseline="true".equals(args.getString("baseline"));Context c=getTargetContext();out=new File(c.getExternalFilesDir(null),baseline?"theme-font-before":"theme-font-after");out.mkdirs();savedTheme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"No Activity");SystemClock.sleep(700);savedSize=(String)get("uiScaleMode");
            String base=args.getString("base"),m3u="#EXTM3U\n#EXTINF:-1 group-title=\"测试\",测试频道\n"+base+"/vod.mp4?line=1\n#EXTINF:-1 group-title=\"测试\",测试频道\n"+base+"/vod.mp4?line=2\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No video");
            IMediaPlayer player=(IMediaPlayer)get("player");Surface surface=((DirectVideoView)get("videoView")).getVideoSurface();long start=player.getCurrentPosition();
            for(String preset:new String[]{"auto","small","large"}){
                setting(preset);
                for(String id:new String[]{"ui2","ui3","ui4","ui5","ui6"}){
                    select(id);main(()->call("openTvUiMenu",new Class[]{}));waitForIdleSync();SystemClock.sleep(60);
                    for(int category=0;category<8;category++){
                        final int cat=category;main(()->{ListView l=list(true);l.requestFocus();l.setSelection(cat);});waitForIdleSync();SystemClock.sleep(30);auditMenu(id,preset,category);
                        main(()->{ListView l=list(false);l.requestFocus();l.setSelection(l.getCount()-1);});waitForIdleSync();auditMenu(id,preset,category);
                        if(category==0&&preset.equals("auto"))shot(id+"-sources");
                        if(category==6&&preset.equals("auto"))shot(id+"-preferences");
                    }
                    final float[] size={0};main(()->{size[0]=menuRow(list(true).getChildAt(0)).size;menu().dismiss();});
                    if(!baseline){main(()->call("showTvUiStyles",new Class[]{}));waitForIdleSync();SystemClock.sleep(50);auditMenu(id,preset,8);main(()->menu().dismiss());}
                    auditOther(id,preset,size[0]);
                    check(get("player")==player,"Menu restarted player");check(((DirectVideoView)get("videoView")).getVideoSurface()==surface&&surface.isValid(),"Menu replaced Surface");
                }
            }
            check(player.getCurrentPosition()>start+1000,"Video did not advance");
            result.putString("stream",(baseline?"BASELINE":"PASS")+": 5 themes x 3 size presets x 8 menus x both focus columns; radio/plain baseline; long labels; rows/card/exit typography; same advancing player\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(results.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(activity!=null){final String mode=savedSize;setting(mode);main(()->{menu().dismiss();activity.finish();});}TvUiPlugin.select(getTargetContext(),savedTheme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
