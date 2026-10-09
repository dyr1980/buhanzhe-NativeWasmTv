package xiao.bu.tv;

/** The decoder's Main10 support alone cannot establish an HDR display path. */
final class HdrDisplayPolicy {
    // Display.HdrCapabilities constants, kept here without an API 24 class dependency.
    private static final int HDR10 = 2, HLG = 3, HDR10_PLUS = 4;

    static boolean supportsTransfer(int transfer, int[] hdrTypes) {
        if (hdrTypes == null) return false;
        for (int type : hdrTypes) {
            if (transfer == 6 && (type == HDR10 || type == HDR10_PLUS)) return true;
            if (transfer == 7 && type == HLG) return true;
        }
        return false;
    }

    static boolean passthrough(int mode, int transfer, int[] hdrTypes) {
        return mode == 1 && supportsTransfer(transfer, hdrTypes);
    }

    static boolean decoderToneMapping(int mode, int sdk) {
        return mode != 3 && sdk >= 31;
    }

    private HdrDisplayPolicy() {}
}
