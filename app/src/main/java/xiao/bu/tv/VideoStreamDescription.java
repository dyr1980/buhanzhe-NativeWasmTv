package xiao.bu.tv;

/** Original video stream, independent of HDR mapping or decoder output depth. */
final class VideoStreamDescription {
    static final VideoStreamDescription UNKNOWN = new VideoStreamDescription(0L);
    final int bitDepth;
    final String dynamicRange;
    final String colorPrimaries;
    final String colorSpace;
    final String colorTransfer;

    VideoStreamDescription(long snapshot) {
        bitDepth = (int) (snapshot & 31);
        int primaries = (int) ((snapshot >>> 5) & 31);
        int transfer = (int) ((snapshot >>> 10) & 31);
        int matrix = (int) ((snapshot >>> 15) & 31);
        boolean available = (snapshot & (1L << 24)) != 0;
        boolean dolbyVision = (snapshot & (1L << 22)) != 0;
        dynamicRange = available && (dolbyVision || transfer == 16 || transfer == 18)
                ? "HDR" : available && (transfer == 1 || transfer >= 4 && transfer <= 15 || transfer == 17)
                ? "SDR" : "未知";
        colorPrimaries = available ? colorName(primaries, false) : "未知";
        colorSpace = available ? colorName(matrix, true) : "未知";
        colorTransfer = available ? transferName(transfer) : "未知";
    }

    String rangeLabel() {
        return "未知".equals(dynamicRange) ? "HDR/SDR未知" : dynamicRange;
    }

    String debugLabel() {
        String colors = "未知".equals(colorPrimaries) ? colorSpace : colorPrimaries;
        if (!"未知".equals(colorSpace) && !colorSpace.equals(colors)) colors += "/" + colorSpace;
        return rangeLabel() + " · " + (bitDepth > 0 ? bitDepth + "bit" : "色深未知")
                + " · " + ("未知".equals(colors) ? "色彩空间未知" : colors)
                + " · " + ("未知".equals(colorTransfer) ? "传递函数未知" : colorTransfer);
    }

    private static String colorName(int value, boolean matrix) {
        switch (value) {
            case 0: return matrix ? "RGB" : "未知";
            case 1: return "BT.709";
            case 4: return matrix ? "FCC" : "BT.470M";
            case 5: return "BT.601-625";
            case 6: return "BT.601-525";
            case 7: return "SMPTE240M";
            case 8: return matrix ? "YCgCo" : "Film";
            case 9: return "BT.2020";
            case 10: return matrix ? "BT.2020-CL" : "XYZ";
            case 11: return matrix ? "SMPTE2085" : "DCI-P3";
            case 12: return matrix ? "Chroma-NCL" : "Display-P3";
            case 13: return matrix ? "Chroma-CL" : "未知";
            case 14: return matrix ? "ICtCp" : "未知";
            case 22: return !matrix ? "EBU3213" : "未知";
            default: return "未知";
        }
    }

    private static String transferName(int value) {
        switch (value) {
            case 1: return "BT.709";
            case 4: return "Gamma2.2";
            case 5: return "Gamma2.8";
            case 6: return "SMPTE170M";
            case 7: return "SMPTE240M";
            case 8: return "Linear";
            case 9: return "Log100";
            case 10: return "Log316";
            case 11: return "IEC61966-2-4";
            case 12: return "BT.1361";
            case 13: return "sRGB";
            case 14: return "BT.2020-10";
            case 15: return "BT.2020-12";
            case 16: return "PQ";
            case 17: return "SMPTE428";
            case 18: return "HLG";
            default: return "未知";
        }
    }
}
