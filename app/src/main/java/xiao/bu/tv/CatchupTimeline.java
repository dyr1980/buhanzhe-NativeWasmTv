package xiao.bu.tv;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Verifies response media timestamps, never request parameters or HTTP Date. */
final class CatchupTimeline {
    static final int UNKNOWN=0, VERIFIED=1, MISMATCH=2;
    static final class Result {
        final int state;
        final long actual;
        Result(int state,long actual){this.state=state;this.actual=actual;}
    }
    static Result inspect(String playlist,long start,long stop,boolean first) {
        if(playlist==null||playlist.contains("#EXT-X-STREAM-INF"))return new Result(UNKNOWN,-1);
        long elapsed=0,actual=-1;double duration=0;boolean discontinuity=false;
        for(String raw:playlist.split("\n")){
            String line=raw.trim();
            if(line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")){
                long anchor=parseTime(line.substring(line.indexOf(':')+1));
                if(anchor>=0&&!discontinuity){actual=anchor-elapsed;break;}
            }else if(line.startsWith("#EXTINF:")){
                try{duration=Double.parseDouble(line.substring(8).split(",",2)[0]);}
                catch(NumberFormatException ignored){duration=0;}
            }else if(line.equals("#EXT-X-DISCONTINUITY")){
                if(elapsed>0)discontinuity=true;
            }else if(!line.isEmpty()&&!line.startsWith("#")){
                if(duration>0&&duration<3600)elapsed+=Math.round(duration*1000);duration=0;
            }
        }
        if(actual<0)return new Result(UNKNOWN,-1);
        // Permit segment/keyframe alignment, but never an unrelated live window.
        boolean matches=actual>=start-30000&&actual<stop
                &&(!first||actual<=start+30000);
        return new Result(matches?VERIFIED:MISMATCH,actual);
    }
    static long parseTime(String text){
        if(text==null)return -1;
        String value=text.trim();
        if(!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2})"))return -1;
        value=value.replaceFirst("Z$","+0000").replaceFirst("([+-]\\d{2}):(\\d{2})$","$1$2");
        int dot=value.indexOf('.');
        if(dot>=0){int zone=Math.max(value.lastIndexOf('+'),value.lastIndexOf('-'));String fraction=value.substring(dot+1,zone);value=value.substring(0,dot)+"."+(fraction+"000").substring(0,3)+value.substring(zone);}
        SimpleDateFormat format=new SimpleDateFormat(dot>=0?"yyyy-MM-dd'T'HH:mm:ss.SSSZ":"yyyy-MM-dd'T'HH:mm:ssZ",Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);
        ParsePosition position=new ParsePosition(0);Date parsed=format.parse(value,position);
        return parsed!=null&&position.getIndex()==value.length()?parsed.getTime():-1;
    }
}
