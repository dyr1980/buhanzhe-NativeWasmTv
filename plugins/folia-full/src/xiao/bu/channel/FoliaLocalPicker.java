package xiao.bu.channel;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.webkit.ValueCallback;
import android.widget.*;
import java.io.File;
import java.util.*;
import xiao.bu.tv.jar.ChannelPlugin;

/** Read-only folder import with a Material theme independent of the host Activity. */
final class FoliaLocalPicker {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Context theme;
    private ValueCallback<Uri[]> callback;
    private AlertDialog dialog;
    private final ChannelPlugin.Host host;
    private File current;
    private List<File> folders;
    private final List<Uri> files=new ArrayList<>();
    private ListView list;
    private TextView path,summary;
    private Button confirm,cancelButton,parentButton;
    private int generation;
    FoliaLocalPicker(Activity activity,ChannelPlugin.Host host){this.activity=activity;this.host=host;}
    void open(ValueCallback<Uri[]> result,boolean multi){
        cancel();callback=result;
        android.content.res.Configuration configuration=new android.content.res.Configuration(activity.getResources().getConfiguration());
        configuration.densityDpi=Math.min(configuration.densityDpi,(int)(activity.getResources().getDisplayMetrics().heightPixels*160f/480f));
        ContextThemeWrapper wrapper=new ContextThemeWrapper(activity,android.R.style.Theme_Material_Dialog_Alert);
        wrapper.applyOverrideConfiguration(configuration);theme=wrapper;
        if(Build.VERSION.SDK_INT>=29){openTree();return;}
        if(activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){show();return;}
        activity.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},39672);
        final long started=SystemClock.elapsedRealtime();
        handler.postDelayed(new Runnable(){public void run(){
            if(callback==null)return;
            if(activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){show();return;}
            if(SystemClock.elapsedRealtime()-started>30000||(SystemClock.elapsedRealtime()-started>1000&&activity.hasWindowFocus())){
                Toast.makeText(activity,"需要允许读取存储空间才能导入文件夹",Toast.LENGTH_LONG).show();finish(null);return;
            }
            handler.postDelayed(this,200);
        }},300);
    }
    private int dp(float value){return (int)(value*theme.getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private android.graphics.drawable.StateListDrawable surface(int color){android.graphics.drawable.StateListDrawable d=new android.graphics.drawable.StateListDrawable();GradientDrawable focus=background(0xffa3e8dc,10);focus.setStroke(dp(2),0xffffffff);d.addState(new int[]{android.R.attr.state_focused},focus);d.addState(new int[]{android.R.attr.state_pressed},background(0xffa3e8dc,10));d.addState(new int[]{},background(color,10));return d;}
    private TextView label(String text,int size,int color){TextView v=new TextView(theme);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String text){Button b=new Button(theme);b.setText(text);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff102521,0xffd7dce5}));b.setBackground(surface(0xff303540));b.setMinHeight(dp(44));return b;}
    private void show(){
        current=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if(!current.isDirectory())current=Environment.getExternalStorageDirectory();
        createDialog(true);refresh();
    }
    private void createDialog(boolean browse){
        if(callback==null||activity.isFinishing())return;
        LinearLayout body=new LinearLayout(theme);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(20),dp(24),dp(18));body.setBackground(background(0xff20242d,18));
        TextView title=label("选择音乐文件夹",21,0xffffffff);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        TextView hint=label("方向键选择 · 确定进入 · 右键导入 · 返回上一级\n仅导入当前文件夹，不包含子文件夹",13,0xffa6adbb);hint.setPadding(0,dp(8),0,dp(14));body.addView(hint);
        path=label("",13,0xff80cbc4);path.setMaxLines(3);path.setPadding(dp(12),dp(10),dp(12),dp(10));path.setBackground(background(0xff292f39,9));body.addView(path);
        summary=label("",14,0xffc6cbd5);summary.setPadding(0,dp(14),0,dp(10));body.addView(summary);
        if(browse){
            parentButton=button("↑  上一级");body.addView(parentButton,new LinearLayout.LayoutParams(-1,dp(44)));
            parentButton.setOnClickListener(v->goParent());
            list=new ListView(theme);list.setDivider(null);list.setPadding(0,dp(6),0,dp(6));
            GradientDrawable selected=background(0xff3b5659,10);selected.setStroke(dp(2),0xffa3e8dc);list.setSelector(selected);list.setDrawSelectorOnTop(false);
            body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            list.setOnItemClickListener((p,v,index,id)->{current=folders.get(index);refresh();});
        }
        LinearLayout actions=new LinearLayout(theme);actions.setGravity(Gravity.RIGHT);actions.setPadding(0,dp(14),0,0);
        Button cancel=button("取消");cancelButton=cancel;actions.addView(cancel,new LinearLayout.LayoutParams(dp(88),dp(46)));
        confirm=button("导入此文件夹");confirm.setTextColor(0xff102521);confirm.setBackground(surface(0xff80cbc4));
        LinearLayout.LayoutParams positive=new LinearLayout.LayoutParams(dp(174),dp(46));positive.leftMargin=dp(12);actions.addView(confirm,positive);body.addView(actions);
        if(browse)body.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,Math.min(dp(620),activity.getResources().getDisplayMetrics().heightPixels-dp(24))));
        dialog=new AlertDialog.Builder(theme).setView(body).create();
        dialog.setOnCancelListener(d->finish(null));dialog.setOnDismissListener(d->{if(callback!=null)finish(null);});
        dialog.setOnKeyListener((d,key,event)->{
            if(key==KeyEvent.KEYCODE_BACK&&browse){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){if(!goParent())finish(null);}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER){
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
                    View focused=dialog.getCurrentFocus();
                    if(browse&&focused==list){int index=Math.max(0,list.getSelectedItemPosition());if(index<folders.size()){current=folders.get(index);refresh();}}
                    else if(focused instanceof Button&&focused.isEnabled())focused.performClick();
                }
                return true;
            }
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            View focused=dialog.getCurrentFocus();
            if(browse&&focused==list){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){goParent();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){focusAction();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP&&list.getSelectedItemPosition()<=0){parentButton.requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&list.getSelectedItemPosition()>=folders.size()-1){focusAction();return true;}
            }
            if(browse&&focused==parentButton&&key==KeyEvent.KEYCODE_DPAD_DOWN){focusFolders(0);return true;}
            if(browse&&(focused==cancelButton||focused==confirm)&&key==KeyEvent.KEYCODE_DPAD_UP){if(folders.isEmpty()&&parentButton.isEnabled())parentButton.requestFocus();else focusFolders(Math.max(0,folders.size()-1));return true;}
            if(focused==confirm&&key==KeyEvent.KEYCODE_DPAD_LEFT){cancelButton.requestFocus();return true;}
            if(focused==cancelButton&&key==KeyEvent.KEYCODE_DPAD_RIGHT){focusAction();return true;}
            return false;
        });
        cancel.setOnClickListener(v->finish(null));confirm.setOnClickListener(v->{if(!files.isEmpty())finish(files.toArray(new Uri[0]));});
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(dp(560),activity.getResources().getDisplayMetrics().widthPixels-dp(32)),browse?Math.min(dp(620),activity.getResources().getDisplayMetrics().heightPixels-dp(24)):-2);}
    }
    private void focusAction(){if(confirm.isEnabled())confirm.requestFocus();else cancelButton.requestFocus();}
    private void focusFolders(int index){if(folders!=null&&!folders.isEmpty()){list.requestFocusFromTouch();list.setSelection(index);}else if(confirm.isEnabled())confirm.requestFocusFromTouch();else if(parentButton.isEnabled())parentButton.requestFocusFromTouch();else cancelButton.requestFocusFromTouch();}
    private boolean goParent(){
        File root=Environment.getExternalStorageDirectory(),previous=current;
        if(current==null||current.equals(root))return false;
        File next=current.getParentFile();if(next==null||!next.canRead()||!next.getAbsolutePath().startsWith(root.getAbsolutePath()+"/")&&!next.equals(root))return false;
        current=next;refresh();int index=folders.indexOf(previous);if(index>=0)focusFolders(index);return true;
    }
    private static boolean supported(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|flac|wav|ogg|opus|m4a|aac|wma|ape|aiff|alac|lrc|ttml|yrc|qrc|vtt|txt|png|jpe?g|webp)");}
    private static boolean music(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|flac|wav|ogg|opus|m4a|aac|wma|ape|aiff|alac)");}
    private String counts(File folder){File[] children=folder.listFiles();if(children==null)return "无法读取";int count=0,audio=0;for(File f:children)if(f.isFile()){count++;if(music(f.getName()))audio++;}return count+" 个文件 · "+audio+" 首音乐";}
    private void refresh(){
        files.clear();folders=new ArrayList<>();int total=0,audio=0;File[] children=current.listFiles();
        if(children!=null)for(File file:children){if(file.isDirectory()&&file.canRead())folders.add(file);else if(file.isFile()){total++;if(music(file.getName()))audio++;if(supported(file.getName()))files.add(Uri.fromFile(file));}}
        Collections.sort(folders,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        list.setAdapter(new BaseAdapter(){public int getCount(){return folders.size();}public Object getItem(int p){return folders.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){
                File folder=folders.get(p);LinearLayout row=new LinearLayout(theme);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(12),dp(8),dp(12));row.setMinimumHeight(dp(64));
                ImageView icon=new ImageView(theme);icon.setImageDrawable(new android.graphics.drawable.Drawable(){
                    final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    public void draw(android.graphics.Canvas canvas){android.graphics.Rect r=getBounds();canvas.save();canvas.translate(r.left,r.top);canvas.scale(r.width()/32f,r.height()/32f);paint.setColor(0xff80cbc4);canvas.drawRoundRect(3,6,16,17,2,2,paint);canvas.drawRoundRect(3,10,29,27,2,2,paint);canvas.restore();}
                    public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter f){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
                });icon.setPadding(0,0,dp(12),0);row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(32)));
                LinearLayout text=new LinearLayout(theme);text.setOrientation(LinearLayout.VERTICAL);text.addView(label(folder.getName(),16,0xfff0f2f6));TextView detail=label(counts(folder),12,0xffa6adbb);detail.setPadding(0,dp(4),0,0);text.addView(detail);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(label("›",24,0xffa6adbb));return row;
            }});
        path.setText(current.getAbsolutePath());setSummary(total,audio,children==null?"无法读取此文件夹":null);
        parentButton.setEnabled(!current.equals(Environment.getExternalStorageDirectory()));focusFolders(0);
    }
    private void setSummary(int total,int audio,String error){
        summary.setText(error!=null?error:total+" 个文件 · "+audio+" 首音乐 · 可导入 "+files.size()+" 个文件");
        confirm.setEnabled(error==null&&audio>0&&!files.isEmpty());confirm.setAlpha(confirm.isEnabled()?1f:.4f);confirm.setText("导入此文件夹 ("+files.size()+")");
    }
    private void openTree(){
        // Resolve the optional host capability lazily so existing Android 7 APKs keep working.
        final int token=generation;
        try{
            Class<?> capability=Class.forName("xiao.bu.tv.jar.ChannelPlugin$DirectoryHost",false,host.getClass().getClassLoader());
            if(!capability.isInstance(host))throw new ClassNotFoundException();
            ValueCallback<Uri[]> result=uris->{if(callback==null||generation!=token)return;if(uris==null||uris.length==0)finish(null);else scanTree(uris[0]);};
            capability.getMethod("chooseDirectory",ValueCallback.class).invoke(host,result);
        }catch(Exception e){Toast.makeText(activity,"当前宿主不支持文件夹授权，请更新 APK 后重试",Toast.LENGTH_LONG).show();finish(null);}
    }
    private void scanTree(Uri tree){
        createDialog(false);path.setText("已选择文件夹");summary.setText("正在统计文件…");confirm.setEnabled(false);final int token=generation;
        new Thread(()->{
            List<Uri> found=new ArrayList<>();int total=0,audio=0;String name="已选择文件夹",error=null;
            try{
                String id=DocumentsContract.getTreeDocumentId(tree);Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                try(Cursor c=activity.getContentResolver().query(root,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
                try(Cursor c=activity.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                    if(c==null)throw new java.io.IOException("无法读取此文件夹");
                    while(c.moveToNext()){if(DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)))continue;total++;String n=c.getString(1);if(music(n))audio++;if(supported(n))found.add(DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)));}
                }
            }catch(Exception e){error="无法读取此文件夹，请重新授权";}
            final String title=name,failure=error;final int count=total,songs=audio;
            handler.post(()->{if(callback==null||generation!=token)return;files.clear();files.addAll(found);path.setText(title);setSummary(count,songs,failure);});
        },"folia-folder-count").start();
    }
    void cancel(){generation++;handler.removeCallbacksAndMessages(null);finish(null);}
    private void finish(Uri[] selected){ValueCallback<Uri[]> result=callback;callback=null;generation++;handler.removeCallbacksAndMessages(null);AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();if(result!=null)result.onReceiveValue(selected);}
}
