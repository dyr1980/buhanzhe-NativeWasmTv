package xiao.bu.channel.bl;

import java.io.*;
import java.util.*;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

/** Bounded readers for the Web dm protobuf contract and legacy XML fallback. */
final class BlDanmakuData {
    static final class Line {long at,id;String text="";int mode=1,color=0xffffffff,size=25,weight;}
    static final class Meta {long pageMs=360000;int total;boolean disabled;}
    static final class Reader {
        final byte[] bytes;int pos;final int end;
        Reader(byte[] bytes){this(bytes,0,bytes.length);}
        Reader(byte[] bytes,int from,int to){this.bytes=bytes;pos=from;end=to;}
        long number()throws IOException{long n=0;for(int shift=0;shift<64;shift+=7){if(pos>=end)throw new EOFException();int b=bytes[pos++]&255;n|=(long)(b&127)<<shift;if((b&128)==0)return n;}throw new IOException("无效弹幕整数");}
        Reader child()throws IOException{long size=number();if(size<0||size>end-pos)throw new IOException("无效弹幕长度");Reader child=new Reader(bytes,pos,pos+(int)size);pos+=(int)size;return child;}
        String text()throws IOException{Reader c=child();if(c.end-c.pos>4096)throw new IOException("弹幕文字过长");return new String(bytes,c.pos,c.end-c.pos,"UTF-8");}
        void skip(int tag)throws IOException{int type=tag&7;if(type==0){number();return;}if(type==2){child();return;}int size=type==1?8:type==5?4:-1;if(size<0||size>end-pos)throw new IOException("无效弹幕字段");pos+=size;}
    }
    static Meta meta(byte[] bytes)throws IOException{
        Reader r=new Reader(bytes);Meta meta=new Meta();while(r.pos<r.end){int tag=(int)r.number();if(tag==8)meta.disabled=r.number()==1;else if(tag==34){Reader config=r.child();while(config.pos<config.end){int key=(int)config.number();if(key==8)meta.pageMs=config.number();else if(key==16)meta.total=(int)Math.min(10000,config.number());else config.skip(key);}}else r.skip(tag);}
        if(meta.pageMs<1000||meta.pageMs>3600000)meta.pageMs=360000;return meta;
    }
    static List<Line> protobuf(byte[] bytes)throws IOException{
        Reader r=new Reader(bytes);ArrayList<Line> lines=new ArrayList<>();while(r.pos<r.end&&lines.size()<6000){int tag=(int)r.number();if(tag!=10){r.skip(tag);continue;}Reader elem=r.child();Line line=new Line();while(elem.pos<elem.end){int key=(int)elem.number();switch(key){case 8:line.id=elem.number();break;case 16:line.at=elem.number();break;case 24:line.mode=(int)elem.number();break;case 32:line.size=(int)elem.number();break;case 40:line.color=0xff000000|((int)elem.number()&0xffffff);break;case 58:line.text=elem.text();break;case 72:line.weight=(int)elem.number();break;default:elem.skip(key);}}
            if(line.at>=0&&!line.text.isEmpty()&&line.mode>=1&&line.mode<=6){if(line.text.length()>120)line.text=line.text.substring(0,120);lines.add(line);}}
        sort(lines);return lines;
    }
    static List<Line> xml(byte[] bytes)throws Exception{
        ArrayList<Line> result=new ArrayList<>();XmlPullParser p=Xml.newPullParser();p.setInput(new ByteArrayInputStream(bytes),"UTF-8");int event;
        while((event=p.next())!=XmlPullParser.END_DOCUMENT&&result.size()<12000)if(event==XmlPullParser.START_TAG&&"d".equals(p.getName())){String[] data=String.valueOf(p.getAttributeValue(null,"p")).split(",");if(data.length<4)continue;try{Line l=new Line();l.at=(long)(Double.parseDouble(data[0])*1000);l.mode=Integer.parseInt(data[1]);l.size=Integer.parseInt(data[2]);l.color=0xff000000|(Integer.parseInt(data[3])&0xffffff);l.text=p.nextText();if(l.text.length()>120)l.text=l.text.substring(0,120);if(!l.text.isEmpty())result.add(l);}catch(NumberFormatException ignored){}}
        sort(result);return result;
    }
    static void sort(List<Line> lines){Collections.sort(lines,new Comparator<Line>(){public int compare(Line a,Line b){return a.at<b.at?-1:a.at==b.at?0:1;}});}
}
