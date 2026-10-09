package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;

/** Verify component deadlines even when the server keeps the connection open. */
public final class ComponentDownloadInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            NetworkClient.initialize(getTargetContext());
            for(boolean body:new boolean[]{false,true}) {
                final ServerSocket server=new ServerSocket(0);
                final Socket[] accepted={null};
                Thread worker=new Thread(()->{try {
                    accepted[0]=server.accept();
                    if(body) {
                        accepted[0].getOutputStream().write(
                                "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\na".getBytes("UTF-8"));
                        accepted[0].getOutputStream().flush();
                    }
                    while(accepted[0].getInputStream().read()!=-1) { }
                }catch(Exception ignored){}});
                worker.setDaemon(true); worker.start();
                HttpURLConnection connection=NetworkClient.openComponent(
                        new URL("http://127.0.0.1:"+server.getLocalPort()+"/version.cjs"),false);
                long start=SystemClock.elapsedRealtime();
                boolean failed=false;
                try {
                    connection.getResponseCode();
                    while(connection.getInputStream().read()!=-1) { }
                }catch(java.io.IOException expected){failed=true;}
                finally {
                    connection.disconnect(); server.close();
                    if(accepted[0]!=null) accepted[0].close();
                }
                long elapsed=SystemClock.elapsedRealtime()-start;
                if(!failed || elapsed<7000 || elapsed>12000)
                    throw new AssertionError("Deadline failed: body="+body+" elapsed="+elapsed);
                result.putLong(body?"stalledBodyMs":"stalledHeadersMs",elapsed);
            }
            result.putString("stream","Component header/body deadlines passed\n");
            finish(-1,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
}
