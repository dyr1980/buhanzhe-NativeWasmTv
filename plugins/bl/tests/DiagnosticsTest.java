package xiao.bu.channel.bl;

import java.io.IOException;
import okhttp3.*;

public final class DiagnosticsTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        BlDiagnostics log=new BlDiagnostics();
        Request request=new Request.Builder().url("https://api.bilibili.com/x/test?SESSDATA=secret-cookie&csrf=secret-csrf&w_rid=secret-sign").build();
        Response response=new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable").header("Content-Type","text/html").header("X-Bili-Trace-Id","trace-fixture").build();
        BlDiagnostics.Entry entry=log.record("页面请求",BlDiagnostics.response(response),"BV=BVtest; cid=2");
        check(entry.details.contains("HTTP：503")&&entry.details.contains("GET https://api.bilibili.com/x/test"),"Missing HTTP endpoint/status");
        check(entry.details.contains("trace-fixture")&&entry.details.contains("text/html"),"Missing trace or response type");
        check(!entry.details.contains("secret-")&&!entry.details.contains("?"),"URL query escaped redaction");
        IOException cause=new IOException("SESSDATA=secret-cookie csrf: secret-csrf Authorization: Bearer-secret token='secret-token' https://user:password@example.com/v.mp4?sign=secret-sign");
        entry=log.record("播放",new IOException("media failed",cause),"DASH; line=2/3");
        check(!entry.details.contains("secret-")&&!entry.details.contains("password"),"Credential escaped error-chain redaction");
        check(entry.details.contains("原因：IOException")&&entry.details.contains("DASH; line=2/3"),"Missing cause chain or playback context");
        check(!android.util.Log.last.contains("secret-"),"Unredacted logcat output");
        String json=BlDiagnostics.clean("{\"SESSDATA\":\"secret-json\",\"access_token\":\"secret-access\"}");
        check(!json.contains("secret-"),"Unredacted JSON credentials");
        check(BlDiagnostics.clean(json).equals(json),"Repeated redaction changed an already safe message");
        check(!BlDiagnostics.clean("Authorization: Bearer secret-bearer-token").contains("secret-"),"Bearer credential escaped redaction");
        check(BlDiagnostics.advice(new java.net.UnknownHostException()).contains("DNS"),"Missing DNS advice");
        check(BlDiagnostics.advice(new javax.net.ssl.SSLException("certificate")).contains("证书"),"Missing TLS advice");
        check(BlDiagnostics.advice(new java.net.SocketTimeoutException()).contains("超时"),"Missing timeout advice");
        for(int i=0;i<25;i++)log.record("bounded",new IOException("failure "+i),"");
        check(log.snapshot().size()==20&&log.latest().details.contains("failure 24"),"Unbounded or incorrectly ordered history");
        System.out.println("PASS: HTTP/business context, cause chain, actionable network hints, credential redaction, bounded history");
    }
}
