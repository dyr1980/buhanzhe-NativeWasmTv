package xiao.bu.channel.bl;

import org.json.*;
import java.io.IOException;

/** Normalize progressive segments or independently muxed DASH video/audio for the player. */
final class BlStreams {
    static String url(JSONObject track){return track.optString("baseUrl",track.optString("base_url",track.optString("url")));}
    static JSONArray select(JSONObject data,int quality,String codec,int audioId)throws Exception{
        JSONObject dash=data.optJSONObject("dash");if(dash==null)return BlApi.array(data,"durl");JSONArray videos=BlApi.array(dash,"video"),audios=BlApi.array(dash,"audio");JSONObject chosen=null,audio=null;int score=Integer.MAX_VALUE;
        for(int i=0;i<videos.length();i++){JSONObject v=videos.optJSONObject(i);if(v==null||url(v).isEmpty())continue;String name=v.optString("codecs");boolean avc=v.optInt("codecid",7)==7||name.startsWith("avc");if(codec.equals("AVC")&&!avc)continue;if(codec.equals("HEVC")&&!name.startsWith("hev")&&!name.startsWith("hvc")&&v.optInt("codecid")!=12)continue;
            int q=v.optInt("id"),cost=q==quality?0:q<quality?quality-q:1000+q-quality;if(!avc&&codec.equals("自动"))cost+=2000;if(cost<score){score=cost;chosen=v;}}
        if(chosen==null)throw new IOException("当前视频没有所选编码，请改用 AVC / 自动");
        for(int i=0;i<audios.length();i++){JSONObject a=audios.optJSONObject(i);if(a==null||url(a).isEmpty())continue;String c=a.optString("codecs");if(!c.isEmpty()&&!c.startsWith("mp4a"))continue;if(a.optInt("id")==audioId){audio=a;break;}if(audio==null||a.optInt("bandwidth")>audio.optInt("bandwidth"))audio=a;}
        if(audio==null)throw new IOException("接口未返回可用的 AAC 音轨");
        JSONObject item=new JSONObject().put("url",url(chosen)).put("bl_audio",url(audio)).put("length",(long)(dash.optDouble("duration",data.optDouble("timelength")/1000)*1000)).put("bl_quality",chosen.optInt("id")).put("bl_codec",chosen.optString("codecs"));JSONArray backups=chosen.optJSONArray("backupUrl");if(backups==null)backups=chosen.optJSONArray("backup_url");if(backups!=null)item.put("backup_url",backups);JSONArray audioBackups=audio.optJSONArray("backupUrl");if(audioBackups==null)audioBackups=audio.optJSONArray("backup_url");if(audioBackups!=null)item.put("bl_audio_backups",audioBackups);return new JSONArray().put(item);
    }
}
