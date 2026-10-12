package xiao.bu.tv;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns in-flight requests, without evicting the shared keep-alive pool. */
final class ProxyHttpConnections implements Closeable {
    private final Set<Tracked> active = new HashSet<Tracked>();
    private boolean closed;

    HttpURLConnection track(HttpURLConnection connection) throws IOException {
        Tracked tracked = new Tracked(connection);
        synchronized (this) {
            if (!closed) { active.add(tracked); return tracked; }
        }
        connection.disconnect();
        throw new IOException("HLS proxy closed");
    }

    private synchronized void completed(Tracked connection) { active.remove(connection); }

    @Override public void close() {
        List<Tracked> snapshot;
        synchronized (this) {
            closed = true;
            snapshot = new ArrayList<Tracked>(active);
            active.clear();
        }
        // Cancellation can synchronously wake a reader; never hold the registry lock.
        for (Tracked connection : snapshot) connection.disconnect();
    }

    private final class Tracked extends HttpURLConnection {
        private final HttpURLConnection delegate;
        private volatile boolean cancelled;
        Tracked(HttpURLConnection delegate) { super(delegate.getURL()); this.delegate = delegate; }
        private void checkOpen() throws IOException { if (cancelled) throw new IOException("HLS request cancelled"); }
        private InputStream body(InputStream stream) {
            if (stream == null) return null;
            return new FilterInputStream(stream) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { completed(Tracked.this); }
                }
            };
        }
        @Override public void disconnect() { cancelled = true; try { delegate.disconnect(); } finally { completed(this); } }
        @Override public void connect() throws IOException { checkOpen(); delegate.connect(); }
        @Override public boolean usingProxy() { return delegate.usingProxy(); }
        @Override public InputStream getInputStream() throws IOException { checkOpen(); return body(delegate.getInputStream()); }
        @Override public InputStream getErrorStream() { return body(delegate.getErrorStream()); }
        @Override public OutputStream getOutputStream() throws IOException { checkOpen(); return delegate.getOutputStream(); }
        @Override public int getResponseCode() throws IOException { checkOpen(); return delegate.getResponseCode(); }
        @Override public String getResponseMessage() throws IOException { return delegate.getResponseMessage(); }
        @Override public URL getURL() { return delegate.getURL(); }
        @Override public String getHeaderField(String name) { return delegate.getHeaderField(name); }
        @Override public String getHeaderField(int index) { return delegate.getHeaderField(index); }
        @Override public String getHeaderFieldKey(int index) { return delegate.getHeaderFieldKey(index); }
        @Override public Map<String,List<String>> getHeaderFields() { return delegate.getHeaderFields(); }
        @Override public void setConnectTimeout(int value) { delegate.setConnectTimeout(value); }
        @Override public int getConnectTimeout() { return delegate.getConnectTimeout(); }
        @Override public void setReadTimeout(int value) { delegate.setReadTimeout(value); }
        @Override public int getReadTimeout() { return delegate.getReadTimeout(); }
        @Override public void setRequestMethod(String value) throws ProtocolException { delegate.setRequestMethod(value); }
        @Override public String getRequestMethod() { return delegate.getRequestMethod(); }
        @Override public void setRequestProperty(String key,String value) { delegate.setRequestProperty(key,value); }
        @Override public void addRequestProperty(String key,String value) { delegate.addRequestProperty(key,value); }
        @Override public String getRequestProperty(String key) { return delegate.getRequestProperty(key); }
        @Override public Map<String,List<String>> getRequestProperties() { return delegate.getRequestProperties(); }
        @Override public void setInstanceFollowRedirects(boolean value) { delegate.setInstanceFollowRedirects(value); }
        @Override public boolean getInstanceFollowRedirects() { return delegate.getInstanceFollowRedirects(); }
        @Override public void setDoInput(boolean value) { delegate.setDoInput(value); }
        @Override public boolean getDoInput() { return delegate.getDoInput(); }
        @Override public void setDoOutput(boolean value) { delegate.setDoOutput(value); }
        @Override public boolean getDoOutput() { return delegate.getDoOutput(); }
        @Override public void setUseCaches(boolean value) { delegate.setUseCaches(value); }
        @Override public boolean getUseCaches() { return delegate.getUseCaches(); }
        @Override public void setIfModifiedSince(long value) { delegate.setIfModifiedSince(value); }
        @Override public long getIfModifiedSince() { return delegate.getIfModifiedSince(); }
        @Override public void setFixedLengthStreamingMode(int value) { delegate.setFixedLengthStreamingMode(value); }
        @Override public void setChunkedStreamingMode(int value) { delegate.setChunkedStreamingMode(value); }
    }
}
