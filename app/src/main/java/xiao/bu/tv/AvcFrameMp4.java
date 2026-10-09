package xiao.bu.tv;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** A single AVC IDR in an MP4. No decoding, audio, platform muxer or whole-TS file. */
final class AvcFrameMp4 {
    private static final int MAX_PES = 2 * 1024 * 1024;
    private static final byte[] MATRIX = ints(0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000);

    static boolean write(byte[] ts, File output, int width, int height) throws IOException {
        int pid = HlsProxyServer.findAvcVideoPid(ts);
        if (pid < 0) return false; // Other codecs keep the platform extraction path.
        Frame frame = new Frame();
        ByteArrayOutputStream pes = new ByteArrayOutputStream(192 * 1024);
        boolean collecting = false;
        for (int offset = 0; offset + 188 <= ts.length; offset += 188) {
            if (ts[offset] != 0x47 || (ts[offset + 1] & 0x80) != 0) continue;
            int packetPid = ((ts[offset + 1] & 31) << 8) | (ts[offset + 2] & 255);
            if (packetPid != pid) continue;
            int payload = HlsProxyServer.payloadOffset(ts, offset);
            if (payload < 0) continue;
            if ((ts[offset + 1] & 0x40) != 0) {
                if (collecting && frame.read(pes.toByteArray())) {
                    writeMp4(output, frame, width, height); return true;
                }
                pes.reset(); collecting = false;
                if (payload + 9 > offset + 188 || ts[payload] != 0
                        || ts[payload + 1] != 0 || ts[payload + 2] != 1) continue;
                payload += 9 + (ts[payload + 8] & 255);
                collecting = true;
            }
            if (collecting && payload < offset + 188) {
                if (pes.size() + offset + 188 - payload > MAX_PES)
                    throw new IOException("视频关键帧过大");
                pes.write(ts, payload, offset + 188 - payload);
            }
        }
        if (collecting && frame.read(pes.toByteArray())) {
            writeMp4(output, frame, width, height); return true;
        }
        throw new IOException("缓存分片尚无完整的 AVC 关键帧，请稍后重试");
    }

    private static final class Frame {
        byte[] sps, pps, sample;
        boolean read(byte[] data) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            boolean key = false;
            for (int at = 0; at + 3 < data.length;) {
                int prefix = startCode(data, at);
                if (prefix == 0) { at++; continue; }
                int start = at + prefix, end = start;
                // Keep the byte scan inline: per-byte method calls are costly on
                // Dalvik, even though no video is being decoded here.
                while (end + 2 < data.length) {
                    if (data[end] == 0 && data[end + 1] == 0
                            && (data[end + 2] == 1 || (data[end + 2] == 0
                            && end + 3 < data.length && data[end + 3] == 1))) break;
                    end++;
                }
                if (end + 2 >= data.length) end = data.length;
                int next = end;
                while (end > start && data[end - 1] == 0) end--;
                if (end > start) {
                    int type = data[start] & 31;
                    if (key && (type == 9 || type == 1)) break;
                    if (type == 7 && end - start >= 4 && end - start < 65536)
                        sps = Arrays.copyOfRange(data, start, end);
                    else if (type == 8 && end - start >= 2 && end - start < 65536)
                        pps = Arrays.copyOfRange(data, start, end);
                    else if (type == 5) {
                        out.writeInt(end - start); out.write(data, start, end - start); key = true;
                    }
                }
                at = next;
            }
            if (!key || sps == null || pps == null) return false;
            sample = bytes.toByteArray(); return true;
        }
    }

    private static int startCode(byte[] data, int at) {
        if (at + 2 < data.length && data[at] == 0 && data[at + 1] == 0) {
            if (data[at + 2] == 1) return 3;
            if (at + 3 < data.length && data[at + 2] == 0 && data[at + 3] == 1) return 4;
        }
        return 0;
    }

    private static void writeMp4(File file, Frame frame, int width, int height) throws IOException {
        if (width < 1 || width > 65535 || height < 1 || height > 65535)
            throw new IOException("视频尺寸无效");
        byte[] ftyp = box("ftyp", ascii("isom"), ints(0x200), ascii("isomiso2avc1mp41"));
        byte[] avcC = box("avcC", new byte[]{1, frame.sps[1], frame.sps[2], frame.sps[3],
                (byte)255, (byte)225}, shorts(frame.sps.length), frame.sps,
                new byte[]{1}, shorts(frame.pps.length), frame.pps);
        byte[] entry = box("avc1", new byte[6], shorts(1), new byte[16], shorts(width, height),
                ints(0x480000, 0x480000, 0), shorts(1), new byte[32], shorts(24, 65535), avcC);
        byte[] stbl = box("stbl",
                box("stsd", ints(0, 1), entry),
                box("stts", ints(0, 1, 1, 40000)),
                box("stsc", ints(0, 1, 1, 1, 1)),
                box("stsz", ints(0, frame.sample.length, 1)),
                box("stco", ints(0, 1, ftyp.length + 8)),
                box("stss", ints(0, 1, 1)));
        byte[] minf = box("minf", box("vmhd", ints(1), new byte[8]),
                box("dinf", box("dref", ints(0, 1), box("url ", ints(1)))), stbl);
        byte[] mdia = box("mdia",
                box("mdhd", ints(0, 0, 0, 1000000, 40000), shorts(0x55c4, 0)),
                box("hdlr", ints(0, 0), ascii("vide"), new byte[12], ascii("Video\0")), minf);
        byte[] trak = box("trak",
                box("tkhd", ints(7, 0, 0, 1, 0, 40), new byte[16], MATRIX,
                        ints(width << 16, height << 16)), mdia);
        byte[] moov = box("moov",
                box("mvhd", ints(0, 0, 0, 1000, 40, 0x10000), shorts(0x100),
                        new byte[10], MATRIX, new byte[24], ints(2)), trak);
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(ftyp);
            output.write(ints(frame.sample.length + 8)); output.write(ascii("mdat"));
            output.write(frame.sample); output.write(moov);
        } finally { output.close(); }
    }

    private static byte[] ascii(String value) { return value.getBytes(java.nio.charset.Charset.forName("US-ASCII")); }
    private static byte[] ints(int... values) {
        byte[] bytes = new byte[values.length * 4];
        for (int i = 0; i < values.length; i++) for (int j = 0; j < 4; j++)
            bytes[i * 4 + j] = (byte)(values[i] >>> (24 - j * 8));
        return bytes;
    }
    private static byte[] shorts(int... values) {
        byte[] bytes = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            bytes[i * 2] = (byte)(values[i] >>> 8); bytes[i * 2 + 1] = (byte)values[i];
        }
        return bytes;
    }
    private static byte[] box(String type, byte[]... parts) throws IOException {
        int size = 8;
        for (byte[] part : parts) size += part.length;
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        output.write(ints(size)); output.write(ascii(type));
        for (byte[] part : parts) output.write(part);
        return output.toByteArray();
    }
}
