package xiao.bu.tv;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Conservative decoder compatibility and timestamp surgery; coded samples stay intact. */
final class TsSwitchProfile {
    static final long MASK = (1L << 33) - 1;
    final String signature;
    final long anchor, videoOffsetMs;
    final boolean hasAudio;
    private TsSwitchProfile(String signature, long anchor, long videoPts, boolean hasAudio) {
        this.signature=signature; this.anchor=anchor;
        this.hasAudio=hasAudio;
        videoOffsetMs=Math.max(0, distance(videoPts,anchor)/90);
    }
    static long distance(long a,long b) {
        long d=(a-b)&MASK; return d>(1L<<32)?d-(1L<<33):d;
    }
    static int payload(byte[] p,int o) {
        int control=(p[o+3]>>4)&3;
        if((control&1)==0)return o+188;
        return o+4+((control&2)!=0?1+(p[o+4]&255):0);
    }
    static long pts(byte[] p,int o) {
        return ((long)(p[o]&14)<<29)|((long)(p[o+1]&255)<<22)
                |((long)(p[o+2]&254)<<14)|((long)(p[o+3]&255)<<7)|((p[o+4]&254)>>1);
    }
    private static void putPts(byte[] p,int o,long value) {
        value&=MASK;
        p[o]=(byte)((p[o]&241)|((value>>29)&14));p[o+1]=(byte)(value>>22);
        p[o+2]=(byte)(((value>>14)&254)|1);p[o+3]=(byte)(value>>7);
        p[o+4]=(byte)(((value<<1)&254)|1);
    }
    static TsSwitchProfile inspect(File file)throws IOException {
        byte[] b=new byte[(int)Math.min(file.length(),768*1024)];
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(b);}
        if(b.length<188*3 || (file.length()%188)!=0)return null;
        int pmt=-1,video=-1,audio=-1,codec=0,programs=0;
        byte[] pat=null,tracks=null;
        for(int o=0;o+188<=b.length;o+=188) {
            if(b[o]!=0x47 || (b[o+3]&192)!=0)return null;
            int pid=((b[o+1]&31)<<8)|(b[o+2]&255),s=payload(b,o);
            if(s>=o+188 || (b[o+1]&64)==0)continue;
            if(pid==0 || pid==pmt) {
                s+=1+(b[s]&255);
                if(s+8>=o+188)return null;
                int end=s+3+(((b[s+1]&15)<<8)|(b[s+2]&255))-4;
                if(end>o+188)return null; // Multi-packet PSI needs a full demuxer: reopen safely.
                if(pid==0 && b[s]==0) {
                    programs=0;
                    for(int n=s+8;n+4<=end;n+=4)if(b[n]!=0||b[n+1]!=0) {
                        programs++;pmt=((b[n+2]&31)<<8)|(b[n+3]&255);
                        pat=new byte[]{b[s+3],b[s+4],b[n],b[n+1],b[n+2],b[n+3]};
                    }
                    if(programs!=1)return null;
                } else if(pid==pmt && b[s]==2) {
                    int start=s+12+(((b[s+10]&15)<<8)|(b[s+11]&255));
                    if(start>end)return null;
                    ByteArrayOutputStream normalized=new ByteArrayOutputStream();
                    normalized.write(b,s+3,2);normalized.write(b,s+8,end-(s+8));
                    tracks=normalized.toByteArray();
                    video=audio=-1;
                    for(int n=start;n+5<=end;) {
                        int type=b[n]&255,id=((b[n+1]&31)<<8)|(b[n+2]&255);
                        if(type==27||type==36){if(video!=-1)return null;video=id;codec=type;}
                        else if(type==15){if(audio!=-1)return null;audio=id;}
                        else return null; // Alternate audio, subtitles, MP2, AC3 etc. use reopen.
                        n+=5+(((b[n+3]&15)<<8)|(b[n+4]&255));
                        if(n>end)return null;
                    }
                    break;
                }
            }
        }
        if(video<0||tracks==null||pat==null)return null;
        ByteArrayOutputStream es=new ByteArrayOutputStream(),aac=new ByteArrayOutputStream();
        long firstVideo=-1,firstDts=-1,firstAudio=-1;
        for(int o=0;o+188<=b.length;o+=188) {
            int pid=((b[o+1]&31)<<8)|(b[o+2]&255),s=payload(b,o);
            if(pid!=video&&pid!=audio||s>=o+188)continue;
            if((b[o+1]&64)!=0) {
                if(s+9>o+188||b[s]!=0||b[s+1]!=0||b[s+2]!=1)return null;
                int flags=(b[s+7]>>6)&3,skip=9+(b[s+8]&255);
                if(s+skip>o+188 || flags==1 || flags>=2&&skip<(flags==3?19:14))return null;
                if(flags>=2) {
                    long time=pts(b,s+9),dts=flags==3?pts(b,s+14):time;
                    if(pid==video&&firstVideo<0){firstVideo=time;firstDts=dts;}
                    if(pid==audio&&firstAudio<0)firstAudio=time;
                }
                s+=skip;
            }
            (pid==video?es:aac).write(b,s,o+188-s);
        }
        if(firstVideo<0 || audio>=0&&firstAudio<0)return null;
        TreeMap<Integer,byte[]> configuration=new TreeMap<Integer,byte[]>();
        byte[] coded=es.toByteArray();boolean randomAccess=false,firstPicture=true;
        for(int n=0;n+5<coded.length;n++)if(coded[n]==0&&coded[n+1]==0&&coded[n+2]==1) {
            int start=n+3,end=start+1;
            while(end+3<coded.length && !(coded[end]==0&&coded[end+1]==0&&coded[end+2]==1))end++;
            if(end+3>=coded.length)break;
            while(end>start&&coded[end-1]==0)end--;
            int type=codec==27?(coded[start]&31):((coded[start]>>1)&63);
            boolean picture=codec==27&&type>=1&&type<=5 || codec==36&&type<=31;
            if(picture&&firstPicture) {
                randomAccess=codec==27&&type==5 || codec==36&&(type==19||type==20);
                firstPicture=false;if(!randomAccess)return null;
            }
            boolean parameter=codec==27&&(type==7||type==8)||codec==36&&(type>=32&&type<=34);
            if(parameter) {
                byte[] value=Arrays.copyOfRange(coded,start,end),old=configuration.get(type);
                if(old!=null&&!Arrays.equals(value,old))return null;
                configuration.put(type,value);
            }
            // Rendering metadata matters too. Unsupported/dynamic SEI uses full reopen.
            if(codec==27&&type==6 || codec==36&&(type==39||type==40)) {
                ByteArrayOutputStream rbsp=new ByteArrayOutputStream();int zeros=0;
                for(int j=start+(codec==27?1:2);j<end;j++) {
                    int v=coded[j]&255;if(zeros>=2&&v==3){zeros=0;continue;}
                    rbsp.write(v);zeros=v==0?zeros+1:0;
                }
                byte[] sei=rbsp.toByteArray();int j=0;
                while(j+1<sei.length) {
                    int t=0,size=0,v;
                    do{if(j>=sei.length)return null;v=sei[j++]&255;t+=v;}while(v==255);
                    do{if(j>=sei.length)return null;v=sei[j++]&255;size+=v;}while(v==255);
                    if(j+size>sei.length)return null;
                    if(t==4)return null;
                    if(t==137||t==144||t==147)configuration.put(1000+t,Arrays.copyOfRange(sei,j,j+size));
                    j+=size;
                }
            }
        }
        if(!randomAccess || !configuration.containsKey(codec==27?7:32)
                || !configuration.containsKey(codec==27?8:33)
                || codec==36&&!configuration.containsKey(34))return null;
        byte[] audioConfig=null;
        if(audio>=0) {
            byte[] data=aac.toByteArray();
            for(int n=0;n+7<data.length;n++)if((data[n]&255)==255&&(data[n+1]&246)==240) {
                if((data[n+6]&3)!=0)return null;
                if(((data[n+2]&1)<<2|((data[n+3]>>6)&3))==0)return null; // PCE needs full audio parsing.
                audioConfig=new byte[]{(byte)(data[n+1]&9),(byte)(data[n+2]&253),(byte)(data[n+3]&192)};break;
            }
            if(audioConfig==null)return null;
        }
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(pat);digest.update(tracks);
            for(Map.Entry<Integer,byte[]> item:configuration.entrySet()) {
                digest.update((byte)(item.getKey()>>8));digest.update(item.getKey().byteValue());digest.update(item.getValue());
            }
            if(audioConfig!=null)digest.update(audioConfig);
            StringBuilder key=new StringBuilder();for(byte v:digest.digest())key.append(String.format(Locale.US,"%02x",v&255));
            long anchor=firstDts;
            if(firstAudio>=0&&distance(firstAudio,anchor)<0)anchor=firstAudio;
            return new TsSwitchProfile(key.toString(),anchor,firstVideo,audioConfig!=null);
        } catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
    }
    static void rewrite(byte[] p,long delta)throws IOException {
        if(p[0]!=0x47)throw new IOException("Not an aligned MPEG-TS packet");
        if(((p[3]>>4)&2)!=0 && (p[4]&255)>=7 && (p[5]&16)!=0) {
            long base=((long)(p[6]&255)<<25)|((long)(p[7]&255)<<17)|((long)(p[8]&255)<<9)
                    |((long)(p[9]&255)<<1)|((p[10]&128)>>7);
            base=(base+delta)&MASK;p[6]=(byte)(base>>25);p[7]=(byte)(base>>17);p[8]=(byte)(base>>9);
            p[9]=(byte)(base>>1);p[10]=(byte)((p[10]&127)|((base&1)<<7));
        }
        int s=payload(p,0);
        if((p[1]&64)!=0 && s+9<=188 && p[s]==0&&p[s+1]==0&&p[s+2]==1) {
            int flags=(p[s+7]>>6)&3;
            if(flags>=2) {
                if(s+(flags==3?19:14)>188)throw new IOException("Split PES timestamp header");
                putPts(p,s+9,pts(p,s+9)+delta);
                if(flags==3)putPts(p,s+14,pts(p,s+14)+delta);
            }
        }
    }
    static byte[] discontinuityPacket(byte[] first) {
        byte[] p=new byte[188];Arrays.fill(p,(byte)255);
        p[0]=0x47;p[1]=(byte)(first[1]&31);p[2]=first[2];
        p[3]=(byte)(32|((first[3]-1)&15));p[4]=(byte)183;p[5]=(byte)128;return p;
    }
}
