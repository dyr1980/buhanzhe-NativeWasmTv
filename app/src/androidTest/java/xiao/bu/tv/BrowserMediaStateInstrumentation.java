package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import org.json.JSONObject;

/** Real documents/tabs with deterministic resource events; no external websites required. */
public final class BrowserMediaStateInstrumentation extends Instrumentation {
    private MainActivity activity;
    private WebSourceView source;
    private String mediaFile;
    private boolean exitOnly;
    private boolean overlaysOnly;
    private boolean folderInputOnly;
    private boolean backgroundTabOnly;
    private boolean retentionOnly;
    private boolean browserFailureOnly;
    private boolean memoryOnly;
    private boolean faviconOnly;
    private boolean cpuOnly;
    private boolean cpuGuardsOnly;
    private boolean tabCloseOnly;
    private boolean audioHandoff;
    private boolean webMediaOnly;
    private interface Work { void run() throws Exception; }
    private static Object get(Object owner, String key) throws Exception {
        Field field = owner.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(owner);
    }
    private static Object call(Object owner, String key, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(key, types); method.setAccessible(true); return method.invoke(owner, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void main(Work work) throws Exception {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { work.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private JSONObject state() throws Exception {
        JSONObject[] value = {null};
        main(() -> value[0] = (JSONObject) call(activity, "buildLocalMediaState", new Class<?>[]{boolean.class}, false));
        return value[0];
    }
    private JSONObject awaitPage(String title) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        JSONObject value;
        do {
            value = state();
            if (title.equals(value.optString("name"))) return value;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected page " + title + ": " + value);
    }
    private void observe(String url) throws Exception {
        main(() -> ((WebViewClient) get(source, "sourceClient")).onLoadResource((WebView) get(source, "webView"), url));
        waitForIdleSync();
        check(state().getJSONArray("sniffedResources").length() == 1, "Resource not observed");
    }
    private String evaluate(String expression) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String[] value = {null};
        main(() -> ((WebView) get(source, "webView")).evaluateJavascript(expression, answer -> {
            value[0] = answer; done.countDown();
        }));
        check(done.await(cpuOnly ? 20 : 5, java.util.concurrent.TimeUnit.SECONDS), "Renderer stopped responding");
        return value[0];
    }
    private void awaitVideo() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        do {
            if ("true".equals(evaluate("!!(window.video&&video.readyState>=2&&!video.paused&&video.currentTime>0)"))) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Fixture video did not play");
    }
    private void awaitNativePlayback() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        do {
            JSONObject value = state();
            if (value.optBoolean("prepared") && value.optBoolean("playing")) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Sniffed fixture did not start: " + state());
    }
    private void checkSniffedPlaybackIdentity(String base) throws Exception {
        if (audioHandoff) {
            evaluate("window.backgroundAudio=document.createElement('audio');backgroundAudio.src='/codec.mp4?background';"
                    + "backgroundAudio.loop=true;document.body.appendChild(backgroundAudio);backgroundAudio.play();");
        }
        main(() -> source.openLinkInNewTab(base + "/b"));
        awaitPage("Page B");
        String first = base + "/codec.mp4?first", second = base + "/codec.mp4?second";
        evaluate("window.__ntvSniffMarker=true;window.sniffBackgroundVideo=document.createElement('video');"
                + "sniffBackgroundVideo.muted=" + !audioHandoff + ";sniffBackgroundVideo.loop=true;sniffBackgroundVideo.src='/codec.mp4?first';"
                + "document.body.appendChild(sniffBackgroundVideo);sniffBackgroundVideo.play();");
        if (audioHandoff) {
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!"true".equals(evaluate("!sniffBackgroundVideo.paused&&!sniffBackgroundVideo.muted&&sniffBackgroundVideo.webkitAudioDecodedByteCount>0"))) {
                check(SystemClock.elapsedRealtime() < deadline, "Unmuted WebView audio did not decode");
                SystemClock.sleep(100);
            }
        }
        main(() -> {
            WebViewClient client = (WebViewClient) get(source, "sourceClient");
            WebView view = (WebView) get(source, "webView");
            client.onLoadResource(view, first); client.onLoadResource(view, second);
            check((base + "/b").equals(call(activity, "currentDebugSourcePath", new Class<?>[0])),
                    "Visible page debug URL still describes catalog entry");
        });
        waitForIdleSync();
        String firstKey = null;
        Object entry = call(activity, "currentChannel", new Class<?>[0]);
        for (String url : new String[]{first, second}) {
            main(() -> {
                Object resource = call(activity, "findSniffedResource", new Class<?>[]{String.class}, url);
                check(resource != null, "Missing sniffed fixture");
                call(activity, "startSniffedResource", new Class<?>[]{resource.getClass()}, resource);
                checkSniffedCard("Page B");
            });
            awaitNativePlayback();
            JSONObject playing = state();
            check("Page B".equals(playing.getString("name")) && "网页资源".equals(playing.getString("group")),
                    "Hidden page reverted to old channel: " + playing);
            check(!playing.getBoolean("webPageVisible") && playing.getBoolean("canReturnToWeb") && !playing.getBoolean("backExitsApp")
                    && playing.getBoolean("fileDownloadAvailable") && !playing.getBoolean("favoriteAvailable")
                    && playing.getInt("sourceCount") == 0 && (base + "/b").equals(playing.getString("pageUrl")),
                    "Sniffed metadata changed native controls or exposed old channel sources");
            main(() -> {
                check(url.equals(call(activity, "currentDebugSourcePath", new Class<?>[0])), "Stale sniffed debug URL");
                check(entry == call(activity, "currentChannel", new Class<?>[0]), "Presentation rewrote catalog selection");
                check(entry == get(activity, "activePlayerChannel"), "Catalog playback identity changed");
                checkSniffedCard("Page B");
                String catalogName = ((Channel) entry).name;
                call(activity, "showLoading", new Class<?>[]{String.class, String.class}, catalogName, "正在缓冲");
                checkSniffedCard("Page B");
                call(activity, "hideLoading", new Class<?>[0]);
                call(activity, "showChannelBar", new Class<?>[]{String.class, String.class}, catalogName, "播放中");
                checkSniffedCard("Page B");
            });
            String key = playing.getString("sourceKey");
            check(!key.equals(firstKey), "Switching sniffed resource reused old control/download identity");
            firstKey = key;
        }
        long[] focusBeforeDrain = {0};
        if (audioHandoff) main(() -> focusBeforeDrain[0] = (Long)get(activity, "playbackAudioFocusGeneration"));
        SystemClock.sleep(1000);
        main(() -> check(!(Boolean)get(source, "streamPageSuspended"), "Old selection drained the new page before 15s"));
        long suspendDeadline = SystemClock.elapsedRealtime() + 22000;
        boolean[] suspended = {false};
        do {
            main(() -> suspended[0] = (Boolean)get(source, "streamPageSuspended")
                    && "about:blank".equals(((WebView)get(source, "webView")).getUrl()));
            if (!suspended[0]) SystemClock.sleep(100);
        } while (!suspended[0] && SystemClock.elapsedRealtime() < suspendDeadline);
        check(suspended[0], "Stable sniffed playback did not unload original document");
        check("true".equals(evaluate("!window.__ntvSniffMarker&&!document.querySelector('video,audio,iframe')")),
                "Unloaded document retained media or JS state");
        JSONObject stable = state();
        check(stable.getBoolean("playing") && stable.getBoolean("backExitsApp") && !stable.getBoolean("canReturnToWeb")
                && "Page B".equals(stable.getString("name")), "Draining WebView disrupted native playback/identity");
        if (audioHandoff) {
            SystemClock.sleep(500);
            main(() -> {
                check(((java.util.Set<?>)get(source, "pendingShutdowns")).isEmpty(), "Not all WebViews drained");
                check((Long)get(activity, "playbackAudioFocusGeneration") == focusBeforeDrain[0] + 1,
                        "Cleanup did not complete exactly one owned-focus handoff");
                check(!(Boolean)call(activity, "isPlaybackMuted", new Class<?>[0]), "Native audio muted after WebView drain");
            });
            checkAudioHandoffGuards();
        }
        main(() -> {
            call(activity, "showChannelBar", new Class<?>[]{String.class, String.class}, ((Channel)entry).name, "播放中");
            checkSniffedCard("Page B");
        });
        main(() -> check(((java.util.Map<?,?>)get(source,"retainedTabWebViews")).isEmpty(), "Background renderer survived native playback"));
        checkSniffedBackOverlays();
        main(() -> activity.onBackPressed());
        check(!activity.isFinishing() && state().getBoolean("playing"), "First BACK stopped playback or exited");
        check("true".equals(evaluate("location.href==='about:blank'")), "BACK reloaded original page");
        if (exitOnly) {
            main(() -> activity.onBackPressed());
            check(activity.isFinishing(), "Second BACK did not exit");
            Bundle progress = new Bundle(); progress.putString("stream", "PASS first BACK keeps native playback and blank WebView; second BACK exits without reloading\n");
            sendStatus(1, progress);
            return;
        }
        // Continue the independent browser lifecycle tests by explicit navigation,
        // not by the removed BACK-to-web behavior.
        main(() -> {
            call(activity, "releasePlayer", new Class<?>[0]);
            call(activity, "closeWebSource", new Class<?>[0]);
            source.open((Integer)get(activity, "playRequestId"), base + "/a");
        });
        awaitPage("Page A");
        SystemClock.sleep(2300);
        check(evaluate("document.title").contains("Page A"), "Late drain blanked reloaded page");
        main(() -> {
            check(get(activity, "playingSniffedResource") == null, "Return retained stale media identity");
            check((base + "/a").equals(call(activity, "currentDebugSourcePath", new Class<?>[0])), "Return kept media debug URL");
        });
        Bundle progress = new Bundle(); progress.putString("stream", "PASS sniffed native title/URL, source switching, 15s document drain, native playback preserved and page reload\n");
        sendStatus(1, progress);
    }

    private void checkSniffedCard(String title) throws Exception {
        check(title.contentEquals(((android.widget.TextView)get(activity,"channelName")).getText()), "Bottom card has stale page title");
        check(!((android.widget.TextView)get(activity,"statusText")).getText().toString().contains("线路"), "Bottom card exposed catalog sources");
        check(((android.widget.TextView)activity.findViewById(R.id.channel_card_number)).getText().length() == 0, "Bottom card exposed catalog number");
        check(((android.widget.TextView)get(activity,"channelEpg")).getVisibility() != View.VISIBLE, "Bottom card exposed catalog EPG");
    }

    private JSONObject awaitWebMedia(String title, boolean playing) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        JSONObject value;
        do {
            value = state();
            if (title.equals(value.optString("name")) && value.optBoolean("webMedia")
                    && value.optBoolean("playing") == playing) return value;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing web media state: " + value);
    }

    private void checkWebMediaSession(String base) throws Exception {
        WebTabBar bar = (WebTabBar)get(source, "tabBar");
        WebTabBar.Tab tabA = bar.active();
        check("true".equals(evaluate("!!window.__ntvWebMedia")), "Media hooks not installed");
        evaluate("window.plays=0;window.pauses=0;navigator.mediaSession.metadata=new MediaMetadata({"
                + "title:'Track A',artist:'Artist A',album:'Album A',artwork:[{src:'/cover.png'}]});"
                + "navigator.mediaSession.playbackState='playing';"
                + "navigator.mediaSession.setActionHandler('play',function(){plays++;navigator.mediaSession.playbackState='playing'});"
                + "navigator.mediaSession.setActionHandler('pause',function(){pauses++;navigator.mediaSession.playbackState='paused'});");
        JSONObject current = awaitWebMedia("Track A", true);
        check("Artist A".equals(current.optString("artist")), "Artist not exposed");
        check((base + "/cover.png").equals(current.optString("webArtworkUrl")), "Cover URL not resolved");
        check(!current.optBoolean("previousAvailable") && !current.optBoolean("nextAvailable"), "Web controls can switch native channels");
        JSONObject command = new JSONObject().put("action", "pause").put("webPageKey", current.getString("webPageKey"))
                .put("webMediaToken", current.getString("webMediaToken"));
        call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command);
        awaitWebMedia("Track A", false);
        check("1".equals(evaluate("pauses")), "Site pause handler not called once");
        command.put("action", "play");
        call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command);
        awaitWebMedia("Track A", true);
        check("1".equals(evaluate("plays")), "Site play handler not called once");
        evaluate("navigator.mediaSession.metadata=new MediaMetadata({title:'Track A2'});");
        current = awaitWebMedia("Track A2", true);
        check(current.optString("webArtworkUrl").isEmpty(), "Old artwork survived track change");
        main(() -> source.openLinkInNewTab(base + "/b"));
        awaitPage("Page B");
        current = state();
        check(!current.optBoolean("webMedia") && !current.optBoolean("available")
                && current.optString("webArtworkUrl").isEmpty(), "Old metadata/player leaked into new tab");
        boolean rejected = false;
        try { call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command); }
        catch (InvocationTargetException expected) { rejected = true; }
        check(rejected, "Old page command accepted after tab switch");
        main(() -> call(bar, "select", new Class<?>[]{WebTabBar.Tab.class}, tabA));
        awaitWebMedia("Track A2", true);
        check("1".equals(evaluate("plays")), "Old command affected background tab");
        evaluate("navigator.mediaSession.setActionHandler('play',null);navigator.mediaSession.setActionHandler('pause',null);"
                + "navigator.mediaSession.playbackState='none';navigator.mediaSession.metadata=null;"
                + "window.fallbackMedia=document.createElement('audio');fallbackMedia.src='/codec.mp4';fallbackMedia.pause();"
                + "document.body.appendChild(fallbackMedia);");
        long deadline = SystemClock.elapsedRealtime() + 8000;
        do { current = state(); if(current.optBoolean("webMedia") && current.optBoolean("audioOnly"))break;
            SystemClock.sleep(100); } while(SystemClock.elapsedRealtime()<deadline);
        check(current.optBoolean("audioOnly") && current.optBoolean("playAvailable"), "HTML audio fallback unavailable");
        if (mediaFile != null) {
            command = new JSONObject().put("action", "play").put("webPageKey", current.getString("webPageKey"))
                    .put("webMediaToken", current.getString("webMediaToken"));
            call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command);
            awaitWebMedia("Page A", true);
            command.put("action", "pause");
            call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command);
            awaitWebMedia("Page A", false);
            check("true".equals(evaluate("fallbackMedia.paused")), "HTML audio did not pause");
        }
        main(() -> source.openLinkInNewTab(base + "/session"));
        SystemClock.sleep(500);
        boolean[] early = {false};
        main(() -> early[0] = ((WebPageScriptManager)get(source, "pageScriptManager")).hasDocumentStartScript());
        if (early[0]) {
            current = awaitWebMedia("Inline track", true);
            command = new JSONObject().put("action", "pause").put("webPageKey", current.getString("webPageKey"))
                    .put("webMediaToken", current.getString("webMediaToken"));
            call(activity, "handleMediaControl", new Class<?>[]{JSONObject.class}, command);
            awaitWebMedia("Inline track", false);
            check("1".equals(evaluate("inlinePauses")), "Early inline handler was not captured");
        } else {
            // Chrome 68 has no document-start API. A site can feature-detect before
            // injection; do not claim that its skipped registration was captured.
            deadline = SystemClock.elapsedRealtime() + 8000;
            do { current = state(); if(current.optBoolean("webMedia"))break;
                SystemClock.sleep(100); } while(SystemClock.elapsedRealtime()<deadline);
            check(current.optBoolean("audioOnly"), "Old engine lost inline HTML media fallback");
        }
    }

    private WebViewShutdown.Completion audioCompletion() throws Exception {
        return (WebViewShutdown.Completion)call(activity, "sniffedWebAudioCompletion", new Class<?>[0]);
    }

    private void checkAudioHandoffGuards() throws Exception {
        main(() -> {
            WebView web = (WebView)get(source, "webView");
            int[] successes = {0}, failures = {0};
            WebViewShutdown.Completion observe = stopped -> { if (stopped) successes[0]++; else failures[0]++; };
            WebViewShutdown cancelled = new WebViewShutdown(web, () -> {}, () -> {});
            cancelled.whenComplete(observe); cancelled.cancel(); cancelled.cancel();
            call(cancelled, "finish", new Class<?>[]{boolean.class}, false);
            check(successes[0] == 0 && failures[0] == 1, "Cancelled drain completed successfully/duplicated");
            WebViewShutdown timeout = new WebViewShutdown(web, () -> {}, () -> {});
            timeout.whenComplete(observe);
            call(timeout, "finish", new Class<?>[]{boolean.class}, true);
            call(timeout, "finish", new Class<?>[]{boolean.class}, false);
            check(successes[0] == 0 && failures[0] == 2, "Timeout treated as confirmed audio stop");
            WebViewShutdown blank = new WebViewShutdown(web, () -> {}, () -> {});
            blank.whenComplete(observe);
            call(blank, "finish", new Class<?>[]{boolean.class}, false);
            blank.cancel();
            check(successes[0] == 1 && failures[0] == 2, "Blank completion duplicated by later cancellation");
        });
        main(() -> {
            long revision = (Long)get(activity, "playbackAudioFocusGeneration");
            audioCompletion().onComplete(false);
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Failed drain requested focus");
            WebViewShutdown.Completion stale = audioCompletion();
            Field generation = MainActivity.class.getDeclaredField("sniffedPlaybackGeneration");
            generation.setAccessible(true); generation.setLong(activity, generation.getLong(activity) + 1);
            stale.onComplete(true);
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Old playback callback requested focus");
            WebViewShutdown.Completion oldPlayer = audioCompletion();
            Field player = MainActivity.class.getDeclaredField("player"); player.setAccessible(true);
            Object active = player.get(activity);
            try { player.set(activity, null); oldPlayer.onComplete(true); }
            finally { player.set(activity, active); }
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Old player callback requested focus");
            WebViewShutdown.Completion oldRequest = audioCompletion();
            Field request = MainActivity.class.getDeclaredField("playRequestId"); request.setAccessible(true);
            int original = request.getInt(activity);
            try { request.setInt(activity, original + 1); oldRequest.onComplete(true); }
            finally { request.setInt(activity, original); }
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Old request callback requested focus");
            WebViewShutdown.Completion success = audioCompletion();
            success.onComplete(true);
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision + 1, "Owned focus not renewed after drain");
            success.onComplete(true);
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision + 1, "Duplicate callback requested focus");
        });
        android.media.AudioManager manager = (android.media.AudioManager)activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        int originalMode = manager.getMode();
        check(originalMode == android.media.AudioManager.MODE_NORMAL, "Cannot run call simulation during a real call");
        WebViewShutdown.Completion[] duringCall = {null};
        try {
            main(() -> {
                WebViewShutdown.Completion beforeCall = audioCompletion();
                manager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                check(manager.getMode() == android.media.AudioManager.MODE_IN_COMMUNICATION, "Call-mode fixture unavailable");
                long revision = (Long)get(activity, "playbackAudioFocusGeneration");
                beforeCall.onComplete(true);
                check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Cleanup requested focus during call");
                check((Boolean)get(activity, "mutedByCallMode"), "Call did not keep native muted");
                duringCall[0] = audioCompletion();
            });
        } finally {
            main(() -> {
                manager.setMode(originalMode);
                call(activity, "refreshCallAudioMute", new Class<?>[0]);
            });
        }
        main(() -> {
            long revision = (Long)get(activity, "playbackAudioFocusGeneration");
            duringCall[0].onComplete(true);
            check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Call-started cleanup reacquired focus later");
        });
        final boolean[] competitorLost = {false};
        android.media.AudioManager.OnAudioFocusChangeListener competitor = change -> {
            if (change < 0) competitorLost[0] = true;
        };
        WebViewShutdown.Completion[] pending = {null};
        try {
            main(() -> {
                pending[0] = audioCompletion();
                check(manager.requestAudioFocus(competitor, android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                        "Competing audio client did not acquire focus");
            });
            SystemClock.sleep(300);
            main(() -> {
                check((Boolean)get(activity, "mutedByAudioFocus"), "Competing client did not mute native audio");
                long revision = (Long)get(activity, "playbackAudioFocusGeneration");
                pending[0].onComplete(true);
                audioCompletion().onComplete(true); // Loss already existed at cleanup start.
                check((Long)get(activity, "playbackAudioFocusGeneration") == revision, "Cleanup stole competing focus");
                check((Boolean)get(activity, "mutedByAudioFocus"), "Cleanup bypassed focus mute");
            });
            SystemClock.sleep(200);
            check(!competitorLost[0], "Competing focus owner was interrupted");
        } finally {
            main(() -> {
                manager.abandonAudioFocus(competitor);
                // Explicit test playback restart, not the automatic cleanup path.
                call(activity, "requestPlaybackAudioFocus", new Class<?>[0]);
                call(activity, "applyPlaybackMuteState", new Class<?>[0]);
            });
        }
        Bundle progress = new Bundle();
        progress.putString("stream", "PASS unmuted WebView audio drain, native unmuted playback, stale player/request/selection, failed/duplicate completion, communication mode and competing focus owner protection\n");
        sendStatus(1, progress);
    }

    private View seedOldBrowserOverlays(WebTabBar bar) throws Exception {
        call(bar, "showChannelGroup", new Class<?>[]{View.class, int.class}, bar, 0);
        View layer = (View)get(bar, "folderLayer");
        check(layer != null && layer.getParent() != null, "Folder fixture not attached to root");
        call(activity, "showChannelBar", new Class<?>[]{String.class,String.class}, "Old page", "Old status");
        Field deferred = MainActivity.class.getDeclaredField("channelCardDeferredForArtwork");
        deferred.setAccessible(true); deferred.setBoolean(activity, true);
        return layer;
    }

    private void checkOverlaysDismissed(WebTabBar bar, View oldLayer) throws Exception {
        check(get(bar,"folderLayer") == null && oldLayer.getParent() == null, "Old folder overlay survived navigation");
        check(((View)get(activity,"channelBar")).getVisibility() == View.GONE, "Old bottom card survived navigation");
        check(!(Boolean)get(activity,"channelCardDeferredForArtwork"), "Old card still pending artwork completion");
        call(activity,"onArtworkTransitionChanged",new Class<?>[0]);
        check(((View)get(activity,"channelBar")).getVisibility() == View.GONE, "Late artwork callback restored old card");
    }

    private void checkBrowserOverlays(String base) throws Exception {
        WebTabBar bar = (WebTabBar)get(source,"tabBar");
        WebTabBar.Tab tabA = bar.active();
        main(() -> {
            View layer=seedOldBrowserOverlays(bar);
            source.openLinkInNewTab(base+"/b");
            checkOverlaysDismissed(bar,layer); // Before network/onPageStarted callbacks.
        });
        awaitPage("Page B");
        main(() -> {
            View layer=seedOldBrowserOverlays(bar);
            call(bar,"select",new Class<?>[]{WebTabBar.Tab.class},tabA);
            checkOverlaysDismissed(bar,layer); // Retained tabs do not reload.
        });
        awaitPage("Page A");
        main(() -> {
            View layer=seedOldBrowserOverlays(bar);
            call(source,"navigateAddress",new Class<?>[]{String.class},base+"/c");
            checkOverlaysDismissed(bar,layer);
        });
        awaitPage("Page C");
        main(() -> {
            View layer=seedOldBrowserOverlays(bar);
            source.hideForStreamPlayback();
            check(get(bar,"folderLayer")==null && layer.getParent()==null,"Folder survived browser hiding");
            source.restoreAfterStreamPlayback();
            layer=seedOldBrowserOverlays(bar);
            source.closePage();
            check(get(bar,"folderLayer")==null && layer.getParent()==null,"Folder survived browser close");
        });
    }
    private void folderPointer(View root, int action, float x, float y, int button, int deviceId) {
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        boolean touch = deviceId < 0;
        p.id = 0; p.toolType = touch ? MotionEvent.TOOL_TYPE_FINGER : MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = x; c.y = y;
        c.setAxisValue(MotionEvent.AXIS_VSCROLL, -1);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 1,
                new MotionEvent.PointerProperties[]{p}, new MotionEvent.PointerCoords[]{c}, 0,
                touch || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE
                        ? 0 : button, 1, 1, Math.max(0, deviceId), 0,
                touch ? InputDevice.SOURCE_TOUCHSCREEN : InputDevice.SOURCE_MOUSE, 0);
        try {
            if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
                MouseButtonCompat.setButton(event, button);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL)
                root.dispatchTouchEvent(event);
            else root.dispatchGenericMotionEvent(event);
        } finally { event.recycle(); }
    }

    private void startRetentionResource(String url) throws Exception {
        main(() -> ((WebViewClient)get(source, "sourceClient")).onLoadResource((WebView)get(source, "webView"), url));
        waitForIdleSync();
        main(() -> {
            Object resource = call(activity, "findSniffedResource", new Class<?>[]{String.class}, url);
            check(resource != null, "Retention resource not discovered");
            call(activity, "startSniffedResource", new Class<?>[]{resource.getClass()}, resource);
        });
        awaitNativePlayback();
    }

    private void checkBookmarkRetention(String base) throws Exception {
        WebTabBar bar = (WebTabBar)get(source, "tabBar");
        WebTabBar.Tab previous = bar.active();
        main(() -> {
            WebBookmarkStore.Node folder = new WebBookmarkStore.Node(true,"国内电台", "");
            folder.children.add(new WebBookmarkStore.Node(false,"云听",base + "/b"));
            call(bar,"showWebFolder",new Class<?>[]{View.class,WebBookmarkStore.Node.class},bar,folder);
            ((android.view.ViewGroup)get(bar,"folderBody")).getChildAt(1).performClick();
        });
        awaitPage("云听");
        long pageDeadline = SystemClock.elapsedRealtime() + 8000;
        while (!evaluate("document.title").contains("Page B")) {
            check(SystemClock.elapsedRealtime() < pageDeadline,"Bookmark document did not load");SystemClock.sleep(100);
        }
        WebTabBar.Tab bookmark = bar.active();
        main(() -> call(bar,"select",new Class<?>[]{WebTabBar.Tab.class},previous));
        awaitPage("Page A");
        main(() -> call(bar,"select",new Class<?>[]{WebTabBar.Tab.class},bookmark));
        awaitPage("云听");
        evaluate("window.__returnMarker='kept';window.keptAudio=document.createElement('audio');keptAudio.src='/codec.mp4?web';document.body.appendChild(keptAudio);keptAudio.play();");
        Object originalView = get(source, "webView");
        startRetentionResource(base + "/codec.mp4?return");
        main(() -> {
            check((Boolean)get(activity,"audioOnlyPlayback"), "Fixture is not audio only");
            check("云听".equals(get(get(activity, "audioArtwork"), "title")), "Record player inherited catalog title");
            JSONObject current = new JSONObject((String)call(activity, "buildControlState", new Class<?>[]{String.class}, "home")).getJSONObject("current");
            check("云听".equals(current.getString("name")) && "国内电台".equals(current.getString("group")), "Home did not use bookmark channel");
        });
        WebViewShutdown.Completion oldCleanup = audioCompletion();
        Object originalPlayer = get(activity,"player");
        ChannelCatalog.Group replacement = new ChannelCatalog.Group("Refresh fixture",ChannelCatalog.SOURCE_CUSTOM,
                new Channel[]{new Channel("1","Different catalog channel","",base+"/codec.mp4?catalog",null,null)});
        call(activity,"applyPlaylistGroups",new Class<?>[]{ChannelCatalog.Group[].class},
                (Object)new ChannelCatalog.Group[]{replacement});
        check(get(activity,"player")==originalPlayer,"Catalog refresh replaced bookmark playback");
        SystemClock.sleep(5000);
        JSONObject retained = state();
        check(retained.getBoolean("canReturnToWeb") && !retained.getBoolean("backExitsApp"), "Page expired at old 3s deadline: " + retained);
        main(() -> {
            call(activity, "openChannelList", new Class<?>[0]); activity.onBackPressed();
            check(!source.isPageVisible() && get(activity,"player") != null, "Channel list Back returned to webpage prematurely");
            activity.onBackPressed();
            check(source.isPageVisible() && get(activity,"player") == null && get(source,"webView") == originalView,
                    "Back did not restore the same retained page and stop native playback");
            long focus = (Long)get(activity,"playbackAudioFocusGeneration");
            oldCleanup.onComplete(true);
            check((Long)get(activity,"playbackAudioFocusGeneration") == focus, "Old cleanup stole focus after return");
        });
        check("true".equals(evaluate("window.__returnMarker==='kept'&&!window.__ntvMediaPause")), "Returned page reloaded or stayed paused");
        main(() -> {
            Field automatic = MainActivity.class.getDeclaredField("webViewAutoPlaySniffed");automatic.setAccessible(true);automatic.setBoolean(activity,true);
            ((WebSourceView.Listener)get(source,"listener")).onStreamDiscovered((Integer)get(activity,"playRequestId"),
                    base + "/codec.mp4?late", base + "/b", "", "");
            check(source.isPageVisible() && get(activity,"player") == null, "Return immediately re-triggered automatic native playback");
            automatic.setBoolean(activity,false);
        });
        call(activity,"handleWebSettings",new Class<?>[]{JSONObject.class},new JSONObject().put("webViewAutoCloseSniffed",false));
        check(!activity.getSharedPreferences(MainActivity.PREFERENCES,0).getBoolean("web_view_auto_close_sniffed",true), "Auto-close preference not persisted");
        startRetentionResource(base + "/codec.mp4?keep");
        SystemClock.sleep(16500);
        check(state().getBoolean("canReturnToWeb") && state().getBoolean("playing"), "Disabled auto-close did not retain page");
        check("true".equals(evaluate("window.__returnMarker==='kept'&&!!window.__ntvMediaPause&&keptAudio.paused")), "Kept page was cleared or playing background audio");
        main(() -> activity.onBackPressed());
        check(source.isPageVisible(), "Disabled auto-close did not allow returning after 15s");
        call(activity,"handleWebSettings",new Class<?>[]{JSONObject.class},new JSONObject().put("webViewAutoCloseSniffed",true));
        startRetentionResource(base + "/codec.mp4?close");
        long started = SystemClock.elapsedRealtime(), deadline = started + 24000;
        while (state().getBoolean("canReturnToWeb")) {
            check(SystemClock.elapsedRealtime() < deadline,"Enabled auto-close never cleared page");
            SystemClock.sleep(150);
        }
        check(SystemClock.elapsedRealtime() - started >= 14500, "Cleanup happened before 15 seconds");
        check(state().getBoolean("playing") && state().getBoolean("backExitsApp")
                && "云听".equals(state().getString("name")) && "国内电台".equals(state().getString("group")),
                "Cleanup lost bookmark identity or playback");
        long blankDeadline = SystemClock.elapsedRealtime() + 4000;
        while (!"true".equals(evaluate("!window.__returnMarker"))) {
            check(SystemClock.elapsedRealtime() < blankDeadline, "Document did not drain"); SystemClock.sleep(100);
        }
        checkSniffedBackOverlays();
        main(() -> activity.onBackPressed());check(!activity.isFinishing(),"First Back exited");
        main(() -> activity.onBackPressed());check(activity.isFinishing(),"Second Back did not exit");
    }

    private void checkBrowserSelection() throws Exception {
        main(() -> call(activity,"openChannelList",new Class<?>[0]));
        waitForIdleSync();
        main(() -> {
            int[] position = (int[])call(activity,"displayedChannelLocation",new Class<?>[0]);
            check(position[0] >= 0 && "江苏频道".equals(ChannelCatalog.GROUPS[position[0]].title)
                    && position[1] == 1,"Browser channel not mapped to its directory entry");
            check((Integer)get(activity,"browsingGroupIndex") == position[0]
                    && ((android.widget.ListView)get(activity,"channelList")).getCheckedItemPosition() == 1,
                    "Channel menu retained the opener selection");
            JSONObject home = new JSONObject((String)call(activity,"buildControlState",new Class<?>[]{String.class},"home"))
                    .getJSONObject("current");
            check(home.getInt("groupIndex") == position[0] && home.getInt("channelIndex") == 1,
                    "Phone channel picker retained old indices");
            activity.onBackPressed();
        });
    }

    private static Object staticValue(Class<?> owner, String key) throws Exception {
        Field field = owner.getDeclaredField(key);field.setAccessible(true);return field.get(null);
    }

    private void awaitIcon(WebTabBar bar, int color) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+10000;
        boolean[] ready={false};
        do {
            main(() -> ready[0]=bar.active().icon!=null && bar.active().icon.getPixel(0,0)==color);
            if(ready[0]) return;
            SystemClock.sleep(100);
        } while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("New site's favicon did not arrive: "+color);
    }

    private void checkFaviconIsolation(String base) throws Exception {
        String other=base.replace("127.0.0.1","localhost");
        WebTabBar bar=(WebTabBar)get(source,"tabBar");
        WebBookmarkStore store=(WebBookmarkStore)get(bar,"bookmarkStore");
        main(() -> source.open((Integer)get(activity,"playRequestId"),base+"/icon-a"));
        awaitPage("Icon A");awaitIcon(bar,android.graphics.Color.RED);
        android.graphics.Bitmap red=bar.active().icon;
        Object originalView=get(source,"webView");
        main(() -> {
            source.open((Integer)get(activity,"playRequestId"),other+"/icon-b");
            check(get(source,"webView")==originalView,"Fixture did not reuse WebView");
            check(bar.active().icon==null,"New host immediately inherited old favicon");
            bar.updateActiveIcon(base+"/icon-a",red);
            check(bar.active().icon==null,"Stale URL published into new tab");
        });
        awaitPage("Icon B");
        main(() -> {
            WebView view=(WebView)get(source,"webView");
            WebViewClient client=(WebViewClient)get(source,"sourceClient");
            // Android may supply getFavicon() from the reused view at both points.
            client.onPageStarted(view,other+"/icon-b",red);
            client.onPageFinished(view,other+"/icon-b");
            check(bar.active().icon==null,"Start/finish published previous document icon");
            check(store.iconForUrl(other+"/icon-b")==null,"Old icon contaminated new host cache");
        });
        WebView background=(WebView)get(source,"webView");
        android.webkit.WebChromeClient backgroundChrome=(android.webkit.WebChromeClient)get(background,"browserChromeClient");
        main(() -> source.openLinkInNewTab(base+"/icon-a"));
        awaitPage("Icon A");awaitIcon(bar,android.graphics.Color.RED);
        main(() -> {
            android.graphics.Bitmap blue=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888);
            blue.eraseColor(android.graphics.Color.BLUE);
            if(background!=get(source,"webView")) backgroundChrome.onReceivedIcon(background,blue);
            check(bar.active().icon.getPixel(0,0)==android.graphics.Color.RED,"Background view replaced current icon");
        });
        main(() -> source.openLinkInNewTab(other+"/icon-c"));
        awaitPage("Icon C");awaitIcon(bar,android.graphics.Color.BLUE);
        main(() -> {
            check(store.iconForUrl(base+"/icon-a").getPixel(0,0)==android.graphics.Color.RED,"New icon overwrote another domain");
            source.openLinkInNewTab(other+"/icon-c");
            check(bar.active().icon!=null && bar.active().icon.getPixel(0,0)==android.graphics.Color.BLUE,"Same-domain valid cache was not reused");
        });
    }

    @SuppressWarnings("unchecked")
    private void checkMemoryPressure(String base) throws Exception {
        StringBuilder code = new StringBuilder("window.__memoryRuns=(window.__memoryRuns||0)+1;/*");
        for (int i=0;i<200000;i++) code.append('x');
        code.append("*/");
        String sources = new org.json.JSONArray().put(new JSONObject().put("name","Memory fixture")
                .put("enabled",true).put("source",code.toString())).toString();
        WebPageScriptManager[] policies = new WebPageScriptManager[4];
        WebView[] views = new WebView[4];
        main(() -> {
            for (int i=0;i<views.length;i++) {
                views[i] = i==0 ? (WebView)get(source,"webView") : new WebView(activity);
                policies[i] = new WebPageScriptManager(views[i]);
                policies[i].update(true,false,true,sources);
                check(get(policies[0],"userScripts")==get(policies[i],"userScripts"),"Duplicate compiled script text per WebView");
            }
            policies[0].applyToCurrentDocument();policies[0].applyToCurrentDocument();
        });
        check("1".equals(evaluate("window.__memoryRuns")),"Shared script no longer executes once per document");
        main(() -> {
            Object shared = get(policies[0],"userScripts");
            policies[1].update(false,true,true,sources);
            check(get(policies[1],"userScripts")==shared,"Policy toggle duplicated userscript text");
            WebPageScriptManager.trimMemory();
            check(get(policies[0],"userScripts")==shared,"Trimming invalidated active scripts");
            policies[2].update(true,false,true,"[]");
            check(((java.util.List<?>)get(policies[2],"userScripts")).isEmpty()
                    && !((java.util.List<?>)get(policies[0],"userScripts")).isEmpty(),"Script config leaked between views");
            int chars = ((String)((java.util.List<?>)shared).get(0)).length();
            Bundle progress=new Bundle();progress.putString("stream","MEMORY shared compiled text: "+chars+" chars, 4 managers / 1 immutable list\n");sendStatus(1,progress);
            for (int i=0;i<views.length;i++) {
                policies[i].release();
                check(((java.util.List<?>)get(policies[i],"userScripts")).isEmpty()
                        && "".equals(get(policies[i],"lastSources")),"Retired policy retained scripts");
                if(i>0)views[i].destroy();
            }
        });
        evaluate("window.__memoryPageMarker='retained'");
        startRetentionResource(base+"/codec.mp4?memory");
        Object originalPlayer=get(activity,"player"), originalWeb=get(source,"webView");
        main(() -> {
            android.graphics.Bitmap cover=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888);
            AudioArtworkView artwork=(AudioArtworkView)get(activity,"audioArtwork");artwork.setCover(cover);
            android.util.LruCache<String,android.graphics.Bitmap> cache=(android.util.LruCache<String,android.graphics.Bitmap>)staticValue(AlbumArtLoader.class,"decoded");
            AlbumArtLoader.trimMemory();
            cache.put("memory-one",cover);
            cache.put("memory-two",android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888));
            int bytes=cache.size();check(bytes==2*1024*1024,"Artwork cache fixture size unexpected");
            int oldGeneration=(Integer)staticValue(AlbumArtLoader.class,"memoryGeneration");
            WebBookmarkStore store=(WebBookmarkStore)get(get(source,"tabBar"),"bookmarkStore");
            java.util.List<WebBookmarkStore.Node> nodes=new java.util.ArrayList<>();
            for(int i=0;i<180;i++) {
                WebBookmarkStore.Node node=new WebBookmarkStore.Node(false,"Memory "+i,"https://memory-"+i+".invalid/");
                store.roots().add(node);nodes.add(node);
                store.cacheIcon(node.url,android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888));
            }
            check(((java.util.Map<?,?>)get(store,"iconMemory")).size()<=128,"Favicon cache exceeded hot set");
            check(get(nodes.get(0),"icon") instanceof java.lang.ref.WeakReference,"Bookmark node still strongly owns bitmap");
            AdBlockRuleStore rules=AdBlockRuleStore.get(activity);
            java.util.Map<String,Integer> hosts=(java.util.Map<String,Integer>)get(rules,"hostCache");hosts.put("memory.invalid",1);
            long start=SystemClock.elapsedRealtime();
            activity.getApplication().onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
            activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
            check(cache.size()==0 && hosts.isEmpty() && ((java.util.Map<?,?>)get(store,"iconMemory")).isEmpty(),"Memory callback retained disposable caches");
            check(artwork.cover()==cover && !cover.isRecycled(),"Memory trim released visible artwork pixels");
            Method put=AlbumArtLoader.class.getDeclaredMethod("cacheDecoded",String.class,android.graphics.Bitmap.class,int.class);put.setAccessible(true);
            put.invoke(null,"stale",cover,oldGeneration);check(cache.size()==0,"Old load repopulated trimmed cache");
            put.invoke(null,"fresh",cover,(Integer)staticValue(AlbumArtLoader.class,"memoryGeneration"));check(cache.size()>0,"New loads cannot refill cache");
            check(get(activity,"player")==originalPlayer && get(source,"webView")==originalWeb,"Memory callback changed playback or renderer");
            AudioArtworkView idle=new AudioArtworkView(activity,null);idle.show("idle",true,cover);idle.clear();idle.trimMemory();
            check(get(idle,"vinyl")==null && (Integer)get(idle,"backgroundHeight")==0,"Idle artwork texture retained");
            store.roots().removeAll(nodes); // Test-only tree entries were never persisted.
            Bundle progress=new Bundle();progress.putString("stream","MEMORY dropped "+bytes+" artwork cache bytes; trim callback "+(SystemClock.elapsedRealtime()-start)+" ms; visible cover remains valid\n");sendStatus(1,progress);
        });
        check("\"retained\"".equals(evaluate("window.__memoryPageMarker")),"Memory trim reloaded retained page");
        SystemClock.sleep(700);
        check(state().getBoolean("playing"),"Memory trim stopped native playback");
        main(() -> activity.onBackPressed());
        check(source.isPageVisible() && "\"retained\"".equals(evaluate("window.__memoryPageMarker")),"Return broke after memory trim");
    }

    private void checkBrowserFailures(String base) throws Exception {
        ChannelCatalog.Group[] groups = {
            new ChannelCatalog.Group("旧频道",ChannelCatalog.SOURCE_CUSTOM,
                    new Channel[]{new Channel("1","旧网页","","webview://"+base+"/a",null,null)}),
            new ChannelCatalog.Group("江苏频道",ChannelCatalog.SOURCE_CUSTOM,
                    new Channel[]{new Channel("2","其他频道","","webview://"+base+"/c",null,null),
                    new Channel("3","江苏新闻","","webview://"+base+"/b",null,null)})};
        call(activity,"applyPlaylistGroups",new Class<?>[]{ChannelCatalog.Group[].class},(Object)groups);
        main(() -> source.openBookmarkedPage(base+"/b","江苏新闻","江苏频道",true));
        awaitPage("江苏新闻");
        long deadline = SystemClock.elapsedRealtime()+8000;
        while (!evaluate("document.title").contains("Page B")) {
            check(SystemClock.elapsedRealtime()<deadline,"Document not ready");SystemClock.sleep(100);
        }
        evaluate("window.__failureMarker='kept'");
        Object web = get(source,"webView");
        checkBrowserSelection();
        main(() -> call(activity,"openChannelList",new Class<?>[0]));
        call(activity,"applyPlaylistGroups",new Class<?>[]{ChannelCatalog.Group[].class},(Object)groups);
        main(() -> {
            int[] location = (int[])call(activity,"displayedChannelLocation",new Class<?>[0]);
            check((Integer)get(activity,"browsingGroupIndex")==location[0]
                    && ((android.widget.ListView)get(activity,"channelList")).getCheckedItemPosition()==1,
                    "Catalog refresh moved the open list back to old channel");
            activity.onBackPressed();
        });
        startRetentionResource(base+"/codec.mp4?valid");
        checkBrowserSelection();
        // Old opening timeout must never interrupt a newer successful selection.
        Runnable stale = (Runnable)get(activity,"sniffedOpenTimeout");
        main(() -> activity.onBackPressed());
        startRetentionResource(base+"/codec.mp4?newer");
        main(() -> stale.run());
        check(state().getBoolean("playing") && !source.isPageVisible(),"Stale timeout interrupted new playback");
        main(() -> activity.onBackPressed());
        for (String path : new String[]{"/denied.mp4", "/invalid.mp4"}) {
            String url = base+path;
            main(() -> ((WebViewClient)get(source,"sourceClient")).onLoadResource((WebView)get(source,"webView"),url));
            waitForIdleSync();
            main(() -> {
                Object resource = call(activity,"findSniffedResource",new Class<?>[]{String.class},url);
                call(activity,"startSniffedResource",new Class<?>[]{resource.getClass()},resource);
            });
            deadline = SystemClock.elapsedRealtime()+12000;
            while (!state().getBoolean("webPageVisible")) {
                check(SystemClock.elapsedRealtime()<deadline,"Failed resource did not return: "+path+" "+state());
                SystemClock.sleep(100);
            }
            check(get(activity,"player")==null && get(source,"webView")==web
                    && "\"kept\"".equals(evaluate("window.__failureMarker")),"Failure reloaded page or left native player alive");
            check(!state().getBoolean("backExitsApp"),"Failure left app-exit mode active");
            checkBrowserSelection();
        }
        startRetentionResource(base+"/codec.mp4?timeout");
        main(() -> {
            Field prepared = MainActivity.class.getDeclaredField("prepared");prepared.setAccessible(true);prepared.setBoolean(activity,false);
            ((Runnable)get(activity,"sniffedOpenTimeout")).run();
        });
        check(source.isPageVisible() && get(activity,"player")==null,"Opening timeout did not return to page");
        main(() -> {
            Field automatic = MainActivity.class.getDeclaredField("webViewAutoPlaySniffed");automatic.setAccessible(true);automatic.setBoolean(activity,true);
            ((WebSourceView.Listener)get(source,"listener")).onStreamDiscovered((Integer)get(activity,"playRequestId"),
                    base+"/codec.mp4?late",base+"/b","","");
            check(source.isPageVisible() && get(activity,"player")==null,"Failure return automatically retried itself");
            automatic.setBoolean(activity,false);
        });
        startRetentionResource(base+"/codec.mp4?cleanup");
        deadline = SystemClock.elapsedRealtime()+24000;
        while (state().getBoolean("canReturnToWeb")) {
            check(SystemClock.elapsedRealtime()<deadline,"Page never cleaned up");SystemClock.sleep(150);
        }
        checkBrowserSelection();
        check(!source.isPageVisible() && state().getBoolean("playing"),"Menu selection after cleanup changed playback");
    }

    private void checkSniffedBackOverlays() throws Exception {
        Object originalPlayer = get(activity, "player");
        Object originalRequest = get(activity, "playRequestId");
        for (String mode : new String[]{"activity", "pointer", "pointer-key", "physical", "legacy"}) {
            main(() -> {
                call(activity, "openChannelList", new Class<?>[0]);
                check(((View)get(activity, "channelListPanel")).getVisibility() == View.VISIBLE, "Channel list did not open");
                if ("activity".equals(mode)) activity.onBackPressed();
                else if ("legacy".equals(mode)) check(activity.returnToRetainedWebPage(), "Legacy sniffed Back not handled");
                else if ("physical".equals(mode)) {
                    activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
                    activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
                } else call(activity, "applyLocalPointer", new Class<?>[]{String.class,float.class,float.class,
                        int.class,int.class,float.class,int.class,int.class,String.class},
                        "pointer".equals(mode) ? "back" : "key", 0f, 0f, 0, 0, 1f, android.view.KeyEvent.KEYCODE_BACK, 0, "");
                check(((View)get(activity, "channelListPanel")).getVisibility() != View.VISIBLE,
                        mode + " Back skipped the channel list after sniffed playback");
                check(((View)get(activity, "backPrompt")).getVisibility() != View.VISIBLE
                        && (Long)get(activity, "lastBackPressedAt") == 0L, mode + " panel Back armed app exit");
                check(!activity.isFinishing() && get(activity, "player") == originalPlayer
                        && originalRequest.equals(get(activity, "playRequestId")), mode + " Back altered native playback");
            });
            check(state().optBoolean("playing"), mode + " panel Back stopped playback");
        }
        main(() -> {
            call(activity, "openManagementPanel", new Class<?>[0]);
            activity.onBackPressed();
            check(((View)get(activity, "managementPanel")).getVisibility() != View.VISIBLE,
                    "Management panel Back skipped by sniffed playback");
            check((Long)get(activity, "lastBackPressedAt") == 0L, "Closing management panel armed exit");
        });
        check("true".equals(evaluate("!document.querySelector('video,audio,iframe')")), "Panel Back reactivated WebView");
        Bundle progress = new Bundle(); progress.putString("stream", "PASS all five Back entry points dismiss the channel list without exit or playback changes; management panel closes; WebView remains cleared\n");
        sendStatus(1, progress);
    }

    private void checkFolderInput(String base, int deviceId) throws Exception {
        WebTabBar bar = (WebTabBar)get(source, "tabBar");
        int beforeCount = ((java.util.List<?>)get(bar, "tabs")).size();
        WebView web = (WebView)get(source, "webView");
        View root = (View)get(activity, "root");
        int[] leaked = {0}, point = new int[2];
        WebBookmarkStore.Node folder = new WebBookmarkStore.Node(true, "Input fixture", "");
        folder.children.add(new WebBookmarkStore.Node(false, "Page B", base + "/b"));
        main(() -> {
            web.setOnTouchListener((v,e) -> { leaked[0]++; return false; });
            web.setOnGenericMotionListener((v,e) -> { leaked[0]++; return false; });
            web.setOnHoverListener((v,e) -> { leaked[0]++; return false; });
            call(bar, "showWebFolder", new Class<?>[]{View.class, WebBookmarkStore.Node.class}, bar, folder);
        });
        waitForIdleSync();
        main(() -> {
            android.view.ViewGroup body = (android.view.ViewGroup)get(bar, "folderBody");
            View item = body.getChildAt(1);
            int[] origin = new int[2]; root.getLocationOnScreen(origin); item.getLocationOnScreen(point);
            point[0] += item.getWidth()/2 - origin[0]; point[1] += item.getHeight()/2 - origin[1];
            check(item.getWidth() > 0 && item.getHeight() > 0, "Folder item not laid out");
            // Generic mouse events are dispatched separately from touch targeting.
            if (deviceId >= 0) {
                folderPointer(root, MotionEvent.ACTION_HOVER_MOVE, point[0], point[1], 0, deviceId);
                folderPointer(root, MotionEvent.ACTION_SCROLL, point[0], point[1], 0, deviceId);
            }
            folderPointer(root, MotionEvent.ACTION_DOWN, point[0], point[1], 1, deviceId);
            if (deviceId >= 0) {
                folderPointer(root, MotionEvent.ACTION_BUTTON_PRESS, point[0], point[1], 1, deviceId);
                folderPointer(root, MotionEvent.ACTION_BUTTON_RELEASE, point[0], point[1], 1, deviceId);
            }
            check(leaked[0] == 0, "Folder input leaked to WebView before UP: " + leaked[0]);
            folderPointer(root, MotionEvent.ACTION_UP, point[0], point[1], 1, deviceId);
            check(leaked[0] == 0, "Folder click leaked to WebView");
        });
        waitForIdleSync();
        awaitPage("Page B");
        main(() -> {
            check(get(bar, "folderLayer") == null, "Bookmark click did not close folder");
            check(((java.util.List<?>)get(bar, "tabs")).size() == beforeCount + 1, "Bookmark did not open exactly one new tab");
            web.setOnTouchListener(null); web.setOnGenericMotionListener(null); web.setOnHoverListener(null);
        });
    }

    private void checkBackgroundTab(String base) throws Exception {
        WebTabBar bar = (WebTabBar)get(source, "tabBar");
        WebTabBar.Tab original = bar.active();
        WebView originalView = (WebView)get(source, "webView");
        int before = ((java.util.List<?>)get(bar, "tabs")).size();
        Object navigation = get(source, "resourceNavigation");
        View[] action = {null};
        main(() -> {
            call(source, "showSmartContextPopup", new Class<?>[]{float.class, float.class, JSONObject.class},
                    100f, 150f, new JSONObject().put("linkUrl", base + "/b"));
            android.view.ViewGroup layer = (android.view.ViewGroup)get(source, "smartContextLayer");
            android.view.ViewGroup menu = (android.view.ViewGroup)layer.getChildAt(0);
            for (int i = 0; i < menu.getChildCount(); i++) {
                View item = menu.getChildAt(i);
                if (item instanceof android.widget.TextView && "在后台打开新标签".contentEquals(
                        ((android.widget.TextView)item).getText())) action[0] = item;
            }
            check(action[0] != null, "Missing background link menu action");
        });
        waitForIdleSync();
        main(() -> {
            View root = (View)get(activity, "root");
            int[] point = new int[2], origin = new int[2];
            action[0].getLocationOnScreen(point); root.getLocationOnScreen(origin);
            float x = point[0] - origin[0] + action[0].getWidth()/2f;
            float y = point[1] - origin[1] + action[0].getHeight()/2f;
            check(action[0].getWidth() > 0 && action[0].getHeight() > 0, "Menu not laid out");
            folderPointer(root, MotionEvent.ACTION_DOWN, x, y, 1, 0);
            folderPointer(root, MotionEvent.ACTION_UP, x, y, 1, 0);
        });
        waitForIdleSync();
        main(() -> {
            check(get(source, "smartContextLayer") == null, "Fly mouse did not activate menu");
            java.util.List<?> tabs = (java.util.List<?>)get(bar, "tabs");
            check(tabs.size() == before + 1, "Expected exactly one background tab");
            check(bar.active() == original && get(source, "webView") == originalView,
                    "Background open switched the visible page");
            check(navigation.equals(get(source, "resourceNavigation")), "Background open reset resource identity");
            WebTabBar.Tab added = (WebTabBar.Tab)tabs.get(tabs.size()-1);
            check((base + "/b").equals(added.url) && added.state == null, "Invalid deferred tab");
            check(!((java.util.Map<?,?>)get(source, "retainedTabWebViews")).containsKey(added),
                    "Background open created a hidden renderer");
            source.openLinkInBackgroundTab("javascript:alert(1)");
            check(tabs.size() == before + 1, "Unsafe URL created a tab");
        });
        check("Page A".equals(state().optString("name")), "Controller changed to background tab");
        main(() -> {
            java.util.List<?> tabs = (java.util.List<?>)get(bar, "tabs");
            call(bar, "select", new Class<?>[]{WebTabBar.Tab.class}, tabs.get(tabs.size()-1));
        });
        awaitPage("Page B");
        main(() -> {
            WebTabBar.Tab current = bar.active();
            java.util.List<?> tabs = (java.util.List<?>)get(bar, "tabs");
            while (tabs.size() < 32) bar.openBackground(base + "/c", "Capacity fixture");
            for (Object value : tabs) ((WebTabBar.Tab)value).pinned = value != current;
            check(bar.openBackground(base + "/b", "Overflow") == null,
                    "Full background open evicted the only unpinned active tab");
            check(bar.active() == current && tabs.contains(current) && tabs.size() == 32,
                    "Capacity limit altered the active tab");
            // The remaining ordinary background tab can be replaced, but never current.
            WebTabBar.Tab victim = (WebTabBar.Tab)tabs.get(0); victim.pinned = false;
            check(bar.openBackground(base + "/c", "Replacement") != null
                    && !tabs.contains(victim) && bar.active() == current && tabs.size() == 32,
                    "Background eviction did not preserve the active tab");
            for (Object value : tabs) ((WebTabBar.Tab)value).pinned = false;
        });
    }

    private void checkWebCpu(String base) throws Exception {
        // A local copy isolates CPU from network stalls and the public site's
        // upgrade-insecure-requests policy. It is the same encoded video, not a transcode.
        final String site = mediaFile == null ? "https://www.w3.org/2010/05/video/mediaevents.html" : base+"/cpu";
        main(() -> source.open((Integer)get(activity,"playRequestId"), site));
        long deadline = SystemClock.elapsedRealtime() + 45000;
        while (!"true".equals(evaluate("location.href==="+JSONObject.quote(site)+"&&!!document.body&&document.readyState==='complete'"))) {
            check(SystemClock.elapsedRealtime() < deadline, "Public test page did not load");
            SystemClock.sleep(250);
        }
        evaluate("window.ntvCpuVideo=document.querySelector('video')||document.createElement('video');ntvCpuVideo.controls=true;ntvCpuVideo.loop=true;ntvCpuVideo.autoplay=true;"
                + "ntvCpuVideo.style.cssText='position:fixed;top:0;left:0;width:100%;height:100%;z-index:99999;background:black';"
                + "ntvCpuVideo.src="+JSONObject.quote(mediaFile == null ? "https://media.w3.org/2010/05/sintel/trailer_hd.mp4" : base+"/codec.mp4")+";"
                + "document.body.appendChild(ntvCpuVideo);window.ntvCpuEvents=[];['playing','pause','waiting','stalled','error'].forEach(function(n){ntvCpuVideo.addEventListener(n,function(){ntvCpuEvents.push(n)})});"
                + "var promise=ntvCpuVideo.play();if(promise&&promise.catch)promise.catch(function(e){ntvCpuEvents.push(String(e))});");
        deadline = SystemClock.elapsedRealtime() + 45000;
        while (true) {
            JSONObject ready = new JSONObject(evaluate("({width:ntvCpuVideo.videoWidth,height:ntvCpuVideo.videoHeight,paused:ntvCpuVideo.paused,time:ntvCpuVideo.currentTime,error:ntvCpuVideo.error&&ntvCpuVideo.error.code,hidden:document.hidden,guard:!!window.__ntvMediaPause,decoded:ntvCpuVideo.webkitDecodedFrameCount,events:ntvCpuEvents})"));
            if (ready.optInt("width")==1920 && ready.optInt("height")==1080 && !ready.optBoolean("paused",true) && ready.optDouble("time")>0) break;
            check(SystemClock.elapsedRealtime() < deadline, "1080p media did not play: " + ready);
            SystemClock.sleep(250);
        }
        // Count our viewport scans without modifying the observer or video settings.
        evaluate("window.metaScans=0;window.metaOriginal=Document.prototype.getElementsByTagName;"
                + "Document.prototype.getElementsByTagName=function(n){if(n==='meta')metaScans++;return metaOriginal.apply(this,arguments)};"
                + "window.churn=document.createElement('div');churn.style.display='none';document.body.appendChild(churn);"
                + "var fragment=document.createDocumentFragment();for(var i=0;i<6000;i++){var s=document.createElement('span');s.textContent='comment '+i;fragment.appendChild(s);}churn.appendChild(fragment);"
                + "window.cpuFrame=0;window.cpuSerial=0;window.cpuStress=false;function cpuTick(){if(cpuStress){cpuSerial++;for(var i=0;i<20;i++)"
                + "churn.children[(cpuFrame*20+i)%6000].textContent='comment '+cpuSerial;cpuFrame++;}}window.cpuTimer=setInterval(cpuTick,50);");
        SystemClock.sleep(2000);
        for (boolean stress : new boolean[]{false,true,false,true}) {
            evaluate("cpuStress="+stress+";metaScans=0;cpuFrame=0;window.decodedStart=ntvCpuVideo.webkitDecodedFrameCount;window.droppedStart=ntvCpuVideo.webkitDroppedFrameCount;");
            long cpu = android.os.Process.getElapsedCpuTime(), start = SystemClock.elapsedRealtime();
            Bundle progress = new Bundle(); progress.putString("stream", "CPU_SAMPLE stress="+stress+" pid="+android.os.Process.myPid()+"\n"); sendStatus(1,progress);
            SystemClock.sleep(12000);
            long elapsed = SystemClock.elapsedRealtime()-start, used = android.os.Process.getElapsedCpuTime()-cpu;
            String sample = evaluate("({width:ntvCpuVideo.videoWidth,height:ntvCpuVideo.videoHeight,paused:ntvCpuVideo.paused,ready:ntvCpuVideo.readyState,"
                    + "decoded:ntvCpuVideo.webkitDecodedFrameCount-decodedStart,dropped:ntvCpuVideo.webkitDroppedFrameCount-droppedStart,metaScans:metaScans,domFrames:cpuFrame})");
            progress = new Bundle(); progress.putString("stream", "CPU_RESULT stress="+stress+" appCpuMs="+used+" wallMs="+elapsed+" video="+sample+"\n"); sendStatus(1,progress);
            // The clip can loop within a sample; readyState can briefly become 1
            // at the loop boundary without a playback failure.
            JSONObject measured=new JSONObject(sample);
            check(!measured.optBoolean("paused",true) && measured.optInt("width")==1920 && measured.optInt("decoded")>0,"Playback regressed: "+sample);
        }
        evaluate("cpuStress=false;clearInterval(cpuTimer);ntvCpuVideo.pause();Document.prototype.getElementsByTagName=metaOriginal;");
    }

    private void checkCpuGuards(String base) throws Exception {
        main(() -> {
            String image=base+"/cover.webp?url=https%3A%2F%2Fexample.com%2Fimage.png";
            for(int i=0;i<1000;i++) check(call(source,"cachedMediaPlaylist",new Class<?>[]{String.class},image)==null,"Image became media");
            java.util.Map<?,?> cache=(java.util.Map<?,?>)get(source,"resourceUrlCache");
            check(cache.containsKey(image),"Negative result was not cached");
            for(String url:new String[]{base+"/audio.MP3?token=1",base+"/a.m3u8?key=2",base+"/a?type=m3u8"})
                check(url.equals(call(source,"cachedMediaPlaylist",new Class<?>[]{String.class},url)),"Media detection changed");
            String nested=base+"/a?streamurl=https%3A%2F%2Fcdn.example%2Fa.m3u8%3Ftoken%3D3";
            check("https://cdn.example/a.m3u8?token=3".equals(call(source,"cachedMediaPlaylist",new Class<?>[]{String.class},nested)),"Signed nested URL changed");
            for(int i=0;i<300;i++)call(source,"cachedMediaPlaylist",new Class<?>[]{String.class},base+"/segment"+i+".m4s");
            check(cache.size()==128,"Resource cache is not bounded");
            source.trimMemory();check(cache.isEmpty(),"Pressure did not trim classification cache");
            call(source,"cachedMediaPlaylist",new Class<?>[]{String.class},image);
            call(source,"resetResourcePage",new Class<?>[]{String.class},base+"/a");
            check(cache.isEmpty(),"Navigation retained classification cache");
        });
    }

    private void checkTabCloseDisplay(String base) throws Exception {
        WebTabBar bar = (WebTabBar)get(source, "tabBar");
        WebView left = (WebView)get(source, "webView");
        WebTabBar.Tab leftTab = bar.active();
        evaluate("window.closeTestMarker='left';document.body.style.background='#00ff00'");
        main(() -> source.openLinkInNewTab(base + "/heavy"));
        awaitPage("Heavy video"); awaitVideo();
        WebView closing = (WebView)get(source, "webView");
        WebTabBar.Tab closingTab = bar.active();
        android.webkit.WebChromeClient closingChrome =
                (android.webkit.WebChromeClient)get(closing,"browserChromeClient");
        View fullscreen = new View(activity);
        boolean[] fullscreenHidden = {false};
        check(left != closing, "Fixture requires retained-tab memory budget");
        main(() -> {
            closingChrome.onShowCustomView(fullscreen, () -> fullscreenHidden[0] = true);
            call(bar, "closeNow", new Class<?>[]{WebTabBar.Tab.class}, closingTab);
            check(bar.active() == leftTab && get(source,"loadedTab") == leftTab,
                    "Closing current tab did not select its left neighbor");
            check(get(source,"webView") == left && left.getParent() == source,
                    "Selected tab did not restore its retained renderer");
            check(source.indexOfChild(left) > source.indexOfChild(closing),
                    "Closed video renderer is layered ABOVE the selected left tab during asynchronous drain");
            check(left.getVisibility() == View.VISIBLE && left.getAlpha() == 1f && left.isEnabled(),
                    "Selected renderer is hidden or disabled");
            check(fullscreenHidden[0] && fullscreen.getParent() == null
                    && get(source,"fullscreenView") == null, "Closed tab kept fullscreen overlay");
            check(source.indexOfChild(bar) > source.indexOfChild(left), "Page covered the tab toolbar");
            WebViewShutdown pending=(WebViewShutdown)get(closing,"shutdown");
            check(pending!=null && pending.isAwaitingHandoff() && !(Boolean)get(pending,"navigating"),
                    "Closed video unloaded before replacement became stable");
            // Late fullscreen requests from the retired renderer cannot cover A.
            closingChrome.onShowCustomView(new View(activity), () -> {});
            check(get(source,"fullscreenView") == null, "Closed page reclaimed fullscreen");
        });
        check("\"left\"".equals(evaluate("window.closeTestMarker")), "Retained page reloaded");
        SystemClock.sleep(2300);
        main(() -> check(closing.getParent() == null, "Closed renderer survived drain"));
        assertGreenPagePixels();

        // Multiple closes before a renderer commits: late drain callbacks must
        // never hide, blank or cover the surviving left-hand page.
        for (int pass = 0; pass < 6; pass++) {
            main(() -> {
                source.openLinkInNewTab(base + "/b");
                source.openLinkInNewTab(base + "/c");
                call(bar,"closeNow",new Class<?>[]{WebTabBar.Tab.class},bar.active());
                call(bar,"closeNow",new Class<?>[]{WebTabBar.Tab.class},bar.active());
                check(get(source,"webView") == left && bar.active() == leftTab,
                        "Rapid close lost surviving renderer");
                for (int i=0;i<source.getChildCount();i++) {
                    View child=source.getChildAt(i);
                    if(child instanceof WebView && child!=left)
                        check(i<source.indexOfChild(left),"Retired child covers rapid-close survivor");
                }
            });
            check("\"left\"".equals(evaluate("window.closeTestMarker")), "Rapid close lost page state");
        }
        SystemClock.sleep(2300);
        assertGreenPagePixels();

        // Also exercise the real animated close entry and restoration path
        // when the left tab has already been evicted under memory pressure.
        main(() -> source.openLinkInNewTab(base + "/b"));
        awaitPage("Page B");
        WebView closedWithoutRetainedLeft = (WebView)get(source,"webView");
        main(() -> {
            call(source,"releaseTabWebView",new Class<?>[]{WebTabBar.Tab.class,boolean.class},leftTab,true);
            call(bar,"close",new Class<?>[]{WebTabBar.Tab.class},bar.active());
        });
        awaitPage("Page A");
        check(evaluate("location.pathname").equals("\"/a\""),"Evicted left tab displays closed page");
        main(() -> check(get(source,"webView")!=closedWithoutRetainedLeft && bar.active()==leftTab,
                "Healthy-memory close reused departing renderer or selected wrong tab"));
        SystemClock.sleep(2300);
        check(evaluate("document.title").equals("\"Page A\""),"Late callback replaced restored document");
    }

    private void assertGreenPagePixels() throws Exception {
        // Verify compositor output too, not just Java selection/document state.
        android.graphics.Bitmap screen = android.graphics.Bitmap.createBitmap(
                activity.getWindow().getDecorView().getWidth(), activity.getWindow().getDecorView().getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        java.util.concurrent.CountDownLatch copied = new java.util.concurrent.CountDownLatch(1);
        int[] copyResult = {-1};
        main(() -> android.view.PixelCopy.request(activity.getWindow(),screen, result -> {
            copyResult[0]=result;copied.countDown();
        },new android.os.Handler(android.os.Looper.getMainLooper())));
        check(copied.await(5,java.util.concurrent.TimeUnit.SECONDS) && copyResult[0]==android.view.PixelCopy.SUCCESS,
                "Window pixel copy failed: "+copyResult[0]);
        try {
            int color=screen.getPixel(screen.getWidth()/2,screen.getHeight()/2);
            if (android.graphics.Color.green(color)<=220) {
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),"tab-close-failure.png"))) {
                    screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                }
                Bundle diagnostic = new Bundle();
                diagnostic.putString("stream", "tab-close focus="+activity.hasWindowFocus()+" finishing="+activity.isFinishing()+" state="+state()+" DOM="+evaluate("JSON.stringify({url:location.href,hidden:document.hidden,bg:getComputedStyle(document.body).backgroundColor,w:innerWidth,h:innerHeight})")+"\n");
                sendStatus(1,diagnostic);
                main(() -> {
                    for(int i=0;i<source.getChildCount();i++) {
                        View child=source.getChildAt(i);
                        android.util.Log.i("TabCloseTest", "child="+i+" "+child+" active="+(child==get(source,"webView"))
                                +" alpha="+child.getAlpha()+" shown="+child.isShown()+" visibility="+child.getVisibility());
                    }
                });
            }
            check(android.graphics.Color.green(color)>220 && android.graphics.Color.red(color)<40
                    && android.graphics.Color.blue(color)<40,
                    "Selected left page is not visible in compositor: " + Integer.toHexString(color));
        } finally { screen.recycle(); }
    }

    @Override public void onCreate(Bundle args) {
        super.onCreate(args); mediaFile = args == null ? null : args.getString("mediaFile");
        exitOnly = args != null && "true".equals(args.getString("exitOnly"));
        audioHandoff = args != null && "true".equals(args.getString("audioHandoff"));
        webMediaOnly = args != null && "true".equals(args.getString("webMediaOnly"));
        backgroundTabOnly = args != null && "true".equals(args.getString("backgroundTabOnly"));
        retentionOnly = args != null && "true".equals(args.getString("retentionOnly"));
        browserFailureOnly = args != null && "true".equals(args.getString("browserFailureOnly"));
        memoryOnly = args != null && "true".equals(args.getString("memoryOnly"));
        faviconOnly = args != null && "true".equals(args.getString("faviconOnly"));
        cpuOnly = args != null && "true".equals(args.getString("cpuOnly"));
        cpuGuardsOnly = args != null && "true".equals(args.getString("cpuGuardsOnly"));
        tabCloseOnly = args != null && "true".equals(args.getString("tabCloseOnly"));
        overlaysOnly = args != null && "true".equals(args.getString("overlaysOnly"));
        folderInputOnly = args != null && "true".equals(args.getString("folderInputOnly")); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = -1; ServerSocket server = null;
        try {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            final ServerSocket fixture = server;
            new Thread(() -> {
                while (!fixture.isClosed()) try (Socket socket = fixture.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                    String request = input.readLine(), line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) { }
                    if (request.contains("/red-icon.png") || request.contains("/blue-icon.png")) {
                        android.graphics.Bitmap icon=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888);
                        icon.eraseColor(request.contains("/red-icon.png")?android.graphics.Color.RED:android.graphics.Color.BLUE);
                        ByteArrayOutputStream encoded=new ByteArrayOutputStream();icon.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded);icon.recycle();
                        byte[] bytes=encoded.toByteArray();
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
                        socket.getOutputStream().write(bytes);continue;
                    }
                    if (request.contains(".ico")) {
                        socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));continue;
                    }
                    if (request.contains("/denied.mp4")) {
                        socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));
                        continue;
                    }
                    if (request.contains("/codec.mp4") && mediaFile != null) {
                        File media = new File(mediaFile);
                        OutputStream out = socket.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: " + media.length() + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
                        try (InputStream in = new FileInputStream(media)) { byte[] buffer = new byte[16384]; int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n); }
                        continue;
                    }
                    if (request.contains("/redirect")) {
                        socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: /b\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("US-ASCII"));
                        continue;
                    }
                    boolean heavy = request.contains("/heavy");
                    String title = heavy ? "Heavy video" : request.contains("/b") ? "Page B" : request.contains("/c") ? "Page C" : "Page A";
                    if(request.contains("/icon-")) title=request.contains("/icon-a")?"Icon A":request.contains("/icon-b")?"Icon B":"Icon C";
                    // Real MediaStream video under DOM/render load, without external
                    // codecs, network media URLs or third-party website variability.
                    String content = heavy ? "<video id='video' autoplay muted playsinline></video><canvas id='canvas' width='640' height='360'></canvas>"
                            + "<script>var c=canvas.getContext('2d'),frame=0;"
                            + "for(var i=0;i<6000;i++){var e=document.createElement('span');e.textContent='video '+i+' ';document.body.appendChild(e);}"
                            + "function draw(){c.fillStyle='hsl('+(frame++%360)+',70%,50%)';c.fillRect(0,0,640,360);requestAnimationFrame(draw);}draw();"
                            + (mediaFile == null ? "video.srcObject=canvas.captureStream(30);" : "video.src='/codec.mp4';video.loop=true;")
                            + "video.play();</script>"
                            + (request.contains("/heavy-child") ? "" : "<iframe src='/heavy-child'></iframe>") : title;
                    if (request.contains("/session")) content = "<audio src='/codec.mp4'></audio><script>window.inlinePauses=0;if(navigator.mediaSession){"
                            + "navigator.mediaSession.metadata=new MediaMetadata({title:'Inline track',artwork:[{src:'/inline.png'}]});"
                            + "navigator.mediaSession.playbackState='playing';"
                            + "navigator.mediaSession.setActionHandler('pause',function(){inlinePauses++;navigator.mediaSession.playbackState='paused'});"
                            + "navigator.mediaSession.setActionHandler('play',function(){navigator.mediaSession.playbackState='playing'});}</script>";
                    String favicon=request.contains("/icon-") ? "<link rel='icon' href='"+(request.contains("/icon-a")?"/red-icon.png":request.contains("/icon-b")?"/missing.ico":"/blue-icon.png")+"'>" : "";
                    byte[] body = ("<!doctype html><title>" + title + "</title>"+favicon+"<body>" + content + "</body>").getBytes("UTF-8");
                    OutputStream output = socket.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: " + body.length
                            + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII")); output.write(body); output.flush();
                } catch (Exception ignored) { }
            }, "browser-media-fixture").start();
            final String base = "http://127.0.0.1:" + server.getLocalPort();
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // Let deferred startup tasks register before isolating this local fixture.
            // Otherwise a native startup URL resolved during the first tab switch
            // can replace the test document without exercising browser navigation.
            SystemClock.sleep(1200);
            waitForIdleSync();
            source = (WebSourceView) get(activity, "webSourceView");
            main(() -> {
                ((java.util.concurrent.atomic.AtomicInteger) get(activity, "catalogLoadGeneration")).incrementAndGet();
                // A first-install component download can finish after the fixture
                // opens. It is unrelated to browser/memory tests and must not zap it.
                Field pendingInstall = MainActivity.class.getDeclaredField("pendingCjsChannelIndex");
                pendingInstall.setAccessible(true); pendingInstall.setInt(activity, -1);
                Field generation = MainActivity.class.getDeclaredField("playRequestId");
                generation.setAccessible(true);
                generation.setInt(activity, generation.getInt(activity) + 1);
                call(activity, "cancelCustomSourceTimeout", new Class<?>[0]);
                call(activity, "clearPendingPlayer", new Class<?>[0]);
                call(activity, "cancelRemoteResolve", new Class<?>[0]);
                call(activity, "releasePlayer", new Class<?>[0]);
                call(activity, "closeWebSource", new Class<?>[0]);
                if (mediaFile != null && get(activity, "proxy") == null)
                    call(activity, "resetProxyForChannelSwitch", new Class<?>[0]);
                Field auto = MainActivity.class.getDeclaredField("webViewAutoPlaySniffed"); auto.setAccessible(true); auto.setBoolean(activity, false);
                source.open((Integer) get(activity, "playRequestId"), base + "/a");
            });
            JSONObject a = awaitPage("Page A");
            if(tabCloseOnly) {
                checkTabCloseDisplay(base);
                result.putString("stream","PASS video/fullscreen close waits for stable replacement, retained-page pixels, 12 rapid closes, stale fullscreen/drain callbacks, animated close with evicted-tab restoration\n");
                finish(-1,result);return;
            }
            if(cpuGuardsOnly) {
                checkCpuGuards(base);
                result.putString("stream","PASS bounded positive/negative resource URL cache, signed/nested URL compatibility, trim and navigation invalidation\n");
                finish(-1,result);return;
            }
            if(cpuOnly) {
                checkWebCpu(base);
                result.putString("stream","PASS 1920x1080 WebView playback CPU/DOM workload samples\n");
                finish(-1,result);return;
            }
            if(faviconOnly) {
                checkFaviconIsolation(base);
                result.putString("stream","PASS real WebView favicon: cross-host reuse clears old icon, start/finish cannot poison cache, stale URL/background callbacks rejected, new icon and same-domain cache work\n");
                finish(-1,result);return;
            }
            if (memoryOnly) {
                checkMemoryPressure(base);
                result.putString("stream","PASS shared/isolated script configuration, bounded weak favicon ownership, pressure cache eviction, stale artwork veto, visible artwork/native playback/retained webpage preserved\n");
                finish(-1,result);return;
            }
            if (browserFailureOnly) {
                checkBrowserFailures(base);
                result.putString("stream","PASS browser/bookmark channel menu and phone picker selection, HTTP 403/invalid media return without reload, timeout return, stale timer veto and auto-retry suppression\n");
                finish(-1,result);return;
            }
            if (retentionOnly) {
                checkBookmarkRetention(base);
                result.putString("stream", "PASS bookmark channel identity/record title, 15s cleanup, return without reload, persisted opt-out, paused background media, stale cleanup veto and overlay-first double-back exit\n");
                finish(-1,result); return;
            }
            if (webMediaOnly) {
                checkWebMediaSession(base);
                result.putString("stream", "PASS webpage metadata/artwork, native API handlers via controller, track refresh, current-tab identity, stale command rejection and HTML audio fallback\n");
                finish(-1, result);
                return;
            }
            if (backgroundTabOnly) {
                checkBackgroundTab(base);
                result.putString("stream", "PASS background link menu mouse click, deferred load, controller identity and capacity protection\n");
                finish(-1, result);
                return;
            }
            if (folderInputOnly) {
                checkFolderInput(base, 0); // Remote fly mouse.
                checkFolderInput(base, 1); // Physical mouse/drag listener.
                checkFolderInput(base, -1); // Touchscreen.
                result.putString("stream", "PASS folder input isolation and single new-tab navigation for remote mouse, physical mouse and touch\n");
                finish(-1, result);
                return;
            }
            if (overlaysOnly) {
                checkBrowserOverlays(base);
                result.putString("stream", "PASS root folder overlays and bottom cards dismissed synchronously on new tab, retained tab, address navigation, hide and close; late artwork callback cannot revive old card\n");
                finish(-1, result);
                return;
            }
            if (exitOnly) {
                checkSniffedPlaybackIdentity(base);
                result.putString("stream", "PASS stable sniffed playback cleanup and double-back exit\n");
                finish(-1, result);
                return;
            }
            check(a.getBoolean("webPageVisible") && (base + "/a").equals(a.getString("pageUrl")), "Controller still describes entry channel");
            WebTabBar bar = (WebTabBar) get(source, "tabBar");
            WebTabBar.Tab tabA = bar.active();
            WebView viewA = (WebView) get(source, "webView");
            WebViewClient clientA = (WebViewClient) get(source, "sourceClient");
            observe(base + "/shared.mp4");
            String keyA = state().getString("webPageKey");
            main(() -> source.openLinkInNewTab(base + "/b"));
            JSONObject b = awaitPage("Page B");
            WebTabBar.Tab tabB = bar.active();
            check(!keyA.equals(b.getString("webPageKey")) && b.getJSONArray("sniffedResources").length() == 0, "New tab inherited A resources");
            main(() -> clientA.onLoadResource(viewA, base + "/late-a.mp4")); waitForIdleSync();
            check(state().getJSONArray("sniffedResources").length() == 0, "Late background callback contaminated B");
            observe(base + "/shared.mp4");
            boolean rejected = false;
            try { call(activity, "handleWebControl", new Class<?>[]{JSONObject.class}, new JSONObject()
                    .put("action", "playSniffed").put("url", base + "/shared.mp4").put("pageKey", keyA)); }
            catch (InvocationTargetException expected) { rejected = expected.getCause() instanceof org.json.JSONException; }
            check(rejected, "Stale resource button accepted same URL on another tab");
            check(((java.util.Map<?,?>) get(source, "retainedTabWebViews")).containsKey(tabA), "Test needs enough memory for retained WebView");
            main(() -> call(bar, "select", new Class<?>[]{WebTabBar.Tab.class}, tabA));
            JSONObject restored = awaitPage("Page A");
            check(keyA.equals(restored.getString("webPageKey")) && restored.getJSONArray("sniffedResources").length() == 1,
                    "Retained A did not restore its own resource list");
            main(() -> {
                JSONObject receiver = new JSONObject().put("name", "Old receiver channel").put("audioOnly", true).put("fileDownloadAvailable", true);
                call(activity, "applyVisibleWebPageState", new Class<?>[]{JSONObject.class}, receiver);
                check("Page A".equals(receiver.getString("name")) && !receiver.getBoolean("fileDownloadAvailable"), "Receiver overlay not updated");
                source.hideForStreamPlayback();
                JSONObject nativeMedia = new JSONObject().put("name", "Native file");
                call(activity, "applyVisibleWebPageState", new Class<?>[]{JSONObject.class}, nativeMedia);
                check("Native file".equals(nativeMedia.getString("name")), "Hidden page overwrote native media title");
                source.restoreAfterStreamPlayback();
            });
            if (mediaFile != null) checkSniffedPlaybackIdentity(base);
            main(() -> call(source, "navigateAddress", new Class<?>[]{String.class}, base + "/c"));
            JSONObject c = awaitPage("Page C");
            check(!keyA.equals(c.getString("webPageKey")) && c.getJSONArray("sniffedResources").length() == 0, "Navigation retained previous document resources");
            final org.json.JSONArray emptySnapshot = (org.json.JSONArray) call(activity, "sniffedResourcesJson", new Class<?>[0]);
            main(() -> {
                WebView view = (WebView) get(source, "webView");
                WebViewClient client = (WebViewClient) get(source, "sourceClient");
                for (int i = 0; i < 1000; i++) client.onLoadResource(view, base + "/load-" + i + ".mp4");
                check(((java.util.Set<?>) get(source, "discoveredStreamUrls")).size() == 30,
                        "Resource flood was not bounded before entering the main queue");
            });
            waitForIdleSync();
            check(state().getJSONArray("sniffedResources").length() == 30, "Resource admission lost the first 30 entries");
            check(emptySnapshot.length() == 0, "Published JSON snapshot was mutated");
            final long[] cacheTime = {0};
            main(() -> {
                synchronized (get(activity, "sniffedResources")) {
                    Method json = MainActivity.class.getDeclaredMethod("sniffedResourcesJson"); json.setAccessible(true);
                    Object snapshot = json.invoke(activity);
                    long start = System.nanoTime();
                    for (int i = 0; i < 10000; i++) check(json.invoke(activity) == snapshot, "Unchanged resource JSON rebuilt");
                    cacheTime[0] = (System.nanoTime() - start) / 1000000;
                }
                call(activity, "clearSniffedResources", new Class<?>[0]);
                check(((org.json.JSONArray) call(activity, "sniffedResourcesJson", new Class<?>[0])).length() == 0, "Cleared resources left stale snapshot");
            });
            SniffedMediaProbe.Result manifest = new SniffedMediaProbe.Result();
            SniffedMediaProbe.inspectManifest("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720\nmain.m3u8\n", manifest);
            check(manifest.width == 1280 && manifest.height == 720 && manifest.bitrate == 2400000, "Manifest parser regression");
            main(() -> call(bar, "closeNow", new Class<?>[]{WebTabBar.Tab.class}, tabB));
            WebView reused = (WebView) get(source, "webView");
            main(() -> source.open((Integer) get(activity, "playRequestId"), base));
            awaitPage("Page A");
            main(() -> source.open((Integer) get(activity, "playRequestId"), base + "/#section"));
            awaitPage("Page A");
            check(!(Boolean) get(get(source, "sourceClient"), "awaitingDocument"), "Fragment navigation stuck behind document barrier");
            main(() -> source.open((Integer) get(activity, "playRequestId"), base + "/redirect"));
            awaitPage("Page B");
            check(reused == get(source, "webView"), "Redirect rebuilt renderer");
            WebViewClient previousClient = (WebViewClient) get(source, "sourceClient");
            main(() -> {
                int request = (Integer) get(activity, "playRequestId");
                source.closePage();
                source.open(request, base + "/heavy");
                check(reused == get(source, "webView"), "Close/open rebuilt a healthy WebView");
                WebViewClient nextClient = (WebViewClient) get(source, "sourceClient");
                previousClient.onLoadResource(reused, base + "/old-client.mp4");
                nextClient.onLoadResource(reused, base + "/queued-old-document.mp4");
                check(((java.util.Set<?>) get(source, "discoveredStreamUrls")).isEmpty(), "Old document crossed start barrier");
            });
            awaitPage("Heavy video");
            awaitVideo();
            long[] slowestSwitch = {0};
            for (int i = 0; i < 4; i++) {
                final String target = base + (i % 2 == 0 ? "/a" : "/heavy");
                main(() -> {
                    long start = SystemClock.elapsedRealtime();
                    source.closePage();
                    source.open((Integer) get(activity, "playRequestId"), target);
                    slowestSwitch[0] = Math.max(slowestSwitch[0], SystemClock.elapsedRealtime() - start);
                    check(reused == get(source, "webView"), "Repeated switch rebuilt WebView");
                });
                awaitPage(i % 2 == 0 ? "Page A" : "Heavy video");
                if (i % 2 != 0) awaitVideo();
            }
            // Sleeping tab follows the single-renderer path, even on a high-memory device.
            main(() -> {
                bar.active().sleeping = true;
                source.openLinkInNewTab(base + "/c");
                check(reused == get(source, "webView"), "Sleeping tab switch rebuilt WebView");
            });
            awaitPage("Page C");
            main(() -> call(bar, "closeNow", new Class<?>[]{WebTabBar.Tab.class}, bar.active()));
            awaitPage("Heavy video");
            final WebView restoredRenderer = (WebView)get(source,"webView");
            check(restoredRenderer != reused, "Closing active tab reused its departing renderer despite available memory");
            check(!bar.active().sleeping, "Closing tab did not wake selected sleeping tab");
            awaitVideo();
            main(() -> {
                // Exercise the saved-history restore path used when memory policy
                // has reclaimed a background renderer, on an already-used view.
                Bundle history = new Bundle();
                check(restoredRenderer.saveState(history) != null, "Cannot save tab history");
                bar.active().sleeping = true;
                WebTabBar.Tab restoredTab = bar.openNew(base + "/heavy", "");
                restoredTab.state = history;
                call(source, "loadBrowserTab", new Class<?>[]{WebTabBar.Tab.class}, restoredTab);
                check(restoredRenderer == get(source, "webView"), "History restoration rebuilt renderer");
            });
            awaitPage("Heavy video");
            awaitVideo();
            main(() -> source.closePage());
            waitForIdleSync();
            long blankDeadline = SystemClock.elapsedRealtime() + 5000;
            while (!"null".equals(evaluate("document.querySelector('video')"))
                    && SystemClock.elapsedRealtime() < blankDeadline) SystemClock.sleep(50);
            check("null".equals(evaluate("document.querySelector('video')")), "Closed page retained video");
            main(() -> {
                source.closePage(); // Idempotent, no extra navigation/history calls.
                source.open((Integer) get(activity, "playRequestId"), base + "/heavy");
                check(restoredRenderer == get(source, "webView"), "Reopening browser rebuilt blank renderer");
            });
            awaitPage("Heavy video");
            awaitVideo();
            main(() -> {
                int request = (Integer) get(activity, "playRequestId");
                source.closePage();
                ((Runnable) get(source, "unloadClosedPage")).run();
                check(get(restoredRenderer, "shutdown") != null, "Idle shutdown was not started");
                source.open(request, base + "/heavy");
                check(get(restoredRenderer, "shutdown") == null, "Reopening did not cancel shutdown");
            });
            awaitPage("Heavy video"); awaitVideo();
            SystemClock.sleep(2300);
            check(evaluate("document.title").contains("Heavy video"), "Late shutdown cleared reopened page");
            main(() -> source.openLinkInNewTab(base + "/a"));
            awaitPage("Page A");
            main(() -> source.openLinkInNewTab(base + "/heavy"));
            awaitPage("Heavy video"); awaitVideo();
            WebTabBar.Tab playingTab = bar.active();
            WebView retiringVideo = (WebView) get(source, "webView");
            main(() -> {
                call(bar, "closeNow", new Class<?>[]{WebTabBar.Tab.class}, playingTab);
                check(Boolean.TRUE.equals(get(retiringVideo, "retiring")), "Playing tab not retired");
                check(get(retiringVideo, "shutdown") != null, "Playing tab destroyed inside close callback");
            });
            awaitPage("Page A");
            long drainDeadline = SystemClock.elapsedRealtime() + 5000;
            while (get(retiringVideo, "shutdown") != null && SystemClock.elapsedRealtime() < drainDeadline) SystemClock.sleep(50);
            main(() -> check(retiringVideo.getParent() == null, "Drained renderer remained attached"));
            main(() -> source.open((Integer) get(activity, "playRequestId"), base + "/heavy"));
            awaitPage("Heavy video"); awaitVideo();
            long exitStart = SystemClock.elapsedRealtime();
            main(() -> activity.finish());
            while (!activity.isDestroyed() && SystemClock.elapsedRealtime() - exitStart < 10000) SystemClock.sleep(20);
            check(activity.isDestroyed(), "Activity exit did not complete while video was playing");
            long teardown = SystemClock.elapsedRealtime() - exitStart;
            main(() -> {
                source.destroyPage(); // Also called by Activity.onDestroy; must be idempotent.
                check(get(source, "webView") == null, "Destroyed browser retains active instance");
                check(((java.util.Map<?,?>) get(source, "retainedTabWebViews")).isEmpty(), "Destroyed browser retains tabs");
            });
            result.putString("stream", "PASS active page title/URL; new-tab isolation; late callback rejected; same-URL stale action rejected; retained-tab resource restoration; receiver overlay; native playback preserved; document navigation reset; 1000 resource events bounded to 30; immutable JSON snapshot/reset; 10k cached reads=" + cacheTime[0] + "ms; manifest parser; bare-origin/fragment/redirect navigation; heavy DOM + playing video; renderer reuse; old-document barrier; sleeping/closed-tab reuse; saved-history restore; unload/reopen; idempotent teardown; slowest switch=" + slowestSwitch[0] + "ms; video Activity exit=" + teardown + "ms\n");
        } catch (Throwable error) { code = 0; result.putString("stream", android.util.Log.getStackTraceString(error)); }
        finally {
            if (activity != null) try { main(() -> call(activity, "closeWebSource", new Class<?>[0])); } catch (Exception ignored) { }
            if (server != null) try { server.close(); } catch (IOException ignored) { }
        }
        finish(code, result);
    }
}
