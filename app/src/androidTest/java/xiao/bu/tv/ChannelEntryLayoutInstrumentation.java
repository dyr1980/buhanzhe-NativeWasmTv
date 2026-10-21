package xiao.bu.tv;

import android.app.*;
import android.content.*;
import android.graphics.Paint;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Five-character categories and shared empty/disabled guide entry behavior. */
public final class ChannelEntryLayoutInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    interface Work { void run() throws Exception; }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity); }
    private void set(String name,Object value) throws Exception { Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value); }
    private Object call(String name,Class<?>[] types,Object... args)throws Exception { Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args); }
    private void main(Work work)throws Exception { Throwable[] error={null};runOnMainSync(()->{try{if(activity!=null&&get("channelListPanel")!=null)((View)get("channelListPanel")).removeCallbacks((Runnable)get("hideChannelList"));work.run();if(activity!=null&&get("channelListPanel")!=null)((View)get("channelListPanel")).removeCallbacks((Runnable)get("hideChannelList"));}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]); }
    private void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private void settle() { waitForIdleSync();SystemClock.sleep(130); }
    private void right() { activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT)); }
    private void toast(Work work)throws Exception {
        if(Build.VERSION.SDK_INT<18){main(work);return;}
        AccessibilityEvent event=getUiAutomation().executeAndWaitForEvent(()->{try{main(work);}catch(Exception e){throw new AssertionError(e);}},
                e->e.getEventType()==AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED&&e.getText().toString().contains("当前节目暂无节目单"),4000);
        check(event.getText().toString().contains("当前节目暂无节目单"),"Empty-guide toast absent");event.recycle();
    }
    public void onStart() {
        Bundle result=new Bundle();int code=1;
        ChannelCatalog.Group[] saved=ChannelCatalog.GROUPS;
        Context c=getTargetContext();String theme=c.getSharedPreferences("tv_ui",0).getString("selected","builtin");
        String scale=c.getSharedPreferences(MainActivity.PREFERENCES,0).getString("ui_scale_mode","standard");
        Object priorManager=null;boolean priorEnabled=true;PowerManager.WakeLock wake=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"ntv:channel-entry-test");
        try {
            wake.acquire(180000);
            check(c.getPackageName().endsWith(".iptvdiag"),"Isolated diagnostic application required");
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1600);
            main(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));settle();
            priorManager=get("epgManager");priorEnabled=(Boolean)get("epgEnabled");
            EpgManager manager=new EpgManager(c);Field guide=EpgManager.class.getDeclaredField("guide");guide.setAccessible(true);
            Object empty=guide.get(manager);Field loading=EpgManager.class.getDeclaredField("loading");loading.setAccessible(true);loading.setBoolean(manager,true);
            SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);long now=System.currentTimeMillis();
            String xml="<tv><channel id=\"entry-test\"><display-name>测试频道</display-name></channel><programme channel=\"entry-test\" start=\""+date.format(new Date(now-60000))+"\" stop=\""+date.format(new Date(now+3600000))+"\"><title>Fixture guide</title></programme></tv>";
            Method parse=EpgManager.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);Object full=parse.invoke(null,(Object)xml.getBytes("UTF-8"));
            Channel channel=new Channel("1","测试频道","entry-test","http://127.0.0.1:1/test.mp4",null,null);
            final ChannelCatalog.Group[] fixtures={new ChannelCatalog.Group("央视频道组",ChannelCatalog.SOURCE_CUSTOM,new Channel[]{channel})};
            main(()->{
                ((java.util.concurrent.atomic.AtomicInteger)get("catalogLoadGeneration")).incrementAndGet();
                ChannelCatalog.GROUPS=fixtures;
                set("currentGroupIndex",0);set("currentChannelIndex",0);set("currentSourceIndex",0);set("epgManager",manager);set("epgEnabled",true);
                call("openChannelList",new Class[]{boolean.class},false);call("showChannelMenu",new Class[]{int.class},0);
            });settle();
            for(String id:new String[]{"builtin","ui2","ui3","ui4","ui5","ui6"}) {
                TvUiPlugin.select(c,id);settle();main(()->activity.onTvUiChanged());settle();
                main(()->{ChannelCatalog.GROUPS=fixtures;guide.set(manager,empty);set("epgEnabled",true);set("epgExpanded",false);call("applyEpgEnabled",new Class[]{});call("showEpgForBrowsingChannel",new Class[]{int.class},0);((ListView)get("channelList")).setSelection(0);});settle();
                main(()->check(((View)get("epgToggle")).isEnabled(),"Empty guide entry disabled "+id));
                if(id.equals("builtin"))toast(()->((View)get("epgToggle")).performClick());else main(()->((View)get("epgToggle")).performClick());
                main(()->{check(!(Boolean)get("epgExpanded"),"Empty guide opened "+id);((ListView)get("channelList")).requestFocus();call("setFavoriteActionFocused",new Class[]{boolean.class},false);});settle();
                if(id.equals("builtin"))toast(this::right);else main(this::right);
                main(()->{check(!(Boolean)get("epgExpanded"),"RIGHT opened empty guide "+id);right();check(!(Boolean)get("epgExpanded"),"Favorite RIGHT opened empty guide "+id);});
                main(()->{set("epgEnabled",false);call("applyEpgEnabled",new Class[]{});call("showEpgForBrowsingChannel",new Class[]{int.class},0);check(((View)get("epgToggle")).getVisibility()==View.GONE,"Disabled guide label visible "+id);});
                main(()->{set("epgEnabled",true);guide.set(manager,full);call("applyEpgEnabled",new Class[]{});});
                for(String size:new String[]{"50","90","standard","150","200"}) {
                    main(()->call("handleWebSettings",new Class[]{org.json.JSONObject.class},new org.json.JSONObject().put("uiScaleMode",size)));settle();
                    main(()->{ChannelCatalog.GROUPS=fixtures;guide.set(manager,full);call("openChannelList",new Class[]{boolean.class},false);call("showChannelMenu",new Class[]{int.class},0);((ListView)get("groupList")).setSelection(0);});settle();
                    main(()->{
                        ListView groups=(ListView)get("groupList");View row=groups.getChildAt(0);check(row!=null,"No group row "+id+size+" count="+groups.getCount()+" first="+groups.getFirstVisiblePosition()+" size="+groups.getWidth()+","+groups.getHeight()+" shown="+groups.isShown()+" root="+((View)get("root")).getWidth()+","+((View)get("root")).getHeight()+" finishing="+activity.isFinishing());
                        if(id.equals("builtin")){
                            TextView name=(TextView)row.findViewById(R.id.channel_item_name);Paint measure=new Paint(name.getPaint());
                            check(name.getWidth()+1>=measure.measureText("央视频道组"),"Classic category clipped "+size+" width="+name.getWidth()+" need="+measure.measureText("央视频道组"));
                            check(name.getLayout()!=null&&name.getLayout().getEllipsisCount(0)==0,"Five-character ellipsis "+size+" text="+name.getText()+" width="+name.getWidth()+" layout="+(name.getLayout()==null?-1:name.getLayout().getWidth())+" need="+measure.measureText(name.getText().toString()));
                        }else{
                            float d=activity.themeScale();Paint measure=new Paint();measure.setTextSize((id.equals("ui6")?18:20)*d);
                            float available=groups.getWidth()-(id.equals("ui6")?94:32)*d;
                            check(available+1>=measure.measureText("央视频道组"),"Theme category clipped "+id+size+" available="+available);
                        }
                        ChannelCatalog.GROUPS=fixtures;guide.set(manager,full);boolean before=(Boolean)get("epgExpanded");boolean available=(Boolean)call("hasBrowsingEpg",new Class[]{int.class},0);
                        ((View)get("epgToggle")).performClick();check((Boolean)get("epgExpanded"),"Available guide did not open "+id+size+" before="+before+" available="+available+" enabled="+get("epgEnabled")+" position="+call("browsingChannelPosition",new Class[]{}));
                    });settle();
                }
            }
            result.putString("stream","PASS "+checks+" checks: six themes x five scales, five-character categories, empty click/RIGHT toast, disabled guide hidden, available guide opens\n");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally {
            ChannelCatalog.GROUPS=saved;final Object old=priorManager;final boolean enabled=priorEnabled;
            try{if(activity!=null)main(()->{if(old!=null)set("epgManager",old);set("epgEnabled",enabled);activity.finish();});TvUiPlugin.select(c,theme);}catch(Exception ignored){}
            c.getSharedPreferences(MainActivity.PREFERENCES,0).edit().putString("ui_scale_mode",scale).apply();if(wake.isHeld())wake.release();
        }
        finish(code,result);
    }
}
