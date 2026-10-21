package xiao.bu.channel.bl;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** newBV v2 live contract: AVC, protocol suitable for the decoder, quality and CDN lines. */
final class BlLiveStreams {
    static JSONObject select(JSONObject data,boolean system)throws Exception{
        JSONObject info=data.optJSONObject("playurl_info"),play=info==null?null:info.optJSONObject("playurl");
        if(play==null)throw new IOException("直播已结束或暂无播放地址");JSONArray streams=BlApi.array(play,"stream");JSONObject chosen=null;
        String[] protocols=system?new String[]{"http_hls"}:new String[]{"http_stream","http_hls"};
        String[] formats=system?new String[]{"ts"}:new String[]{"flv","ts","fmp4"};
        outer:for(String protocol:protocols)for(String format:formats)for(int i=0;i<streams.length();i++){JSONObject s=streams.optJSONObject(i);if(s==null||!s.optString("protocol_name").equals(protocol))continue;JSONArray list=BlApi.array(s,"format");for(int j=0;j<list.length();j++){JSONObject f=list.optJSONObject(j);if(f==null||!f.optString("format_name").equals(format))continue;JSONArray codecs=BlApi.array(f,"codec");for(int k=0;k<codecs.length();k++){JSONObject c=codecs.optJSONObject(k);if(c!=null&&c.optString("codec_name").equals("avc")&&BlApi.array(c,"url_info").length()>0){chosen=c;break outer;}}}}
        if(chosen==null)throw new IOException("直播未提供可用的 AVC 流");ArrayList<String> urls=new ArrayList<>();JSONArray hosts=BlApi.array(chosen,"url_info");for(int i=0;i<Math.min(8,hosts.length());i++){JSONObject host=hosts.optJSONObject(i);if(host==null)continue;String url=host.optString("host")+chosen.optString("base_url")+host.optString("extra");if(!urls.contains(url))urls.add(url);}
        if(urls.isEmpty())throw new IOException("直播线路为空");JSONObject result=new JSONObject().put("quality",chosen.optInt("current_qn")).put("accept_quality",BlApi.array(chosen,"accept_qn")).put("quality_description",BlApi.array(play,"g_qn_desc"));return finish(result,urls);
    }
    static JSONObject legacy(JSONObject data)throws Exception{ArrayList<String> urls=new ArrayList<>();JSONArray list=BlApi.array(data,"durl");for(int i=0;i<Math.min(8,list.length());i++){JSONObject item=list.optJSONObject(i);if(item!=null&&!item.optString("url").isEmpty())urls.add(item.optString("url"));}if(urls.isEmpty())throw new IOException("直播未开播或无可用地址");int quality=data.optInt("current_qn",data.optInt("current_quality"));JSONObject result=new JSONObject().put("quality",quality).put("accept_quality",new JSONArray().put(quality)).put("quality_description",BlApi.array(data,"quality_description"));return finish(result,urls);}
    private static JSONObject finish(JSONObject result,List<String> urls)throws Exception{JSONArray backups=new JSONArray();for(int i=1;i<urls.size();i++)backups.put(urls.get(i));return result.put("durl",new JSONArray().put(new JSONObject().put("url",urls.get(0)).put("backup_url",backups).put("bl_live",true).put("bl_codec","直播 · AVC")));}
    static String quality(JSONObject data,int q){JSONArray names=BlApi.array(data,"quality_description");for(int i=0;i<names.length();i++){JSONObject item=names.optJSONObject(i);if(item!=null&&item.optInt("qn")==q)return item.optString("desc","直播画质 "+q);}return q==0?"自动":"直播画质 "+q;}
}
