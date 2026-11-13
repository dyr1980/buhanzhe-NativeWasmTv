package xiao.bu.tv;

import android.content.Context;

import org.json.JSONObject;

/**
 * 私有轻量内核的 localStorage 同步器（占位实现）。
 *
 * <p>上游 SharedWebProfile 用它把网页的 localStorage 写回内核。
 * 返回 "null" 表示"本占位实现不处理"，调用方会走默认分支，
 * 与轻量内核禁用时的预期行为一致。</p>
 */
final class LiteWebStorage {

    private LiteWebStorage() {
    }

    /** 占位：恒返回 "null"（上游以 !"null".equals(result) 判断是否接管）。 */
    static String replace(Context context, String origin, JSONObject storage) {
        return "null";
    }
}
