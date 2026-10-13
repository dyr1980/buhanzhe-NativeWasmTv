package xiao.bu.tv;

import java.net.URI;
import java.net.URISyntaxException;

/** Escape raw filename characters without changing signed queries or IPv6 authorities. */
final class HlsUri {
    static URI resolve(URI base, String reference) {
        String value = reference;
        for (int attempt = 0; attempt <= reference.length(); attempt++) {
            try {
                return base.resolve(new URI(value));
            } catch (URISyntaxException error) {
                String reason = error.getReason();
                int index = error.getIndex();
                if (index < 0 || !(reason.startsWith("Illegal character in path")
                        || reason.startsWith("Illegal character in query")
                        || reason.startsWith("Illegal character in fragment"))) {
                    throw new IllegalArgumentException(error);
                }
                char character = value.charAt(index);
                String escaped = "%" + Integer.toHexString(character).toUpperCase(java.util.Locale.US);
                if (character < 16) escaped = "%0" + Integer.toHexString(character);
                value = value.substring(0, index) + escaped + value.substring(index + 1);
            }
        }
        throw new IllegalArgumentException("Invalid HLS reference: " + reference);
    }
}
