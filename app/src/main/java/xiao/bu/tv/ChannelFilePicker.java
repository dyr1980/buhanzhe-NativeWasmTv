package xiao.bu.tv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;

/** File input support for bundled channel websites. One pending selection per Activity. */
final class ChannelFilePicker {
    static final int REQUEST=39671;
    private ValueCallback<Uri[]> pending;
    void open(Activity activity,ValueCallback<Uri[]> callback,String type,boolean multiple) {
        cancel();pending=callback;
        Intent intent=new Intent(android.os.Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(type==null||type.isEmpty()?"*/*":type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(android.os.Build.VERSION.SDK_INT>=18)intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);
        try{activity.startActivityForResult(Intent.createChooser(intent,"选择音乐或歌词文件"),REQUEST);}
        catch(android.content.ActivityNotFoundException error){cancel();}
    }
    boolean result(int request,int result,Intent data) {
        if(request!=REQUEST)return false;
        ValueCallback<Uri[]> callback=pending;pending=null;
        if(callback!=null) {
            Uri[] files=null;
            if(result==Activity.RESULT_OK&&data!=null) {
                if(android.os.Build.VERSION.SDK_INT>=16&&data.getClipData()!=null){files=new Uri[data.getClipData().getItemCount()];for(int i=0;i<files.length;i++)files[i]=data.getClipData().getItemAt(i).getUri();}
                else if(data.getData()!=null)files=new Uri[]{data.getData()};
            }
            callback.onReceiveValue(files);
        }
        return true;
    }
    void cancel(){if(pending!=null){ValueCallback<Uri[]> callback=pending;pending=null;callback.onReceiveValue(null);}}
}
