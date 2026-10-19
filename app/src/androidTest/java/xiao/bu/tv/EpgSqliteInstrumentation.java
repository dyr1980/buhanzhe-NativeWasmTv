package xiao.bu.tv;
import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real device SQLite persistence, cold restore and transaction rollback. */
public final class EpgSqliteInstrumentation extends Instrumentation {
    public void onCreate(Bundle b){super.onCreate(b);start();}
    static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public void onStart(){Bundle result=new Bundle();int code=1;
        String source="https://sqlite-regression.invalid/epg.xml";
        try{
            long now=System.currentTimeMillis();
            SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US);
            String xml="<tv><channel id=\"test\"><display-name>SQLite Test</display-name><icon src=\"https://example.org/logo.png\"/></channel><programme channel=\"test\" start=\""+format.format(new Date(now-60000))+"\" stop=\""+format.format(new Date(now+3600000))+"\" catchup-id=\"replay-1\"><title>Persistent programme</title></programme></tv>";
            Method parse=EpgManager.class.getDeclaredMethod("parse",byte[].class);parse.setAccessible(true);Object guide=parse.invoke(null,(Object)xml.getBytes("UTF-8"));
            Method save=EpgManager.class.getDeclaredMethod("saveDatabase",String.class,String.class,guide.getClass(),long.class);save.setAccessible(true);
            Method read=EpgManager.class.getDeclaredMethod("readDatabase",String.class);read.setAccessible(true);
            EpgManager first=new EpgManager(getTargetContext());save.invoke(first,source,source,guide,now);
            ((android.database.sqlite.SQLiteOpenHelper)field(first,"database")).close();
            EpgManager cold=new EpgManager(getTargetContext());Object restored=read.invoke(cold,source);
            check(restored!=null,"Cold SQLite cache missing");
            Object snapshot=field(restored,"guide");Map programmes=(Map)field(snapshot,"programsByChannel");
            List rows=(List)programmes.get("TEST");check(rows!=null&&rows.size()==1,"Programmes lost");
            EpgManager.Program programme=(EpgManager.Program)rows.get(0);
            check("Persistent programme".equals(programme.title)&&"replay-1".equals(programme.catchupId),"Programme fields lost");
            check(((Map)field(snapshot,"logosByChannel")).size()==1,"Logo lost");
            CountDownLatch done=new CountDownLatch(1);cold.refresh(new String[]{source},()->done.countDown());
            check(done.await(10,TimeUnit.SECONDS),"Fresh cache tried network");
            check(cold.getLastError().length()==0,"Fresh cache restore failed");
            check(((Map)field(field(cold,"guide"),"programsByChannel")).size()==1,"Fresh DB not published");
            rows.set(0,new EpgManager.Program(now,now+3600000,null,""));
            try{save.invoke(cold,source,source,snapshot,now);throw new AssertionError("Bad transaction accepted");}catch(InvocationTargetException expected){}
            Object rolledBack=read.invoke(cold,source);Map intact=(Map)field(field(rolledBack,"guide"),"programsByChannel");
            check("Persistent programme".equals(((EpgManager.Program)((List)intact.get("TEST")).get(0)).title),"Rollback lost old guide");
            android.database.sqlite.SQLiteOpenHelper helper=(android.database.sqlite.SQLiteOpenHelper)field(cold,"database");
            for(String table:new String[]{"sources","aliases","logos","programmes"})helper.getWritableDatabase().delete(table,"source=?",new String[]{source});helper.close();
            code=-1;result.putString("stream","PASS SQLite cold restore, programme/alias/logo/catchup preservation, same-day offline refresh and failed-write rollback\n");
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finish(code,result);
    }
}
