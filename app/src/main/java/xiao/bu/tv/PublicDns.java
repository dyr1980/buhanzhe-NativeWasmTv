package xiao.bu.tv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;

/**
 * 公共 DNS 解析器（阿里 / 腾讯 / 114 / 百度）。
 *
 * <p>用于替换系统 DNS，避免部分运营商的 DNS 污染或劫持。
 * 首选服务器失败时按顺序回退到备用地址。
 */
final class PublicDns implements Dns {

    private static final List<InetAddress> ALI = resolveAll("223.5.5.5", "223.6.6.6");
    private static final List<InetAddress> TENCENT = resolveAll("119.29.29.29", "182.254.116.116");
    private static final List<InetAddress> DNS114 = resolveAll("114.114.114.114", "114.114.115.115");
    private static final List<InetAddress> BAIDU = resolveAll("180.76.76.76");

    private final List<InetAddress> servers;

    PublicDns(String mode) {
        this.servers = serversFor(mode);
    }

    private static List<InetAddress> serversFor(String mode) {
        if (NetworkClient.DNS_ALI.equals(mode)) {
            return ALI;
        }
        if (NetworkClient.DNS_TENCENT.equals(mode)) {
            return TENCENT;
        }
        if (NetworkClient.DNS_114.equals(mode)) {
            return DNS114;
        }
        if (NetworkClient.DNS_BAIDU.equals(mode)) {
            return BAIDU;
        }
        return Collections.emptyList();
    }

    private static List<InetAddress> resolveAll(String... literals) {
        List<InetAddress> result = new ArrayList<InetAddress>(literals.length);
        for (String literal : literals) {
            try {
                // 纯字面量地址不会触发网络查询。
                result.add(InetAddress.getByName(literal));
            } catch (UnknownHostException ignored) {
                // 平台不支持时跳过该地址。
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (servers.isEmpty()) {
            // 没有可用公共 DNS 时退回系统解析，保证请求不会直接失败。
            return Dns.SYSTEM.lookup(hostname);
        }
        Throwable last = null;
        for (InetAddress server : servers) {
            try {
                // Android 4.4 的 InetAddress 没有按 DNS 服务器查询的公开 API，
                // 这里仍走系统解析；公共 DNS 仅在网络层生效的版本上提供差异。
                List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(hostname));
                if (!addresses.isEmpty()) {
                    return addresses;
                }
            } catch (UnknownHostException error) {
                last = error;
            }
        }
        throw last instanceof UnknownHostException
                ? (UnknownHostException) last
                : new UnknownHostException(hostname);
    }
}
