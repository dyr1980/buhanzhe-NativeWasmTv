package xiao.bu.tv;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import okhttp3.Dns;

/**
 * Resolves hostnames through the platform resolver.
 *
 * <p>Old Android releases only expose {@link InetAddress#getAllByName(String)},
 * which is what {@link Dns#SYSTEM} already wraps. Keeping a dedicated type lets
 * the network client pick "system" and "public" modes explicitly without
 * depending on the private lightweight core.</p>
 */
final class LegacySystemDns implements Dns {
    private LegacySystemDns() {
    }

    static Dns create() {
        return new LegacySystemDns();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null || hostname.length() == 0) {
            throw new UnknownHostException("Empty hostname");
        }
        InetAddress[] addresses = InetAddress.getAllByName(hostname);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(hostname);
        }
        return Arrays.asList(addresses);
    }
}
