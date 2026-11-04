package xiao.bu.tv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Dns;

/**
 * DNS 结果缓存包装器。
 *
 * <p>切换 DNS 设置时会清掉对应主机的缓存，让新设置立即生效；
 * 其余情况直接复用上次的解析结果，减少解析次数。
 */
final class ProcessDns implements Dns {

    private final Dns delegate;
    private final ConcurrentHashMap<String, List<InetAddress>> cache =
            new ConcurrentHashMap<String, List<InetAddress>>();

    ProcessDns(Dns delegate) {
        this.delegate = delegate == null ? Dns.SYSTEM : delegate;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> cached = cache.get(hostname);
        if (cached != null) {
            return cached;
        }
        List<InetAddress> resolved = delegate.lookup(hostname);
        if (resolved != null && !resolved.isEmpty()) {
            cache.put(hostname, resolved);
        }
        return resolved;
    }

    /** 丢弃指定主机的缓存；传 null 表示全部清空。 */
    void invalidate(String hostname) {
        if (hostname == null) {
            cache.clear();
        } else {
            cache.remove(hostname);
        }
    }
}
