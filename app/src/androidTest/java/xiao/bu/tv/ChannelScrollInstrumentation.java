package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Real pointer HTTP requests, cursor hit-testing and native ListView scrolling. */
public final class ChannelScrollInstrumentation extends Instrumentation {
    private MainActivity activity;
    private ViewGroup root;
    private FlyMouseCursorView cursor;
    private ListView[] lists;
    private boolean remoteOnly;
    private Object field(String name) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(activity,args);
    }
    private interface Work { void run() throws Exception; }
    private void ui(Work work) {
        Throwable[] error=new Throwable[1];
        runOnMainSync(() -> { try { work.run(); } catch(Throwable e) { error[0]=e; } });
        if(error[0]!=null) throw new RuntimeException(error[0]);
    }
    private void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
    private int position(ListView list) {
        return list.getFirstVisiblePosition()*100000-(list.getChildCount()==0?0:list.getChildAt(0).getTop());
    }
    private void wheel(int x,int y) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:9966/api/pointer").openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(2500); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");
        byte[] data=new JSONObject().put("action","scroll").put("scrollX",x).put("scrollY",y).toString().getBytes("UTF-8");
        try { c.getOutputStream().write(data); c.getOutputStream().close(); check(c.getResponseCode()==200,"Pointer request failed"); }
        finally { c.disconnect(); }
        SystemClock.sleep(180);
    }
    private void checkRealChannelHighlight(StringBuilder report) throws Exception {
        ListView list=lists[1];
        ui(() -> {
            int group=0;
            for(int i=1;i<ChannelCatalog.GROUPS.length;i++)
                if(ChannelCatalog.GROUPS[i].channels.length>ChannelCatalog.GROUPS[group].channels.length) group=i;
            invoke("showChannelMenu",new Class<?>[]{int.class},group);
            setInTouchMode(false);
            list.requestFocus();
            list.setSelectionFromTop(2,100);
        });
        SystemClock.sleep(250);
        int[] expected=new int[2];
        ui(() -> {
            check(list.getChildCount()>=3,"Need three visible channels");
            View row=list.getChildAt(1);
            expected[0]=list.getFirstVisiblePosition()+1;
            if(expected[0]==list.getSelectedItemPosition()) {
                row=list.getChildAt(2); expected[0]++;
            }
            expected[1]=position(list);
            Rect bounds=new Rect(); row.getDrawingRect(bounds);
            root.offsetDescendantRectToMyCoords(row,bounds);
            cursor.moveBy(bounds.left+bounds.width()/3f-cursor.cursorX(),bounds.exactCenterY()-cursor.cursorY());
            invoke("dispatchFlyMouseMotionEvent",new Class<?>[]{int.class,long.class},
                    MotionEvent.ACTION_HOVER_ENTER,SystemClock.uptimeMillis());
            invoke("dispatchFlyMouseMotionEvent",new Class<?>[]{int.class,long.class},
                    MotionEvent.ACTION_HOVER_MOVE,SystemClock.uptimeMillis());
        });
        SystemClock.sleep(200);
        ui(() -> {
            check(list.getCheckedItemPosition()==expected[0],"Hover did not check channel");
            check(list.getSelectedItemPosition()==expected[0],"Blue selector stayed on old channel: selected="
                    +list.getSelectedItemPosition()+", hovered="+expected[0]);
            check(position(list)==expected[1],"Hover moved viewport: "+expected[1]+" -> "+position(list));
            checkHighlightBounds(list);
        });
        wheel(0,17);
        ui(() -> checkHighlightBounds(list));
        wheel(0,-9);
        ui(() -> checkHighlightBounds(list));
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
        SystemClock.sleep(150);
        ui(() -> {
            check(list.getSelectedItemPosition()==expected[0]-1,"D-pad did not continue from hovered channel");
            checkHighlightBounds(list);
        });
        report.append("PASS real channel hover/selector alignment, stable viewport, partial wheel and D-pad\n");
    }
    private void checkHighlightBounds(ListView list) {
        Bitmap frame=Bitmap.createBitmap(list.getWidth(),list.getHeight(),Bitmap.Config.ARGB_8888);
        list.draw(new Canvas(frame)); frame.recycle();
        View row=list.getSelectedView();
        check(row!=null,"Selected row disappeared");
        Rect bounds=list.getSelector().getBounds();
        check(bounds.top==row.getTop() && bounds.bottom==row.getBottom(),
                "Selector detached from row: "+bounds+" vs "+row.getTop()+".."+row.getBottom());
    }
    private void checkRemoteChannelNavigation(StringBuilder report) throws Exception {
        ListView list=lists[1];
        ui(() -> {
            int group=0;
            for(int i=1;i<ChannelCatalog.GROUPS.length;i++)
                if(ChannelCatalog.GROUPS[i].channels.length>ChannelCatalog.GROUPS[group].channels.length) group=i;
            invoke("showChannelMenu",new Class<?>[]{int.class},group);
            setInTouchMode(false);
            list.requestFocus();
            list.setItemChecked(0,true);
            list.setSelectionFromTop(0,0);
        });
        SystemClock.sleep(250);
        int[] before=new int[1];
        ui(() -> before[0]=position(list));
        for(int i=1;i<=3;i++) {
            final int expected=i;
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            SystemClock.sleep(100);
            ui(() -> {
                check(list.getSelectedItemPosition()==expected,"Down key did not move focus");
                check(position(list)==before[0],"Visible focus movement scrolled the list");
                checkHighlightBounds(list);
            });
        }
        for(int i=2;i>=0;i--) {
            final int expected=i;
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
            SystemClock.sleep(100);
            ui(() -> {
                check(list.getSelectedItemPosition()==expected,"Up key did not move focus");
                check(position(list)==before[0],"Reverse visible movement scrolled the list");
            });
        }
        int[] visible=new int[1];
        ui(() -> visible[0]=list.getLastVisiblePosition());
        for(int i=0;i<=visible[0];i++) {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            SystemClock.sleep(80);
        }
        ui(() -> {
            check(position(list)>before[0],"Navigation past bottom did not scroll");
            View row=list.getSelectedView();
            check(row!=null && row.getTop()>=list.getPaddingTop()
                    && row.getBottom()<=list.getHeight()-list.getPaddingBottom(),
                    "Focused edge row is clipped");
            checkHighlightBounds(list);
        });
        int[] scrolled=new int[1];
        ui(() -> scrolled[0]=position(list));
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
        SystemClock.sleep(100);
        ui(() -> check(position(list)==scrolled[0],"Reverse from bottom moved viewport prematurely"));
        for(int i=0;i<=visible[0];i++) {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
            SystemClock.sleep(80);
        }
        ui(() -> {
            check(list.getSelectedItemPosition()==0,"Up navigation did not return to first channel");
            check(position(list)==before[0],"Up navigation did not restore top viewport");
        });
        report.append("PASS remote focus moves inside viewport and scrolls at bottom edge\n");
    }
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); remoteOnly=args!=null && "true".equals(args.getString("remoteOnly")); start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder(); int code=-1;
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1200);
            ui(() -> {
                invoke("releasePlayer",new Class<?>[0]); invoke("closeWebSource",new Class<?>[0]);
                invoke("hideLoading",new Class<?>[0]);
                Field f=MainActivity.class.getDeclaredField("flyMouseEnabled"); f.setAccessible(true); f.set(activity,true);
                invoke("applyFlyMouseVisibility",new Class<?>[0]);
                invoke("openChannelList",new Class<?>[0]);
            });
            SystemClock.sleep(200);
            ui(() -> {
                root=(ViewGroup)field("root"); cursor=(FlyMouseCursorView)field("flyMouseCursor");
                lists=new ListView[]{(ListView)field("groupList"),(ListView)field("channelList"),(ListView)field("epgList")};
            });
            checkRemoteChannelNavigation(report);
            if(remoteOnly) {
                result.putString("stream",report.toString()); finish(code,result); return;
            }
            checkRealChannelHighlight(report);
            ui(() -> {
                Field expanded=MainActivity.class.getDeclaredField("epgExpanded");
                expanded.setAccessible(true); expanded.set(activity,true);
                String[] rows=new String[120]; for(int i=0;i<rows.length;i++) rows[i]="测试项目 "+i;
                for(ListView list:lists) {
                    list.setOnItemSelectedListener(null);
                    list.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,rows));
                }
                invoke("setEpgColumnVisible",new Class<?>[]{boolean.class},true);
                invoke("updateChannelPanelWidth",new Class<?>[0]);
            });
            SystemClock.sleep(200);
            for(int web=0;web<2;web++) {
                final boolean pageVisible=web==1;
                ui(() -> ((View)field("webSourceView")).setVisibility(pageVisible?View.VISIBLE:View.GONE));
                for(int i=0;i<lists.length;i++) {
                    final int target=i; int[] before=new int[3], after=new int[3];
                    ui(() -> lists[(target+1)%3].requestFocusFromTouch());
                    SystemClock.sleep(100); // Let focus-induced selection/layout settle before measuring scrolling.
                    ui(() -> {
                        // Cursor and keyboard focus intentionally differ.
                        Rect r=new Rect(); check(lists[target].getLocalVisibleRect(r),"List is not visible");
                        root.offsetDescendantRectToMyCoords(lists[target],r);
                        cursor.moveBy(r.exactCenterX()-cursor.cursorX(),r.exactCenterY()-cursor.cursorY());
                        for(int j=0;j<3;j++) before[j]=position(lists[j]);
                    });
                    wheel(0,300);
                    ui(() -> {
                        for(int j=0;j<3;j++) after[j]=position(lists[j]);
                        check(after[target]>before[target],"Hovered list did not scroll: "+target);
                        for(int j=0;j<3;j++) if(j!=target) check(before[j]==after[j],"Wrong list scrolled: "+j);
                    });
                    wheel(0,-150);
                    ui(() -> check(position(lists[target])<after[target],"Reverse wheel failed"));
                    report.append("PASS list ").append(i).append(" up/down; background WebView=").append(pageVisible).append('\n');
                }
            }
            int[] before=new int[3];
            SystemClock.sleep(250);
            ui(() -> { for(int i=0;i<3;i++) before[i]=position(lists[i]); });
            wheel(300,0);
            ui(() -> { for(int i=0;i<3;i++) check(before[i]==position(lists[i]),"Horizontal wheel changed list "+i+": "+before[i]+" -> "+position(lists[i])); });
            report.append("PASS horizontal wheel leaves lists unchanged\n");
        } catch(Throwable error) { code=0; report.append(android.util.Log.getStackTraceString(error)); }
        finally { if(activity!=null) ui(() -> activity.finish()); }
        result.putString("stream",report.toString()); finish(code,result);
    }
}
