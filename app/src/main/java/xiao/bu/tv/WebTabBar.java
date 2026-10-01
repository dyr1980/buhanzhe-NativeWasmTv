package xiao.bu.tv;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Two-row browser chrome; page actions stay outside the scrolling bookmarks. */
final class WebTabBar extends LinearLayout {
    interface Callback {
        void onReload();
        void onNavigate(String value);
        void onChannel(int groupIndex, int channelIndex);
        void onOpenBookmark(String url, String title, String group, boolean newTab);
        void onSelect(Tab tab);
        void onNewTab();
        void onTabClosed(Tab tab);
        void onAllTabsClosed();
        void onTabAdBlockChanged(Tab tab);
        void onTabMuteChanged(Tab tab);
        void onTabSleepChanged(Tab tab);
        void onToolbarHeightChanged(int heightDp);
        void onImagesChanged(boolean enabled);
        void onGlobalAdBlockChanged(boolean enabled);
        void onWebRtcChanged(boolean enabled);
        void onEnterFullscreen();
    }

    static final class Tab {
        final int id;
        String url;
        String title;
        String bookmarkTitle = "", bookmarkGroup = "", bookmarkUrl = "";
        boolean pinned;
        boolean muted;
        boolean sleeping;
        Bundle state;
        Bitmap icon;
        long resourceRevision;

        Tab(int id, String url, String title, boolean pinned) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.pinned = pinned;
        }
    }

    private static final int TAB_CARD_HEIGHT_DP = 22;
    private static final int TAB_ROW_HEIGHT_DP = TAB_CARD_HEIGHT_DP + 4;
    static final int COMPACT_HEIGHT_DP = TAB_ROW_HEIGHT_DP + 4;
    static final int HEIGHT_DP = COMPACT_HEIGHT_DP + 28;
    private static final String PINNED_TABS = "web_pinned_tabs_v1";
    private static final String BOOKMARK_BAR = "web_bookmark_bar_visible_v1";
    private static final String CHANNEL_FOLDERS = "web_channel_folders_visible_v1";
    private static final String AD_BLOCK_DISABLED = "web_ad_block_disabled_domains_v1";
    // Saved WebView states can be sizeable, so keep a safety bound while allowing
    // wide television screens to use substantially more than the old 12 tabs.
    private static final int MAX_TABS = 32;
    private static final long ADD_ANIMATION_MS = 100L;
    private static final long CLOSE_ANIMATION_MS = 90L;

    private final ArrayList<Tab> tabs = new ArrayList<Tab>();
    private final WebBookmarkStore bookmarkStore;
    private final HashMap<Integer, View> chipViews = new HashMap<Integer, View>();
    private final Set<String> adBlockDisabledDomains = new HashSet<String>();
    private final HorizontalScrollView tabScroll;
    private final LinearLayout tabStrip;
    private final LinearLayout bookmarkStrip;
    private final HorizontalScrollView bookmarkScroll;
    private final LinearLayout tabsRow;
    private final LinearLayout bookmarkRow;
    private final BrowserIconView addTabButton;
    private final BrowserIconView moreButton;
    private final Callback callback;
    private final float density;
    private final Paint bookmarkTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean bookmarkBarVisible;
    private boolean channelFoldersVisible;
    private boolean imagesEnabled = true;
    private boolean globalAdBlockEnabled = true;
    private boolean webRtcEnabled;
    private int nextId = 1;
    private float interfaceScale = 1f;
    private int animateAddedTabId = -1;
    private Tab active;
    private FrameLayout folderLayer;
    private LinearLayout folderPanel;
    private LinearLayout folderBody;
    private TextView folderTitle;

    /** Modal for both touch and mouse input; clickability alone only covers touch. */
    private static final class BrowserPopupLayer extends FrameLayout {
        BrowserPopupLayer(Context context) {
            super(context);
            setClickable(true);
            setFocusableInTouchMode(true);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            super.dispatchTouchEvent(event);
            return true;
        }

        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
            // Let rows/context menus and ScrollView handle the event first. Even
            // if they decline it (e.g. a wheel at the end), never route the same
            // mouse press/release, hover or wheel to the WebView behind this layer.
            super.dispatchGenericMotionEvent(event);
            return true;
        }
    }

    WebTabBar(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        density = getResources().getDisplayMetrics().density;
        bookmarkTextPaint.setTextSize(11f
                * getResources().getDisplayMetrics().scaledDensity);
        bookmarkBarVisible = preferences().getBoolean(BOOKMARK_BAR, true);
        channelFoldersVisible = preferences().getBoolean(CHANNEL_FOLDERS, true);
        bookmarkStore = new WebBookmarkStore(context, preferences());
        adBlockDisabledDomains.addAll(preferences().getStringSet(AD_BLOCK_DISABLED,
                java.util.Collections.<String>emptySet()));
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(2), dp(4), dp(2));
        setBackgroundColor(0xffeef0f3);

        // Row one is reserved for site cards. Keep the add button outside the
        // scrollable card flow so it always remains reachable.
        tabsRow = row();
        tabScroll = new HorizontalScrollView(context);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabStrip = row();
        tabScroll.addView(tabStrip, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        tabsRow.addView(tabScroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addTabButton = iconAction(BrowserIconView.ADD, "新建标签",
                v -> callback.onNewTab());
        LinearLayout.LayoutParams addTabParams = new LinearLayout.LayoutParams(dp(36), dp(TAB_CARD_HEIGHT_DP));
        addTabParams.setMargins(dp(2), 0, dp(2), 0);
        tabsRow.addView(addTabButton, addTabParams);
        addView(tabsRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(TAB_ROW_HEIGHT_DP)));

        bookmarkRow = row();
        moreButton = iconAction(BrowserIconView.MORE, "更多", this::showManagementMenu);
        bookmarkScroll = new HorizontalScrollView(context);
        bookmarkScroll.setHorizontalScrollBarEnabled(false);
        bookmarkScroll.setFillViewport(false);
        bookmarkScroll.setOnGenericMotionListener((view, event) ->
                scrollBookmarkBarWithMouse(event));
        bookmarkStrip = row();
        bookmarkStrip.setPadding(dp(1), 0, dp(1), 0);
        bookmarkScroll.addView(bookmarkStrip, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        bookmarkRow.addView(bookmarkScroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addView(bookmarkRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));
        updateMoreButtonPlacement();

        loadPinnedTabs();
        render();
    }

    int heightDp() { return bookmarkBarVisible ? HEIGHT_DP : COMPACT_HEIGHT_DP; }

    int heightPx() { return dp(heightDp()); }

    void setInterfaceScale(float scale) {
        float safeScale = Math.max(0.35f, Math.min(1.60f, scale));
        if (Math.abs(interfaceScale - safeScale) < 0.001f) return;
        dismissFolderPanel();
        interfaceScale = safeScale;
        bookmarkTextPaint.setTextSize(11f
                * getResources().getDisplayMetrics().scaledDensity * interfaceScale);
        setPadding(dp(4), dp(2), dp(4), dp(2));

        ViewGroup.LayoutParams tabsParams = tabsRow.getLayoutParams();
        tabsParams.height = dp(TAB_ROW_HEIGHT_DP);
        tabsRow.setLayoutParams(tabsParams);
        LinearLayout.LayoutParams addParams = (LinearLayout.LayoutParams)
                addTabButton.getLayoutParams();
        addParams.width = dp(36);
        addParams.height = dp(TAB_CARD_HEIGHT_DP);
        addParams.setMargins(dp(2), 0, dp(2), 0);
        addTabButton.setLayoutParams(addParams);

        ViewGroup.LayoutParams bookmarkParams = bookmarkRow.getLayoutParams();
        bookmarkParams.height = dp(28);
        bookmarkRow.setLayoutParams(bookmarkParams);
        bookmarkStrip.setPadding(dp(1), 0, dp(1), 0);
        updateMoreButtonPlacement();
        render();
        requestLayout();
        callback.onToolbarHeightChanged(heightDp());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Child controls keep their normal click, edit, scroll and drag behavior.
        // If a gap or a disabled child declines the event, the browser chrome still
        // owns the pointer sequence instead of letting the player handle it below.
        boolean handled = super.dispatchTouchEvent(event);
        return handled || getVisibility() == VISIBLE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissFolderPanel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && width != oldWidth) {
            // The toolbar can be resized independently from the physical display
            // (rotation, overscan and cast layouts). Recalculate from its real width.
            post(this::renderTabs);
        }
    }

    void setPolicies(boolean images, boolean adBlock, boolean webRtc) {
        imagesEnabled = images;
        globalAdBlockEnabled = adBlock;
        webRtcEnabled = webRtc;
    }

    Tab openChannel(String url, String title) {
        if (active == null || active.pinned) return openNew(url, title);
        active.url = safe(url);
        active.bookmarkTitle = active.bookmarkGroup = active.bookmarkUrl = "";
        active.title = displayTitle(title, url);
        active.icon = bookmarkStore.iconForUrl(active.url);
        active.sleeping = false;
        render();
        return active;
    }

    Tab openNew(String url, String title) {
        return openNew(url, title, true);
    }

    Tab openBackground(String url, String title) {
        return openNew(url, title, false);
    }

    private Tab openNew(String url, String title, boolean activate) {
        if (!makeRoomForNewTab(activate)) {
            Toast.makeText(getContext(), "标签已达上限，请先关闭或取消固定一个标签",
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        Tab added = new Tab(nextId++, safe(url), displayTitle(title, url), false);
        added.icon = bookmarkStore.iconForUrl(added.url);
        tabs.add(added);
        if (activate) active = added;
        animateAddedTabId = added.id;
        render();
        return added;
    }

    void updateActive(String url, String title) {
        if (active == null || active.sleeping) return;
        boolean changed = false;
        if (url != null && url.length() > 0 && !url.equals(active.url)) {
            active.url = url;
            active.icon = bookmarkStore.iconForUrl(url);
            changed = true;
        }
        if (title != null && title.trim().length() > 0
                && !title.trim().equals(active.title)) {
            active.title = title.trim();
            changed = true;
        }
        if (changed) {
            if (active.pinned) savePinnedTabs();
            renderTabs();
        }
    }

    void trimMemory() { bookmarkStore.trimMemory(); }

    void updateActiveIcon(String pageUrl, Bitmap favicon) {
        if (active == null || active.sleeping || favicon == null || favicon.isRecycled()
                || !canonicalPage(active.url).equals(canonicalPage(pageUrl))) return;
        String host = domain(pageUrl);
        if (host.length() == 0) return;
        bookmarkStore.cacheIcon(pageUrl, favicon);
        active.icon = bookmarkStore.iconForUrl(active.url);
        // One domain has one favicon cache entry. Refresh every already-open tab
        // for that domain now, so opening the same site repeatedly never waits
        // for a second WebView favicon callback.
        for (Tab tab : tabs) {
            if (!host.equals(domain(tab.url))) continue;
            Bitmap cached = bookmarkStore.iconForUrl(tab.url);
            if (cached != null) tab.icon = cached;
        }
        render();
    }

    Tab active() { return active; }
    boolean activeAdBlockEnabled() { return isAdBlockEnabled(active); }
    boolean activeMuted() { return active != null && active.muted; }

    private void select(Tab tab) {
        if (tab == null) return;
        dismissTransientPanels();
        if (tab.sleeping) {
            tab.sleeping = false;
            tab.state = null;
            callback.onTabSleepChanged(tab);
        }
        if (tab == active) {
            render();
            return;
        }
        active = tab;
        render();
        callback.onSelect(tab);
    }

    private void togglePinned(Tab tab) {
        tab.pinned = !tab.pinned;
        savePinnedTabs();
        render();
    }

    private void toggleAdBlock(Tab tab) {
        String host = domain(tab.url);
        if (host.length() == 0) return;
        if (!adBlockDisabledDomains.add(host)) adBlockDisabledDomains.remove(host);
        preferences().edit().putStringSet(AD_BLOCK_DISABLED,
                new HashSet<String>(adBlockDisabledDomains)).apply();
        render();
        callback.onTabAdBlockChanged(tab);
    }

    private void toggleMute(Tab tab) {
        tab.muted = !tab.muted;
        render();
        callback.onTabMuteChanged(tab);
    }

    private void toggleSleep(Tab tab) {
        tab.sleeping = !tab.sleeping;
        if (tab.sleeping) tab.state = null;
        render();
        callback.onTabSleepChanged(tab);
    }

    private void close(Tab tab) {
        View chip = chipViews.get(tab.id);
        if (chip == null) {
            closeNow(tab);
            return;
        }
        if (Build.VERSION.SDK_INT >= 16) {
            chip.animate().cancel();
            chip.animate().alpha(0f).scaleX(0.72f).setDuration(CLOSE_ANIMATION_MS)
                    .withEndAction(() -> closeNow(tab)).start();
        } else {
            // withEndAction was added in 4.1; 4.0.x closes without animation.
            closeNow(tab);
        }
    }

    private void closeNow(Tab tab) {
        int position = tabs.indexOf(tab);
        if (position < 0) return;
        boolean wasActive = tab == active;
        tabs.remove(position);
        callback.onTabClosed(tab);
        if (tab.pinned) savePinnedTabs();
        if (!wasActive) {
            render();
            return;
        }
        if (tabs.isEmpty()) {
            active = null;
            render();
            callback.onAllTabsClosed();
            return;
        }
        active = tabs.get(Math.max(0, Math.min(position - 1, tabs.size() - 1)));
        if (active.sleeping) {
            active.sleeping = false;
            active.state = null;
            callback.onTabSleepChanged(active);
        }
        render();
        callback.onSelect(active);
    }

    private void closeTabsToRight(Tab tab) {
        int position = tabs.indexOf(tab);
        if (position < 0 || position == tabs.size() - 1) return;
        boolean activeRemoved = tabs.indexOf(active) > position;
        while (tabs.size() > position + 1) {
            Tab removed = tabs.remove(tabs.size() - 1);
            callback.onTabClosed(removed);
        }
        savePinnedTabs();
        if (activeRemoved) {
            active = tab;
            active.sleeping = false;
            render();
            callback.onSelect(active);
        } else render();
    }

    private boolean makeRoomForNewTab(boolean allowReplaceActive) {
        if (tabs.size() < MAX_TABS) return true;
        // Prefer a sleeping background tab, then the oldest ordinary background
        // tab. Pinned tabs and the active tab are preserved whenever possible.
        for (Tab candidate : new ArrayList<Tab>(tabs)) {
            if (!candidate.pinned && candidate != active && candidate.sleeping) {
                discardTab(candidate);
                return true;
            }
        }
        for (Tab candidate : new ArrayList<Tab>(tabs)) {
            if (!candidate.pinned && candidate != active) {
                discardTab(candidate);
                return true;
            }
        }
        if (allowReplaceActive && active != null && !active.pinned) {
            discardTab(active);
            return true;
        }
        return false;
    }

    private void discardTab(Tab tab) {
        tab.state = null;
        tabs.remove(tab);
        callback.onTabClosed(tab);
    }

    private void render() {
        renderTabs();
        renderBookmarkBar();
    }

    private void renderTabs() {
        tabStrip.removeAllViews();
        chipViews.clear();
        int pinnedCount = 0;
        for (Tab tab : tabs) if (tab.pinned) pinnedCount++;
        int normalCount = tabs.size() - pinnedCount;
        int viewportPx = tabScroll.getWidth();
        int screenDp = (int) ((viewportPx > 0 ? viewportPx
                : getResources().getDisplayMetrics().widthPixels) / Math.max(.5f, density));
        int totalBudget = Math.max(186, viewportPx > 0 ? screenDp : screenDp - 54);
        int available = totalBudget - pinnedCount * 36;
        int naturalNormalWidth = normalCount == 0 ? 0 : available / normalCount - 4;
        boolean iconOnly = normalCount > 0 && naturalNormalWidth < 72;
        // In compact mode distribute the available row instead of hard-capping
        // every tab at 32dp, which left half of wide screens visibly unused.
        int compactNormalWidth = normalCount == 0 ? 32
                : Math.max(32, Math.min(64, available / normalCount - 4));
        int normalWidth = Math.max(52, Math.min(154, naturalNormalWidth));

        for (final Tab tab : tabs) {
            LinearLayout chip = row();
            chip.setPadding(dp(2), 0, dp(2), 0);
            int background = tab == active ? 0xffffffff
                    : tab.sleeping ? 0xffd3d6dc : 0xffdfe2e7;
            chip.setBackgroundDrawable(roundRect(background, 6));
            int chipWidth = tab.pinned ? 32 : iconOnly ? compactNormalWidth : normalWidth;
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    dp(chipWidth), dp(TAB_CARD_HEIGHT_DP));
            chipParams.setMargins(dp(2), 0, dp(2), 0);

            // Once the row is crowded, make the active tab itself the close target.
            // This keeps every compact tab useful without trying to squeeze a favicon
            // and a close button into the same small hit area.
            boolean activeCompactClose = iconOnly && tab == active;
            View primaryIcon;
            if (activeCompactClose) {
                BrowserIconView close = smallIconAction(
                        BrowserIconView.CLOSE, "关闭当前标签");
                close.setOnClickListener(v -> close(tab));
                primaryIcon = close;
            } else {
                View siteIcon = createSiteIcon(tab);
                siteIcon.setOnClickListener(v -> select(tab));
                primaryIcon = siteIcon;
            }
            chip.addView(primaryIcon, fixed(tab.pinned || iconOnly
                    ? Math.max(16, chipWidth - 4) : 25));
            if (!tab.pinned && !iconOnly) {
                TextView title = new TextView(getContext());
                title.setText(tab.sleeping ? "休眠 · " + displayTitle(tab.title, tab.url)
                        : displayTitle(tab.title, tab.url));
                title.setTextColor(tab.sleeping ? 0xff777b82 : 0xff202124);
                title.setTextSize(scaledSp(11f));
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL);
                title.setContentDescription("标签 " + title.getText());
                title.setOnClickListener(v -> select(tab));
                attachTabContext(title, tab);
                chip.addView(title, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
                BrowserIconView close = smallIconAction(BrowserIconView.CLOSE, "关闭标签");
                close.setOnClickListener(v -> close(tab));
                attachTabContext(close, tab);
                chip.addView(close, fixed(24));
            }
            attachTabContext(chip, tab);
            attachTabContext(primaryIcon, tab);
            attachTabDropTarget(chip, tab);
            tabStrip.addView(chip, chipParams);
            chipViews.put(tab.id, chip);
            if (tab.id == animateAddedTabId) {
                chip.setAlpha(.35f);
                chip.setScaleX(.82f);
                chip.animate().alpha(1f).scaleX(1f).setDuration(ADD_ANIMATION_MS).start();
            }
        }
        animateAddedTabId = -1;
        revealActiveTab();
    }

    private void revealActiveTab() {
        final int activeId = active == null ? -1 : active.id;
        tabScroll.post(() -> {
            View chip = chipViews.get(activeId);
            if (chip == null || tabScroll.getWidth() <= 0) return;
            int left = chip.getLeft();
            int right = chip.getRight();
            int visibleLeft = tabScroll.getScrollX();
            int visibleRight = visibleLeft + tabScroll.getWidth();
            if (left < visibleLeft) {
                tabScroll.smoothScrollTo(left, 0);
            } else if (right > visibleRight) {
                tabScroll.smoothScrollTo(right - tabScroll.getWidth(), 0);
            }
        });
    }

    private View createSiteIcon(Tab tab) {
        View icon;
        if (tab.icon == null) {
            BrowserIconView fallback = new BrowserIconView(getContext(), BrowserIconView.GLOBE);
            fallback.setIconColor(tab.sleeping ? 0xff8a8d92 : 0xff303134);
            icon = fallback;
        } else {
            BrowserFaviconView favicon = new BrowserFaviconView(getContext(), tab.icon);
            favicon.setAlpha(tab.sleeping ? .55f : 1f);
            icon = favicon;
        }
        icon.setContentDescription(tab.icon == null ? "网站图标" : "网站图标 已加载");
        return icon;
    }

    private void attachTabContext(View view, final Tab tab) {
        view.setOnLongClickListener(v -> startTabDrag(v, tab));
        if (Build.VERSION.SDK_INT >= 23) {
            view.setOnGenericMotionListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
                        && event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
                    showTabMenu(v, tab);
                    return true;
                }
                return false;
            });
        }
    }

    private void showTabMenu(View anchor, Tab tab) {
        final String host = domain(tab.url);
        showMenu(anchor, 270, 0, new String[]{
                tab.pinned ? "取消固定" : "固定标签",
                (isAdBlockEnabled(tab) ? "关闭" : "开启") + "广告拦截"
                        + (host.length() == 0 ? "" : " · " + host),
                tab.muted ? "开启声音" : "静音网站",
                tab.sleeping ? "设为活跃" : "设为睡眠",
                "关闭标签",
                "关闭右侧标签"
        }, new OnClickListener[]{
                v -> togglePinned(tab), v -> toggleAdBlock(tab), v -> toggleMute(tab),
                v -> toggleSleep(tab), v -> close(tab), v -> closeTabsToRight(tab)
        });
    }

    @SuppressWarnings("deprecation")
    private boolean startTabDrag(View source, Tab tab) {
        ClipData clip = ClipData.newPlainText("nTv browser tab", tab.url);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(source);
        boolean started = Build.VERSION.SDK_INT >= 24
                ? source.startDragAndDrop(clip, shadow, tab, 0)
                : source.startDrag(clip, shadow, tab, 0);
        return started;
    }

    private void attachTabDropTarget(final View view, final Tab target) {
        view.setOnDragListener((dropView, event) -> {
            Object state = event.getLocalState();
            if (!(state instanceof Tab)) return false;
            Tab moving = (Tab) state;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    dropView.setScaleX(1.04f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    dropView.setScaleX(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    dropView.setScaleX(1f);
                    if (moving == target) return false;
                    int from = tabs.indexOf(moving);
                    int to = tabs.indexOf(target);
                    if (from < 0 || to < 0) return false;
                    tabs.remove(from);
                    if (from < to) to--;
                    if (event.getX() > dropView.getWidth() / 2f) to++;
                    tabs.add(Math.max(0, Math.min(to, tabs.size())), moving);
                    savePinnedTabs();
                    render();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    dropView.setAlpha(1f);
                    dropView.setScaleX(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void showManagementMenu(View anchor) {
        showMenu(anchor, 220, -178, new String[]{
                "刷新网页",
                bookmarkStore.findBookmark(active == null ? "" : active.url) != null
                        ? "取消收藏当前网页" : "收藏当前网页",
                bookmarkBarVisible ? "隐藏网页收藏栏" : "显示网页收藏栏",
                channelFoldersVisible ? "隐藏频道列表" : "显示频道列表",
                imagesEnabled ? "隐藏图片" : "显示图片",
                globalAdBlockEnabled ? "关闭广告拦截" : "开启广告拦截",
                webRtcEnabled ? "关闭 WebRTC" : "开启 WebRTC",
                "进入全屏"
        }, new OnClickListener[]{
                v -> callback.onReload(),
                v -> toggleFavorite(),
                v -> setBookmarkBarVisible(!bookmarkBarVisible),
                v -> setChannelFoldersVisible(!channelFoldersVisible),
                v -> {
                    imagesEnabled = !imagesEnabled;
                    callback.onImagesChanged(imagesEnabled);
                },
                v -> {
                    globalAdBlockEnabled = !globalAdBlockEnabled;
                    callback.onGlobalAdBlockChanged(globalAdBlockEnabled);
                },
                v -> {
                    webRtcEnabled = !webRtcEnabled;
                    callback.onWebRtcChanged(webRtcEnabled);
                },
                v -> callback.onEnterFullscreen()
        });
    }

    private void showMenu(View anchor, int widthDp, int xDp, String[] labels,
            OnClickListener[] actions) {
        dismissFolderPanel();
        View rootView = getRootView().findViewById(R.id.root);
        if (!(rootView instanceof ViewGroup)) return;
        final ViewGroup host = (ViewGroup) rootView;
        final FrameLayout layer = new BrowserPopupLayer(getContext());
        folderLayer = layer;

        LinearLayout menu = new LinearLayout(getContext());
        folderPanel = menu;
        menu.setOrientation(VERTICAL);
        menu.setClickable(true);
        menu.setPadding(dp(6), dp(6), dp(6), dp(6));
        menu.setBackgroundDrawable(roundRect(0xffffffff, 10));
        if (Build.VERSION.SDK_INT >= 21) menu.setElevation(dp(8));
        for (int i = 0; i < labels.length; i++) {
            final OnClickListener action = actions[i];
            TextView item = new TextView(getContext());
            item.setText(labels[i]);
            item.setTextSize(scaledSp(13f));
            item.setTextColor(0xff202124);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), 0, dp(12), 0);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundDrawable(roundRect(0xfff7f8fa, 6));
            item.setOnClickListener(v -> {
                dismissFolderPanel();
                action.onClick(v);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(38));
            params.setMargins(0, dp(2), 0, dp(2));
            menu.addView(item, params);
        }

        host.addView(layer, new ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        int width = dp(widthDp);
        int height = dp(12 + labels.length * 42);
        int gap = dp(4);
        int[] hostLocation = new int[2];
        int[] anchorLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        anchor.getLocationOnScreen(anchorLocation);
        int anchorLeft = anchorLocation[0] - hostLocation[0];
        int anchorTop = anchorLocation[1] - hostLocation[1];
        int anchorBottom = anchorTop + anchor.getHeight();
        int x = anchorLeft + dp(xDp);
        x = Math.max(gap, Math.min(x, Math.max(gap, host.getWidth() - width - gap)));
        int y = anchorBottom + dp(2);
        if (y + height > host.getHeight() - gap) y = anchorTop - height - dp(2);
        y = Math.max(gap, Math.min(y, Math.max(gap, host.getHeight() - height - gap)));
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(width, height);
        menuParams.leftMargin = x;
        menuParams.topMargin = y;
        layer.addView(menu, menuParams);

        layer.setOnClickListener(v -> dismissFolderPanel());
        layer.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissFolderPanel();
                return true;
            }
            return false;
        });
        layer.requestFocus();
    }

    private void toggleFavorite() {
        if (active == null || !isWebUrl(active.url)) return;
        WebBookmarkStore.Node existing = bookmarkStore.findBookmark(active.url);
        if (existing != null) bookmarkStore.remove(existing);
        else {
            WebBookmarkStore.Node added = bookmarkStore.addBookmark(canonicalPage(active.url),
                    displayTitle(active.title, active.url));
            if (active.icon != null) bookmarkStore.cacheIcon(added.url, active.icon);
        }
        render();
    }

    private void renderBookmarkBar() {
        bookmarkStrip.removeAllViews();
        bookmarkScroll.setVisibility(bookmarkBarVisible ? VISIBLE : GONE);
        if (!bookmarkBarVisible) return;
        bookmarkStrip.setOnLongClickListener(v -> {
            promptFolderName(null, null);
            return true;
        });
        if (Build.VERSION.SDK_INT >= 23) {
            bookmarkStrip.setOnGenericMotionListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
                        && event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
                    showBookmarkBarMenu(v);
                    return true;
                }
                return false;
            });
        }
        bookmarkStrip.setOnDragListener((target, event) -> handleRootBookmarkDrop(event));

        boolean addedChannel = false;
        if (channelFoldersVisible) {
            ChannelCatalog.Group[] groups = ChannelCatalog.GROUPS;
            for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                if (groups[groupIndex].channels.length == 0) continue;
                final int selectedGroup = groupIndex;
                String groupTitle = importedBookmarkGroupTitle(groups[groupIndex].title);
                LinearLayout folder = bookmarkItem(groupTitle,
                        BrowserIconView.FOLDER, 0xff1976d2);
                folder.setBackgroundDrawable(roundRect(0xffe4edf9, 5));
                folder.setContentDescription("频道文件夹 " + groupTitle);
                folder.setOnClickListener(v -> showChannelGroup(v, selectedGroup));
                addBookmarkBarItem(folder, groupTitle, true);
                addedChannel = true;
            }
        }
        if (addedChannel && !bookmarkStore.roots().isEmpty()) {
            View divider = new View(getContext());
            divider.setBackgroundColor(0xffc8ccd2);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(13));
            dividerParams.setMargins(dp(2), 0, dp(3), 0);
            bookmarkStrip.addView(divider, dividerParams);
        }
        for (WebBookmarkStore.Node node : new ArrayList<WebBookmarkStore.Node>(bookmarkStore.roots())) {
            addWebBookmarkBarItem(node);
        }
    }

    private void addBookmarkBarItem(View item, String title, boolean folder) {
        String compact = compactTitle(title);
        int measuredTextDp = (int) Math.ceil(bookmarkTextPaint.measureText(compact)
                / Math.max(.5f, density));
        int width = Math.max(folder ? 44 : 50,
                Math.min(folder ? 132 : 170, 25 + measuredTextDp));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(20));
        params.setMargins(0, 0, dp(1), 0);
        bookmarkStrip.addView(item, params);
    }

    private void addWebBookmarkBarItem(final WebBookmarkStore.Node node) {
        LinearLayout item = node.folder
                ? bookmarkItem(node.title, BrowserIconView.FOLDER, 0xff5f6368)
                : bookmarkItem(node.title, bookmarkStore.icon(node),
                        BrowserIconView.GLOBE, 0xff5f6368);
        item.setContentDescription((node.folder ? "网页文件夹 " : "网页收藏 ")
                + compactTitle(node.title));
        item.setOnClickListener(node.folder
                ? v -> showWebFolder(v, node)
                : v -> openBookmark(node, true));
        attachBookmarkMouseDrag(item, node);
        attachBookmarkContext(item, node, null);
        attachBookmarkDropTarget(item, node, null);
        addBookmarkBarItem(item, node.title, node.folder);
    }

    private LinearLayout bookmarkItem(String text, int icon, int iconColor) {
        LinearLayout item = row();
        item.setPadding(dp(2), 0, dp(2), 0);
        BrowserIconView image = new BrowserIconView(getContext(), icon);
        image.setIconColor(iconColor);
        image.setFocusable(false);
        item.addView(image, fixed(18));
        TextView title = new TextView(getContext());
        title.setText(compactTitle(text));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextSize(scaledSp(11f));
        title.setTextColor(0xff303134);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        item.addView(title, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        return item;
    }

    private LinearLayout bookmarkItem(String text, Bitmap bitmap, int fallback, int fallbackColor) {
        LinearLayout item = row();
        item.setPadding(dp(2), 0, dp(2), 0);
        View icon;
        if (bitmap == null || bitmap.isRecycled()) {
            BrowserIconView vector = new BrowserIconView(getContext(), fallback);
            vector.setIconColor(fallbackColor);
            icon = vector;
        } else {
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setPadding(dp(2), dp(2), dp(2), dp(2));
            image.setImageBitmap(bitmap);
            icon = image;
        }
        icon.setFocusable(false);
        item.addView(icon, fixed(18));
        TextView title = new TextView(getContext());
        title.setText(compactTitle(text));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextSize(scaledSp(11f));
        title.setTextColor(0xff303134);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        item.addView(title, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        return item;
    }

    private void showChannelGroup(View anchor, int groupIndex) {
        if (groupIndex < 0 || groupIndex >= ChannelCatalog.GROUPS.length) return;
        ChannelCatalog.Group group = ChannelCatalog.GROUPS[groupIndex];
        int count = group.channels.length;
        int height = Math.min(360, 48 + Math.max(1, count) * 40);
        LinearLayout list = showFolderPanel(anchor,
                importedBookmarkGroupTitle(group.title), 250, height);
        if (list == null) return;
        if (count == 0) {
            TextView empty = menuItem("暂无频道");
            list.addView(empty, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(40)));
        } else {
            for (int i = 0; i < count; i++) {
                final int channelIndex = i;
                TextView item = menuItem(compactTitle(group.channels[i].name));
                item.setOnClickListener(v -> {
                    dismissFolderPanel();
                    String url = group.channels[channelIndex].sourceUrl(0);
                    if (url != null && url.startsWith("webview://")) {
                        callback.onOpenBookmark(url.substring("webview://".length()),
                                group.channels[channelIndex].name, group.title, true);
                    } else callback.onChannel(groupIndex, channelIndex);
                });
                list.addView(item, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, dp(40)));
            }
        }
    }

    private void showWebFolder(View anchor, final WebBookmarkStore.Node folder) {
        LinearLayout list = showFolderPanel(anchor, compactTitle(folder.title), 286, 360);
        if (list != null) renderWebFolderPanel(list, folder);
    }

    private void renderWebFolderPanel(LinearLayout list,
            final WebBookmarkStore.Node folder) {
        list.removeAllViews();
        if (folderTitle != null) folderTitle.setText(compactTitle(folder.title));
        WebBookmarkStore.Node parent = bookmarkStore.parentOf(folder);
        TextView back = menuItem("‹  " + (parent == null ? "网页收藏" : parent.title));
        back.setTextColor(0xff1967d2);
        back.setOnClickListener(v -> {
            if (parent == null) dismissFolderPanel();
            else renderWebFolderPanel(list, parent);
        });
        list.addView(back, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(38)));

        for (final WebBookmarkStore.Node node
                : new ArrayList<WebBookmarkStore.Node>(folder.children)) {
            LinearLayout item = node.folder
                    ? bookmarkItem(node.title, BrowserIconView.FOLDER, 0xff5f6368)
                    : bookmarkItem(node.title, bookmarkStore.icon(node),
                            BrowserIconView.GLOBE, 0xff5f6368);
            item.setPadding(dp(10), 0, dp(8), 0);
            item.setContentDescription((node.folder ? "网页文件夹 " : "网页收藏 ") + node.title);
            item.setOnClickListener(v -> {
                if (node.folder) renderWebFolderPanel(list, node);
                else {
                    dismissFolderPanel();
                    callback.onOpenBookmark(node.url, node.title, folder.title, true);
                }
            });
            attachBookmarkMouseDrag(item, node);
            attachBookmarkContext(item, node,
                    () -> renderWebFolderPanel(list, folder));
            attachBookmarkDropTarget(item, node,
                    () -> renderWebFolderPanel(list, folder));
            list.addView(item, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(40)));
        }
        TextView create = menuItem("＋  新建子文件夹");
        create.setTextColor(0xff1967d2);
        create.setOnClickListener(v -> {
            dismissFolderPanel();
            promptFolderName(folder, null);
        });
        list.addView(create, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(38)));
    }

    private LinearLayout showFolderPanel(View anchor, String title, int widthDp, int heightDp) {
        dismissFolderPanel();
        View rootView = getRootView().findViewById(R.id.root);
        if (!(rootView instanceof ViewGroup)) return null;
        final ViewGroup host = (ViewGroup) rootView;
        final FrameLayout layer = new BrowserPopupLayer(getContext());
        folderLayer = layer;

        final LinearLayout panel = new LinearLayout(getContext());
        folderPanel = panel;
        panel.setOrientation(VERTICAL);
        panel.setClickable(true);
        panel.setBackgroundDrawable(roundRect(0xfff8f9fa, 10));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(9));

        LinearLayout header = row();
        header.setPadding(dp(12), 0, dp(3), 0);
        header.setBackgroundDrawable(roundRect(0xffe9edf3, 10));
        folderTitle = new TextView(getContext());
        folderTitle.setText(compactTitle(title));
        folderTitle.setSingleLine(true);
        folderTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        folderTitle.setTextColor(0xff202124);
        folderTitle.setTextSize(scaledSp(13f));
        folderTitle.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(folderTitle, new LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));
        BrowserIconView close = iconAction(BrowserIconView.CLOSE, "关闭文件夹列表",
                v -> dismissFolderPanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), LayoutParams.MATCH_PARENT));
        panel.addView(header, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(38)));

        ScrollView scroll = new ScrollView(getContext());
        folderBody = new LinearLayout(getContext());
        folderBody.setOrientation(VERTICAL);
        folderBody.setPadding(dp(6), dp(4), dp(6), dp(6));
        scroll.addView(folderBody, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams layerParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        host.addView(layer, layerParams);
        int width = dp(widthDp), height = dp(heightDp), gap = dp(8);
        int[] hostLocation = new int[2];
        int[] anchorLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        anchor.getLocationOnScreen(anchorLocation);
        int anchorLeft = anchorLocation[0] - hostLocation[0];
        int anchorRight = anchorLeft + anchor.getWidth();
        int anchorBottom = anchorLocation[1] - hostLocation[1] + anchor.getHeight();
        int x = anchorRight + gap;
        if (x + width > host.getWidth() - gap) x = anchorLeft - width - gap;
        x = Math.max(gap, Math.min(x, Math.max(gap, host.getWidth() - width - gap)));
        int y = anchorBottom + gap;
        if (y + height > host.getHeight() - gap) y = host.getHeight() - height - gap;
        y = Math.max(gap, y);
        final FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(width, height);
        panelParams.leftMargin = x;
        panelParams.topMargin = y;
        layer.addView(panel, panelParams);

        final float[] drag = new float[4];
        header.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                drag[0] = event.getRawX();
                drag[1] = event.getRawY();
                drag[2] = panelParams.leftMargin;
                drag[3] = panelParams.topMargin;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                int nextX = Math.round(drag[2] + event.getRawX() - drag[0]);
                int nextY = Math.round(drag[3] + event.getRawY() - drag[1]);
                panelParams.leftMargin = Math.max(0,
                        Math.min(nextX, Math.max(0, layer.getWidth() - panel.getWidth())));
                panelParams.topMargin = Math.max(0,
                        Math.min(nextY, Math.max(0, layer.getHeight() - panel.getHeight())));
                panel.setLayoutParams(panelParams);
                return true;
            }
            return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        });
        layer.setOnClickListener(v -> dismissFolderPanel());
        layer.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissFolderPanel();
                return true;
            }
            return false;
        });
        layer.requestFocus();
        return folderBody;
    }

    // Menus live on the Activity root, not inside the tab's WebView. Changing
    // documents or hiding the browser must explicitly detach these overlays.
    void dismissTransientPanels() {
        dismissFolderPanel();
    }

    private void dismissFolderPanel() {
        FrameLayout layer = folderLayer;
        folderLayer = null;
        folderPanel = null;
        folderBody = null;
        folderTitle = null;
        if (layer != null && layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
    }

    boolean containsNativeContextPoint(float screenX, float screenY) {
        Rect bounds = new Rect();
        if (getVisibility() == VISIBLE && getGlobalVisibleRect(bounds)
                && bounds.contains(Math.round(screenX), Math.round(screenY))) return true;
        LinearLayout panel = folderPanel;
        return panel != null && panel.getGlobalVisibleRect(bounds)
                && bounds.contains(Math.round(screenX), Math.round(screenY));
    }

    boolean scrollBookmarkBarAt(float screenX, float screenY, int distanceX) {
        if (!bookmarkBarVisible || bookmarkScroll.getVisibility() != VISIBLE
                || distanceX == 0) return false;
        Rect bounds = new Rect();
        if (!bookmarkScroll.getGlobalVisibleRect(bounds)
                || !bounds.contains(Math.round(screenX), Math.round(screenY))) return false;
        // Trackpad distance is already the inverse of finger movement. Increasing
        // scrollX therefore moves the bookmark content left when two fingers move left.
        bookmarkScroll.scrollBy(distanceX, 0);
        return true;
    }

    private boolean scrollBookmarkBarWithMouse(MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_SCROLL
                || !bookmarkBarVisible || bookmarkScroll.getVisibility() != VISIBLE) {
            return false;
        }
        float horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        float vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        // Mouse wheels report down as a negative vertical value. Convert it to a
        // positive scrollX so the content moves left and reveals items on the right.
        float direction = Math.abs(horizontal) > 0.001f ? horizontal : -vertical;
        if (Math.abs(direction) <= 0.001f) return false;
        bookmarkScroll.scrollBy(Math.round(direction * dp(48)), 0);
        return true;
    }

    private void showBookmarkBarMenu(View anchor) {
        showMenu(anchor, 210, 0, new String[]{ "新建网页文件夹" },
                new OnClickListener[]{ v -> promptFolderName(null, null) });
    }

    private void attachBookmarkContext(View view, final WebBookmarkStore.Node node,
            final Runnable refreshPopup) {
        view.setOnLongClickListener(v -> {
            showBookmarkContext(v, node, refreshPopup);
            return true;
        });
        if (Build.VERSION.SDK_INT >= 23) {
            view.setOnGenericMotionListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
                        && event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
                    showBookmarkContext(v, node, refreshPopup);
                    return true;
                }
                return false;
            });
        }
    }

    private void showBookmarkContext(View anchor, final WebBookmarkStore.Node node,
            final Runnable refreshPopup) {
        if (node.folder) {
            boolean nested = bookmarkStore.parentOf(node) != null;
            ArrayList<String> labels = new ArrayList<String>();
            ArrayList<OnClickListener> actions = new ArrayList<OnClickListener>();
            labels.add("打开文件夹");
            actions.add(v -> showWebFolder(anchor, node));
            labels.add("新建子文件夹");
            actions.add(v -> promptFolderName(node, null, refreshPopup));
            labels.add("重命名文件夹");
            actions.add(v -> promptFolderName(null, node, refreshPopup));
            if (nested) {
                labels.add("移到收藏栏顶层");
                actions.add(v -> {
                    bookmarkStore.move(node, null, bookmarkStore.roots().size());
                    bookmarksChanged(refreshPopup);
                });
            }
            labels.add("删除文件夹");
            actions.add(v -> confirmFolderRemoval(node, refreshPopup));
            showMenu(anchor, 220, 0, labels.toArray(new String[labels.size()]),
                    actions.toArray(new OnClickListener[actions.size()]));
            return;
        }
        boolean nested = bookmarkStore.parentOf(node) != null;
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<OnClickListener> actions = new ArrayList<OnClickListener>();
        labels.add("打开网页");
        actions.add(v -> openBookmark(node, false));
        labels.add("在新标签打开");
        actions.add(v -> openBookmark(node, true));
        labels.add("重命名");
        actions.add(v -> promptFolderName(null, node, refreshPopup));
        if (nested) {
            labels.add("移到收藏栏顶层");
            actions.add(v -> {
                bookmarkStore.move(node, null, bookmarkStore.roots().size());
                bookmarksChanged(refreshPopup);
            });
        }
        labels.add("删除收藏");
        actions.add(v -> {
            bookmarkStore.remove(node);
            bookmarksChanged(refreshPopup);
        });
        showMenu(anchor, 220, 0, labels.toArray(new String[labels.size()]),
                actions.toArray(new OnClickListener[actions.size()]));
    }

    private void promptFolderName(final WebBookmarkStore.Node parent,
            final WebBookmarkStore.Node rename) {
        promptFolderName(parent, rename, null);
    }

    private void promptFolderName(final WebBookmarkStore.Node parent,
            final WebBookmarkStore.Node rename, final Runnable refreshPopup) {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(rename == null ? "" : rename.title);
        input.setSelectAllOnFocus(true);
        input.setHint(rename != null && !rename.folder ? "网页名称" : "文件夹名称");
        int padding = dp(18);
        FrameLayout holder = new FrameLayout(getContext());
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(input, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(52)));
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(rename == null ? "新建网页文件夹" : "重命名")
                .setView(holder)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = input.getText().toString().trim();
                    if (title.length() == 0) return;
                    if (rename == null) bookmarkStore.addFolder(parent, title);
                    else bookmarkStore.rename(rename, title);
                    dialog.dismiss();
                    bookmarksChanged(refreshPopup);
                }));
        dialog.show();
        input.requestFocus();
    }

    private void confirmFolderRemoval(final WebBookmarkStore.Node folder,
            final Runnable refreshPopup) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除“" + folder.title + "”？")
                .setMessage(folder.children.isEmpty() ? "该文件夹将从网页收藏中删除。"
                        : "文件夹中的收藏和子文件夹也会一起删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    bookmarkStore.remove(folder);
                    bookmarksChanged(refreshPopup);
                })
                .show();
    }

    private void bookmarksChanged(Runnable refreshPopup) {
        render();
        if (refreshPopup != null) refreshPopup.run();
    }

    private void attachBookmarkMouseDrag(final View view, final WebBookmarkStore.Node node) {
        final float[] down = new float[2];
        final boolean[] dragging = new boolean[1];
        view.setOnTouchListener((target, event) -> {
            if (!isPhysicalMouse(event)
                    || (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX();
                down[1] = event.getRawY();
                dragging[0] = false;
                target.setPressed(true);
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && !dragging[0]
                    && Math.hypot(event.getRawX() - down[0], event.getRawY() - down[1]) > dp(6)) {
                dragging[0] = startBookmarkDrag(target, node);
                target.setPressed(false);
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                target.setPressed(false);
                if (!dragging[0]) target.performClick();
                dragging[0] = false;
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                target.setPressed(false);
                dragging[0] = false;
                return true;
            }
            return true;
        });
    }

    @SuppressWarnings("deprecation")
    private boolean startBookmarkDrag(View source, WebBookmarkStore.Node node) {
        ClipData clip = ClipData.newPlainText("nTv web bookmark",
                node.folder ? node.title : node.url);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(source);
        boolean started = Build.VERSION.SDK_INT >= 24
                ? source.startDragAndDrop(clip, shadow, node, 0)
                : source.startDrag(clip, shadow, node, 0);
        if (started) source.setAlpha(.55f);
        return started;
    }

    private void attachBookmarkDropTarget(final View view, final WebBookmarkStore.Node target,
            final Runnable refreshPopup) {
        view.setOnDragListener((dropView, event) -> {
            Object state = event.getLocalState();
            if (!(state instanceof WebBookmarkStore.Node)) return false;
            WebBookmarkStore.Node moving = (WebBookmarkStore.Node) state;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // The source must also accept the drag lifecycle so Android sends
                    // ACTION_DRAG_ENDED and its temporary translucency can be restored.
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    dropView.setScaleX(1.04f);
                    dropView.setScaleY(1.04f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    dropView.setScaleX(1f);
                    dropView.setScaleY(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    if (moving == target) {
                        dropView.setScaleX(1f);
                        dropView.setScaleY(1f);
                        return false;
                    }
                    boolean moved;
                    if (target.folder) {
                        moved = bookmarkStore.move(moving, target, target.children.size());
                    } else {
                        WebBookmarkStore.Node parent = bookmarkStore.parentOf(target);
                        ArrayList<WebBookmarkStore.Node> siblings = parent == null
                                ? bookmarkStore.roots() : parent.children;
                        int index = siblings.indexOf(target);
                        if (event.getX() > dropView.getWidth() / 2f) index++;
                        moved = bookmarkStore.move(moving, parent, Math.max(0, index));
                    }
                    dropView.setScaleX(1f);
                    dropView.setScaleY(1f);
                    if (moved) {
                        render();
                        if (refreshPopup != null) refreshPopup.run();
                    }
                    return moved;
                case DragEvent.ACTION_DRAG_ENDED:
                    dropView.setAlpha(1f);
                    dropView.setScaleX(1f);
                    dropView.setScaleY(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private boolean handleRootBookmarkDrop(DragEvent event) {
        Object state = event.getLocalState();
        if (!(state instanceof WebBookmarkStore.Node)) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) return true;
        if (event.getAction() == DragEvent.ACTION_DROP) {
            for (int i = 0; i < bookmarkStrip.getChildCount(); i++) {
                View child = bookmarkStrip.getChildAt(i);
                if (event.getX() >= child.getLeft() && event.getX() < child.getRight()) return false;
            }
            WebBookmarkStore.Node node = (WebBookmarkStore.Node) state;
            boolean moved = bookmarkStore.move(node, null, bookmarkStore.roots().size());
            if (moved) render();
            return moved;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) render();
        return true;
    }

    private static boolean isPhysicalMouse(MotionEvent event) {
        return event != null && event.getDeviceId() != 0
                && (event.getSource() & android.view.InputDevice.SOURCE_MOUSE)
                        == android.view.InputDevice.SOURCE_MOUSE;
    }

    private TextView menuItem(String label) {
        TextView item = new TextView(getContext());
        item.setText(label);
        item.setTextSize(scaledSp(13f));
        item.setTextColor(0xff202124);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), 0, dp(12), 0);
        item.setClickable(true);
        item.setFocusable(true);
        return item;
    }

    private void setBookmarkBarVisible(boolean visible) {
        if (bookmarkBarVisible == visible) return;
        bookmarkBarVisible = visible;
        preferences().edit().putBoolean(BOOKMARK_BAR, visible).apply();
        updateMoreButtonPlacement();
        renderBookmarkBar();
        callback.onToolbarHeightChanged(heightDp());
    }

    private void updateMoreButtonPlacement() {
        ViewGroup parent = (ViewGroup) moreButton.getParent();
        if (parent != null) parent.removeView(moreButton);
        bookmarkRow.setVisibility(bookmarkBarVisible ? VISIBLE : GONE);
        // Hiding bookmarks must not hide the only way to show them again.
        (bookmarkBarVisible ? bookmarkRow : tabsRow).addView(moreButton, fixed(36));
    }

    private void setChannelFoldersVisible(boolean visible) {
        if (channelFoldersVisible == visible) return;
        channelFoldersVisible = visible;
        preferences().edit().putBoolean(CHANNEL_FOLDERS, visible).apply();
        renderBookmarkBar();
    }

    private boolean isAdBlockEnabled(Tab tab) {
        String host = tab == null ? "" : domain(tab.url);
        return host.length() == 0 || !adBlockDisabledDomains.contains(host);
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(getContext());
        result.setOrientation(HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private BrowserIconView iconAction(int icon, String description, OnClickListener listener) {
        BrowserIconView button = smallIconAction(icon, description);
        button.setOnClickListener(listener);
        return button;
    }

    private BrowserIconView smallIconAction(int icon, String description) {
        BrowserIconView button = new BrowserIconView(getContext(), icon);
        button.setContentDescription(description);
        return button;
    }

    private LayoutParams fixed(int widthDp) {
        return new LayoutParams(dp(widthDp), LayoutParams.MATCH_PARENT);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void openBookmark(WebBookmarkStore.Node node, boolean newTab) {
        WebBookmarkStore.Node parent = bookmarkStore.parentOf(node);
        callback.onOpenBookmark(node.url, node.title, parent == null ? "网页收藏" : parent.title, newTab);
    }

    private void loadPinnedTabs() {
        String value = preferences().getString(PINNED_TABS, "[]");
        try {
            JSONArray stored = new JSONArray(value);
            for (int i = 0; i < stored.length() && tabs.size() < MAX_TABS; i++) {
                JSONObject item = stored.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (!isWebUrl(url)) continue;
                Tab tab = new Tab(nextId++, url,
                        displayTitle(item.optString("title", ""), url), true);
                tab.icon = bookmarkStore.iconForUrl(url);
                tab.bookmarkTitle = item.optString("bookmarkTitle", "");
                tab.bookmarkGroup = item.optString("bookmarkGroup", "");
                tab.bookmarkUrl = item.optString("bookmarkUrl", "");
                tabs.add(tab);
            }
        } catch (Exception ignored) { }
    }

    private void savePinnedTabs() {
        JSONArray stored = new JSONArray();
        for (Tab tab : tabs) {
            if (!tab.pinned) continue;
            try {
                stored.put(new JSONObject().put("url", tab.url).put("title", tab.title)
                        .put("bookmarkTitle", tab.bookmarkTitle).put("bookmarkGroup", tab.bookmarkGroup)
                        .put("bookmarkUrl", tab.bookmarkUrl));
            } catch (Exception ignored) { }
        }
        preferences().edit().putString(PINNED_TABS, stored.toString()).apply();
    }

    private SharedPreferences preferences() {
        return getContext().getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String domain(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String canonicalPage(String url) {
        try {
            return Uri.parse(safe(url)).buildUpon().fragment(null).build().toString();
        } catch (RuntimeException ignored) {
            return safe(url);
        }
    }

    private static String displayTitle(String title, String url) {
        String value = safe(title).trim();
        if (value.length() > 0) return value;
        try {
            Uri parsed = Uri.parse(url);
            if (parsed.getHost() != null) return parsed.getHost();
        } catch (RuntimeException ignored) { }
        return "新标签";
    }

    private static boolean isWebUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String importedBookmarkGroupTitle(String value) {
        String title = compactTitle(value);
        String[] prefixes = { "Chrome书签 / ", "Chrome 书签 / " };
        for (String prefix : prefixes) {
            if (title.startsWith(prefix)) {
                String clean = title.substring(prefix.length()).trim();
                return clean.length() == 0 ? "Chrome书签" : clean;
            }
        }
        return title;
    }

    private static String compactTitle(String value) { return safe(value).trim(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private float scaledSp(float value) { return value * interfaceScale; }
    private int dp(int value) { return Math.round(value * density * interfaceScale); }
}
