package xiao.bu.channel;

import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Device-side metadata provider. Metadata failure must never stop the audio. */
final class MetadataService {
    static JSONObject load(Context context,JSONObject config) {
        JSONObject result=new JSONObject();
        try {
            result.put("title",config.optString("title")).put("artist",config.optString("artist"))
                    .put("lrc","").put("cover","").put("status","");
            File dir=new File(context.getCacheDir(),"folia-metadata");dir.mkdirs();
            StringBuilder key=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(config.toString().getBytes("UTF-8")))key.append(String.format(Locale.US,"%02x",b&255));
            File cache=new File(dir,key+".json");
            if(cache.isFile()&&System.currentTimeMillis()-cache.lastModified()<7L*86400000) {
                try(InputStream in=new FileInputStream(cache)){return new JSONObject(new String(read(in,3*1024*1024),"UTF-8"));}
                catch(Exception ignored){cache.delete();}
            }
            String cover=config.optString("cover"),lrcUrl=config.optString("lyrics");
            String id=config.optString("songId");
            String error="";
            if(lrcUrl.length()==0||cover.length()==0) {
                try {
                    if(id.length()==0)id=findSong(config);
                    if(!id.matches("[0-9]+"))throw new IOException("未找到匹配歌曲，可配置 songId 或 lyrics/cover");
                }catch(Exception e){error=e.getMessage();id="";}
            }
            try {
                if(lrcUrl.length()>0)result.put("lrc",text(lrcUrl));
                else if(id.length()>0) {
                    JSONObject lyric=new JSONObject(text("https://music.163.com/api/song/lyric?id="+id+"&lv=-1&kv=-1&tv=-1"));
                    JSONObject lrc=lyric.optJSONObject("lrc");
                    result.put("lrc",lrc==null?"":lrc.optString("lyric"));
                }
            } catch(Exception e){error="歌词获取失败: "+e.getMessage();}
            try {
                if(cover.length()==0&&id.length()>0) {
                    JSONArray songs=new JSONObject(text("https://music.163.com/api/song/detail/?id="+id+"&ids=%5B"+id+"%5D")).optJSONArray("songs");
                    if(songs!=null&&songs.length()>0)cover=songs.getJSONObject(0).getJSONObject("album").optString("picUrl");
                }
                if(cover.startsWith("http://"))cover="https://"+cover.substring(7);
                if(cover.length()>0) {
                    byte[] bytes=request(cover,null,2*1024*1024);
                    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
                    options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    if(options.outWidth<=0||options.outHeight<=0)throw new IOException("封面不是图片");
                    options.inJustDecodeBounds=false;options.inSampleSize=1;
                    while(options.outWidth/options.inSampleSize>720||options.outHeight/options.inSampleSize>720)options.inSampleSize*=2;
                    android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    if(image==null)throw new IOException("封面解码失败");
                    ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);image.recycle();
                    result.put("cover","data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
                }
            }catch(Exception e){if(error.length()==0)error="封面获取失败: "+e.getMessage();}
            if(result.optString("lrc").length()==0&&error.length()==0)error="暂无歌词";
            result.put("status",error);
            if(error.length()==0) {
                File[] old=dir.listFiles();if(old!=null&&old.length>=64)for(File file:old)if(file.lastModified()<System.currentTimeMillis()-86400000L)file.delete();
                File temp=File.createTempFile("metadata-",".tmp",dir);
                try {try(FileOutputStream out=new FileOutputStream(temp)){out.write(result.toString().getBytes("UTF-8"));}temp.renameTo(cache);}finally{temp.delete();}
            }
        }catch(Exception e){try{result.put("status","元数据获取失败: "+e.getMessage());}catch(JSONException ignored){}}
        return result;
    }
    private static String findSong(JSONObject config)throws Exception {
        String title=config.optString("title"),artist=config.optString("artist");
        JSONArray songs=search(title+" "+artist);
        if(songs==null)return "";
        String best="";double score=0;
        for(int i=0;i<songs.length();i++) {
            JSONObject song=songs.getJSONObject(i);String names="";JSONArray artists=song.optJSONArray("artists");
            if(artists!=null)for(int n=0;n<artists.length();n++)names+=" "+artists.getJSONObject(n).optString("name");
            double current=MatchScore.score(title,artist,song.optString("name"),names,config.optLong("durationMs"),song.optLong("duration"));
            if(current>score){score=current;best=song.optString("id");}
        }
        return score>=75?best:"";
    }
    static JSONArray search(String query)throws Exception {
        JSONObject response=new JSONObject(new String(request("https://music.163.com/api/search/get/",
                "s="+URLEncoder.encode(query,"UTF-8")+"&type=1&limit=20&offset=0",1024*1024),"UTF-8"));
        JSONObject result=response.optJSONObject("result");JSONArray songs=result==null?null:result.optJSONArray("songs");
        return songs==null?new JSONArray():songs;
    }
    private static String text(String url)throws Exception{return new String(request(url,null,1024*1024),"UTF-8");}
    static byte[] request(String url,String body,int limit)throws Exception {
        FoliaChannel.requireHttp(url,"资源");
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0");connection.setRequestProperty("Referer","https://music.163.com/");
        connection.setRequestProperty("Accept-Encoding","identity");
        try {
            if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
            if(connection.getResponseCode()!=200)throw new IOException("HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream()){return read(in,limit);}
        }finally{connection.disconnect();}
    }
    static byte[] read(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>limit)throw new IOException("资源超出大小限制");out.write(block,0,n);}return out.toByteArray();
    }
}
