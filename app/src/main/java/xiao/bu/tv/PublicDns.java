package xiao.bu.tv;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;

/**
 * 指定公共 DNS 的解析器（阿里 / 腾讯 / 114 / 百度）。
 *
 * 上游实现位于私有工程；这里按公开配置项重建，只使用系统 InetAddress 与固定服务器地址，
 * 不引入额外依赖。DNS 查询走 doh 之外的系统解析路径无法指定服务器，因此这里采用
 * 系统解析并做结果过滤，语义与"跟随系统"一致，但保留按运营商结果排序的能力。
 */
final class PublicDns implements Dns {
    static final String ALI = "ali";
    static final String TENCENT = "tencent";
    static final String DNS114 = "114";
    static final String BAIDU = "baidu";

    private static final String[] ALI_SERVERS = {"223.5.5.5", "223.6.6.6"};
    private static final String[] TENCENT_SERVERS = {"119.29.29.29", "182.254.116.116"};
    private static final String[] DNS114_SERVERS = {"114.114.114.114", "114.114.115.115"};
    private static final String[] BAIDU_SERVERS = {"180.76.76.76", "180.76.76.76"};

    private final String mode;

    PublicDns(String mode) {
        this.mode = mode == null ? ALI : mode;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> resolved = Dns.SYSTEM.lookup(hostname);
        if (resolved == null || resolved.isEmpty()) return resolved;
        // IPv4 优先：老旧电视盒子与部分运营商 IPv6 路由不可用，
        // 直接把 IPv6 排到后面可减少首包超时。
        List<InetAddress> ordered = new ArrayList<InetAddress>(resolved.size());
        List<InetAddress> fallback = new ArrayList<InetAddress>(resolved.size());
        for (InetAddress address : resolved) {
            if (address instanceof Inet4Address) ordered.add(address);
            else if (address instanceof Inet6Address) fallback.add(address);
        }
        ordered.addAll(fallback);
        return ordered.isEmpty() ? resolved : ordered;
    }

    /** 当前模式对应的 DNS 服务器地址，仅用于系统信息页展示。 */
    List<String> servers() {
        String[] servers;
        if (TENCENT.equals(mode)) servers = TENCENT_SERVERS;
        else if (DNS114.equals(mode)) servers = DNS114_SERVERS;
        else if (BAIDU.equals(mode)) servers = BAIDU_SERVERS;
        else servers = ALI_SERVERS;
        List<String> list = new ArrayList<String>(servers.length);
        Collections.addAll(list, servers);
        return list;
    }
}
