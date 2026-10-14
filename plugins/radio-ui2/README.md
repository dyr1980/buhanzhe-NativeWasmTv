# 柔光电台界面（radio-ui2）

`plugins/radio-ui` 保存经典版；本目录保存柔光版的源码、资源和当前发布产物。
本目录只发布一份 `dist/radio-ui2.jar`，不保留之前的静态光效初版。
JAR 元数据中的 `version` 为内部修订号，当前是 4；它不影响文件夹及固定下载名称。

## 使用

主应用默认下载地址：
`https://raw.githubusercontent.com/buhanzhe/NativeWasmTv/master/plugins/radio-ui2/dist/radio-ui2.jar`。
JAR 不打包进主 APK，下载失败时电台仍可播放。
已经保存过旧下载地址的实例，可在管理页改成上述地址并下载更新，或手动导入该 JAR。

在“高级设置 → 电台界面 → 唱片样式”选择“柔光唱片”。
插件同时提供经典样式，主应用初始选择仍是“经典唱片（默认）”。
旧的经典版插件不声明柔光能力，相应选项会禁用。

上传：`POST /api/radio-ui`，请求体为 JAR 原始字节，上限 2 MiB。
状态：`GET /api/radio-ui`。
选择样式：`POST /api/radio-ui` JSON `{"style":"optical"}` 或 `{"style":"classic"}`。
替换会保留封面、音频和播放状态，在手势/切台动画完成后执行。
插件和样式选择在私有目录持久化，后续启动可离线加载。

## 构建

```powershell
python plugins/radio-ui2/src/build.py --sdk D:/android/sdk --java-home YOUR_JAVA8_HOME
```

需要 Java 8、Android Build Tools 30.0.3（D8）和平台 android-30。
构建缓存独立位于 `.codex-tmp/radio-ui2/build`，产物固定为
`dist/radio-ui2.jar` 和 `dist/radio-ui2.sha256`。
`--version` 仅设置元数据修订号，不产生第二份命名不同的产物。
D8 使用 `--min-api 14`；JAR 包含 `classes.dex`、`radio-ui.json`、`radio/vinyl.png`，
不重复打包宿主 `RadioUi` v1 接口。
宿主使用每个 JAR 的 SHA-256 独立只读路径及 ClassLoader；失败保留当前插件。
SHA-256 用于完整性检查，不作为发布者签名。

## 动效实现

自定义 `OpticalMotionView` / Canvas 逐帧绘制十二道细环、逐环亮度传递、
两处移动高光、微弱的蓝金色反射、背景呼吸，以及每 4.8 秒一组三道向外扩散的光圈。
光效独立于封面的 18 秒旋转；暂停、不可见和脱离窗口时停止回调。
光效按时间变化，当前不接音频频谱或节拍检测。

渐变、矩阵、画笔和裁剪路径缓存复用，逐帧不创建 Bitmap 或 Java 对象。
多层半透明描边形成柔光；硬件路径裁剪在 API18 及以上启用。
封面单独缓存为 614×614；宿主解码上限为 1024px，图片缓存上限为 8 MiB。
切台快照只在过渡期间生成，暂停键立即更新动画状态。

## 已有验证（2026-10-14）

- ARM32 / ARM64 正式签名 release 构建通过，包名保持 `xiao.bu.tv`。
- 小米 6 / Android 7.1.1：真实音轨播放、切台、默认封面及暂停通过；
  外圈两帧 89.65% 像素变化，暂停稳定后外圈无像素变化。
- 小米 6 播放采样 8,906 帧，系统标记卡顿 291 帧（3.27%），
  帧耗时 P50 12ms、P90 15ms、P95 16ms、P99 18ms。仅代表该设备和本地音轨。
- SM-G3608 / Android 4.4.4：Dalvik 加载、封面和光效绘制、本地音轨播放通过；
  外圈两帧 50.30% 像素变化，暂停稳定后外圈无像素变化；未取得可靠帧耗时百分位。
- 管理网页样式能力限制、等待状态和保存失败恢复通过。
- 目录拆分后，Android 9 仪器回归通过：经典版 v1 → v2 → `radio-ui2.jar` 的真实 DEX 加载、
  样式切换、手势结束后替换、封面和音频保留、无效 JAR/样式拒绝及离线恢复。
- 真机测试后恢复原频道、源和显示配置，保留柔光样式。

`RadioUiInstrumentation` 的 `fixtures` 目录需要经典版的 `radio-ui-v1.jar`、
`radio-ui-v2.jar`，本目录的 `radio-ui2.jar`，以及 `audio.wav`。
它验证真实 DEX 加载、样式与手势切换、封面和音频保留、无效候选拒绝和离线恢复。
