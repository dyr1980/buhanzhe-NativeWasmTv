package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Source ownership must survive phone/device refresh, DB reload and cache recovery. */
public final class PlaylistGroupingInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try {
            Context isolated = new ContextWrapper(getTargetContext()) {
                @Override public Context getApplicationContext() { return this; }
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    return super.getSharedPreferences("group-test-" + name,mode);
                }
                @Override public File getFilesDir() {
                    File dir = new File(super.getFilesDir(),"group-test"); dir.mkdirs(); return dir;
                }
                @Override public File getFileStreamPath(String name) { return new File(getFilesDir(),name); }
                @Override public FileInputStream openFileInput(String name) throws java.io.FileNotFoundException {
                    return new FileInputStream(getFileStreamPath(name));
                }
                @Override public FileOutputStream openFileOutput(String name,int mode) throws java.io.FileNotFoundException {
                    return new FileOutputStream(getFileStreamPath(name));
                }
                @Override public boolean deleteFile(String name) { return getFileStreamPath(name).delete(); }
                @Override public File getDatabasePath(String name) { return new File(getFilesDir(),name); }
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory) {
                    return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
                }
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,
                        android.database.DatabaseErrorHandler handler) {
                    return openOrCreateDatabase(name,mode,factory);
                }
            };
            isolated.getSharedPreferences("management",0).edit().clear().commit();
            ChannelCatalogStore store = new ChannelCatalogStore(isolated);
            store.replace(new ChannelCatalog.Group[0], null); store.close();
            PlaylistManager manager = new PlaylistManager(isolated);
            String a="#EXTM3U\n#EXTINF:-1 group-title=\"网络一\",A\nhttp://example.test/a\n"
                    +"#EXTINF:-1 group-title=\"网络二\",B\nhttp://example.test/b\n"
                    +"#EXTINF:-1 group-title=\"网络三\",C\nhttp://example.test/c\n";
            String b="#EXTM3U\n#EXTINF:-1 group-title=\"网络一\",D\nhttp://example.test/d\n";
            PlaylistManager.ImportedFile fa=manager.importLocalPlaylist("a","a.m3u",a.getBytes("UTF-8"));
            PlaylistManager.ImportedFile fb=manager.importLocalPlaylist("b","b.m3u",b.getBytes("UTF-8"));
            isolated.getSharedPreferences("management",0).edit()
                    .putString("disabled_playlist_groups_v1","[\"网络二\"]").commit();
            JSONArray sources=new JSONArray().put(source("UpperA","网络频道",fa.location))
                    .put(source("b","备用来源",fb.location));
            ChannelCatalog.Group[] refreshed=manager.updateSources(sources).groups;
            equal(refreshed,new PlaylistManager(isolated).loadCached(),"device cold start");
            if(groupCount(refreshed,"网络二")!=0)throw new AssertionError("legacy switch lost");
            int count=groupCount(refreshed,"网络一"); if(count!=2)throw new AssertionError("same-title merge");
            JSONArray states=new JSONArray().put(new JSONObject().put("id","source:UpperA").put("enabled",true));
            ChannelCatalog.Group[] collapsed=manager.updateGroupStates(states);
            if(groupCount(collapsed,"网络频道")!=3 || groupCount(collapsed,"网络二")!=0)
                throw new AssertionError("parent did not replace children");
            equal(collapsed,new PlaylistManager(isolated).loadCached(),"parent cold start");
            equal(collapsed,manager.updateSources(sources).groups,"parent refresh");
            ChannelCatalog.Group[] childEnabled=manager.updateGroupStates(new JSONArray().put(new JSONObject()
                    .put("id","child:UpperA:网络一").put("enabled",true)));
            if(groupCount(childEnabled,"网络频道")!=0 || groupCount(childEnabled,"网络一")!=2
                    || groupCount(childEnabled,"网络二")!=0)
                throw new AssertionError("enabling child did not expand parent or lost sibling switch");
            manager.updateGroupStates(new JSONArray().put(new JSONObject().put("id","source:UpperA").put("enabled",false)));
            ChannelCatalog.Group[] hidden=manager.updateGroupStates(new JSONArray().put(new JSONObject()
                    .put("id","child:UpperA:网络一").put("enabled",false)));
            if(groupCount(hidden,"网络一")!=1) throw new AssertionError("disabled other source with same title");
            equal(hidden,new PlaylistManager(isolated).loadCached(),"child cold start");
            JSONArray payload=new JSONArray().put(new JSONObject().put("id","UpperA").put("playlist",a))
                    .put(new JSONObject().put("id","b").put("playlist",b));
            equal(hidden,manager.applyMobileMerge(sources,(a+b).getBytes("UTF-8"),payload).groups,"phone refresh");
            equal(hidden,new PlaylistManager(isolated).loadCached(),"phone cold start");
            store=new ChannelCatalogStore(isolated);store.replace(new ChannelCatalog.Group[0], null);store.close();
            equal(hidden,new PlaylistManager(isolated).loadCached(),"cache recovery");
            result.putString("stream","PASS device/phone refresh, cold DB reload, raw-cache recovery, parent replacement, independent same-title children\n");
        } catch(Throwable error) { code=0;result.putString("stream",android.util.Log.getStackTraceString(error)); }
        finish(code,result);
    }
    private static JSONObject source(String id,String name,String location) throws Exception {
        return new JSONObject().put("id",id).put("name",name).put("location",location).put("enabled",true);
    }
    private static int groupCount(ChannelCatalog.Group[] groups,String title) {
        for(ChannelCatalog.Group group:groups)if(title.equals(group.title))return group.channels.length;
        return 0;
    }
    private static String signature(ChannelCatalog.Group[] groups) {
        StringBuilder result=new StringBuilder();
        for(ChannelCatalog.Group group:groups) {
            result.append(group.title).append(':');
            for(Channel channel:group.channels) result.append(channel.name).append(',');
            result.append(';');
        }
        return result.toString();
    }
    private static void equal(ChannelCatalog.Group[] a,ChannelCatalog.Group[] b,String label) {
        if(!signature(a).equals(signature(b))) throw new AssertionError(label+" changed grouping");
    }
}
