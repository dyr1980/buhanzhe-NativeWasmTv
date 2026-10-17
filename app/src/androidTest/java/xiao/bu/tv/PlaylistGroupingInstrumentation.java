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

/** Merged groups, channel order and visibility survive refresh and restart across sources. */
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
            String b="#EXTM3U\n#EXTINF:-1 group-title=\"网络一\",A\nhttp://example.test/a-backup\n"
                    +"#EXTINF:-1 group-title=\"网络一\",D\nhttp://example.test/d\n";
            PlaylistManager.ImportedFile fa=manager.importLocalPlaylist("a","a.m3u",a.getBytes("UTF-8"));
            PlaylistManager.ImportedFile fb=manager.importLocalPlaylist("b","b.m3u",b.getBytes("UTF-8"));
            isolated.getSharedPreferences("management",0).edit()
                    .putString("disabled_playlist_groups_v1","[\"网络二\",\"child:UpperA:网络一\"]")
                    .putString("playlist_channel_settings_v1",new JSONObject().put("child:UpperA:网络一",
                            new JSONObject().put("order",new JSONArray().put("a"))
                            .put("hidden",new JSONArray().put("a"))).toString()).commit();
            JSONArray sources=new JSONArray().put(source("UpperA","网络频道",fa.location))
                    .put(source("b","备用来源",fb.location));
            ChannelCatalog.Group[] refreshed=manager.updateSources(sources).groups;
            equal(refreshed,new PlaylistManager(isolated).loadCached(),"device cold start");
            if(groupCount(refreshed,"网络二")!=0)throw new AssertionError("legacy group switch lost");
            if(groupCount(refreshed,"网络一")!=2)throw new AssertionError("visible duplicate lost in legacy migration");
            JSONArray groups=manager.getGroupSettingsJson();int sameTitle=0;
            for(int i=0;i<groups.length();i++) {
                JSONObject row=groups.getJSONObject(i);
                if(row.has("sourceId")||row.has("parent")||!row.getString("id").startsWith("group:"))
                    throw new AssertionError("source hierarchy leaked to merged management");
                if(row.getString("name").equals("网络一")) {sameTitle++;if(row.getInt("channelCount")!=2)throw new AssertionError("raw source count leaked");}
            }
            if(sameTitle!=1)throw new AssertionError("same-title groups were not merged");
            ChannelCatalog.Group[] hidden=manager.updateGroupStates(new JSONArray().put(new JSONObject()
                    .put("id","group:网络一").put("enabled",false)));
            if(groupCount(hidden,"网络一")!=0)throw new AssertionError("merged group toggle missed another source");
            equal(hidden,new PlaylistManager(isolated).loadCached(),"merged switch cold start");
            manager.updateGroupStates(new JSONArray().put(new JSONObject().put("id","group:网络一").put("enabled",true)));
            JSONObject editor=manager.getChannelSettingsJson("group:网络一");
            JSONObject edit=new JSONObject().put("groupId","group:网络一").put("revision",editor.getString("revision"))
                    .put("order",new JSONArray().put("d").put("a")).put("hidden",new JSONArray().put("a"));
            ChannelCatalog.Group[] channelHidden=manager.updateChannelSettings(edit);
            if(!signature(channelHidden).contains("网络一:D,"))throw new AssertionError("merged channel order/hide missed duplicate");
            equal(channelHidden,new PlaylistManager(isolated).loadCached(),"channel cold start");
            equal(channelHidden,manager.updateSources(sources).groups,"channel source refresh");
            boolean stale=false;
            try {manager.updateChannelSettings(edit);}catch(java.io.IOException expected){stale=true;}
            if(!stale)throw new AssertionError("stale client overwrote channel settings");
            a+="#EXTINF:-1 group-title=\"网络一\",E\nhttp://example.test/e\n";
            manager.importLocalPlaylist("a","a.m3u",a.getBytes("UTF-8"));
            ChannelCatalog.Group[] appended=manager.updateSources(sources).groups;
            if(!signature(appended).contains("网络一:D,E,"))throw new AssertionError("refresh did not append new channel or resurrected hidden duplicate");
            JSONArray payload=new JSONArray().put(new JSONObject().put("id","UpperA").put("playlist",a))
                    .put(new JSONObject().put("id","b").put("playlist",b));
            equal(appended,manager.applyMobileMerge(sources,(a+b).getBytes("UTF-8"),payload).groups,"phone refresh");
            equal(appended,new PlaylistManager(isolated).loadCached(),"phone cold start");
            store=new ChannelCatalogStore(isolated);store.replace(new ChannelCatalog.Group[0],null);store.close();
            equal(appended,new PlaylistManager(isolated).loadCached(),"raw cache recovery");
            JSONArray allOff=new JSONArray();
            for(int i=0;i<groups.length();i++)allOff.put(new JSONObject().put("id",groups.getJSONObject(i).getString("id")).put("enabled",false));
            boolean rejected=false;
            try {manager.updateGroupStates(allOff);}catch(java.io.IOException expected){rejected=true;}
            if(!rejected)throw new AssertionError("last visible group could be hidden");
            equal(appended,new PlaylistManager(isolated).loadCached(),"failed group update rollback");
            verifyCctvOrder(isolated);
            result.putString("stream","PASS merged same-title groups, cross-source dedup/order/hide, legacy migration, new channel append, device/phone refresh, DB/raw cache recovery, stale revision and rollback\n"
                    +ManagementChannelChecks.run(this,isolated,manager));
        } catch(Throwable error) { code=1;result.putString("stream",android.util.Log.getStackTraceString(error)); }
        finish(code,result);
    }
    private static void verifyCctvOrder(Context context) throws Exception {
        PlaylistManager manager=new PlaylistManager(context);
        StringBuilder text=new StringBuilder("#EXTM3U\n");
        for(int i=1;i<=20;i++)text.append("#EXTINF:-1 group-title=\"央视频道\",")
                .append(i==18?"CCTV-5+ 体育赛事":"CCTV"+i)
                .append("\nhttp://example.test/cctv/").append(i).append('\n');
        PlaylistManager.ImportedFile file=manager.importLocalPlaylist("cctv-order","cctv.m3u",text.toString().getBytes("UTF-8"));
        JSONArray sources=new JSONArray().put(source("cctv-order","排序回归",file.location));
        manager.updateSources(sources);
        java.lang.reflect.Field custom=ChannelCatalog.class.getDeclaredField("customGroups");custom.setAccessible(true);
        ChannelCatalog.Group[] previous=(ChannelCatalog.Group[])custom.get(null);
        try {
            for(int target:new int[]{0,5,19}) {
                JSONObject editor=manager.getChannelSettingsJson("group:央视频道");
                JSONArray channels=editor.getJSONArray("channels"),order=new JSONArray();String plus=null;
                java.util.ArrayList<String> ids=new java.util.ArrayList<String>();
                for(int i=0;i<channels.length();i++) {
                    JSONObject channel=channels.getJSONObject(i);String id=channel.getString("id");
                    if(channel.getString("name").contains("5+"))plus=id;else ids.add(id);
                }
                if(plus==null)throw new AssertionError("CCTV5+ merged with CCTV5");
                ids.add(target,plus);for(String id:ids)order.put(id);
                ChannelCatalog.Group[] saved=manager.updateChannelSettings(new JSONObject().put("groupId","group:央视频道")
                        .put("revision",editor.getString("revision")).put("order",order).put("hidden",new JSONArray()));
                verifyCatalogOrder(saved,target,"save");
                verifyCatalogOrder(new PlaylistManager(context).loadCached(),target,"cold start");
                verifyCatalogOrder(manager.updateSources(sources).groups,target,"source refresh");
            }
        }finally {ChannelCatalog.setCustomGroups(previous);}
    }
    private static void verifyCatalogOrder(ChannelCatalog.Group[] groups,int target,String stage) {
        ChannelCatalog.setCustomGroups(groups);
        for(ChannelCatalog.Group group:ChannelCatalog.GROUPS)if("央视频道".equals(group.title)) {
            if(group.channels.length<=target || !group.channels[target].name.contains("5+"))
                throw new AssertionError("CCTV5+ order overridden at "+stage+" target="+target);
            boolean normal=false;for(Channel channel:group.channels)
                if(java.util.Arrays.asList(channel.urls).contains("http://example.test/cctv/5")) {
                    if(channel.name.contains("5+"))throw new AssertionError("CCTV5 merged with CCTV5+");
                    normal=true;
                }
            if(!normal)throw new AssertionError("CCTV5 disappeared after moving CCTV5+");
            return;
        }
        throw new AssertionError("CCTV group missing at "+stage);
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
