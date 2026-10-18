package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import org.json.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Real guide HTTP loading and visible programme rows through repeated theme changes. */
public final class EpgThemeStabilityInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private Bundle args; private File out; private String base;
    interface Work {void run()throws Exception;} interface Condition {boolean get()throws Exception;}
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private Object get(String n)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(activity);}
    private void set(String n,Object v)throws Exception{Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,v);}
    private Object call(String n,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(activity,values);}
    private void main(Work w)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{w.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private void until(Condition c,long ms,String message)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!c.get()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(c.get(),message);}
    private static String visibleText(View v){
        if(v.getVisibility()!=View.VISIBLE)return "";
        StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());
        if(v.getContentDescription()!=null)s.append(' ').append(v.getContentDescription());
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(' ').append(visibleText(((ViewGroup)v).getChildAt(i)));
        return s.toString();
    }
    private void rows(String prefix,boolean second)throws Exception{
        main(()->{
            ListView list=(ListView)get("channelList");String visible=visibleText(list);
            check(list.isShown()&&list.getChildCount()>0,"Channel list not visible");
            check(visible.contains(prefix+"-ONE"),"Visible first programme absent: "+visible);
            if(second)check(visible.contains(prefix+"-TWO"),"Visible second programme absent: "+visible);
        });
    }
    private void select(String id)throws Exception{
        TvUiPlugin.select(activity,id);
        until(()->(Integer)get("appliedTvUiRevision")==TvUiPlugin.revision(),5000,"Theme callback missing "+id);
        waitForIdleSync();SystemClock.sleep(90);
    }
    private EpgManager fresh(String... urls)throws Exception{
        EpgManager manager=new EpgManager(activity);
        main(()->{((View)get("root")).removeCallbacks((Runnable)get("deferredEpgRefresh"));set("epgUrls",urls);set("epgManager",manager);call("applyEpgEnabled",new Class[]{});});
        return manager;
    }
    private void shot(String name)throws Exception{
        android.graphics.Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot unavailable");
        try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);}finally{image.recycle();}
    }
    private void releaseSlow()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/release").openConnection();try{check(c.getResponseCode()==200,"Release fixture failed");c.getInputStream().close();}finally{c.disconnect();}
    }
    private void enabled(boolean value)throws Exception{
        LocalControlServer server=(LocalControlServer)get("controlServer");
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getPort()+"/api/settings").openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        try{try(OutputStream stream=c.getOutputStream()){stream.write(new JSONObject().put("epgEnabled",value).toString().getBytes("UTF-8"));}check(c.getResponseCode()==200,"EPG setting failed");c.getInputStream().close();}finally{c.disconnect();}
        waitForIdleSync();
    }
    private void clickGuideHandle()throws Exception{
        final android.graphics.Rect visible=new android.graphics.Rect();
        main(()->{
            View toggle=(View)get("epgToggle");
            check(toggle.isShown()&&toggle.getWidth()>0&&toggle.getHeight()>0,"Guide entry has no visible size");
            check(toggle.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0,"Guide entry is clipped off screen");
        });
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,visible.exactCenterX(),visible.exactCenterY(),0);
        MotionEvent up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,visible.exactCenterX(),visible.exactCenterY(),0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}
        waitForIdleSync();SystemClock.sleep(90);
    }
    private void guideRows()throws Exception{
        main(()->{
            View column=(View)get("epgColumn");ListView list=(ListView)get("epgList");
            check((Boolean)get("epgExpanded")&&column.isShown()&&column.getWidth()>0,"Guide did not open with a visible column");
            String visible=visibleText(list);
            check(list.isShown()&&list.getWidth()>0&&visible.contains("FAST-ONE")&&visible.contains("NEXT"),"Guide rows absent: "+visible);
        });
    }
    private void key(int key)throws Exception{sendKeyDownUpSync(key);waitForIdleSync();SystemClock.sleep(90);}
    private void entry(String id,IMediaPlayer player,JSONArray checks)throws Exception{
        select(id);
        main(()->{set("epgExpanded",false);call("showEpgForBrowsingChannel",new Class[]{int.class},0);});
        waitForIdleSync();SystemClock.sleep(90);
        clickGuideHandle();guideRows();shot("entry-"+id);
        clickGuideHandle();
        main(()->{
            check(!(Boolean)get("epgExpanded")&&!((View)get("epgColumn")).isShown(),"Touch did not collapse guide "+id);
            ListView channels=(ListView)get("channelList");
            call("setFavoriteActionFocused",new Class[]{boolean.class},false);
            channels.requestFocusFromTouch();channels.requestFocus();channels.setSelection(0);channels.setItemChecked(0,true);
        });
        waitForIdleSync();
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);guideRows();
        final int[] before={-1};main(()->{ListView list=(ListView)get("epgList");check(list.hasFocus(),"Remote did not focus guide "+id);before[0]=list.getSelectedItemPosition();});
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        main(()->{ListView list=(ListView)get("epgList");check(list.hasFocus()&&list.getSelectedItemPosition()>before[0],"Remote DOWN did not advance programme "+id);});
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        main(()->{
            check(!(Boolean)get("epgExpanded")&&!((View)get("epgColumn")).isShown(),"Remote LEFT did not collapse guide "+id);
            check(((ListView)get("channelList")).hasFocus()&&(Boolean)get("favoriteActionFocused"),"Remote LEFT did not return to channel actions "+id);
            check(get("player")==player,"Guide entry restarted playback "+id);
        });
        checks.put(new JSONObject().put("case","visible touch entry + remote guide navigation").put("theme",id).put("passed",true));
    }
    public void onStart(){Bundle result=new Bundle();int code=1;JSONArray checks=new JSONArray();ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;String theme="builtin";
        try{
            Context c=getTargetContext();base=args.getString("base");out=new File(c.getExternalFilesDir(null),args.getString("outDir","epg-stability"));out.mkdirs();
            theme=new JSONObject(TvUiPlugin.status(c)).getString("selected");
            c.getSharedPreferences(MainActivity.PREFERENCES,0).edit().putBoolean("epg_enabled",true).apply();
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));until(()->activity!=null,15000,"Activity absent");SystemClock.sleep(1000);
            String m3u="#EXTM3U\n#EXTINF:-1 tvg-id=\"c1\" group-title=\"EPG测试\",CCTV-1 综合\n"+base+"/vod.mp4?channel=1\n#EXTINF:-1 tvg-id=\"c2\" group-title=\"EPG测试\",CCTV-2 财经\n"+base+"/vod.mp4?channel=2\n";
            Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
            ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,m3u.getBytes("UTF-8"),base+"/list.m3u");
            main(()->{ChannelCatalog.GROUPS=groups;set("currentGroupIndex",0);set("autoSwitchSource",false);call("startChannel",new Class[]{int.class},0);});
            until(()->(Boolean)get("prepared")&&(Boolean)get("videoRenderingStarted"),20000,"No video frame");
            IMediaPlayer player=(IMediaPlayer)get("player");long position=player.getCurrentPosition();
            String nonce="?run="+SystemClock.elapsedRealtime();
            EpgManager cold=fresh(base+"/cold.xml"+nonce);
            main(()->call("openChannelList",new Class[]{}));
            until(()->!cold.programsFor(groups[0].channels[0]).isEmpty(),5000,"Collapsed list did not request EPG");
            until(()->!cold.isLoading(),5000,"Cold guide did not finish");waitForIdleSync();rows("COLD",true);
            checks.put(new JSONObject().put("case","collapsed list cold load").put("passed",true));
            main(()->call("closeChannelList",new Class[]{}));
            EpgManager partial=fresh(base+"/fast.xml"+nonce,base+"/slow.xml"+nonce);
            main(()->call("openChannelList",new Class[]{}));
            until(()->!partial.programsFor(groups[0].channels[0]).isEmpty(),5000,"Fast guide blocked behind slow source");
            check(partial.isLoading(),"Slow source was not held for race test");
            java.util.List<EpgManager.Program> snapshot=partial.programsFor(groups[0].channels[0]);
            for(String id:new String[]{"ui2","ui3","ui4","ui5","ui6","builtin"}){select(id);rows("FAST",false);check(get("epgManager")==partial,"Theme replaced guide manager");}
            releaseSlow();until(()->!partial.isLoading(),10000,"Merged guide did not complete");waitForIdleSync();rows("FAST",false);
            check(partial.programsFor(groups[0].channels[1]).get(0).title.contains("SLOW-TWO"),"Second guide not merged");
            check(snapshot==partial.programsFor(groups[0].channels[0]),"Published programme list changed in place");
            checks.put(new JSONObject().put("case","partial load + theme switch + late source merge").put("passed",true));
            for(int round=0;round<4;round++)for(String id:TvUiPlugin.IDS){
                final boolean expanded=round%2==1;
                select(id);main(()->{set("epgExpanded",expanded);call("showEpgForBrowsingChannel",new Class[]{int.class},0);});
                waitForIdleSync();SystemClock.sleep(60);rows("FAST",false);
                main(()->{
                    if((Boolean)get("epgExpanded")){check(((View)get("epgColumn")).isShown(),"Guide column disappeared");check(visibleText((View)get("epgList")).contains("FAST-ONE"),"Expanded programme absent");}
                    check(((TextView)get("channelEpg")).getText().toString().contains("FAST-ONE"),"Card lost programme");
                    check(get("player")==player,"Theme restarted playback");
                });
                if(round==3)shot(id);
                checks.put(new JSONObject().put("round",round).put("theme",id).put("passed",true));
            }
            for(String id:TvUiPlugin.IDS)entry(id,player,checks);
            enabled(false);
            for(String id:TvUiPlugin.IDS){
                select(id);
                main(()->{
                    check(!visibleText((View)get("channelList")).contains("FAST-ONE"),"Disabled EPG returned after theme change "+id);
                    check(!((View)get("epgToggle")).isShown()&&!((View)get("epgColumn")).isShown(),"Disabled EPG entry or column visible "+id);
                });
            }
            enabled(true);
            for(String id:TvUiPlugin.IDS){
                select(id);rows("FAST",false);
                main(()->{View toggle=(View)get("epgToggle");check(toggle.isShown()&&toggle.getWidth()>0&&toggle.getHeight()>0,"Re-enabled EPG entry absent "+id);check(get("player")==player,"EPG switch restarted playback "+id);});
            }
            checks.put(new JSONObject().put("case","EPG off/on across all 6 UIs").put("passed",true));
            check(player.getCurrentPosition()>position+1000,"Playback stopped advancing");
            if("true".equals(args.getString("checkDefault"))){
                EpgManager remote=new EpgManager(c);remote.refresh(new String[]{EpgManager.DEFAULT_URL},null);
                until(()->!remote.isLoading(),80000,"Default EPG timed out");
                String title=new EpgManager.CurrentProgram().titleFor(remote,ChannelCatalog.CCTV_CHANNELS[0],System.currentTimeMillis());
                check(title.length()>0,"Default current EPG unavailable: "+remote.getLastError());
                checks.put(new JSONObject().put("case","default online EPG").put("program",title).put("source",EpgManager.DEFAULT_URL).put("passed",true));
            }
            result.putString("stream","PASS: collapsed cold load; fast source before slow; late merge; repeated theme changes; 6 visible touch entries; remote programme navigation; visible channel/guide/card programmes; EPG off/on in all UIs; same advancing player\n");code=-1;
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            try{releaseSlow();}catch(Exception ignored){}
            try{if(out!=null)try(FileOutputStream f=new FileOutputStream(new File(out,"results.json"))){f.write(checks.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}
            try{if(activity!=null)main(()->activity.finish());TvUiPlugin.select(getTargetContext(),theme);}catch(Exception ignored){}ChannelCatalog.GROUPS=saved;
        }
        finish(code,result);
    }
}
