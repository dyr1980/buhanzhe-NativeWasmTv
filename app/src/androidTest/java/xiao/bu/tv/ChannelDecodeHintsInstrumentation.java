package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

/** Real SQLite migration, retention and precedence checks in the isolated test package. */
public final class ChannelDecodeHintsInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void drain(ChannelDecodeHints hints)throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        ((Handler)field(hints,"worker")).post(done::countDown);
        if(!done.await(20,TimeUnit.SECONDS))throw new AssertionError("SQLite worker stalled");
    }
    private static void stop(ChannelDecodeHints hints)throws Exception {
        hints.close();((Thread)field(hints,"thread")).join(5000);
        if(((Thread)field(hints,"thread")).isAlive())throw new AssertionError("SQLite worker leaked");
    }
    private static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static SQLiteDatabase db(ChannelDecodeHints hints)throws Exception {
        return ((SQLiteOpenHelper)field(hints,"store")).getWritableDatabase();
    }
    private static ChannelDecodeHints.Hint get(ChannelDecodeHints hints,String url)throws Exception {
        hints.get(url);drain(hints);return hints.get(url);
    }
    private static long count(SQLiteDatabase db,String sql) {
        try(android.database.Cursor c=db.rawQuery(sql,null)){check(c.moveToFirst(),"Missing SQL result");return c.getLong(0);}
    }
    private static void saveProbe(ChannelDecodeHints hints,String url,ChannelDecodeHints.Hint hint)throws Exception {
        Method save=ChannelDecodeHints.class.getDeclaredMethod("save",String.class,ChannelDecodeHints.Hint.class);save.setAccessible(true);
        Throwable[] error={null};((Handler)field(hints,"worker")).post(()->{
            try{save.invoke(hints,ChannelDecodeHints.key(url),hint);}catch(Throwable e){error[0]=e;}
        });drain(hints);if(error[0]!=null)throw new AssertionError(error[0]);
    }
    public static String verify(Context context)throws Exception {
        ChannelDecodeHints hints=null;
        context.deleteDatabase("channel_decode_hints.db");
        String source="https://example.invalid/live.m3u8?private=credential";
        VideoStreamDescription interlaced=new VideoStreamDescription(8L|(1L<<24)|(2L<<27));
        VideoStreamDescription progressive=new VideoStreamDescription(10L|(1L<<24)|(1L<<27));
        try {
            // Expired successful v1 records must migrate; negative/partial entries must not poison v2.
            SQLiteDatabase legacy=context.openOrCreateDatabase("channel_decode_hints.db",0,null);
            legacy.execSQL("CREATE TABLE hints (source_key TEXT PRIMARY KEY,w INTEGER,h INTEGER,codec TEXT,depth INTEGER,scan TEXT,fps REAL,expires INTEGER)");
            legacy.execSQL("INSERT INTO hints VALUES (?,?,?,?,?,?,?,?)",new Object[]{ChannelDecodeHints.key(source),1920,1080,"H.264",8,"interlaced",25,null});
            legacy.execSQL("INSERT INTO hints VALUES (?,?,?,?,?,?,?,?)",new Object[]{"failed",0,0,"--",0,"unknown",0,Long.MAX_VALUE});
            legacy.setVersion(1);legacy.close();
            hints=new ChannelDecodeHints(context);drain(hints);
            check(get(hints,source)!=null&&get(hints,source).interlaced50(),"Expired complete v1 hint lost");
            check(count(db(hints),"SELECT COUNT(*) FROM hints")==1,"Failed v1 hint migrated");
            check(count(db(hints),"PRAGMA user_version")==2,"Schema version not upgraded");
            hints.remember(source,1280,720,"H.265",progressive,50f);drain(hints);
            check(get(hints,source).origin==1&&get(hints,source).depth==10&&!get(hints,source).interlaced50(),"Current IJK did not override probe");
            hints.cancel();saveProbe(hints,source,new ChannelDecodeHints.Hint(1920,1080,"H.264",8,"interlaced",25,0,System.currentTimeMillis()+1000));
            check(get(hints,source).height==720,"Late probe overwrote current IJK");
            hints.remember(source,0,0,"--",VideoStreamDescription.UNKNOWN,0);drain(hints);
            check(get(hints,source).depth==10,"Incomplete IJK destroyed good metadata");
            SQLiteDatabase db=db(hints);
            db.execSQL("CREATE TABLE writes (n INTEGER)");db.execSQL("INSERT INTO writes VALUES (0)");
            db.execSQL("CREATE TRIGGER watch_hints AFTER INSERT ON hints BEGIN UPDATE writes SET n=n+1; END");
            for(int n=0;n<100;n++)hints.remember(source,1280,720,"H.265",progressive,50f);
            drain(hints);check(count(db,"SELECT n FROM writes")==0,"Unchanged playback repeatedly wrote SQLite");
            hints.remember(source,1920,1080,"H.264",interlaced,25f);drain(hints);
            check(count(db,"SELECT n FROM writes")==1&&get(hints,source).interlaced50(),"Later accurate IJK change not saved");
            try(android.database.Cursor c=db.rawQuery("SELECT source_key FROM hints",null)) {
                check(c.moveToFirst()&&!c.getString(0).contains("credential")&&!c.getString(0).contains("https"),"URL leaked to database");
            }
            db.beginTransaction();
            try {
                for(int i=0;i<600;i++)db.execSQL("INSERT INTO hints VALUES (?,?,?,?,?,?,?,?,?)",new Object[]{ChannelDecodeHints.key("https://example.invalid/extra/"+i+".mp4"),1280,720,"H.264",8,"progressive",25,0,System.currentTimeMillis()+10000+i});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            stop(hints);hints=new ChannelDecodeHints(context);drain(hints);
            check(count(db(hints),"SELECT COUNT(*) FROM hints")==601,"Durable records evicted at 512");
            check(((Map<?,?>)field(hints,"memory")).size()<=512,"Memory cache unbounded");
            // Disk-only hits must be loaded by the scheduler before it starts a network probe.
            hints.schedule(Arrays.asList(source,source,"https://example.invalid/extra/0.mp4","https://example.invalid/extra/0.mp4"));
            drain(hints);drain(hints);drain(hints);drain(hints);
            ThreadPoolExecutor probeWorker=(ThreadPoolExecutor)field(field(hints,"probe"),"worker");
            check(probeWorker.getTaskCount()==0,"Persistent or duplicate hint triggered network probe");
            check(get(hints,source)!=null&&get(hints,source).interlaced50(),"Disk-only hint unavailable after reopening");
            String finalSource="https://example.invalid/last.mp4";
            hints.remember(finalSource,1280,720,"H.265",progressive,50f);stop(hints);
            hints=new ChannelDecodeHints(context);drain(hints);
            check(get(hints,finalSource)!=null,"Close dropped a queued IJK update");
            hints.invalidate(source);drain(hints);check(get(hints,source)==null,"Stale hint not invalidated");
            Method accept=ChannelDecodeHints.class.getDeclaredMethod("acceptProbe",String.class,ChannelDecodeHints.Hint.class,long.class,boolean.class);
            accept.setAccessible(true);final ChannelDecodeHints rejected=hints;
            ((Handler)field(hints,"worker")).post(()->{
                try {accept.invoke(rejected,ChannelDecodeHints.key(source),new ChannelDecodeHints.Hint(1920,1080,"H.264",8,"interlaced",25,0,1),0L,true);}
                catch(Exception error){throw new RuntimeException(error);}
            });drain(hints);check(get(hints,source)==null,"Rejected hint resurrected by old probe");
            db(hints).execSQL("DROP TABLE hints");
            hints.remember("https://example.invalid/changed.mp4",1280,720,"H.265",progressive,50f);drain(hints);
            check(get(hints,"https://example.invalid/changed.mp4")!=null,"SQLite failure blocked advisory memory");
            stop(hints);hints=null;
            return "PASS v1 migration, permanent 601-row retention, 512-entry memory limit, zero duplicate probes, repeated IJK updates, late-probe precedence, no redundant writes, privacy, invalidation, close drain and SQLite failure isolation";
        }finally{if(hints!=null)stop(hints);context.deleteDatabase("channel_decode_hints.db");}
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {result.putString("stream",verify(getTargetContext()));finish(0,result);}
        catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(-1,result);}
    }
}
