package xiao.bu.tv;

import java.io.*;
import java.util.*;

/** A stable, monotonically numbered HLS timeline for matching single-program TS feeds. */
final class HlsSwitchSession {
    interface Transport {
        String playlist(String url)throws IOException;
        String origin(String rewrittenUrl)throws IOException;
        File segment(String url)throws IOException;
        default void prefetch(List<String> urls)throws IOException {}
        default void release(File file) {}
        boolean safe(String url);
    }
    static final class Part {
        final String url;final double seconds;final long originSequence;
        Part(String url,double seconds,long sequence){this.url=url;this.seconds=seconds;originSequence=sequence;}
    }
    static final class Prepared {
        final String originUrl,mediaUrl;final List<Part> parts;final int first;
        final boolean eligible;final TsSwitchProfile profile;
        Prepared(String origin,String media,List<Part> parts,int first,boolean eligible,TsSwitchProfile profile) {
            originUrl=origin;mediaUrl=media;this.parts=parts;this.first=first;this.eligible=eligible;this.profile=profile;
        }
    }
    static Prepared prepare(Transport transport,String origin)throws IOException {
        String media=origin,text="";boolean eligible=true;
        for(int depth=0;depth<6;depth++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Channel warmup cancelled");
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
                && !text.contains("#EXT-X-DISCONTINUITY") && !text.contains("#EXT-X-KEY:")
                && !text.contains("#EXT-X-I-FRAMES-ONLY");
        // Warmup is a single-program live TS optimization. VOD, fMP4,
        // byte ranges and separate renditions need the player's demuxer and
        // initialization data; never reject them using a bare TS segment probe.
        if (!eligible) return null;
        List<Part> parts=parse(transport,text);
        if(parts.isEmpty())throw new IOException("HLS playlist has no media segments");
        // Align with the usual live start (-3); warm an actual random-access segment.
        int first = Math.max(0, parts.size() - 3);
        List<String> urls=new ArrayList<String>();for(int n=first;n<Math.min(parts.size(),first+2);n++)urls.add(parts.get(n).url);
        transport.prefetch(urls);
        File file=transport.segment(parts.get(first).url);
        TsSwitchProfile profile;
        try{profile=transport.safe(parts.get(first).url)?TsSwitchProfile.inspect(file):null;}
        finally{transport.release(file);}
        return new Prepared(origin,media,parts,first,profile!=null,profile);
    }
    private static List<Part> parse(Transport transport,String text)throws IOException {
        List<Part> parts=new ArrayList<Part>();long seq=0;double duration=-1;
        for(String raw:text.split("\\r?\\n")) {
            String line=raw.trim();
            try {
                if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:"))seq=Long.parseLong(line.substring(22));
                else if(line.startsWith("#EXTINF:"))duration=Double.parseDouble(line.substring(8).split(",",2)[0]);
                else if(line.length()>0&&!line.startsWith("#")) {
                    if(!(duration>0&&duration<=60))throw new IOException("Invalid HLS segment duration");
                    parts.add(new Part(transport.origin(line),duration,seq++));duration=-1;
                }
            }catch(NumberFormatException error){throw new IOException("Invalid HLS playlist number",error);}
        }
        return parts;
    }
    static final class Entry {
        final long sequence,epoch,startMs,offset90k;final Part part;final boolean boundary;
        Entry(long sequence,long epoch,long startMs,long offset90k,Part part,boolean boundary) {
            this.sequence=sequence;this.epoch=epoch;this.startMs=startMs;this.offset90k=offset90k;
            this.part=part;this.boundary=boundary;
        }
        long endMs(){return startMs+Math.round(part.seconds*1000);}
    }
    private final Transport transport;
    private final TreeMap<Long,Entry> entries=new TreeMap<Long,Entry>();
    private final String base;
    private final int targetDuration;
    private Prepared active;
    private String currentSignature;
    private long epoch=1,nextSequence,reservedThrough=-1,lastOriginSequence=-1,lastReloadAt;
    private long boundaryMs=-1,boundaryEpoch,deliveredEpoch;
    private long removedDiscontinuities;
    private volatile boolean closed;
    HlsSwitchSession(Transport transport,String base,Prepared prepared) {
        this.transport=transport;this.base=base;active=prepared;currentSignature=prepared.profile.signature;
        int duration=1;for(Part part:prepared.parts)duration=Math.max(duration,(int)Math.ceil(part.seconds));
        targetDuration=duration;
        append(prepared,true);lastReloadAt=System.nanoTime();
    }
    private void append(Prepared prepared,boolean channelBoundary) {
        long start=entries.isEmpty()?0:entries.lastEntry().getValue().endMs();
        long offset=(90000+start*90-prepared.profile.anchor)&TsSwitchProfile.MASK;
        for(int n=prepared.first;n<prepared.parts.size();n++) {
            Part part=prepared.parts.get(n);
            Entry entry=new Entry(nextSequence++,epoch,start,offset,part,channelBoundary&&n==prepared.first&&epoch>1);
            entries.put(entry.sequence,entry);start=entry.endMs();lastOriginSequence=part.originSequence;
        }
    }
    synchronized boolean compatible(Prepared prepared) {
        if(prepared==null)return false;
        for(Part part:prepared.parts)if(Math.ceil(part.seconds)>targetDuration)return false;
        return !closed&&prepared.eligible&&currentSignature!=null
                &&currentSignature.equals(prepared.profile.signature);
    }
    synchronized long switchTo(Prepared prepared) throws IOException {
        return switchTo(prepared,0,Long.MAX_VALUE);
    }
    synchronized long switchTo(Prepared prepared,long playbackMs,long maxWaitMs) throws IOException {
        if(!compatible(prepared))throw new IOException("HLS decoder parameters changed");
        Entry reserved=entries.get(reservedThrough);
        long nextStart=reserved==null?0:reserved.endMs();
        // Live HLS seek is unsupported in the bundled demuxer. A long compressed
        // queue must reopen from the warmed IDR rather than discard reference frames.
        if(nextStart+prepared.profile.videoOffsetMs-playbackMs>maxWaitMs)
            throw new IOException("HLS queued handoff exceeds "+maxWaitMs+" ms");
        // Reserved requests may already be in the decoder queue. Never change their bytes.
        Iterator<Map.Entry<Long,Entry>> iterator=entries.entrySet().iterator();
        while(iterator.hasNext())if(iterator.next().getKey()>reservedThrough)iterator.remove();
        nextSequence=reservedThrough+1;
        long start=entries.isEmpty()?0:entries.lastEntry().getValue().endMs();
        active=prepared;epoch++;lastOriginSequence=-1;append(prepared,true);
        boundaryMs=start+prepared.profile.videoOffsetMs;boundaryEpoch=epoch;
        lastReloadAt=System.nanoTime();return boundaryMs;
    }
    synchronized boolean canHandoffWithin(long playbackMs,long maxWaitMs) {
        Entry reserved=entries.get(reservedThrough);
        return currentSignature!=null && (reserved==null?0:reserved.endMs())-playbackMs<=maxWaitMs;
    }
    synchronized boolean targetDelivered(){return boundaryEpoch>0&&deliveredEpoch>=boundaryEpoch;}
    synchronized long epoch(){return epoch;}
    String playlist()throws IOException {
        Prepared source;long version,previousOrigin;boolean reload;
        synchronized(this) {
            if(closed)throw new IOException("HLS switch session closed");
            source=active;version=epoch;previousOrigin=lastOriginSequence;
            reload=System.nanoTime()-lastReloadAt>750000000L;
            if(reload)lastReloadAt=System.nanoTime();
        }
        if(reload) {
            String text=transport.playlist(source.mediaUrl);
            if(text.contains("#EXT-X-ENDLIST")||text.contains("#EXT-X-MAP:")
                    ||text.contains("#EXT-X-BYTERANGE:")||text.contains("#EXT-X-KEY:")
                    ||text.contains("#EXT-X-DISCONTINUITY"))throw new IOException("HLS format changed during switch session");
            List<Part> fresh=parse(transport,text);
            int firstNew=0;while(firstNew<fresh.size()&&fresh.get(firstNew).originSequence<=previousOrigin)firstNew++;
            Prepared resumed=null;
            if(firstNew<fresh.size()&&previousOrigin>=0&&fresh.get(firstNew).originSequence>previousOrigin+1) {
                // A short upstream live window can advance while a decoder opens.
                // Resume from an independently verified random-access segment and
                // declare the missing interval, rather than returning HTTP errors
                // forever. Previously reserved entries remain immutable.
                File file=transport.segment(fresh.get(firstNew).url);TsSwitchProfile profile;
                try{profile=transport.safe(fresh.get(firstNew).url)?TsSwitchProfile.inspect(file):null;}
                finally{transport.release(file);}
                resumed=new Prepared(source.originUrl,source.mediaUrl,fresh,firstNew,profile!=null,profile);
            }
            synchronized(this) {
                if(epoch==version&&!closed) {
                    if(resumed!=null&&lastOriginSequence==previousOrigin) {
                        if(!compatible(resumed))throw new IOException("HLS decoder parameters changed across missing interval");
                        active=resumed;epoch++;lastOriginSequence=-1;append(resumed,true);
                    }
                    for(Part part:fresh)if(part.originSequence>lastOriginSequence) {
                        if(Math.ceil(part.seconds)>targetDuration)throw new IOException("HLS target duration changed");
                        if(lastOriginSequence>=0&&part.originSequence!=lastOriginSequence+1)
                            throw new IOException("HLS origin window skipped a segment");
                        Entry tail=entries.lastEntry().getValue();
                        entries.put(nextSequence,new Entry(nextSequence++,epoch,tail.endMs(),tail.offset90k,part,false));
                        lastOriginSequence=part.originSequence;
                    }
                }
            }
        }
        synchronized(this) {
            // Metadata only; no hour of video is buffered in memory. Retain seek numbering
            // on old platform players, then slide a bounded hour-sized window.
            while(entries.size()>900 && entries.firstKey()<reservedThrough-6) {
                if(entries.pollFirstEntry().getValue().boundary)removedDiscontinuities++;
            }
            StringBuilder out=new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:")
                    .append(targetDuration).append("\n#EXT-X-MEDIA-SEQUENCE:").append(entries.firstKey())
                    .append("\n#EXT-X-DISCONTINUITY-SEQUENCE:").append(removedDiscontinuities).append('\n');
            for(Entry e:entries.values()) {
                if(e.boundary)out.append("#EXT-X-DISCONTINUITY\n");
                out.append("#EXTINF:").append(e.part.seconds).append(",\n")
                        .append(base).append(e.sequence).append(".ts\n");
            }
            return out.toString();
        }
    }
    Entry reserve(long sequence)throws IOException {
        synchronized(this) {
            Entry entry=entries.get(sequence);
            if(entry==null||closed)throw new IOException("Expired HLS switch segment");
            reservedThrough=Math.max(reservedThrough,sequence);return entry;
        }
    }
    File file(Entry entry)throws IOException {
        File file=transport.segment(entry.part.url);
        TsSwitchProfile profile;
        try{profile=TsSwitchProfile.inspect(file);}catch(IOException|RuntimeException error){transport.release(file);throw error;}
        synchronized(this){if(entry.epoch==epoch)currentSignature=profile==null?null:profile.signature;}
        return file;
    }
    void release(File file){transport.release(file);}
    synchronized void delivered(Entry entry){deliveredEpoch=Math.max(deliveredEpoch,entry.epoch);}
    void copy(Entry entry,File file,OutputStream output)throws IOException {
        boolean[] seen=entry.boundary?new boolean[8192]:null;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file),32768))) {
            byte[] packet=new byte[188];
            while(in.available()>0) {
                if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException("HLS switch cancelled");
                in.readFully(packet);TsSwitchProfile.rewrite(packet,entry.offset90k);
                int pid=((packet[1]&31)<<8)|(packet[2]&255);
                if(seen!=null&&!seen[pid]){seen[pid]=true;output.write(TsSwitchProfile.discontinuityPacket(packet));}
                output.write(packet);
            }
        }
    }
    void close(){closed=true;}
}
