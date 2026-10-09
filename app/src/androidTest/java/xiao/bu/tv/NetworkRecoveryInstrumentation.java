package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Validate scoped DNS refresh and total API timeout without relying on a public server. */
public final class NetworkRecoveryInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();int code=-1;
        try {
            java.util.Map<String,Integer> counts=new java.util.HashMap<>();
            ProcessDns dns=new ProcessDns(host->{counts.put(host,counts.containsKey(host)?counts.get(host)+1:1);
                return java.util.Collections.singletonList(InetAddress.getByName("127.0.0.1"));});
            dns.lookup("media.test");dns.lookup("media.test");dns.lookup("other.test");
            dns.invalidate("MEDIA.TEST");dns.lookup("media.test");dns.lookup("other.test");
            if(counts.get("media.test")!=2 || counts.get("other.test")!=1)
                throw new AssertionError("Scoped DNS invalidation failed");
            report.append("PASS failed-host DNS refresh preserves unrelated cached hosts\n");
            NetworkClient.initialize(getTargetContext());
            final ServerSocket server=new ServerSocket(0);final Socket[] accepted={null};
            Thread worker=new Thread(()->{try{accepted[0]=server.accept();while(accepted[0].getInputStream().read()!=-1){}}
                catch(Exception ignored){}});worker.setDaemon(true);worker.start();
            long started=SystemClock.elapsedRealtime();String response;
            try{response=Ku9HttpClient.requestJson("http://127.0.0.1:"+server.getLocalPort()+"/auth", "POST","{}","{}",false,1024,1500);}
            finally{server.close();if(accepted[0]!=null)accepted[0].close();}
            long elapsed=SystemClock.elapsedRealtime()-started;
            if(new org.json.JSONObject(response).optInt("code")!=0 || elapsed<1200 || elapsed>3500)
                throw new AssertionError("API deadline failed: "+elapsed+"ms "+response);
            report.append("PASS stalled API call deadline elapsedMs=").append(elapsed).append('\n');
            if("true".equals(args.getString("reconnectWifi"))) {
                android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getTargetContext()
                        .getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
                if(!wifi.isWifiEnabled())throw new AssertionError("Wi-Fi initially disabled; leave settings unchanged");
                if(args.containsKey("savedWifiSsid")) {
                    boolean found=false;
                    for(android.net.wifi.WifiConfiguration config:wifi.getConfiguredNetworks()) {
                        if(("\""+args.getString("savedWifiSsid")+"\"").equals(config.SSID)) {
                            found=wifi.enableNetwork(config.networkId,true)&&wifi.reconnect();
                            break;
                        }
                    }
                    if(!found)throw new AssertionError("Unable to reconnect saved Wi-Fi");
                } else {
                    if(!wifi.setWifiEnabled(false))throw new AssertionError("Wi-Fi reconnect denied");
                    try{SystemClock.sleep(2500);}finally{wifi.setWifiEnabled(true);}
                }
                android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)getTargetContext()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                long until=SystemClock.elapsedRealtime()+30000;
                while(SystemClock.elapsedRealtime()<until){
                    android.net.NetworkInfo network=connectivity.getActiveNetworkInfo();
                    if(network!=null && network.isConnected() && wifi.getDhcpInfo().gateway!=0)break;
                    SystemClock.sleep(500);
                }
                report.append("Wi-Fi enabled=").append(wifi.isWifiEnabled()).append(" gateway=").append(wifi.getDhcpInfo().gateway).append('\n');
                if(wifi.getDhcpInfo().gateway==0)throw new AssertionError("Wi-Fi did not acquire a gateway");
            }
        }catch(Throwable e){code=0;report.append(android.util.Log.getStackTraceString(e));}
        result.putString("stream",report.toString());finish(code,result);
    }
}
