package xiao.bu.tv;

import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** M3U catchup metadata and URL expansion. No network work on the live path. */
final class IptvCatchup {
    static final int MAX_DAYS = 30;
    final String mode, source;
    final double days;
    final long correctionSeconds;
    private static final Pattern ATTR = Pattern.compile("(?:^|\\s)([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    private static final Pattern TOKEN = Pattern.compile("\\$?\\{([^{}]+)\\}");

    IptvCatchup(String mode, String source, double days, long correctionSeconds) {
        this.mode = mode == null ? "" : mode.toLowerCase(Locale.US);
        this.source = source == null ? "" : source;
        this.days = Double.isNaN(days) || Double.isInfinite(days) ? 0 : Math.max(0, Math.min(MAX_DAYS, days));
        this.correctionSeconds = Math.max(-24 * 3600, Math.min(24 * 3600, correctionSeconds));
    }

    static Map<String,String> attributes(String line) {
        Map<String,String> values = new HashMap<>();
        Matcher m = ATTR.matcher(line);
        while (m.find()) values.put(m.group(1).toLowerCase(Locale.US), m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4));
        return values;
    }

    static IptvCatchup from(String line, Map<String,String> defaults, String base) {
        Map<String,String> values = new HashMap<>(defaults); values.putAll(attributes(line));
        String mode = values.get("catchup"), source = values.get("catchup-source");
        if (mode == null && values.containsKey("timeshift")) mode = "shift";
        if (mode == null && source != null && !source.isEmpty()) mode = "default";
        double days = number(values.get("catchup-days"), number(values.get("timeshift"), 7));
        if (source != null && !"append".equalsIgnoreCase(mode) && !source.startsWith("?") && !source.startsWith("&")) {
            try { source = new URL(new URL(base), source).toString(); } catch (Exception ignored) { }
        }
        return new IptvCatchup(mode,source,days,(long)(number(values.get("catchup-correction"),0)*3600));
    }

    private static double number(String value,double fallback) {
        try { double n=Double.parseDouble(value);return Double.isNaN(n)||Double.isInfinite(n)?fallback:n; } catch (Exception ignored) { return fallback; }
    }

    boolean configured() { return !mode.isEmpty() || !source.isEmpty() || days!=7 || correctionSeconds!=0; }
    boolean inferred() { return "auto-playseek".equals(mode)||"auto-xc".equals(mode); }

    /** Guess only media/IPTV URLs; do not contact the provider or rewrite the live URL. */
    static IptvCatchup automatic(String live,IptvCatchup configured) {
        if(live==null||live.isEmpty())return null;
        if(configured!=null){
            if(configured.enabled())return configured;
            if(configured.days<=0 || !configured.source.isEmpty()
                    || !(configured.mode.isEmpty()||"default".equals(configured.mode)||"append".equals(configured.mode)))return null;
        }
        try{
            URL url=new URL(live);String path=url.getPath().toLowerCase(Locale.US);
            if(!("http".equalsIgnoreCase(url.getProtocol())||"https".equalsIgnoreCase(url.getProtocol()))||url.getHost().isEmpty())return null;
            // Script/website parsing endpoints and VOD files must not become fake archive channels.
            boolean hls=path.endsWith(".m3u8"),ts=path.endsWith(".ts");
            boolean operator=path.contains("/pltv/")||path.contains("/pltv2/")||path.contains("/tvod/");
            if(!hls&&!ts&&!operator)return null;
            String mode=path.matches("/live/[^/]+/[^/]+/[0-9]+\\.(ts|m3u8)")?"auto-xc":"auto-playseek";
            return new IptvCatchup(mode,"",configured==null?7:configured.days,configured==null?0:configured.correctionSeconds);
        }catch(MalformedURLException ignored){return null;}
    }

    boolean enabled() {
        return days > 0 && (inferred()||"shift".equals(mode)||"flussonic".equals(mode)||"fs".equals(mode)
                ||"xc".equals(mode)||"xtream-codes".equals(mode)
                ||(("default".equals(mode)||"append".equals(mode))&&!source.isEmpty()));
    }

    boolean available(long start,long end,long now) {
        return enabled() && start > 0 && end > start && start < now
                && start >= now - (long)(days * 86400000L);
    }

    String url(String live,long startMillis,long endMillis,long nowMillis,String catchupId) throws IOException {
        if (!available(startMillis,endMillis,nowMillis)) throw new IOException("节目未开始或已超出回看期限");
        long start=startMillis/1000-correctionSeconds, end=Math.min(endMillis,nowMillis)/1000-correctionSeconds;
        long now=nowMillis/1000, duration=end-start;
        if(duration<=0)throw new IOException("节目回看时长无效");
        String template;
        if(!source.isEmpty())template="append".equals(mode)||source.startsWith("?")||source.startsWith("&")?append(live,source):source;
        else if("auto-playseek".equals(mode)) template=replacePlayseek(live);
        else if("shift".equals(mode)) template=append(live,"utc={utc}&lutc={lutc}");
        else if("flussonic".equals(mode)||"fs".equals(mode)) {
            Matcher m=Pattern.compile("(?i)^(https?://.+/)(?:index|mono)(?:\\.m3u8)(\\?[^#]*)?$").matcher(live);
            if(!m.matches())throw new IOException("Flussonic 直播地址格式不支持，请指定 catchup-source");
            template=m.group(1)+"index-{utc}-{duration}.m3u8"+(m.group(2)==null?"":m.group(2));
        } else if("xc".equals(mode)||"xtream-codes".equals(mode)||"auto-xc".equals(mode)) {
            Matcher m=Pattern.compile("(?i)^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\\d+)\\.(ts|m3u8)(\\?[^#]*)?$").matcher(live);
            if(!m.matches())throw new IOException("Xtream 直播地址格式不支持，请指定 catchup-source");
            template=m.group(1)+"/timeshift/"+m.group(2)+"/"+m.group(3)+"/"+Math.max(1,(duration+59)/60)+"/{utc:Y-m-d:H-M}/"+m.group(4)+"."+m.group(5)+(m.group(6)==null?"":m.group(6));
        } else template="append".equals(mode)||source.startsWith("?")||source.startsWith("&")?append(live,source):source;
        Matcher matcher=TOKEN.matcher(template);StringBuffer out=new StringBuffer();
        while(matcher.find()) {
            String token=matcher.group(1);String value;
            // DIYP-style local date templates use Java patterns, including MM
            // for months and mm for minutes. Keep them separate from UTC tokens.
            if(token.startsWith("(b)")||token.startsWith("(e)")) {
                String pattern=token.substring(3);long time=token.charAt(1)=='b'?start:end;
                if(pattern.isEmpty())value=Long.toString(time);
                else {
                    if(pattern.length()>80)throw new IOException("回看日期格式过长");
                    try{value=new SimpleDateFormat(pattern,Locale.US).format(new Date(time*1000));}
                    catch(IllegalArgumentException bad){throw new IOException("回看日期格式无效",bad);}
                }
            } else {
                String[] part=token.split(":",2);String key=part[0],format=part.length>1?part[1]:null;
                long time;
                if("catchup-id".equals(key)) {if(catchupId==null||catchupId.isEmpty())throw new IOException("节目单缺少回看标识");value=catchupId;}
                else if("duration".equals(key)||"offset".equals(key)) {
                    long amount="duration".equals(key)?duration:Math.max(0,now-start);
                    try{long divisor=format==null?1:Long.parseLong(format);if(divisor<=0)throw new NumberFormatException();value=Long.toString(amount/divisor);}catch(NumberFormatException bad){throw new IOException("回看时间单位无效");}
                } else {
                    if("utc".equals(key)||"start".equals(key))time=start;
                    else if("utcend".equals(key)||"end".equals(key))time=end;
                    else if("lutc".equals(key)||"now".equals(key)||"timestamp".equals(key))time=now;
                    else if(key.length()==1&&"YmdHMS".contains(key)){time=start;format=key;}
                    else throw new IOException("不支持的回看占位符："+key);
                    value=format==null?Long.toString(time):format(time,format);
                }
            }
            matcher.appendReplacement(out,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        String result=out.toString().replace(" ","%20");
        if(result.indexOf('\r')>=0||result.indexOf('\n')>=0||result.indexOf('{')>=0||result.indexOf('}')>=0)throw new IOException("回看地址无效");
        try { URL address=new URL(result);if(!"http".equalsIgnoreCase(address.getProtocol())&&!"https".equalsIgnoreCase(address.getProtocol()))throw new IOException("回看仅支持 HTTP/HTTPS 地址"); }
        catch(MalformedURLException bad){throw new IOException("回看地址无效",bad);}
        return result;
    }

    private static String format(long seconds,String pattern) throws IOException {
        if(pattern.length()>80)throw new IOException("回看日期格式过长");
        StringBuilder result=new StringBuilder();String tokens="YmdHMS";String[] formats={"yyyy","MM","dd","HH","mm","ss"};
        for(char c:pattern.toCharArray()){int i=tokens.indexOf(c);if(i<0)result.append(c);else result.append(new SimpleDateFormat(formats[i],Locale.US).format(new Date(seconds*1000)));}
        return result.toString();
    }

    private static String replacePlayseek(String live) {
        int hash=live.indexOf('#');String fragment=hash<0?"":live.substring(hash),base=hash<0?live:live.substring(0,hash);
        int query=base.indexOf('?');String path=query<0?base:base.substring(0,query);StringBuilder kept=new StringBuilder();
        if(query>=0)for(String item:base.substring(query+1).split("&")){
            String key=item.split("=",2)[0];try{key=URLDecoder.decode(key,"UTF-8");}catch(Exception ignored){}
            if(item.isEmpty()||"playseek".equalsIgnoreCase(key))continue;
            if(kept.length()>0)kept.append('&');kept.append(item);
        }
        if(kept.length()>0)kept.append('&');
        return path+"?"+kept+"playseek={utc:YmdHMS}-{utcend:YmdHMS}"+fragment;
    }

    private static String append(String live,String query) {
        int hash=live.indexOf('#');String fragment=hash<0?"":live.substring(hash);String base=hash<0?live:live.substring(0,hash);
        if(query.startsWith("?")||query.startsWith("&"))query=query.substring(1);
        return base+(base.endsWith("?")||base.endsWith("&")?"":base.contains("?")?"&":"?")+query+fragment;
    }

    // Private SQLite/snapshot format; escaping keeps templates and signed URLs lossless.
    static String encode(Map<String,IptvCatchup> entries) {
        if(entries.isEmpty())return "";
        StringBuilder out=new StringBuilder();
        try{for(Map.Entry<String,IptvCatchup> row:entries.entrySet()){IptvCatchup c=row.getValue();out.append(URLEncoder.encode(row.getKey(),"UTF-8")).append('\t').append(c.mode).append('\t').append(URLEncoder.encode(c.source,"UTF-8")).append('\t').append(c.days).append('\t').append(c.correctionSeconds).append('\n');}}catch(Exception impossible){throw new IllegalStateException(impossible);}
        return out.toString();
    }
    static Map<String,IptvCatchup> decode(String text) {
        Map<String,IptvCatchup> result=new LinkedHashMap<>();if(text==null||text.length()>65536)return result;
        for(String line:text.split("\n")){try{String[] p=line.split("\t",-1);if(p.length!=5)continue;IptvCatchup c=new IptvCatchup(p[1],URLDecoder.decode(p[2],"UTF-8"),Double.parseDouble(p[3]),Long.parseLong(p[4]));if(c.configured())result.put(URLDecoder.decode(p[0],"UTF-8"),c);}catch(Exception ignored){}}
        return result;
    }
}
