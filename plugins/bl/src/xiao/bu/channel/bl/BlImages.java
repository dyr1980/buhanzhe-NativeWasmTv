package xiao.bu.channel.bl;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Two bounded decode workers, recycled-view tags and a heap-aware bitmap cache. */
final class BlImages {
    private final BlApi api;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache;
    private final ClassLoader assets;
    private volatile boolean closed;
    BlImages(BlApi api,ClassLoader assets){this.api=api;this.assets=assets;int size=(int)Math.min(6*1024*1024,Runtime.getRuntime().maxMemory()/10);cache=new LruCache<String,Bitmap>(size){protected int sizeOf(String key,Bitmap value){return value.getRowBytes()*value.getHeight();}};}
    Bitmap asset(String name){Bitmap found=cache.get("asset:"+name);if(found!=null)return found;try(InputStream in=assets.getResourceAsStream("assets/"+name)){if(in==null)return null;BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=name.equals("background.png")?Bitmap.Config.RGB_565:Bitmap.Config.ARGB_8888;Bitmap b=BitmapFactory.decodeStream(in,null,o);if(b!=null)cache.put("asset:"+name,b);return b;}catch(Exception e){return null;}}
    void icon(ImageView image,String name){Bitmap bitmap=asset(name+".png");android.graphics.drawable.Drawable drawable=image.getDrawable();if(drawable instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)drawable).getBitmap()==bitmap)return;image.setImageBitmap(bitmap);}
    void load(final ImageView image,String address,final boolean cover){
        if(address==null||address.isEmpty()){image.setTag("");image.setImageBitmap(asset(cover?"default_video.png":"default_avatar.png"));return;}
        if(address.startsWith("//"))address="https:"+address;
        if(address.contains("hdslb.com")&&!address.contains("@"))address+=cover?"@384w_216h_1c.jpg":"@80w_80h_1c.jpg";
        final String url=address;image.setTag(url);Bitmap found=cache.get(url);if(found!=null){image.setImageBitmap(found);return;}
        image.setImageBitmap(asset(cover?"default_video.png":"default_avatar.png"));
        workers.execute(new Runnable(){public void run(){if(closed)return;try{
            Bitmap b=cache.get(url);if(b==null){byte[] bytes=api.bytes(url,3*1024*1024);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);int max=cover?512:128;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>max*2||o.outHeight/o.inSampleSize>max*2)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(b!=null)cache.put(url,b);}
            final Bitmap value=b;if(!closed)main.post(new Runnable(){public void run(){if(!closed&&url.equals(image.getTag())&&value!=null)image.setImageBitmap(value);}});
        }catch(Exception ignored){}}});
    }
    void close(){closed=true;workers.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
