package xiao.bu.tv;

/** Test-only entry points provided by the instrumented CMG plugin. */
final class CmgTraceBridge {
    static native boolean enable(String path);
    static native void mark(String channel);
    static native void stop();
}
