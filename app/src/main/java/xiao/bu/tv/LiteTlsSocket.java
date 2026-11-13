package xiao.bu.tv;

/**
 * 私有轻量内核的轻量 TLS 套接字（占位实现）。
 *
 * <p>上游只以 "client.sslSocketFactory() instanceof LiteTlsSocket.Factory"
 * 的形式引用本类，用来判断是否给共享缓存走轻量 TLS 通道。
 * 占位实现提供一个空标记类型：instanceof 恒为 false，
 * 共享缓存一律走标准 OkHttp 通道，行为安全。</p>
 */
final class LiteTlsSocket {

    private LiteTlsSocket() {
    }

    /**
     * 标记类型：仅用于 instanceof 判断，从不被真正安装。
     * 继承 SSLSocketFactory 是编译要求（上游把 sslSocketFactory()
     * 与本类型做 instanceof 比较，必须存在继承关系才能编译）。
     */
    static final class Factory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory fallback =
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();

        @Override
        public String[] getDefaultCipherSuites() {
            return fallback.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return fallback.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket socket, String host, int port,
                                            boolean autoClose) throws java.io.IOException {
            return fallback.createSocket(socket, host, port, autoClose);
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return fallback.createSocket(host, port);
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost,
                                            int localPort) throws java.io.IOException {
            return fallback.createSocket(host, port, localHost, localPort);
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port)
                throws java.io.IOException {
            return fallback.createSocket(host, port);
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port,
                                            java.net.InetAddress localAddress, int localPort)
                throws java.io.IOException {
            return fallback.createSocket(address, port, localAddress, localPort);
        }
    }
}
