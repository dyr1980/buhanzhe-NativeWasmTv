package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual XML parser, lookup, source merge and v1 SQLite migration, fully offline. */
public final class EpgChannelMatchingInstrumentation extends Instrumentation {
    private int checks;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Object field(Object value,String name)throws Exception{
        Field field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);
    }
    private static void guide(EpgManager manager,Object value)throws Exception{
        Field field=EpgManager.class.getDeclaredField("guide");field.setAccessible(true);field.set(manager,value);
    }
    private static Object parse(byte[] xml)throws Exception{
        Method method=EpgManager.class.getDeclaredMethod("parse",byte[].class);method.setAccessible(true);return method.invoke(null,(Object)xml);
    }
    private static Channel channel(String name,String id)throws Exception{
        String line="#EXTM3U\n#EXTINF:-1"+(id==null?"":" tvg-id=\""+id+"\"")+","+name+"\nhttp://fixture.invalid/live.m3u8\n";
        Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);parse.setAccessible(true);
        return ((ChannelCatalog.Group[])parse.invoke(null,line.getBytes("UTF-8"),"http://fixture.invalid/list.m3u"))[0].channels[0];
    }
    private void programme(EpgManager manager,String name,String id,String title)throws Exception{
        List<EpgManager.Program> rows=manager.programsFor(channel(name,id));
        check(rows.size()==1&&title.equals(rows.get(0).title),name+" / "+id+" resolved to "+(rows.isEmpty()?"empty":rows.get(0).title));
    }
    private static byte[] xml()throws Exception{
        String[] ids={"CCTV4","CCTV4-EU","CCTV4-AME","CCTV4K Ultra","CCTV4CaseA","CCTV4CaseB"};
        String[] names={"CCTV4中文国际","CCTV4EUO","CCTV4AME","CCTV4K超高清","Opaque A","Opaque B"};
        String[] titles={"Asia schedule","Europe schedule","Americas schedule","4K schedule","Opaque A schedule","Opaque B schedule"};
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);long now=System.currentTimeMillis();
        StringBuilder xml=new StringBuilder("<tv>");
        for(int i=0;i<ids.length;i++)xml.append("<channel id=\"").append(ids[i]).append("\"><display-name>").append(names[i]).append("</display-name></channel>");
        for(int i=0;i<ids.length;i++)xml.append("<programme channel=\"").append(ids[i]).append("\" start=\"")
                .append(date.format(new Date(now-60000))).append("\" stop=\"").append(date.format(new Date(now+3600000)))
                .append("\"><title>").append(titles[i]).append("</title></programme>");
        return xml.append("</tv>").toString().getBytes("UTF-8");
    }
    private void schedules(EpgManager manager)throws Exception{
        programme(manager,"CCTV4中文国际",null,"Asia schedule");
        programme(manager,"CCTV-4 中文国际（亚洲）",null,"Asia schedule");
        programme(manager,"国际欧洲",null,"Europe schedule");
        programme(manager,"CCTV4中文国际欧洲",null,"Europe schedule");
        programme(manager,"国际美洲",null,"Americas schedule");
        programme(manager,"CCTV-4 中文国际（美洲）",null,"Americas schedule");
        programme(manager,"CCTV4K超高清",null,"4K schedule");
        programme(manager,"CCTV4K",null,"4K schedule");
        programme(manager,"CCTV4",null,"Asia schedule");
        programme(manager,"CCTV4K","CCTV4CaseA","Opaque A schedule");
        programme(manager,"CCTV4","CCTV4CaseB","Opaque B schedule");
        programme(manager,"CCTV4","CCTV4-EU","Europe schedule");
    }
    private static final class FixtureContext extends ContextWrapper {
        final File directory;final String prefix="epg-match-"+System.nanoTime()+"-";
        FixtureContext(Context base){super(base);directory=new File(base.getCacheDir(),prefix);directory.mkdirs();}
        public Context getApplicationContext(){return this;}
        public File getDatabasePath(String name){return new File(directory,name);}
        public File getFileStreamPath(String name){return new File(directory,name);}
        public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
        }
        public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);
        }
        public android.content.SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences(prefix+name,mode);}
    }
    public void onStart(){
        Bundle result=new Bundle();int code=0;FixtureContext context=new FixtureContext(getTargetContext());
        EpgManager manager=null,cold=null;
        try{
            byte[] bytes=xml();Object parsed=parse(bytes);manager=new EpgManager(context);guide(manager,parsed);schedules(manager);
            check(((java.util.Map)field(parsed,"programsByChannel")).size()==6,"Parser collapsed opaque XMLTV IDs");
            Object fallback=parse(bytes);
            Method merge=EpgManager.class.getDeclaredMethod("mergeGuide",parsed.getClass(),parsed.getClass());merge.setAccessible(true);
            merge.invoke(null,parsed,fallback);schedules(manager);
            check(((java.util.Map)field(parsed,"programsByChannel")).size()==6,"Source merge lost/duplicated channels");
            // Simulate an existing v1 index with the reported wrong 4K mapping.
            SQLiteOpenHelper helper=(SQLiteOpenHelper)field(manager,"database");
            SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("epg-guide.db"),null);
            helper.onCreate(old);old.execSQL("INSERT INTO sources VALUES('https://fixture.invalid/epg.xml','https://fixture.invalid/epg.xml',0)");
            old.execSQL("INSERT INTO aliases VALUES('https://fixture.invalid/epg.xml','CCTV4','wrong4K')");
            old.setVersion(1);old.close();
            String source="https://fixture.invalid/epg.xml";
            File backup=context.getFileStreamPath("epg-guide-cache.xml");
            try(FileOutputStream output=new FileOutputStream(backup)){output.write(bytes);}
            context.getSharedPreferences("epg_cache",0).edit().putString("source_url",source).putString("resolved_url",source).commit();
            cold=new EpgManager(context);CountDownLatch restored=new CountDownLatch(1);
            cold.restoreCached(new String[]{source},restored::countDown);
            check(restored.await(10,TimeUnit.SECONDS),"Offline migration did not publish retained XML");schedules(cold);
            SQLiteOpenHelper migrated=(SQLiteOpenHelper)field(cold,"database");
            check(migrated.getReadableDatabase().getVersion()==2,"Old parsed index was not invalidated");
            check(backup.length()==bytes.length,"Migration removed XML backup");
            CountDownLatch refreshed=new CountDownLatch(1);cold.refresh(new String[]{source},refreshed::countDown);
            check(refreshed.await(3,TimeUnit.SECONDS)&&cold.getLastError().isEmpty(),"Fresh rebuilt cache attempted network");schedules(cold);
            result.putString("stream","PASS "+checks+" Android "+android.os.Build.VERSION.RELEASE+" XML/SQLite checks: distinct Asia/Europe/Americas/4K schedules, exact IDs, source merge and offline v1 cache migration");code=-1;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{
            try{if(manager!=null)((SQLiteOpenHelper)field(manager,"database")).close();if(cold!=null)((SQLiteOpenHelper)field(cold,"database")).close();}catch(Throwable ignored){}
            context.getSharedPreferences("epg_cache",0).edit().clear().commit();
            File[] files=context.directory.listFiles();if(files!=null)for(File file:files)file.delete();context.directory.delete();
        }
        finish(code,result);
    }
}
