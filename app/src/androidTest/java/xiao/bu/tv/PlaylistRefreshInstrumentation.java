package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP cache validation and total deadlines; keeps user channels/prefs untouched. */
public class PlaylistRefreshInstrumentation extends Instrumentation {
    private Bundle args;
    private ServerSocket server;
    private final CopyOnWriteArrayList<Socket> clients=new CopyOnWriteArrayList<>();
    private final AtomicInteger notModified=new AtomicInteger();
    private final AtomicInteger compressedReplies=new AtomicInteger();
    private volatile int version=1;
    private volatile boolean slow;

    @Override public void onCreate(Bundle value) {super.onCreate(value);args=value;start();}
    private byte[] body() throws Exception {
        return ("#EXTM3U\n#EXTINF:-1 group-title=\"测试\",频道"+version
                +"\nhttp://example.test/stream"+version+"\n").getBytes("UTF-8");
    }
    private void serve(Socket socket) {
        try(Socket client=socket) {
            client.setSoTimeout(3000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));
            String request=reader.readLine(),line;boolean conditional=false,gzip=false;
            while((line=reader.readLine())!=null && !line.isEmpty()) {
                if(line.equalsIgnoreCase("If-None-Match: \"v"+version+"\""))conditional=true;
                if(line.toLowerCase(java.util.Locale.US).startsWith("accept-encoding:")
                        && line.contains("gzip"))gzip=true;
            }
            if(request.contains("/stalled")){SystemClock.sleep(35000);return;}
            if(request.contains("/delayed.php"))SystemClock.sleep(21000);
            boolean md5=request.contains("md5");
            OutputStream output=client.getOutputStream();byte[] bytes=body();
            boolean compressed=request.contains("/gzip") && gzip;
            if(compressed) {
                ByteArrayOutputStream encoded=new ByteArrayOutputStream();
                try(java.util.zip.GZIPOutputStream stream=new java.util.zip.GZIPOutputStream(encoded)){stream.write(bytes);}
                bytes=encoded.toByteArray();compressedReplies.incrementAndGet();
            }
            if(slow && md5)SystemClock.sleep(8000);
            if(conditional && !slow) {
                notModified.incrementAndGet();
                output.write("HTTP/1.1 304 Not Modified\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));
                return;
            }
            boolean opaque=request.contains("/opaque");
            boolean badMd5=request.contains("bad-md5");
            String validator=md5 ? "Content-MD5: "+android.util.Base64.encodeToString(
                    java.security.MessageDigest.getInstance("MD5").digest(badMd5 ? new byte[]{0} : bytes),android.util.Base64.NO_WRAP)
                    : opaque ? "ETag: \"d94ef5027c44800b94eb6f405a984b15\"" : "ETag: \"v"+version+"\"";
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "+bytes.length
                    +"\r\n"+(compressed?"Content-Encoding: gzip\r\n":"")+validator+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
            if(request.startsWith("HEAD "))return;
            int offset=0;
            if(slow && !md5)for(;offset<12;offset++) {
                output.write(bytes,offset,1);output.flush();SystemClock.sleep(700);
            }
            output.write(bytes,offset,bytes.length-offset);output.flush();
        }catch(Exception ignored) {
            // An intentional timeout cancels the fixture socket.
        }finally {clients.remove(socket);}
    }
    protected Context isolated() {
        final String prefix="refresh-test-"+java.util.UUID.randomUUID();
        return new ContextWrapper(getTargetContext()) {
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                return super.getSharedPreferences(prefix+name,mode);
            }
            @Override public File getFilesDir(){File dir=new File(super.getFilesDir(),prefix);dir.mkdirs();return dir;}
            @Override public File getFileStreamPath(String name){return new File(getFilesDir(),name);}
            @Override public FileInputStream openFileInput(String name) throws FileNotFoundException {
                return new FileInputStream(getFileStreamPath(name));
            }
            @Override public FileOutputStream openFileOutput(String name,int mode) throws FileNotFoundException {
                return new FileOutputStream(getFileStreamPath(name));
            }
            @Override public boolean deleteFile(String name){return getFileStreamPath(name).delete();}
            @Override public File getDatabasePath(String name){return getFileStreamPath(name);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){
                return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,
                    android.database.DatabaseErrorHandler handler){return openOrCreateDatabase(name,mode,factory);}
        };
    }
    private static void equal(byte[] expected,byte[] actual,String reason) {
        if(!Arrays.equals(expected,actual))throw new AssertionError(reason);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();int code=-1;
        Context context=null;
        try {
            NetworkClient.initialize(getTargetContext());
            context=isolated();PlaylistManager manager=new PlaylistManager(context);
            server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
            new Thread(() -> {
                while(!server.isClosed())try {
                    Socket client=server.accept();clients.add(client);
                    new Thread(() -> serve(client),"Playlist fixture request").start();
                }catch(IOException ignored){if(server.isClosed())break;}
            },"Playlist refresh fixture").start();
            String url="http://127.0.0.1:"+server.getLocalPort()+"/list.txt";
            long begin=SystemClock.elapsedRealtime();
            equal(body(),manager.readForMobile(url),"first download");
            report.append("initialMs=").append(SystemClock.elapsedRealtime()-begin).append('\n');
            begin=SystemClock.elapsedRealtime();
            equal(body(),manager.readForMobile(url),"304 cache reuse");
            if(notModified.get()!=1)throw new AssertionError("conditional GET missing");
            report.append("unchangedMs=").append(SystemClock.elapsedRealtime()-begin).append('\n');
            version=2;equal(body(),manager.readForMobile(url),"updated source not fetched");
            equal(body(),manager.readForMobile(url.replace("/list.txt","/opaque.txt")),"opaque hex ETag treated as MD5");
            boolean rejected=false;
            try {manager.readForMobile(url.replace("/list.txt","/bad-md5.txt"));}
            catch(IOException error){rejected=error.getMessage().contains("MD5");}
            if(!rejected)throw new AssertionError("explicit invalid MD5 accepted");
            report.append("opaqueHexEtagAccepted=true explicitBadMd5Rejected=true\n");
            equal(body(),manager.readForMobile(url.replace("/list.txt","/gzip.txt")),"gzip source decoding");
            equal(body(),manager.readForMobile(url.replace("/list.txt","/gzip-md5.txt")),"encoded MD5 identity retry");
            if(compressedReplies.get()!=2)throw new AssertionError("gzip negotiation missing or identity retry repeated");
            rejected=false;
            try {manager.readForMobile(url.replace("/list.txt","/gzip-bad-md5.txt"));}
            catch(IOException error){rejected=error.getMessage().contains("MD5");}
            if(!rejected)throw new AssertionError("gzip digest corruption accepted");
            report.append("gzipDecoded=true wireMd5Retry=true corruptMd5Rejected=true\n");
            if(args.containsKey("sourceUrl")) {
                byte[] online=manager.readForMobile(args.getString("sourceUrl"));
                java.lang.reflect.Method parse=PlaylistManager.class.getDeclaredMethod("parse",byte[].class,String.class);
                parse.setAccessible(true);
                ChannelCatalog.Group[] groups=(ChannelCatalog.Group[])parse.invoke(null,online,args.getString("sourceUrl"));
                int channels=0;for(ChannelCatalog.Group group:groups)channels+=group.channels.length;
                int expectedChannels=Integer.parseInt(args.getString("expectedChannels","210"));
                if(channels<=0 || expectedChannels>=0 && channels!=expectedChannels)
                    throw new AssertionError("online playlist channel count: "+channels);
                equal(online,manager.readForMobile(args.getString("sourceUrl")),"real source repeat/cache download");
                report.append("onlinePlaylistChannels=").append(channels).append(" bytes=").append(online.length).append('\n');
            }
            slow=true;
            begin=SystemClock.elapsedRealtime();boolean timedOut=false;
            try {manager.readForMobile(url);}catch(IOException error){timedOut=true;}
            long elapsed=SystemClock.elapsedRealtime()-begin;
            if(timedOut || elapsed>27000)throw new AssertionError("slow ordinary body rejected: "+elapsed);
            report.append("trickleMs=").append(elapsed).append(" timedOut=").append(timedOut).append('\n');
            slow=false;equal(body(),manager.readForMobile(url),"partial download overwrote valid cache");
            for(File file:context.getFilesDir().listFiles())if(file.getName().startsWith("online-playlist-"))
                try(FileOutputStream out=new FileOutputStream(file)){out.write(0);}
            equal(body(),manager.readForMobile(url),"corrupt cache reused");
            String md5=url.replace("/list.txt","/md5.txt");
            equal(body(),manager.readForMobile(md5),"MD5 source seed");
            slow=true;begin=SystemClock.elapsedRealtime();timedOut=false;
            try {manager.readForMobile(md5);}catch(IOException error){timedOut=true;}
            elapsed=SystemClock.elapsedRealtime()-begin;
            if(timedOut || elapsed>27000)throw new AssertionError("slow PHP HEAD + GET rejected: "+elapsed);
            report.append("headAndGetMs=").append(elapsed).append(" timedOut=").append(timedOut).append('\n');
            slow=false;equal(body(),manager.readForMobile(md5),"MD5 cache recovery");
            begin=SystemClock.elapsedRealtime();
            equal(body(),manager.readForMobile(url.replace("/list.txt","/delayed.php")),"21-second source rejected before new deadline");
            report.append("delayedMs=").append(SystemClock.elapsedRealtime()-begin).append(" accepted=true\n");
            begin=SystemClock.elapsedRealtime();timedOut=false;
            try {manager.readForMobile(url.replace("/list.txt","/stalled.php"));}catch(IOException error){timedOut=true;}
            elapsed=SystemClock.elapsedRealtime()-begin;
            if(!timedOut || elapsed<24000 || elapsed>27000)throw new AssertionError("stalled PHP not bounded at 25 seconds: "+elapsed);
            report.append("stalledMs=").append(elapsed).append(" timedOut=true\n");
            report.append("PASS conditional freshness, updated content, corrupt-cache recovery and bounded reads\n");
        }catch(Throwable error){code=1;report.append(error).append('\n').append(android.util.Log.getStackTraceString(error));}
        finally {
            if(server!=null)try{server.close();}catch(IOException ignored){}
            for(Socket client:clients)try{client.close();}catch(IOException ignored){}
            if(context!=null) {
                context.getSharedPreferences("management",0).edit().clear().commit();
                for(File file:context.getFilesDir().listFiles())file.delete();
                context.getFilesDir().delete();
            }
        }
        result.putString("refresh",report.toString());finish(code,result);
    }
}
