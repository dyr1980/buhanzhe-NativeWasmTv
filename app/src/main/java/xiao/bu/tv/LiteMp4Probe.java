package xiao.bu.tv;

/**
 * Detects whether a byte prefix is an ISO base media file (MP4 / fMP4) that can
 * be handed to the native player without downloading the whole body.
 *
 * <p>Two questions matter when sniffing a web page: "does this look like MP4 at
 * all" and "does the prefix we already have contain everything the player needs
 * to start (ftyp + moov), so we can stop reading".</p>
 */
final class LiteMp4Probe {
    private static final int BOX_HEADER_BYTES = 8;

    private LiteMp4Probe() {
    }

    /** True when {@code length} bytes start with a plausible ISO-BMFF header. */
    static boolean isMp4(byte[] data, int length) {
        if (data == null || length < BOX_HEADER_BYTES) return false;
        int offset = 0;
        // Skip anything before the first top-level box (some servers prepend junk).
        while (offset + BOX_HEADER_BYTES <= length) {
            long size = readUint32(data, offset);
            String type = boxType(data, offset + 4);
            if ("ftyp".equals(type) || "styp".equals(type) || "moov".equals(type)
                    || "moof".equals(type) || "mdat".equals(type) || "free".equals(type)
                    || "skip".equals(type) || "wide".equals(type)) {
                return true;
            }
            if (size <= 0L) return false;
            if (size > Integer.MAX_VALUE) return false;
            offset += (int) size;
            if (offset <= 0) return false;
        }
        return false;
    }

    /**
     * True when the prefix already holds the boxes a player needs to initialize.
     *
     * <p>For plain MP4 that is {@code ftyp} plus {@code moov}. For fragmented MP4
     * the init segment is {@code ftyp} plus {@code moov} as well, with
     * {@code moof}/{@code mdat} following later; those may also appear inside the
     * prefix when the file is small, which is equally acceptable.</p>
     */
    static boolean initializationOnly(byte[] data, int length, boolean complete) {
        if (data == null || length < BOX_HEADER_BYTES) return false;
        boolean hasFtyp = false;
        boolean hasMoov = false;
        boolean hasMediaData = false;
        int offset = 0;
        while (offset + BOX_HEADER_BYTES <= length) {
            long size = readUint32(data, offset);
            String type = boxType(data, offset + 4);
            int header = BOX_HEADER_BYTES;
            if (size == 1L) {
                // 64-bit largesize follows the type field.
                if (offset + 16 > length) break;
                size = readUint64(data, offset + 8);
                header = 16;
            } else if (size == 0L) {
                // A zero size means "until end of file".
                size = length - offset;
            }
            if (size < header) break;
            if ("ftyp".equals(type)) hasFtyp = true;
            else if ("moov".equals(type)) {
                // moov must be fully inside the prefix to be usable.
                if (offset + size > length) return false;
                hasMoov = true;
            } else if ("mdat".equals(type) || "moof".equals(type)) {
                hasMediaData = true;
            }
            if (size > length - offset) break;
            offset += (int) size;
        }
        if (!hasFtyp) return false;
        if (complete) return true;
        return hasMoov || hasMediaData;
    }

    private static long readUint32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }

    private static long readUint64(byte[] data, int offset) {
        long high = readUint32(data, offset);
        long low = readUint32(data, offset + 4);
        return (high << 32) | (low & 0xffffffffL);
    }

    private static String boxType(byte[] data, int offset) {
        StringBuilder type = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            type.append((char) (data[offset + index] & 0xff));
        }
        return type.toString();
    }
}
