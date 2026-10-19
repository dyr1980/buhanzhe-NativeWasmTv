# Folia 源码构建 JAR 频道

前端从 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)
官方源码自行编译，不采集第三方网站的 HTML、JS、CSS 或图片。
固定版本：`0.7.21`，提交 `851af3478bfa98b68dbd21fbc89120cc11b7ea96`。

## 安装和交互

需要本次更新的宿主 APK：新 JAR 约 10.3 MiB，旧宿主的 8 MiB 上限无法加载。
要求 Android 7.0+ 和较新的系统 WebView；已在 Android 7.1.1 / WebView 119 验证。

将 `dist/folia-full.jar` 上传到 HTTPS 服务，在 M3U 中使用：

```m3u
#EXTM3U
#EXTINF:-1 group-title="音乐",Folia 源码版
https://你的服务器/folia-full.jar?sha256=当前JAR的SHA256
```

`dist/example.m3u` 已包含当前产物的真实 SHA-256，替换域名即可。
更新 JAR 后必须同步更新 M3U 中的哈希。

- 触摸、鼠标和手机飞鼠通过原生事件分发到插件视图，保留浏览器实际命中测试。
- 分辨率和缩放沿用宿主浏览器设置。
- 连续按两次返回关闭插件，停止其页面及本地服务，回到频道主页；页面浏览历史不会阻止关闭。
- 宿主频道列表、设置和返回提示显示时，先由对应界面处理输入。
- 原版文件选择、Java 保存 Blob 导出、媒体键桥接仍保留。

## 源码构建

构建前端需要 Node.js 24+、npm 和 Python；Android 设备不需要 Node.js 或 QuickJS。

```powershell
python plugins/folia-full/build-web.py
python plugins/folia-full/build.py
```

`build-web.py` 下载固定提交的官方 GitHub 源码归档，用锁定的 npm 依赖执行 Vite 构建。
`--source 路径` 可以复用已有源码，但会逐文件校验它与固定归档一致；
`--skip-install` 仅适用于该目录已经成功运行 npm ci 的情况。

构建适配全部写在脚本中：目标 Chrome 100、为 worker 提供上游虚拟模块插件、
禁用替换内置页面的 PWA 插件、使用相对 API 地址。酷狗依赖使用同一固定版本的
GitHub codeload 下载地址，未替换依赖实现。源码文件不另做业务修改。

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
原版页面导航、带浏览历史时连续两次返回关闭插件并显示频道主页。
测试代码见 `FoliaFullInstrumentation`。本次没有宣称逐项验证所有账号、主题和在线服务。
