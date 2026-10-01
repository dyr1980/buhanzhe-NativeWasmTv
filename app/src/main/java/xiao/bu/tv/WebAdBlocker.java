package xiao.bu.tv;

import android.content.Context;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small, conservative request filter for web channels. */
final class WebAdBlocker {
    private static final String[] HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adsrvr.org", "scorecardresearch.com",
            "taboola.com", "outbrain.com", "cpro.baidu.com", "pos.baidu.com",
            "union.baidu.com", "gdt.qq.com", "e.qq.com", "tanx.com",
            "alimama.com", "adashx.ut.taobao.com"
    };

    private WebAdBlocker() { }

    private static volatile AdBlockRuleStore rules;
    private static Context appContext;
    private static volatile Set<String> customUrls = java.util.Collections.emptySet();
    private static final String CUSTOM_URLS = "web_ad_block_custom_urls_v1";

    static synchronized void initialize(Context context) {
        if (context == null) return;
        if (appContext == null) {
            appContext = context.getApplicationContext();
            customUrls = java.util.Collections.unmodifiableSet(new HashSet<String>(appContext.getSharedPreferences(MainActivity.PREFERENCES,
                    Context.MODE_PRIVATE).getStringSet(CUSTOM_URLS,
                    java.util.Collections.<String>emptySet())));
        }
        if (rules == null) rules = AdBlockRuleStore.get(context);
    }

    static void refreshAsync(boolean force) {
        AdBlockRuleStore current = rules;
        if (current != null) current.refreshAsync(force);
    }

    static void trimMemory() {
        AdBlockRuleStore current = rules;
        if (current != null) current.trimMemory();
    }

    static int ruleCount() { return rules == null ? 0 : rules.ruleCount(); }
    static long lastUpdatedAt() { return rules == null ? 0L : rules.lastUpdatedAt(); }
    static String version() { return rules == null ? "" : rules.version(); }
    static boolean isUpdating() { return rules != null && rules.isUpdating(); }
    static String lastError() { return rules == null ? "" : rules.lastError(); }
    static String sourceUrl() { return AdBlockRuleStore.RULE_URL; }

    static boolean shouldBlock(String value) {
        if (value == null || value.length() == 0) return false;
        try {
            URI uri = URI.create(value);
            Set<String> custom = customUrls;
            if (!custom.isEmpty() && custom.contains(canonicalUrl(uri))) return true;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            AdBlockRuleStore current = rules;
            if (current != null) {
                int result = current.match(host);
                if (result == AdBlockRuleStore.ALLOW) return false;
                if (result == AdBlockRuleStore.BLOCK) return true;
            }
            for (String blocked : HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
            }
        } catch (IllegalArgumentException ignored) { }
        return false;
    }

    static synchronized boolean markAsAd(Context context, String value) {
        initialize(context);
        try {
            String canonical = canonicalUrl(URI.create(value));
            if (canonical.length() == 0 || customUrls.contains(canonical)) return false;
            Set<String> updated = new HashSet<String>(customUrls);
            updated.add(canonical);
            customUrls = java.util.Collections.unmodifiableSet(updated);
            appContext.getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putStringSet(CUSTOM_URLS, updated).apply();
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String canonicalUrl(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getScheme() == null) return "";
        String scheme = uri.getScheme().toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return "";
        String path = uri.getRawPath();
        if (path == null || path.length() == 0) path = "/";
        int port = uri.getPort();
        return scheme + "://" + uri.getHost().toLowerCase(Locale.US)
                + (port < 0 ? "" : ":" + port) + path;
    }

    static String cosmeticScript() {
        return "function n(){if(document.getElementById('__ntv_ad_css'))return;"
                + "var s=document.createElement('style');s.id='__ntv_ad_css';"
                + "s.textContent='.adsbygoogle,ins.adsbygoogle,[id^=google_ads_],"
                + "iframe[src*=doubleclick]{display:none!important}';"
                + "(document.head||document.documentElement).appendChild(s)}"
                + "if(document.documentElement)n();else document.addEventListener('DOMContentLoaded',n,{once:true});";
    }
}
