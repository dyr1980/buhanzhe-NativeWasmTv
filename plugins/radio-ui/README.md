# 电台界面选项

原生“功能设置 → 偏好设置 → 唱片界面”，或“网页管理 → 高级设置 → 电台界面”选择后应用。电台渲染 JAR 不打进 APK，首次使用需下载，成功后按 SHA-256 缓存。经典与第二套分别由目录描述指定下载产物，切换界面保留原音频播放器。

第二套（`optical`）已改为参考视频中的玻璃唱片，包含统一播放时钟驱动的 20 秒金属环、轴心旋转，以及玻璃下方和上方两层下落粒子。实现与本轮验证范围见 [`../radio-ui2/README.md`](../radio-ui2/README.md)。

## 远程增加选项

在线目录为本仓库 `master` 分支的 `plugins/radio-ui/catalog.json`。上传配置后，用户点击“刷新选项”即可获得新增条目；打开设置页时最多每 6 小时自动刷新一次，失败继续使用已有选项。目录刷新只下载 JSON，不自动替换正在使用的插件。

```json
{
  "schema": 1,
  "styles": [{
    "id": "new-radio",
    "name": "新的电台界面",
    "style": "classic",
    "url": "https://example.com/radio-ui-v5.jar",
    "sha256": "此处填写该文件的64位小写SHA-256"
  }]
}
```

- `id` 是稳定的选项标识；`name` 为显示名称；`style` 传给插件的 `radio_ui.style`，必须是该 JAR 的 `radio-ui.json.styles` 支持的值。
- JAR 必须实现现有 `RadioUi` API 1，入口放在 `xiao.bu.radio` 包下。目录最多 40 项、64 KiB，JAR 最大 2 MiB。
- 下载地址必须是 HTTPS，可使用相对于目录的地址。建议使用版本化或固定 Git 提交地址，避免同一地址内容变化导致旧目录的哈希失配。
- 远程同 ID 条目可更新名称和插件描述；APK 中的目录仅作为初始选项。已成功下载的选中界面冷启动优先继续使用，手动选择才应用新描述。
- 下载、校验或初始化失败会保留原选择和画面；损坏的缓存会重新下载。初次下载失败后可重新选择重试。

增加插件时先发布 JAR，再提交对应目录条目。APK 的初始目录位于 `app/src/main/assets/radio-ui/catalog.json`，仅包含 JSON，不包含 JAR。在线目录同 ID 描述会覆盖初始目录，因此发布时应同步两份配置。

本次经典条目保留固定提交 `74f7baa57d61ff80a47d6521ae18e336eccc91e3` 下的 `plugins/radio-ui2/dist/radio-ui2.jar`，SHA-256 为 `0c2f6249d1ea81d58671c6612fdcdac4237e3bdcfc54a55e162ca958e988ad0b`，经典视觉继续使用原产物。仅第二套 `optical` 使用 `master/plugins/radio-ui2/dist/radio-ui2.jar` 对应的新产物及最终哈希。覆盖第二套发布路径时需同步更新目录 SHA-256；缓存按哈希隔离。

## 构建和接口

经典插件源码仍在本目录，第二套玻璃唱片的源码与当前构建产物位于 [`../radio-ui2`](../radio-ui2/README.md)。该 JAR 保留 `classic`、`optical` 两种能力，目录可分别引用新旧产物。

```powershell
python plugins/radio-ui/src/build.py --sdk D:/android/sdk --java-home YOUR_JAVA8_HOME
python plugins/radio-ui2/src/build.py --sdk D:/android/sdk --java-home YOUR_JAVA8_HOME
```

需要 Android Build Tools 30.0.3 和 android-30 平台；D8 以 API14 构建。JAR 包含 `classes.dex`、`radio-ui.json` 和界面资源，不重复打入宿主接口。插件入口实现 `xiao.bu.tv.radio.RadioUi`，提供 `(Context, ClassLoader)` 构造方法。界面生命周期在主线程运行，下载、文件校验和 DEX 加载在后台进行。播放器、网络流及封面下载仍由宿主管理；插件加载失败时标题界面仍可显示，音频继续播放。SHA-256 用于完整性检查，不是发布者签名。

管理接口为 `GET /api/radio-ui`（状态/选项）、`POST /api/radio-ui` 的 `{"id":"optical"}`（选择）或 `{"refresh":true}`（刷新目录）。不再接受 URL 配置或 JAR 上传。

目录选择与下载机制此前在小米 6 Android 7.1.1 独立测试包验证过：切换、远程新增选项、请求失败/校验失败保留原选择、初始化重试、损坏缓存修复和离线重载，保留真实 IJK 音频播放器。`RadioUiInstrumentation` 参数 `audio` 为测试 MP3 URL，`cert` 为本地 HTTPS 测试目录的证书路径；TLS 测试信任仅由仪器测试设置，不进入正式 APK。

玻璃唱片修订 5 使用独立的 `GlassRecordInstrumentation` 验证。当前已完成 Android 7.1.1 首轮真实下载、固定相位渲染与分层粒子检查；完整回归结果按该仪器测试记录确认。Android 4.4 本轮尚未接入设备，API14 构建兼容路径保留。
