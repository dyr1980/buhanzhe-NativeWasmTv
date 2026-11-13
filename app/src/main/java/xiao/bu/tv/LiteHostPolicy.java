package xiao.bu.tv;

/**
 * 私有轻量内核的站点兼容策略接口（占位实现）。
 *
 * <p>上游 WebSiteCompatibility 声明 "implements LiteHostPolicy"，
 * LiteEngine.installHostPolicy() 接收该类型。轻量内核被禁用时
 * 策略不会被真正消费，空接口即可满足编译。</p>
 */
interface LiteHostPolicy {
}
