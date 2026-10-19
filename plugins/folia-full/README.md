# Folia 源码构建 JAR 频道

前端从 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)
官方源码自行编译，不采集第三方网站的 HTML、JS、CSS 或图片。
固定版本：`0.7.21`，提交 `851af3478bfa98b68dbd21fbc89120cc11b7ea96`。

## 安装和交互

需要本次更新的宿主 APK：新 JAR 约 10.4 MiB，旧宿主的 8 MiB 上限无法加载。
要求 Android 7.0+、**Android System WebView 111 或以上**。推荐 WebView 119+。
已在 Android 7.1.1 / WebView 119 真机验证，并使用 Chromium 111.0.5563.33 验证前端。
系统版本与 WebView 内核版本是两回事，Android 7 自带的旧内核不满足要求。
插件启动时检查原始内核 UA，低于 111 会明确提示更新，不受宿主伪装 UA 的设置影响。

将 `dist/folia-full.jar` 上传到 HTTPS 服务，在 M3U 中使用：

```m3u
#EXTM3U
#EXTINF:-1 group-title="音乐",Folia 源码版
https://你的服务器/folia-full.jar?sha256=当前JAR的SHA256
```

`dist/example.m3u` 已包含当前产物的在线下载地址和真实 SHA-256。
更新 JAR 后必须同步更新 M3U 中的哈希。

- 触摸、鼠标和手机飞鼠通过原生事件分发到插件视图，保留浏览器实际命中测试。
- 分辨率和缩放沿用宿主浏览器设置。
- 连续按两次返回关闭插件，停止其页面及本地服务，回到频道主页；页面浏览历史不会阻止关闭。
- 宿主频道列表、设置和返回提示显示时，先由对应界面处理输入。
- 原版文件选择、Java 保存 Blob 导出、媒体键桥接仍保留。

## 源码构建

构建前端需要 Node.js 24+、npm 和 Python；Android 设备不需要 Node.js 或 QuickJS。
依赖由上游 package-lock.json 和本目录 package-lock.json 分别锁定。

```powershell
python plugins/folia-full/build-web.py
python plugins/folia-full/build.py
```

`build-web.py` 下载固定提交的官方 GitHub 源码归档，用锁定的 npm 依赖执行 Vite 构建。
`--source 路径` 可以复用已有源码，但会逐文件校验它与固定归档一致；
`--skip-install` 仅适用于该目录已经成功运行 npm ci 的情况。

构建适配全部写在脚本中：目标 Chrome 111、为 worker 提供上游虚拟模块插件、
禁用替换内置页面的 PWA 插件、使用相对 API 地址。酷狗依赖使用同一固定版本的
GitHub codeload 下载地址，未替换依赖实现。源码文件不另做业务修改。

样式使用 Lightning CSS 1.32.0，将 4/8 位十六进制透明色降级为传统 rgba()，
同时处理颜色语法和 CSS 嵌套。宿主 targetSdk 25 下，即使内核 119 也会禁用
CSS hex-alpha，因此仅设置 Chrome 构建目标不能解决白色按钮问题。
compat-assets.mjs 使用 Babel parser 处理生成 JS 中的完整颜色字符串/模板常量，
避免对整个脚本直接替换。core-js 3.51.0 为页面和 worker 补齐 Promise.withResolvers、
Object/Map.groupBy 等较新 API。兼容依赖只在构建时打包，不在手机启动时下载。

111 是当前支持下限，依据上游 Tailwind 4 的 color-mix 等基础功能及旧内核验证设定，
并不承诺低于 111 的 WebView 可完整运行。WebGPU、GPU 模型分析和部分高级特效仍取决于
内核与设备能力，转译库不能提供硬件功能。

`assets/upstream-manifest.json` 记录官方提交、源码归档 SHA-256 和每个生成文件的哈希。
`build.py` 会验证来源和文件哈希后再打包。Java 编译需要 Java 8、Android 30 和
Build Tools 30.0.3，默认路径可用参数覆盖。旧 vendor.py / sanitize.py 已移除。

版权、完整源码来源和 AGPL-3.0 说明见 `NOTICE`、`LICENSE`。

## 运行架构与服务

JAR 包含 Android DEX 插件和本地编译的原版 Web 前端。
Java 只在 `127.0.0.1` 提供静态文件和网络桥接，WebView 执行原版界面及播放器。
页面入口不打开远程网站。切换频道或双返回会释放 WebView、端口和请求连接。

源码构建前端不等于将上游 Node.js 后端移植到 Java。
当前在线 API 仍默认使用：

| 路由 | 服务 |
| --- | --- |
| `/ncm-api` 及其他相对服务路径 | `https://music.aikun-bili.top` |
| `/kugou-api` | `https://kugouapi.aikun-bili.top` |
| `/qq-api` | `https://qqmusicapi.aikun-bili.top` |

M3U 的 `serviceOrigin` 参数可覆盖第一项 HTTPS 来源，参数值需要 URL 编码；
另外两项的默认服务地址在 `assets/ntv-bridge.js` 中。服务接口须与源码版本匹配。
Java 转发 fetch、XHR、POST、Cookie 和媒体 Range 请求。
未复制站点的账号、密钥或令牌。AI、同步、登录和在线音乐仍需要相应服务与用户配置。
桌面 Electron 的专属能力不包含在这个 Web 插件中。

## 本次验证

独立测试包 `xiao.bu.tv.channeltest`，不会替换正式包：
HTTPS JAR 校验加载、官方源码前端挂载、100% / 150% 下真实触摸和手机飞鼠点击、
原版更新弹窗及引导、深色帮助/设置页无白色控件、兼容 API、
带浏览历史时连续两次返回关闭插件并显示频道主页。
测试代码见 `FoliaFullInstrumentation`。本次没有宣称逐项验证所有账号、主题和在线服务。
