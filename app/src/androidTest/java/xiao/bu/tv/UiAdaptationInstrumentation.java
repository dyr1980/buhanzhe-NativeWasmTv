package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class UiAdaptationInstrumentation extends Instrumentation {
    private MainActivity activity;
    private Object field(String name) throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();int code=-1;
        try {
            for(int[] size:new int[][]{{800,480},{1280,720},{1920,1080},{3840,2160},{480,800}}) {
                float reference=UiScalePolicy.resolve(size[0],size[1],"auto",-1,1f);
                for(float density:new float[]{1f,1.5f,2f,3f,4f}) {
                    float scale=UiScalePolicy.resolve(size[0],size[1],"auto",-1,density);
                    float row=46f*scale*density;
                    float expected=Math.min(Math.max(44f*density,Math.max(60f,Math.min(size[0],size[1])/9f)),
                            Math.min(size[0],size[1])/6f);
                    if(Math.abs(row-expected)>1f)
                        throw new AssertionError("44dp adaptive baseline failed");
                    if(14f*scale*density<17f || row>Math.min(size[0],size[1])/5f)
                        throw new AssertionError("Unreadable text or oversized row");
                    float standard=UiScalePolicy.resolve(size[0],size[1],"standard",-1,density);
                    float large=UiScalePolicy.resolve(size[0],size[1],"large",-1,density);
                    float small=UiScalePolicy.resolve(size[0],size[1],"small",-1,density);
                    if(Math.abs(standard-scale)>.001f || large<=scale || small>=scale)
                        throw new AssertionError("Display presets must adjust automatic baseline");
                }
                report.append("PASS auto ").append(size[0]).append('x').append(size[1])
                        .append(" DPI 160–640, titlePx=").append(reference*14).append('\n');
            }
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1200);
            runOnMainSync(()->{try {
                Method open=MainActivity.class.getDeclaredMethod("openChannelList",boolean.class);
                open.setAccessible(true);open.invoke(activity,false);
            }catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(300);
            runOnMainSync(()->{try {
                Method show=MainActivity.class.getDeclaredMethod("showChannelMenu",int.class);
                int group=0;
                for(int i=1;i<ChannelCatalog.GROUPS.length;i++)
                    if(ChannelCatalog.GROUPS[i].channels.length>ChannelCatalog.GROUPS[group].channels.length) group=i;
                show.setAccessible(true);show.invoke(activity,group);
            }catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(600);
            final Throwable[] failure={null};
            runOnMainSync(()->{try {
                ListView list=(ListView)field("channelList");View row=list.getChildAt(0);
                report.append("device list=").append(list.getWidth()).append('x').append(list.getHeight())
                        .append(" count=").append(list.getCount()).append(" children=").append(list.getChildCount())
                        .append(" visibility=").append(((View)field("channelListPanel")).getVisibility()).append('\n');
                TextView title=(TextView)row.findViewById(R.id.channel_item_name);
                report.append("actual titlePx=").append(title.getTextSize()).append(" rowPx=")
                        .append(row.getHeight()).append('\n');
                View panel=(View)field("channelListPanel"),root=(View)field("root");
                if(title.getTextSize()<16f || row.getHeight()<59 || list.getChildCount()<5)
                    throw new AssertionError("Default channel rows remain too small or too few");
                if(panel.getWidth()>root.getWidth() || list.getWidth()<=0)
                    throw new AssertionError("Panel overflow");
                ListView groups=(ListView)field("groupList");
                for(int i=0;i<groups.getChildCount();i++) {
                    TextView name=(TextView)groups.getChildAt(i).findViewById(R.id.channel_item_name);
                    if(name.getLayout()!=null && name.getLayout().getEllipsisCount(0)>0
                            && name.getText().length()<=4)
                        throw new AssertionError("Short group title clipped by count badge");
                }
                report.append("PASS device titlePx=").append(title.getTextSize())
                        .append(" rowPx=").append(row.getHeight()).append(" visibleRows=")
                        .append(list.getChildCount()).append(" panelWidth=").append(panel.getWidth()).append('\n');
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
                root.draw(new android.graphics.Canvas(bitmap));
                java.io.File file=new java.io.File(activity.getExternalFilesDir(null),"ui-auto.png");
                java.io.FileOutputStream out=new java.io.FileOutputStream(file);
                try{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{out.close();bitmap.recycle();}
                report.append("screenshot=").append(file.getAbsolutePath()).append('\n');
            }catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw failure[0];
            final String original=(String)field("uiScaleMode");
            try {
                for(String mode:new String[]{"small","standard","large","extra_large","extra_extra_large","auto"}) {
                    runOnMainSync(()->{try {
                        Field setting=MainActivity.class.getDeclaredField("uiScaleMode");setting.setAccessible(true);setting.set(activity,mode);
                        View root=(View)field("root");
                        Method refresh=MainActivity.class.getDeclaredMethod("refreshUiScaleForViewport",int.class,int.class,boolean.class);
                        refresh.setAccessible(true);refresh.invoke(activity,root.getWidth(),root.getHeight(),true);
                    }catch(Throwable e){failure[0]=e;}});
                    SystemClock.sleep(250);
                    runOnMainSync(()->{try {
                        ListView list=(ListView)field("channelList");View root=(View)field("root");
                        float density=activity.getResources().getDisplayMetrics().density;
                        float scale=UiScalePolicy.resolve(root.getWidth(),root.getHeight(),mode,-1,density);
                        int expected=Math.round(Math.round(46*density)*scale);
                        if(Math.abs(list.getChildAt(0).getHeight()-expected)>1)
                            throw new AssertionError("Preset did not resize actual row: "+mode);
                        if(((View)field("channelListPanel")).getWidth()>root.getWidth())
                            throw new AssertionError("Preset panel overflow: "+mode);
                        report.append("PASS actual display preset ").append(mode).append(" rowPx=")
                                .append(list.getChildAt(0).getHeight()).append('\n');
                    }catch(Throwable e){failure[0]=e;}});
                    if(failure[0]!=null)throw failure[0];
                }
            }finally {
                runOnMainSync(()->{try {
                    Field setting=MainActivity.class.getDeclaredField("uiScaleMode");setting.setAccessible(true);setting.set(activity,original);
                }catch(Exception e){throw new RuntimeException(e);}});
            }
        }catch(Throwable e){code=0;report.append(android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());}
        result.putString("stream",report.toString());finish(code,result);
    }
}
