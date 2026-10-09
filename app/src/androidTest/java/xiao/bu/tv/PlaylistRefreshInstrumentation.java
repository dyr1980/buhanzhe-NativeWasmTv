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
public final class PlaylistRefreshInstrumentation extends Instrumentation {
    private Bundle args;
    private ServerSocket server;
    private final CopyOnWriteArrayList<Socket> clients=new CopyOnWriteArrayList<>();
    private final AtomicInteger notModified=new AtomicInteger();
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
            String request=reader.readLine(),line;boolean conditional=false;
            while((line=reader.readLine())!=null && !line.isEmpty())
                if(line.equalsIgnoreCase("If-None-Match: \"v"+version+"\""))conditional=true;
            boolean md5=request.contains("/md5");
            OutputStream output=client.getOutputStream();byte[] bytes=body();
            if(slow && md5)SystemClock.sleep(8000);
            if(conditional && !slow) {
                notModified.incrementAndGet();
                output.write("HTTP/1.1 304 Not Modified\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));
                return;
            }
            String validator=md5 ? "Content-MD5: "+android.util.Base64.encodeToString(
                    java.security.MessageDigest.getInstance("MD5").digest(bytes),android.util.Base64.NO_WRAP)
                    : "ETag: \"v"+version+"\"";
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "+bytes.length
                    +"\r\n"+validator+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
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
    private Context isolated() {
        final String prefix="refresh-test-"+System.currentTimeMillis();
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
            slow=true;boolean bounded=!"false".equals(args.getString("expectBounded"));
            begin=SystemClock.elapsedRealtime();boolean timedOut=false;
            try {manager.readForMobile(url);}catch(IOException error){timedOut=true;}
            long elapsed=SystemClock.elapsedRealtime()-begin;
            if(bounded && (!timedOut || elapsed>7500))throw new AssertionError("unbounded body read: "+elapsed);
            if(!bounded && timedOut)throw new AssertionError("baseline unexpectedly failed");
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
            if(bounded && (!timedOut || elapsed>7500))throw new AssertionError("HEAD + GET budget reset: "+elapsed);
            report.append("headAndGetMs=").append(elapsed).append(" timedOut=").append(timedOut).append('\n');
            slow=false;equal(body(),manager.readForMobile(md5),"MD5 cache recovery");
            report.append("PASS conditional freshness, updated content, corrupt-cache recovery and bounded reads\n");
        }catch(Throwable error){code=1;report.append(android.util.Log.getStackTraceString(error));}
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
