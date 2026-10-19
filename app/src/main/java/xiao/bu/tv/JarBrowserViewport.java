package xiao.bu.tv;

import android.webkit.WebView;
import java.util.Locale;

/** Same logical width / page scale calculation as WebSourceView's desktop viewport. */
final class JarBrowserViewport {
    static int width(String mode){return "4k".equals(mode)?3840:"2k".equals(mode)?2560:"1080p".equals(mode)?1920:1280;}
    static void apply(WebView view,String mode,float scale) {
        if(view.getWidth()<=0)return;
        int width=width(mode);
        float density=view.getResources().getDisplayMetrics().density;
        float zoom=Math.max(.5f,Math.min(3f,scale));
        float initial=view.getWidth()/(Math.max(.1f,density)*width)*zoom;
        String value="width="+Math.max(1,Math.round(width/zoom))+",initial-scale="
                +String.format(Locale.US,"%.4f",initial)+",minimum-scale="+String.format(Locale.US,"%.4f",initial)
                +",maximum-scale="+String.format(Locale.US,"%.4f",initial)+",user-scalable=no";
        view.getSettings().setUseWideViewPort(true);
        view.getSettings().setLoadWithOverviewMode(true);
        view.setInitialScale(Math.max(1,Math.round(view.getWidth()/(float)width*zoom*100)));
        String js="(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='"+value+"';window.dispatchEvent(document.createEvent? (function(){var e=document.createEvent('Event');e.initEvent('resize',false,false);return e;})():new Event('resize'));})()";
        if(android.os.Build.VERSION.SDK_INT>=19)view.evaluateJavascript(js,null);else view.loadUrl("javascript:"+js);
    }
}
