package xiao.bu.tv;

/**
 * 轻量内核页面渲染画布（占位实现）。
 *
 * 上游实现随私有内核一起授权，公开检出没有 litehtml 的 Java 绑定。
 * 这里保留同一套对外字段/方法，使 LiteWebPlayback 无需改动即可编译；
 * 因为 {@link LiteEngine#isAvailable()} 恒为 false，本类不会被实例化。
 */
final class LiteCanvas extends android.view.View {
    LiteEngine engine;
    int fixedViewportWidth;
    int fixedViewportHeight;

    LiteCanvas(android.content.Context context) {
        super(context);
    }

    boolean editingText() {
        return false;
    }

    void frame(org.json.JSONObject packet) { }

    void textInput(org.json.JSONObject state) { }

    boolean textInputKey(android.view.KeyEvent event) {
        return false;
    }

    void endTextInput() { }

    // 与业务代码调用签名保持一致（返回 boolean 表示是否消费按键）。
    // View 已有同名方法，这里用独立名称避免签名冲突。
    boolean dispatchKeyEventToPage(android.view.KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
}
