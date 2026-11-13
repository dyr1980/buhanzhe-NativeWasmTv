package xiao.bu.tv;

/**
 * 私有轻量内核的网页启动状态快照（占位实现）。
 *
 * <p>字段与上游 LiteWebStartupPolicy 的读取口径完全一致。
 * 轻量内核被禁用（LiteWebPlayback.supported() 恒为 false）时，
 * 这些状态不会被真正填充，仅保证编译与类型一致。</p>
 */
final class LiteStartupState {

    /** 内核是否正处于忙状态。 */
    boolean busy;

    /** 未完成的请求数。 */
    int pendingRequests;

    /** 是否已出现媒体意图（即将出流）。 */
    boolean mediaIntent;

    /** 最近一次页面进度的时刻（elapsedRealtime 毫秒）。 */
    long lastProgressAt;

    /** 页面是否要求用户交互后才能继续。 */
    boolean interactionRequired;

    /** 主文档 HTTP 状态码（0 表示尚未收到）。 */
    int documentStatus;

    /** 主文档就绪时刻（elapsedRealtime 毫秒，0 表示未就绪）。 */
    long documentReadyAt;
}
