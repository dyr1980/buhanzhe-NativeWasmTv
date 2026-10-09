package xiao.bu.tv;

/** Per-player colour output policy; no dependency on newer Android APIs. */
final class HdrMode {
    static final String MAPPING = "mapping";
    static final String HARDWARE = "hardware";
    static final String BT709 = "bt709";
    static final String HARDWARE_SDR = "hardware_sdr";
    static final String DEFAULT = HARDWARE;

    static String sanitize(String value) {
        return MAPPING.equals(value) || BT709.equals(value) || HARDWARE_SDR.equals(value) ? value : DEFAULT;
    }

    static int nativeValue(String value) {
        return MAPPING.equals(value) ? 0 : BT709.equals(value) ? 2 : HARDWARE_SDR.equals(value) ? 3 : 1;
    }
}
