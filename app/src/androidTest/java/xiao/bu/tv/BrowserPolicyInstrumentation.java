package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real WebView regressions for chrome, per-document scripts and the rule cache. */
public final class BrowserPolicyInstrumentation extends Instrumentation {
    private WebView web;
    private WebPageScriptManager policies;
    private interface Work { void run() throws Exception; }
    private void main(Work work) throws Exception {
        Throwable[] error = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static Object get(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private String js(String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1); String[] result = {null};
        main(() -> web.evaluateJavascript(script, value -> { result[0] = value; done.countDown(); }));
        check(done.await(5, TimeUnit.SECONDS), "JS timeout"); return result[0];
    }
    private void load(String url) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        main(() -> {
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) { done.countDown(); }
            });
            web.loadDataWithBaseURL(url, "<!doctype html><html><body>Browser policy regression</body></html>", "text/html", "UTF-8", null);
        });
        check(done.await(8, TimeUnit.SECONDS), "Document timeout");
    }
    private JSONObject script(String metadata, String body) throws Exception {
        return new JSONObject().put("name", "fixture").put("enabled", true)
                .put("source", "// ==UserScript==\n" + metadata + "\n// ==/UserScript==\n" + body);
    }
    private String testScripts() throws Exception {
        main(() -> { web = new WebView(getTargetContext()); web.getSettings().setJavaScriptEnabled(true); policies = new WebPageScriptManager(web); });
        String sources = new JSONArray()
                .put(script("// @match https://*.example.test/*", "var = broken;"))
                .put(script("// @match https://*.example.test/*\n// @run-at document-end", "window.hits=(window.hits||0)+1;"))
                .put(script("// @match https://*.example.test/*\n// @exclude-match https://example.test/private*", "window.visibleHit=(window.visibleHit||0)+1;"))
                .put(script("// @match https://*.other.test/*", "window.wrongHost=true;"))
                .put(script("// @include https://example.test/*", "window.includeHit=(window.includeHit||0)+1;"))
                .toString();
        main(() -> policies.update(true, false, true, sources));
        Object first = ((List<?>) get(policies, "userScripts")).get(0);
        main(() -> policies.update(true, false, true, sources));
        check(first == ((List<?>) get(policies, "userScripts")).get(0), "Unchanged scripts recompiled");
        load("https://example.test/path?q=1#section");
        main(() -> { policies.applyToCurrentDocument(); policies.applyToCurrentDocument(); });
        check("[1,1,1,false,true]".equals(js("JSON.stringify([hits,visibleHit,includeHit,!!window.wrongHost,!!document.getElementById('__ntv_ad_css')])").replace("\\\"", "\"").replaceAll("^\"|\"$", "")), "Scripts isolated/matched/executed incorrectly");
        main(() -> { policies.update(false, false, true, sources); policies.applyToCurrentDocument(); });
        check(first == ((List<?>) get(policies, "userScripts")).get(0), "Ad toggle recompiled userscripts");
        check("1".equals(js("hits")), "Policy update ran userscript twice");
        load("https://sub.example.test/path"); main(policies::applyToCurrentDocument);
        check("1".equals(js("hits")), "Wildcard subdomain did not match");
        check("false".equals(js("!!window.includeHit")), "Include glob matched unrelated URL");
        load("https://example.test/private"); main(policies::applyToCurrentDocument);
        check("false".equals(js("!!window.visibleHit")), "Exclude-match ignored");
        load("https://notexample.test/path"); main(policies::applyToCurrentDocument);
        check("false".equals(js("!!window.hits")), "Host boundary not enforced");
        main(() -> { policies.dispose(); web.destroy(); });
        return "scripts: isolated syntax errors, apex/subdomain/include/exclude, once/document, cached compilation; ";
    }
    private String testRules() throws Exception {
        Context base = getTargetContext(); final String id = "ad-rule-test-" + System.nanoTime();
        Context context = new ContextWrapper(base) {
            @Override public File getDatabasePath(String name) { File dir = new File(base.getCacheDir(), id); dir.mkdirs(); return new File(dir, name); }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return base.getSharedPreferences(id, mode); }
        };
        Constructor<AdBlockRuleStore> constructor = AdBlockRuleStore.class.getDeclaredConstructor(Context.class); constructor.setAccessible(true);
        AdBlockRuleStore store = constructor.newInstance(context);
        File target = context.getDatabasePath("web_ad_block.db");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(target, null);
        db.execSQL("CREATE TABLE domain_rule(host TEXT PRIMARY KEY, action INTEGER)");
        db.execSQL("CREATE TABLE wildcard_rule(pattern TEXT, action INTEGER)");
        db.execSQL("INSERT INTO domain_rule VALUES('ads.example.test',1)");
        db.execSQL("INSERT INTO domain_rule VALUES('allow.ads.example.test',-1)");
        db.execSQL("INSERT INTO wildcard_rule VALUES('tracker*.example.test',1)"); db.close();
        Field count = AdBlockRuleStore.class.getDeclaredField("ruleCount"); count.setAccessible(true); count.setInt(store, 3);
        check(store.match("sub.ads.example.test") == 1, "Domain suffix not blocked");
        check(store.match("allow.ads.example.test") == -1, "Allow override ignored");
        check(store.match("tracker7.example.test") == 1, "Wildcard ignored");
        check(store.match("notads.example.test") == 0, "Host boundary ignored");
        check(AdBlockRuleStore.parse("||ads.example.test^$domain=somewhere.test") == null, "Scoped filter became global");
        check(AdBlockRuleStore.parse("@@||ads.example.test^$script") == null, "Scoped exception became global");
        check(AdBlockRuleStore.parse("||ads.example.test^").action == 1, "Plain filter not accepted");
        long start = SystemClock.elapsedRealtime();
        for (int i = 0; i < 100000; i++) check(store.match("sub.ads.example.test") == 1, "Cache mismatch");
        long elapsed = SystemClock.elapsedRealtime() - start;
        File replacement = context.getDatabasePath("replacement.db");
        db = SQLiteDatabase.openOrCreateDatabase(replacement, null);
        db.execSQL("CREATE TABLE domain_rule(host TEXT PRIMARY KEY, action INTEGER)");
        db.execSQL("CREATE TABLE wildcard_rule(pattern TEXT, action INTEGER)");
        db.execSQL("INSERT INTO domain_rule VALUES('ads.example.test',-1)"); db.close();
        Class<?> imported = Class.forName("xiao.bu.tv.AdBlockRuleStore$ImportResult");
        Constructor<?> result = imported.getDeclaredConstructor(int.class, String.class); result.setAccessible(true);
        call(store, "swapDatabase", new Class<?>[] {File.class, File.class, imported}, replacement, target, result.newInstance(1, "fixture"));
        check(store.match("sub.ads.example.test") == -1, "Old cache survived database swap");
        ((SQLiteDatabase) get(store, "database")).close();
        return "rules: domain/allow/wildcard/scoped filters/cache swap; 100k hot hits=" + elapsed + "ms; ";
    }
    private String testChrome() throws Exception {
        MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(1000);
        WebSourceView source = (WebSourceView) get(activity, "webSourceView");
        main(() -> { source.setListener(null); source.open(93001, "https://example.test/"); });
        WebTabBar bar = (WebTabBar) get(source, "tabBar");
        main(() -> {
            call(bar, "setBookmarkBarVisible", new Class<?>[] {boolean.class}, true);
            check(bar.getChildCount() == 2, "Address row still exists");
            float density = bar.getResources().getDisplayMetrics().density;
            ViewGroup tabStrip = (ViewGroup) get(bar, "tabStrip");
            check(tabStrip.getChildAt(0).getLayoutParams().height == Math.round(22 * density), "Tab card not compact");
            check(((View) get(bar, "tabsRow")).getLayoutParams().height == Math.round(26 * density), "Tab row has excess height");
            check(bar.heightDp() == 58, "Toolbar content height not updated");
            View more = (View) get(bar, "moreButton");
            ViewGroup row = (ViewGroup) get(bar, "bookmarkRow");
            check(more.getParent() == row && row.indexOfChild(more) == row.getChildCount() - 1, "More is not rightmost/outside bookmark scroll");
            call(bar, "setBookmarkBarVisible", new Class<?>[] {boolean.class}, false);
            check(more.getParent() == get(bar, "tabsRow"), "Hiding bookmarks lost More");
            check(bar.heightDp() == WebTabBar.COMPACT_HEIGHT_DP, "Compact height mismatch");
            call(bar, "setBookmarkBarVisible", new Class<?>[] {boolean.class}, true);
            Object scripts = get(source, "pageScriptManager"), desktop = get(source, "desktopProfile");
            WebView view = (WebView) get(source, "webView");
            WebTabBar.Tab tab = bar.active();
            call(source, "retainCurrentWebView", new Class<?>[] {WebTabBar.Tab.class}, tab);
            check((Boolean) call(source, "activateRetainedWebView", new Class<?>[] {WebTabBar.Tab.class}, tab), "Retained activation failed");
            check(scripts == get(source, "pageScriptManager") && desktop == get(source, "desktopProfile"), "Restoring tab re-registered managers");
            check(view == get(source, "webView"), "Restoring tab recreated WebView");
            source.applyConfiguration("720p", true, "native", "native", 1f, true, false, false, "[]");
            Field injection = WebSourceView.class.getDeclaredField("pagePolicyInjectionCount"); injection.setAccessible(true); injection.setInt(source, 0);
            WebViewClient client = (WebViewClient) get(source, "sourceClient");
            for (int i = 0; i < 100; i++) client.onLoadResource(view, "https://example.test/image" + i + ".png");
            check(injection.getInt(source) <= 3, "Unbounded legacy script injections");
            ChannelCatalog.Group[] groups = ChannelCatalog.GROUPS;
            try {
                ChannelCatalog.GROUPS = new ChannelCatalog.Group[]{new ChannelCatalog.Group("Web fixture",
                        ChannelCatalog.SOURCE_CUSTOM, new Channel[]{new Channel("1", "Bookmark", "",
                        "webview://https://example.test/bookmark", null, null)})};
                WebTabBar.Tab previous = bar.active();
                String previousUrl = previous.url;
                call(bar, "showChannelGroup", new Class<?>[]{View.class, int.class}, more, 0);
                ViewGroup folder = (ViewGroup) get(bar, "folderBody");
                check(folder != null && folder.getChildCount() == 1, "Channel bookmark folder missing");
                folder.getChildAt(0).performClick();
                check(bar.active() != previous && "https://example.test/bookmark".equals(bar.active().url), "Folder bookmark did not open new tab");
                check(previousUrl.equals(previous.url), "Folder bookmark replaced original URL");
            } finally { ChannelCatalog.GROUPS = groups; }
            List<WebTabBar.Tab> tabs = (List<WebTabBar.Tab>) get(bar, "tabs");
            List<WebTabBar.Tab> saved = new java.util.ArrayList<WebTabBar.Tab>(tabs);
            WebTabBar.Tab selected = bar.active();
            boolean pinned = selected.pinned;
            try {
                tabs.clear(); selected.pinned = true; tabs.add(selected);
                for (int i = 1; i < 32; i++) tabs.add(new WebTabBar.Tab(95000 + i, "https://example.test/pinned", "Pinned", true));
                String selectedUrl = selected.url;
                source.openLinkInNewTab("https://example.test/must-not-replace");
                check(bar.active() == selected && selectedUrl.equals(selected.url), "Full pinned tabs replaced current page");
            } finally { selected.pinned = pinned; tabs.clear(); tabs.addAll(saved); }
        });
        main(() -> {
            // Capture the actual toolbar view deterministically; unrelated native
            // startup playback must not turn this layout check into a black screen.
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
            small.eraseColor(0xff1976d2);
            bar.updateActiveIcon(bar.active().url, small);
            call(bar, "render", new Class<?>[0]);
            int width = bar.getResources().getDisplayMetrics().widthPixels;
            int height = Math.round(bar.heightDp() * bar.getResources().getDisplayMetrics().density);
            bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            bar.layout(0, 0, width, height);
            android.graphics.Bitmap screenshot = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            bar.draw(new android.graphics.Canvas(screenshot));
            File file = new File(getTargetContext().getExternalFilesDir(null), "browser-policy.png");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
        });
        return "chrome: two rows, reachable More in both modes, retained policy identity, bounded resource injections, folder bookmarks open new tab, pinned capacity preserves current page";
    }
    private String testFavicons() throws Exception {
        main(() -> {
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
            small.eraseColor(android.graphics.Color.RED);
            small.setDensity(120); // BitmapDrawable would enlarge this on high-DPI screens.
            WebBookmarkStore store = new WebBookmarkStore(getTargetContext(),
                    getTargetContext().getSharedPreferences("favicon-test", 0));
            store.cacheIcon("https://small.example.test", small);
            check(store.iconForUrl("https://small.example.test") == small, "Small favicon was resampled");
            android.graphics.Bitmap wide = android.graphics.Bitmap.createBitmap(96, 48, android.graphics.Bitmap.Config.ARGB_8888);
            store.cacheIcon("https://wide.example.test", wide);
            android.graphics.Bitmap cached = store.iconForUrl("https://wide.example.test");
            check(cached.getWidth() == 48 && cached.getHeight() == 24, "Favicon aspect ratio lost");
            BrowserFaviconView icon = new BrowserFaviconView(getTargetContext(), small);
            icon.layout(0, 0, 80, 40);
            android.graphics.Rect bounds = (android.graphics.Rect) get(icon, "bounds");
            int expected = Math.min(16, Math.round(14 * getTargetContext().getResources().getDisplayMetrics().density));
            check(bounds.width() == expected && bounds.height() == expected, "Favicon enlarged by density or tab width");
            android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(80, 40, android.graphics.Bitmap.Config.ARGB_8888);
            icon.draw(new android.graphics.Canvas(pixels));
            check(pixels.getPixel(bounds.left, bounds.top) == android.graphics.Color.RED, "Favicon pixels blurred at integer bounds");
            check(pixels.getPixel(bounds.left - 1, bounds.top) == 0, "Favicon exceeds compact bounds");
            pixels.recycle();
        });
        return "favicons: no upscaling, preserved aspect ratio, density-independent pixel bounds; ";
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1;
        try { result.putString("stream", "PASS " + testScripts() + testRules() + testFavicons() + testChrome() + "\n"); }
        catch (Throwable error) { code = 0; result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finish(code, result);
    }
}
