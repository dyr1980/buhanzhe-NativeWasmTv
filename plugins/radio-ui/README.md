# 经典电台界面 JAR

唱片绘制、旋转、切台动画、手势预览和唱片 PNG 位于独立 DEX JAR。
宿主保留 `RadioUi` v1 接口、加载器和简单标题界面；播放器、网络请求及封面下载仍由宿主管理。
JAR 不打包进主 APK。没有本地插件时尝试从默认 URL 下载；没有插件或下载失败时电台仍可播放。
经典版地址：`https://raw.githubusercontent.com/buhanzhe/NativeWasmTv/master/plugins/radio-ui/dist/radio-ui-v1.jar`。
柔光版源码和最新产物独立放在 [`../radio-ui2`](../radio-ui2/README.md)；主应用默认下载柔光版插件，初始选择仍为经典唱片。
高级设置支持保存自定义 URL、下载更新和手动导入；GitHub URL 使用宿主统一加速设置。

## 使用

安装本次测试 APK，在局域网管理网页打开“高级设置 → 电台界面插件”，
选择 `dist/radio-ui-v1.jar` 并导入。同一入口可以导入新版 JAR，立即替换当前界面。
正在进行手势/切台动画时，等动画完成再替换，音频播放不中断。
插件会保留在应用私有目录，后续启动离线加载，无需重复导入。

上传入口：`POST /api/radio-ui`，请求体为 JAR 原始字节；状态查询为 `GET /api/radio-ui`。
请求体上限 2 MiB。既有管理服务的访问方式不变。

## 构建

```powershell
python plugins/radio-ui/build.py --sdk D:/android/sdk --java-home YOUR_JAVA8_HOME
```

需要 Android Build Tools 30.0.3（D8）和平台 android-30。
D8 以 `--min-api 14` 编译并处理 Java 8 语法；JAR 包含 `classes.dex`、
`radio-ui.json` 和 `radio/vinyl.png`，不包含宿主接口的重复定义。
实现只使用 Android API14 可用类型，新 API 保留运行时版本判断。

元数据 `api` 是接口版本，`version` 是界面版本，`minSdk` 是最低系统版本，
`entry` 类必须实现宿主 `xiao.bu.tv.radio.RadioUi`，提供 `(Context, ClassLoader)` 构造方法。
构造方法和界面生命周期运行在主线程；文件处理和 DEX 加载在后台进行。

导入前检查配置、接口、构造和资源初始化。每个 JAR 以 SHA-256 存入独立只读路径，
避免覆盖已加载的 DEX/资源。只有验证通过才切换持久化的活动版本；失败保留旧插件。
已安装文件在重新加载时再次校验哈希。这里的哈希是完整性检查，不是发布者签名。

## 本轮验证（2026-10-11）

- ARM32、ARM64、x86 release 构建通过；应用版本保持 1.8.0 / versionCode 8。
- Android 4.4 ARMv7 真机、Android 9 ARMv7 转译模拟器均通过实际 DEX 加载。
- 完整窗口中 v1 → v2 热替换；切台动画完成后替换；新旧同名类使用不同 ClassLoader。
- 封面保留、实际界面绘制、独立 MediaPlayer 音频时钟持续前进、无效 JAR 拒绝并保留当前界面。
- 清除进程内加载缓存后，从私有目录离线重新加载已安装 JAR 通过。
- 原有管理网页回归测试通过。
- 测试后恢复两台设备原 APK 和偏好设置。

独立 JAR 187,045 字节（约 183 KiB）。ARM32 主 APK 从 3,628,058 字节减少到
3,450,298 字节，减少 177,760 字节（约 174 KiB）。
插件安装后占用独立存储及 DEX 缓存，所以此方案减少的是 APK 下载体积。
Android 4.0.4 按 API14 编译，本轮无已连接的 4.0.4 设备，未宣称实机验证通过。

仪器测试：`RadioUiInstrumentation`，参数 `fixtures` 指向同时包含
`radio-ui-v1.jar`、`radio-ui-v2.jar`、柔光目录的 `radio-ui2.jar` 和 `audio.wav` 的设备目录。
第二版可用 `python plugins/radio-ui/build.py --version 2` 生成。
