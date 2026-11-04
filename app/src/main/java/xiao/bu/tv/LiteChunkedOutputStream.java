package xiao.bu.tv;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP/1.1 chunked transfer encoder.
 *
 * <p>Wraps the response body so callers can stream an unknown length without
 * buffering it first. {@link #finish()} must be called exactly once; it writes
 * the terminating zero-length chunk.</p>
 */
final class LiteChunkedOutputStream extends OutputStream {
    private final OutputStream output;
    private boolean finished;

    LiteChunkedOutputStream(OutputStream output) {
        if (output == null) throw new IllegalArgumentException("output");
        this.output = output;
    }

    @Override
    public void write(int value) throws IOException {
        byte[] single = {(byte) value};
        write(single, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        if (finished) throw new IOException("Chunked stream already finished");
        if (count == 0) return;
        output.write(Integer.toHexString(count).getBytes("ISO-8859-1"));
        output.write(CRLF);
        output.write(buffer, offset, count);
        output.write(CRLF);
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    /** Terminates the chunked body. Safe to call more than once. */
    void finish() throws IOException {
        if (finished) return;
        finished = true;
        output.write(LAST_CHUNK);
        output.flush();
    }

    /** Finishes the body so a caller that only closes the stream stays correct. */
    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            output.close();
        }
    }

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] LAST_CHUNK = {'0', '\r', '\n', '\r', '\n'};
}
