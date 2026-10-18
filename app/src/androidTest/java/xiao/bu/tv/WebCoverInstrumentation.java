package xiao.bu.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.lang.reflect.Field;

/** Opens the real isolated legacy browser; the host checks its accessible UI and HTTP traffic. */
public final class WebCoverInstrumentation extends Instrumentation {
    private volatile MainActivity activity;
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments;start(); }
    @Override public void callActivityOnResume(Activity value) {
        super.callActivityOnResume(value);
        if (value instanceof MainActivity) activity = (MainActivity) value;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean configureCover=args!=null&&"true".equals(args.getString("configureCover"));
        boolean savedCover=false;
        try {
            getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until = SystemClock.elapsedRealtime() + 20000;
            while (activity == null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100);
            if (activity == null) throw new AssertionError("Activity did not start");
            SystemClock.sleep(2500);
            if(configureCover) {
                savedCover=activity.getSharedPreferences(MainActivity.PREFERENCES,0).getBoolean("web_view_page_cover",false);
                java.lang.reflect.Method settings=MainActivity.class.getDeclaredMethod("handleWebSettings",org.json.JSONObject.class);settings.setAccessible(true);
                settings.invoke(activity,new org.json.JSONObject().put("webViewPageCover",true));
                runOnMainSync(()->{});
                if(!activity.getSharedPreferences(MainActivity.PREFERENCES,0).getBoolean("web_view_page_cover",false))throw new AssertionError("Cover default was not persisted");
                Field server=MainActivity.class.getDeclaredField("controlServer");server.setAccessible(true);
                try(java.io.FileOutputStream file=new java.io.FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"web-cover-port"))) {
                    file.write(String.valueOf(((LocalControlServer)server.get(activity)).getPort()).getBytes("UTF-8"));
                }
            }
            Field field = MainActivity.class.getDeclaredField("webSourceView"); field.setAccessible(true);
            WebSourceView source = (WebSourceView) field.get(activity);
            java.lang.reflect.Method control = MainActivity.class.getDeclaredMethod("handleMediaControl", org.json.JSONObject.class);
            control.setAccessible(true);
            int initialBrightness = PlaybackBrightness.systemPercent(activity);
            if (PlaybackBrightness.percent(activity) != initialBrightness)
                throw new AssertionError("Initial brightness does not match the system or fallback");
            org.json.JSONObject state = new org.json.JSONObject((String) control.invoke(activity,
                    new org.json.JSONObject().put("action", "brightness").put("brightness", 75)));
            if (state.getInt("brightness") != 75 || PlaybackBrightness.percent(activity) != 75)
                throw new AssertionError("Brightness command did not reach the window");
            try {
                control.invoke(activity, new org.json.JSONObject().put("action", "brightness").put("brightness", 0));
                throw new AssertionError("Out-of-range brightness accepted");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!(expected.getCause() instanceof org.json.JSONException)) throw expected;
            }
            control.invoke(activity, new org.json.JSONObject().put("action", "brightness").put("brightness", initialBrightness));
            android.util.Log.i("WebCoverCheck", "PASS brightness: system/fallback=" + initialBrightness
                    + ", command 75, invalid zero rejected, initial value restored");
            File stop = new File(getTargetContext().getExternalFilesDir(null), "web-cover-stop");
            stop.delete();
            java.lang.reflect.Method parse = PlaylistManager.class.getDeclaredMethod("parse", byte[].class, String.class);
            parse.setAccessible(true);
            ChannelCatalog.Group[] groups = (ChannelCatalog.Group[]) parse.invoke(null,
                    "网页测试,#genre#\n遮罩测试,webview://http://127.0.0.1:39481/page\n".getBytes("UTF-8"), "fixture");
            runOnMainSync(() -> {
                try {
                    ChannelCatalog.GROUPS = groups;
                    for (String name : new String[]{"currentGroupIndex", "currentChannelIndex", "currentSourceIndex"}) {
                        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.setInt(activity, 0);
                    }
                    java.lang.reflect.Method start = MainActivity.class.getDeclaredMethod("startChannel", int.class);
                    start.setAccessible(true); start.invoke(activity, 0);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            until = SystemClock.elapsedRealtime() + 180000;
            File action=new File(getTargetContext().getExternalFilesDir(null),"web-cover-action");
            while (!stop.exists() && SystemClock.elapsedRealtime() < until) {
                if(action.exists()) {
                    byte[] encoded=new byte[(int)action.length()];
                    try(java.io.FileInputStream input=new java.io.FileInputStream(action)){input.read(encoded);}
                    action.delete();String label=new String(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT),"UTF-8");
                    android.view.accessibility.AccessibilityNodeInfo tree=getUiAutomation().getRootInActiveWindow();
                    if(tree!=null)try {
                        if("__dump__".equals(label)) {
                            File output=new File(getTargetContext().getExternalFilesDir(null),"web-cover-ui.xml");
                            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)) {
                                org.xmlpull.v1.XmlSerializer xml=android.util.Xml.newSerializer();xml.setOutput(stream,"UTF-8");
                                xml.startDocument("UTF-8",true);xml.startTag(null,"hierarchy");writeNode(xml,tree);xml.endTag(null,"hierarchy");xml.endDocument();
                            }
                        } else if("__reopen__".equals(label)) {
                            runOnMainSync(()->{try{java.lang.reflect.Method start=MainActivity.class.getDeclaredMethod("startChannel",int.class);start.setAccessible(true);start.invoke(activity,0);}catch(Exception e){throw new RuntimeException(e);}});
                        } else clickNode(tree,label);
                    }finally{tree.recycle();}
                }
                SystemClock.sleep(100);
            }
            runOnMainSync(source::closePage);
            result.putString("stream", "Browser fixture closed; host-side result.json records the UI and HTTP assertions");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error)); finish(0, result);
        } finally {
            if(configureCover&&activity!=null)try {
                java.lang.reflect.Method settings=MainActivity.class.getDeclaredMethod("handleWebSettings",org.json.JSONObject.class);settings.setAccessible(true);
                settings.invoke(activity,new org.json.JSONObject().put("webViewPageCover",savedCover));
            } catch(Exception ignored) {}
        }
    }
    private void writeNode(org.xmlpull.v1.XmlSerializer xml,android.view.accessibility.AccessibilityNodeInfo node)throws java.io.IOException {
        android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
        xml.startTag(null,"node");xml.attribute(null,"text",node.getText()==null?"":node.getText().toString());
        xml.attribute(null,"content-desc",node.getContentDescription()==null?"":node.getContentDescription().toString());
        xml.attribute(null,"bounds","["+bounds.left+","+bounds.top+"]["+bounds.right+","+bounds.bottom+"]");
        for(int i=0;i<node.getChildCount();i++) {
            android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null)try{writeNode(xml,child);}finally{child.recycle();}
        }
        xml.endTag(null,"node");
    }
    private boolean clickNode(android.view.accessibility.AccessibilityNodeInfo node,String label) {
        if(label.equals(String.valueOf(node.getContentDescription())) || label.equals(String.valueOf(node.getText())))
            return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        for(int i=0;i<node.getChildCount();i++) {
            android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null)try{if(clickNode(child,label))return true;}finally{child.recycle();}
        }
        return false;
    }

}
