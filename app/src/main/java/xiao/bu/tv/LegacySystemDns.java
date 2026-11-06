package xiao.bu.tv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;

/**
 * 系统解析器（走 JVM / Android 平台默认解析，保留运营商就近调度）。
 *
 * 上游实现位于私有工程。这里直接委托给 okhttp 的 Dns.SYSTEM，
 * 目的只是让 NetworkClient 能在这个分支上按"跟随系统"策略工作。
 */
final class LegacySystemDns implements Dns {
    private static final LegacySystemDns INSTANCE = new LegacySystemDns();

    private LegacySystemDns() { }

    static Dns create() {
        return INSTANCE;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        return Dns.SYSTEM.lookup(hostname);
    }
}
