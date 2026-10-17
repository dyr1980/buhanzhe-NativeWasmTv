package xiao.bu.tv;
import java.io.*;
import java.util.*;
/** Read only HLS manifests to select a safe native-direct live media playlist. */
final class HlsStartupManifest {
    private static final java.util.regex.Pattern DISCONTINUITY_TAG = java.util.regex.Pattern.compile("(?m)^\\s*#EXT-X-DISCONTINUITY[\\t \\r]*$");
    interface Transport {String playlist(String url)throws IOException;String origin(String url)throws IOException;}
    static final class Result {
        final String originUrl,mediaUrl;final List<String> segmentUrls;
        Result(String origin,String media,List<String> segmentUrls){originUrl=origin;mediaUrl=media;this.segmentUrls=segmentUrls;}
    }
    interface NativeUrlPolicy { boolean accepts(String url); }

    /** Uses only the manifest already requested by the player; never performs I/O. */
    static String directMediaPlaylist(String origin, String body, NativeUrlPolicy policy) {
        if (!body.startsWith("#EXTM3U") || body.contains("#EXT-X-STREAM-INF:")
                || body.contains("#EXT-X-KEY:") || body.contains("#EXT-X-MAP:")
                || body.contains("#EXT-X-BYTERANGE:") || body.contains("#EXT-X-ENDLIST")
                || body.contains("#EXT-X-PLAYLIST-TYPE:") || body.contains("#EXT-X-MEDIA:")
                || body.contains("#EXT-X-I-FRAMES-ONLY") || body.contains("URI=")
                || DISCONTINUITY_TAG.matcher(body).find()) return null;
        StringBuilder result = new StringBuilder(body.length());
        int segments = 0;
        double duration = -1;
        try {
            java.net.URI base = java.net.URI.create(origin);
            for (String line : body.split("\\r?\\n")) {
                String value = line.trim();
                if (value.startsWith("#EXTINF:"))
                    duration = Double.parseDouble(value.substring(8).split(",", 2)[0]);
                if (!value.isEmpty() && !value.startsWith("#")) {
                    if (!(duration > 0 && duration <= 60)) return null;
                    String url = HlsUri.resolve(base, value).toString();
                    if (!policy.accepts(url)) return null;
                    result.append(url);
                    segments++;
                    duration = -1;
                } else result.append(line);
                result.append('\n');
            }
        } catch (IllegalArgumentException invalid) { return null; }
        return segments > 0 ? result.toString() : null;
    }

    static Result prepare(Transport transport,String origin)throws IOException {
        String media=origin,text="";boolean eligible=true;
        for(int depth=0;depth<6;depth++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Manifest preparation cancelled");
            text=transport.playlist(media);
            if(!text.trim().startsWith("#EXTM3U"))throw new IOException("Not an HLS playlist");
            if(!text.contains("#EXT-X-STREAM-INF:"))break;
            eligible &= !text.contains("#EXT-X-MEDIA:");
            String variant=null;
            for(String line:text.split("\\r?\\n"))if(line.trim().length()>0&&!line.trim().startsWith("#")) {
                variant=transport.origin(line.trim());break;
            }
            if(variant==null||variant.equals(media))throw new IOException("HLS master has no usable variant");
            media=variant;
            if(depth==5)throw new IOException("HLS master nesting limit");
        }
        eligible &= !text.contains("#EXT-X-ENDLIST") && !text.contains("#EXT-X-PLAYLIST-TYPE:")
                && !text.contains("#EXT-X-MAP:") && !text.contains("#EXT-X-BYTERANGE:")
                && !DISCONTINUITY_TAG.matcher(text).find() && !text.contains("#EXT-X-KEY:")
                && !text.contains("#EXT-X-I-FRAMES-ONLY");
        // Special media layouts retain the normal demuxer and initialization path.
        if (!eligible) return null;
        List<String> segmentUrls=parse(transport,text);
        if(segmentUrls.isEmpty())throw new IOException("HLS playlist has no media segments");
        return new Result(origin,media,segmentUrls);
    }
    private static List<String> parse(Transport transport,String text)throws IOException {
        List<String> segmentUrls=new ArrayList<String>();double duration=-1;
        for(String raw:text.split("\\r?\\n")) {
            String line=raw.trim();
            try {
                if(line.startsWith("#EXTINF:"))duration=Double.parseDouble(line.substring(8).split(",",2)[0]);
                else if(line.length()>0&&!line.startsWith("#")) {
                    if(!(duration>0&&duration<=60))throw new IOException("Invalid HLS segment duration");
                    segmentUrls.add(transport.origin(line));duration=-1;
                }
            }catch(NumberFormatException error){throw new IOException("Invalid HLS playlist number",error);}
        }
        return segmentUrls;
    }
}
