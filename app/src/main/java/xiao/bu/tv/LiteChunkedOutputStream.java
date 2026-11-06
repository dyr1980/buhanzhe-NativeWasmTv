package xiao.bu.tv;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP/1.1 chunked 传输编码的写入端。
 *
 * 上游实现位于私有工程。这里按 RFC 7230 §4.1 重新实现：每次写入包成一个数据块，
 * {@link #finish()} 写出结束块 0\r\n\r\n。调用方负责 header 与 flush。
 */
final class LiteChunkedOutputStream extends OutputStream {
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] LAST = {'0', '\r', '\n', '\r', '\n'};

    private final OutputStream out;
    private byte[] sizeBuffer;
    private boolean finished;

    LiteChunkedOutputStream(OutputStream out) {
        if (out == null) throw new IllegalArgumentException("out == null");
        this.out = out;
    }

    @Override
    public void write(int value) throws IOException {
        write(new byte[] {(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        if (finished) throw new IOException("chunked stream already finished");
        if (buffer == null) throw new NullPointerException("buffer == null");
        if (offset < 0 || count < 0 || offset + count > buffer.length) {
            throw new IndexOutOfBoundsException();
        }
        if (count == 0) return;
        writeSizeLine(count);
        out.write(buffer, offset, count);
        out.write(CRLF);
    }

    private void writeSizeLine(int count) throws IOException {
        if (sizeBuffer == null) sizeBuffer = new byte[12];
        int position = sizeBuffer.length;
        int value = count;
        do {
            int digit = value & 0xF;
            sizeBuffer[--position] = (byte) (digit < 10 ? '0' + digit : 'a' + digit - 10);
            value >>>= 4;
        } while (value != 0);
        out.write(sizeBuffer, position, sizeBuffer.length - position);
        out.write(CRLF);
    }

    /** 写出结束块并标记为已完成；重复调用无副作用。 */
    void finish() throws IOException {
        if (finished) return;
        finished = true;
        out.write(LAST);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        // 不关闭底层 socket：连接由代理服务器统一管理。
        if (!finished) finish();
        out.flush();
    }
}
