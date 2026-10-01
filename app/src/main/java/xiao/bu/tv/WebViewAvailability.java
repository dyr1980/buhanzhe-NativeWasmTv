package xiao.bu.tv;

import android.webkit.WebView;

/** Constructor failures are recoverable feature failures, not application failures. */
final class WebViewAvailability {
    static final String MESSAGE = "系统 WebView 不可用，请安装或更新系统 WebView 后重启应用；仍可播放普通频道";
    interface Factory<T extends WebView> { T create(); }
    private static UnavailableException failure;

    static final class UnavailableException extends RuntimeException {
        UnavailableException(Throwable cause) { super(MESSAGE, cause); }
    }

    // All WebView construction takes place on the UI thread. A failed provider
    // may be half-initialized; retrying it can trigger WebViewCachedFlags.init.
    static <T extends WebView> T create(Factory<T> factory) {
        if (failure != null) throw failure;
        try {
            return factory.create();
        } catch (RuntimeException | LinkageError error) {
            failure = new UnavailableException(error);
            throw failure;
        }
    }

    private WebViewAvailability() { }
}
