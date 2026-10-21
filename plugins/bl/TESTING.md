# BL 首版验证记录（2026-10-20）

## 兼容边界

最低构建目标 Android 4.0 / API 14。UI 以 Google `android-14_r04.zip` 中的 Android 4.0.2 平台 API 编译，D8 最低 API 14；JAR 159 个自有/ZXing 类，约 176 KiB。无 Activity、Fragment、WebView 和原生库，宿主提供网络及 IJK ABI。

4.0.4 实机未连接，本记录不宣称已经在 4.0 真机验证。用户指定使用已连接的 4.4、7.1 测试。

## 设备及结果

| 系统 | 宿主 | 播放路径 | 结果 |
| --- | --- | --- | --- |
| Android 4.4.4 / API 19 | 独立 bltest，裁剪后的 release，调试证书签名 | IJK | 真实推荐/详情/视频播放，实际画面截图；返回、暂停、搜索界面、历史、二维码与退出清理通过 |
| Android 4.4.4 / API 19 | 同上 | API 14 `BlSystemPlayer` | 本地 H.264 Baseline/AAC；播放时间推进、画质/分 P/字幕、后台恢复、Range/HEAD、HLS Key/Map/分片 URI 重写、返回及资源释放通过 |
| Android 7.1.1 / API 25 | 独立 channeltest，裁剪后的 release，调试证书签名 | IJK | 本地全流程与真实视频播放；实际截图已确认插件 Surface 显示正确的视频 |

测试 JAR 通过 HTTPS 下载并校验 SHA-256，API/媒体使用宿主同一个 OkHttpClient（通过对象身份检查）。测试从页面上的触摸按钮和遥控器键进入播放器，没有通过调用插件私有方法直接代替用户导航。

未登录真实搜索可能返回验证要求或空结果；测试记录分别标记 `site_verification_required` / `no_public_results`，不能据此宣称真实搜索结果已全部验证。固定测试服务可返回搜索卡片，覆盖 UI、解析和导航。未使用真实账号扫码确认，也未执行真实点赞、投币、收藏等写操作；这些接口已实现但尚待账号验证。

## 修复过的实机问题

- MIUI 锁屏导致测试 View 尚未布局：仅测试 Instrumentation 使用唤醒/锁屏窗口标志，插件不改设备设置。
- 选择弹层被异步 prepared 回调抢走焦点：移除强制焦点跳转。
- 主线程 `Response.close()` 发送 HTTP/2 RST 导致崩溃：改为 Call.cancel，后台线程负责流关闭。
- 代理 HEAD 的 ResponseBody 长度为 0：保留上游 Content-Length，Range 保留 206/Content-Range。
- 多个 Surface 同层显示宿主残留画面：插件视频使用 media overlay 层，保留原生控制按钮在其上方。
- IJK 系统播放器包装引用 API 16 TimedText：独立实现只依赖 API 14 的平台播放器适配器。
- 发布裁剪可能删除动态 JAR 需要的类/方法：保留宿主插件 SDK、OkHttp/Okio 公共 API、IJK 适配接口。
- WBI 初始化的网络调用原来会持有 Cookie 锁：独立签名锁，避免 UI 查询登录状态等待网络。

## 资源与性能观察

GridView 复用卡片，有界线程/缓存/返回堆栈和媒体代理；退出后等待 API、图片、媒体线程池终止并检查监听端口关闭。4.4 通过同一套退出检查。典型一次运行中，4.4 宿主 Java 已用堆约 14–20 MiB，native heap 约 8–12 MiB；7.1 约 30–36 MiB / 18–24 MiB。这是整个测试进程在历史页面的采样，不是插件单独增量、峰值 PSS 或帧率基准。

## 复现及产物

构建、测试命令见 README。`tests/verify_artifact.py` 检查 JAR 结构与 ABI；`BlPluginInstrumentation` 验证实际宿主与下载插件；`tests/run_device.py` 保存日志及实际播放截图。测试数据与截图保存在本机 `output/bl/tests/`。

交付宿主：`output/bl/nTv-BL-test-arm32.apk`，包名 `xiao.bu.tv.channeltest`，与正式 `xiao.bu.tv` 共存。宿主来自独立源码快照，只合入本任务的插件接口、生命周期和发布保留规则。
