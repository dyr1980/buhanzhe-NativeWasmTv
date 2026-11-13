package xiao.bu.tv;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * 私有轻量内核的 Cookie 仓库（占位实现）。
 *
 * <p>上游公开代码（SharedWebProfile、ChannelHealth、NetworkClient 等）引用了
 * 本类，但类本身属于作者未公开的部分。这里提供一个真实可用的内存
 * Cookie 仓库：PROFILE 是全局单例，snapshot() 返回当前快照，
 * 两者都实现 okhttp3.CookieJar，可直接挂到 OkHttpClient 上。</p>
 *
 * <p>轻量内核被禁用（BUNDLED_LITE_BROWSER=false）时，写入方只有
 * SharedWebProfile 的 RPC 通道，通常为空表，行为等同无 Cookie，
 * 不影响网页源播放。</p>
 */
final class LiteCookies implements CookieJar {

    /** 全局单例（上游以 LiteCookies.PROFILE.xxx 的形式调用）。 */
    static final LiteCookies PROFILE = new LiteCookies();

    private final Map<String, List<Cookie>> store = new ConcurrentHashMap<>();

    /** 返回当前仓库快照（占位实现返回同一实例，内容一致）。 */
    static LiteCookies snapshot() {
        return PROFILE;
    }

    /** 上游在进程启动/复用时调用；内存仓库无需初始化。 */
    void initialize(Context context) {
    }

    /** 某个页面当前持有的 Cookie（按 host 匹配）。 */
    List<Cookie> forHost(HttpUrl page) {
        return loadForRequest(page);
    }

    /** 某个页面待删除的 Cookie（占位：总是空表）。 */
    List<Cookie> deletions(HttpUrl page) {
        return Collections.emptyList();
    }

    /** 上游用于同步网页可见性的回调（占位：不做任何事）。 */
    void observe(HttpUrl page, String cookies, String visible) {
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        save(url, cookies);
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        if (url == null) {
            return Collections.emptyList();
        }
        List<Cookie> stored = store.get(url.host());
        if (stored == null || stored.isEmpty()) {
            return Collections.emptyList();
        }
        List<Cookie> matched = new ArrayList<>();
        for (Cookie cookie : stored) {
            if (cookie.matches(url)) {
                matched.add(cookie);
            }
        }
        return matched;
    }

    private void save(HttpUrl url, List<Cookie> cookies) {
        String host = url.host();
        List<Cookie> merged = new ArrayList<>();
        List<Cookie> existing = store.get(host);
        if (existing != null) {
            merged.addAll(existing);
        }
        for (Cookie cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            merged.removeIf(candidate -> candidate.name().equals(cookie.name())
                    && candidate.path().equals(cookie.path())
                    && candidate.domain().equals(cookie.domain()));
            merged.add(cookie);
        }
        store.put(host, merged);
    }
}
