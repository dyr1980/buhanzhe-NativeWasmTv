package xiao.bu.tv;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Dns;

/**
 * DNS 解析结果进程级缓存。
 *
 * 上游把这份实现放在私有工程里，这里按公开行为重建：同一进程内相同主机名只解析一次，
 * 并发请求共享第一次结果，解析失败不写缓存。失效由 {@link #invalidate(String)} 显式触发。
 */
final class ProcessDns implements Dns {
    private static final ConcurrentHashMap<String, List<InetAddress>> CACHE =
            new ConcurrentHashMap<String, List<InetAddress>>();

    private final Dns upstream;

    ProcessDns(Dns upstream) {
        this.upstream = upstream;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null) throw new UnknownHostException("hostname == null");
        String key = hostname.toLowerCase(java.util.Locale.ROOT);
        List<InetAddress> cached = CACHE.get(key);
        if (cached != null) return cached;
        // ConcurrentHashMap 保证同一 key 的 putIfAbsent 只有一个赢家，
        // 但仍可能重复解析一次；用 computeIfAbsent 让并发只解析一次。
        try {
            return CACHE.computeIfAbsent(key, name -> {
                try {
                    return upstream.lookup(name);
                } catch (UnknownHostException error) {
                    throw new ResolutionFailure(error);
                }
            });
        } catch (ResolutionFailure failure) {
            throw failure.cause;
        }
    }

    void invalidate(String hostname) {
        if (hostname != null) CACHE.remove(hostname.toLowerCase(java.util.Locale.ROOT));
    }

    static void invalidateAll() {
        CACHE.clear();
    }

    static List<InetAddress> cached(String hostname) {
        return hostname == null ? null : CACHE.get(hostname.toLowerCase(java.util.Locale.ROOT));
    }

    private static final class ResolutionFailure extends RuntimeException {
        final UnknownHostException cause;

        ResolutionFailure(UnknownHostException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
