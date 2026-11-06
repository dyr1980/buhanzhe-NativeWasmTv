package xiao.bu.tv;

/**
 * MP4 初始化段探测（占位实现）。
 *
 * 上游实现在私有工程里，用于判断某个 URL 返回的是不是"仅含 init 段"的 MP4，
 * 以此决定嗅探阶段是否值得直接交给播放器。公开检出缺少该实现时，
 * 保守策略是"不是 MP4 / 不是纯 init 段"，让调用方继续走原有的完整探测流程，
 * 不会因此误判可播放。
 */
final class LiteMp4Probe {
    private LiteMp4Probe() { }

    /** ISO BMFF 的文件类型盒子：偏移 4 起为 "ftyp"。 */
    static boolean isMp4(byte[] bytes, int length) {
        if (bytes == null || length < 12) return false;
        return bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    /** 缓冲区内是否只包含初始化信息（没有 moof/mdat 等媒体数据）。 */
    static boolean initializationOnly(byte[] bytes, int length, boolean complete) {
        if (bytes == null || length <= 0 || !complete) return false;
        // 逐盒扫描顶层 box：出现 moof / mdat 即说明已含媒体数据。
        int offset = 0;
        while (offset + 8 <= length) {
            long size = ((long) (bytes[offset] & 0xFF) << 24)
                    | ((long) (bytes[offset + 1] & 0xFF) << 16)
                    | ((long) (bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
            String type = new String(bytes, offset + 4, 4, java.nio.charset.Charset.forName("US-ASCII"));
            if ("moof".equals(type) || "mdat".equals(type)) return false;
            if (size == 1) {
                if (offset + 16 > length) return false;
                size = 0;
                for (int i = 0; i < 8; i++) size = (size << 8) | (bytes[offset + 8 + i] & 0xFF);
            } else if (size == 0) {
                return true;
            }
            if (size < 8) return false;
            offset += (int) Math.min(size, Integer.MAX_VALUE);
        }
        return true;
    }
}
