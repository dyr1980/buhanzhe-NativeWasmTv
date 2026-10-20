package xiao.bu.channel;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.ValueCallback;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Read-only fallback when a TV/phone has no enabled system document picker. */
final class FoliaLocalPicker {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> callback;
    private AlertDialog dialog;
    private final LinkedHashSet<File> selected=new LinkedHashSet<>();
    private File current;
    private List<File> entries;
    private ListView list;
    private TextView path;
    private boolean multiple;
    FoliaLocalPicker(Activity activity){this.activity=activity;}
    void open(ValueCallback<Uri[]> result,boolean multi){
        cancel();callback=result;multiple=multi;selected.clear();
        if(Build.VERSION.SDK_INT>=33){Toast.makeText(activity,"请启用系统文件选择器后重试",Toast.LENGTH_LONG).show();finish(null);return;}
        if(activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){show();return;}
        activity.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},39672);
        final long started=SystemClock.elapsedRealtime();
        handler.postDelayed(new Runnable(){public void run(){
            if(callback==null)return;
            if(activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){show();return;}
            if(SystemClock.elapsedRealtime()-started>30000||(SystemClock.elapsedRealtime()-started>1000&&activity.hasWindowFocus())){
                Toast.makeText(activity,"需要允许读取存储空间才能选择音乐文件",Toast.LENGTH_LONG).show();finish(null);return;
            }
            handler.postDelayed(this,200);
        }},300);
    }
    private void show(){
        if(callback==null||activity.isFinishing())return;
        current=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if(!current.isDirectory())current=Environment.getExternalStorageDirectory();
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        path=new TextView(activity);path.setPadding(20,12,20,12);body.addView(path);
        Button parent=new Button(activity);parent.setText("上一级");body.addView(parent);
        list=new ListView(activity);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        body.addView(list,new LinearLayout.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.48f)));
        dialog=new AlertDialog.Builder(activity).setTitle("选择音乐/歌词文件").setView(body)
            .setPositiveButton("导入",null).setNegativeButton("取消",(d,w)->finish(null)).create();
        dialog.setOnCancelListener(d->finish(null));dialog.setOnDismissListener(d->{if(callback!=null)finish(null);});
        parent.setOnClickListener(v->{File next=current.getParentFile();if(next!=null&&next.canRead()){current=next;refresh();}});
        list.setOnItemClickListener((p,v,index,id)->{
            File file=entries.get(index);
            if(file.isDirectory()){current=file;refresh();return;}
            if(selected.contains(file))selected.remove(file);else{if(!multiple)selected.clear();selected.add(file);}
            updateSelection();
        });
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(selected.isEmpty())return;
            Uri[] result=new Uri[selected.size()];int i=0;for(File file:selected)result[i++]=Uri.fromFile(file);finish(result);
        });refresh();
    }
    private void refresh(){
        File[] children=current.listFiles();entries=new ArrayList<>();
        if(children!=null)for(File file:children)if(file.isDirectory()||file.getName().toLowerCase(Locale.ROOT).matches(".*\\.(mp3|flac|wav|ogg|opus|m4a|aac|wma|ape|aiff|alac|lrc|ttml|yrc|qrc|vtt|txt|png|jpe?g|webp)"))entries.add(file);
        Collections.sort(entries,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));
        List<String> names=new ArrayList<>();for(File file:entries)names.add((file.isDirectory()?"[文件夹] ":"")+file.getName());
        list.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_multiple_choice,names));
        path.setText(current.getAbsolutePath());updateSelection();
    }
    private void updateSelection(){
        for(int i=0;i<entries.size();i++)list.setItemChecked(i,selected.contains(entries.get(i)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("导入 ("+selected.size()+")");
    }
    void cancel(){handler.removeCallbacksAndMessages(null);finish(null);}
    private void finish(Uri[] files){ValueCallback<Uri[]> result=callback;callback=null;handler.removeCallbacksAndMessages(null);AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();if(result!=null)result.onReceiveValue(files);}
}
