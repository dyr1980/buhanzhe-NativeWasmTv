package xiao.bu.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.*;
import org.json.*;

/** Branch routing uses the imported address without changing global settings. */
public final class CjsBranchImportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        JSONObject original = null;
        Method use = null;
        int code = 1;
        try {
            CjsPluginRuntime.initialize(getTargetContext());
            CjsPluginRuntime.ensureCatalog();
            String setting = CjsPluginRuntime.getManifestUrl();
            Field catalog = CjsPluginRuntime.class.getDeclaredField("catalog"); catalog.setAccessible(true);
            original = new JSONObject(((JSONObject) catalog.get(null)).toString());
            use = CjsPluginRuntime.class.getDeclaredMethod("useCatalog", JSONObject.class); use.setAccessible(true);
            Method discover = CjsPluginRuntime.class.getDeclaredMethod("discoverSourceDescriptor", CjsSource.class);
            discover.setAccessible(true);
            String url = "https://raw.githubusercontent.com/TvWasm/cjs/dev/cctv.cjs?id=cctv1";
            discover.invoke(null, CjsSource.parse(url));
            if (!CjsPluginRuntime.knowsSource(CjsSource.parse(url))) throw new AssertionError("Unknown branch source");
            if (!"webview://https://tv.cctv.com/live/cctv1/".equals(CjsPluginRuntime.playbackUrl(url)))
                throw new AssertionError("Wrong channel page");
            if (!setting.equals(CjsPluginRuntime.getManifestUrl())) throw new AssertionError("Global setting changed");
            JSONObject selected = (JSONObject) catalog.get(null);
            JSONArray oldSites = original.getJSONArray("sites"), sites = selected.getJSONArray("sites");
            for (int i = 0; i < oldSites.length(); i++) {
                JSONObject old = oldSites.getJSONObject(i);
                if ("tv.cctv.com".equals(old.getString("id"))) continue;
                boolean found = false;
                for (int j = 0; j < sites.length(); j++) if (old.toString().equals(sites.getJSONObject(j).toString())) found = true;
                if (!found) throw new AssertionError("Other site changed");
            }
            String snapshot = selected.toString();
            try {
                discover.invoke(null, CjsSource.parse(url.replace("cctv1", "bad-id")));
                throw new AssertionError("Invalid channel accepted");
            } catch (InvocationTargetException expected) {
                if (!snapshot.equals(((JSONObject) catalog.get(null)).toString())) throw new AssertionError("Invalid source changed catalog");
            }
            result.putString("stream", "PASS: dev routing, parameter validation, other sites and global directory unchanged\n");
            code = -1;
        } catch (Throwable error) { result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finally {
            if (original != null && use != null) try { use.invoke(null, original); } catch (Exception ignored) { }
        }
        finish(code, result);
    }
}
