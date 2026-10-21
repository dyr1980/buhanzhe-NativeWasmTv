package xiao.bu.channel.bl;

import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;
import okhttp3.*;
import org.json.JSONObject;

/** Real shared OkHttp + fault fixture; no Android UI or real accounts. */
public final class ApiDiagnosticsTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void arm(OkHttpClient client,String base,String fault)throws Exception{try(Response r=client.newCall(new Request.Builder().url(base+"/__fixture/fault?mode="+fault).build()).execute()){check(r.isSuccessful(),"Fixture arm failed");}}
    public static void main(String[] args)throws Exception{
        final Map<String,Object> values=new HashMap<>();
        final SharedPreferences.Editor editor=(SharedPreferences.Editor)Proxy.newProxyInstance(ApiDiagnosticsTest.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){String n=method.getName();if(n.equals("putString")){values.put((String)args[0],args[1]);return proxy;}if(n.equals("remove")){values.remove(args[0]);return proxy;}if(n.equals("apply"))return null;if(n.equals("commit"))return true;return proxy;}});
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(ApiDiagnosticsTest.class.getClassLoader(),new Class[]{SharedPreferences.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){String n=method.getName();if(n.equals("getString"))return values.containsKey(args[0])?values.get(args[0]):args[1];if(n.equals("getAll"))return values;if(n.equals("edit"))return editor;return null;}});
        String base=args[0];OkHttpClient client=new OkHttpClient();BlApi api=new BlApi(client,prefs,new JSONObject().put("apiOrigin",base));
        check(api.client==client,"Shared client identity changed");
        try{
            for(String mode:new String[]{"http","json","business"}){
                arm(client,base,mode);boolean failed=false;
                try{api.popular(1);}catch(BlDiagnostics.Failure error){failed=true;
                    if(mode.equals("http"))check(error.http==503&&error.requestId.equals("bl-fixture-503"),"Missing HTTP or trace metadata");
                    if(mode.equals("json"))check(error.kind.equals("响应格式")&&error.contentType.equals("text/html"),"Missing malformed-response metadata");
                    if(mode.equals("business"))check(error.apiCode==-352,"Missing business code");
                    BlDiagnostics.Entry entry=api.diagnostics.record("测试 API",error,"");check(!entry.details.contains("fixture-secret-token"),"API message leaked a token");check(entry.details.contains("/x/web-interface/popular"),"Missing API path");
                }
                check(failed,"Injected failure did not reach caller");check(api.popular(1).length()>0,"Could not recover after injected failure");
            }
            try{api.bytes(base+"/video.mp4?fail=1&token=fixture-secret-token",1024);throw new AssertionError("403 media was accepted");}catch(BlDiagnostics.Failure error){check(error.http==403,"Missing media status");}
            Field pending=BlApi.class.getDeclaredField("pending");pending.setAccessible(true);check(((Set<?>)pending.get(api)).isEmpty(),"Responses leaked after failures");
            System.out.println("PASS: real OkHttp HTTP 503/trace, JSON format, API -352, CDN 403, recovery, redaction and response cleanup");
        }finally{api.close();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
    }
}
