# Folia 完整 Web 前端 JAR

将 https://music.aikun-bili.top/ 的原版 Folia Web 前端及主题模块打包进 JAR，
通过 nTv 的 M3U HTTPS JAR 频道入口加载。与 `plugins/folia` 的基础歌词原型分开。
页面从设备上的 Java 本地服务读取 JAR 资源，不打开远程网站首页，不运行 Node 或 QuickJS。

## 使用

```m3u
#EXTM3U
#EXTINF:-1 group-title="音乐",Folia 完整版
https://你的服务器/folia-full.jar?sha256=当前JAR的SHA256
```

上传 `dist/folia-full.jar` 后替换示例域名。`dist/example.m3u` 包含当前产物的真实哈希。
需要更新后的宿主 APK；旧版没有文件选择扩展接口。要求 Android 7.0+，并使用支持
现代 JavaScript/CSS 的系统 WebView。已验证 Android 7.1.1 + WebView 119。

## 功能与依赖

| 功能 | 接入方式 / 验证范围 |
| --- | --- |
| 原版首页、歌单、电台、专辑、本地库、搜索 | 原版前端；首页和搜索接口已实测 |
| 网易云 / 酷狗 / QQ 账户和在线功能 | 保留原界面，通过 Java 转发原站 API；未使用用户账户测试登录后的全部操作 |
| 歌词主题、播放队列、播放设置 | 包含原版静态模块，按需从 JAR 加载 |
| 本地音频与歌词 | Android 文件选择器；原版本地 MP3/LRC 导入与 IndexedDB 存储已实测 |
| 歌单 / 歌词文件导出 | Blob 导出由 Java 保存到应用外部 `files/Download` 目录；HTTP 下载交给系统下载管理器 |
| 播放 / 暂停 / 上一首 / 下一首媒体键 | 转发给原版 MediaSession 处理器；无处理器时播放暂停回退到 HTML audio |
| 分辨率、缩放、浏览器身份 | 沿用 nTv 浏览器配置和 DesktopWebProfile |
| 页面状态和本地库持久化 | 使用稳定的应用内回环端口及 WebView IndexedDB/localStorage |
| AI / 云同步 | 需要自行配置服务、凭据及兼容设备；不包含参考站点的内置服务令牌 |

**完整前端不等于把所有后端也移植到 Java。** 当前在线服务仍调用参考部署的
`/ncm-api`、酷狗和 QQ API；Java 负责 HTTP 请求、POST 请求体、媒体 Range 转发和 Cookie。
原站服务失效、平台登录限制、付费权限和地区限制仍会影响对应功能。
可用 `serviceOrigin` 查询参数覆盖网易云等相对路径所用的 HTTPS 服务来源；
酷狗和 QQ 地址目前来自固定前端构建。没有声称全部登录流程、所有主题或 GPU/AI 功能
均已在旧设备验证，桌面 Electron / Folium 本机模块也不包含在 Web 版中。

本地库在 Android 使用文件选择回退，可以多选音频和对应歌词。
系统文件选择器是否提供文件夹选择由设备决定，不能保证具备桌面浏览器的目录句柄能力。

## 架构

`FoliaFull` 创建 WebView；`FoliaServer` 只监听 `127.0.0.1`，为 ES 模块与
IndexedDB 提供稳定的同源环境。静态页面和模块全部来自 JAR。
网络桥接将 fetch、XHR 和远程媒体源转到带随机令牌的本地 Java 路由，
Java 再请求相应在线服务。入口拒绝错误 Host 和跨站 Origin。
响应流随连接结束关闭，频道切换会关闭 WebView、本地端口和请求线程。

播放由原版 Web Audio / HTML audio 管理，不与基础歌词插件的 IJK 时钟混用。
原版应用负责进度、歌词同步、均衡器和可视化。Android WebView 的后台播放和 GPU
能力与桌面 Electron 有差异，当前主要验证前台使用。

## 构建与来源

```powershell
python plugins/folia-full/build.py
```

需要 Java 8、Android 30 平台和 Build Tools 30.0.3；输出 `dist/folia-full.jar`、
`.sha256` 和 M3U 示例。正常构建只使用已固定的资源，不访问参考站点。

手动更新前端快照：

```powershell
python plugins/folia-full/vendor.py --refresh
python plugins/folia-full/sanitize.py
python plugins/folia-full/build.py
```

`vendor.py` 下载同源静态依赖图，排除将 SPA 首页当作缺失模块的响应。
`sanitize.py` 清空前端内嵌的服务 KEY/TOKEN/SECRET 值。
`assets/upstream-manifest.json` 记录来源和每个文件的 SHA-256。
上游源码、版权和 AGPL-3.0 说明见 `NOTICE`、`LICENSE`。

参考部署没有公布构建对应的准确 Git 提交；本目录记录的是可核对文件哈希的部署快照，
未声称与上游当前 main 分支完全一致。

## 验证

`FoliaFullInstrumentation` 在独立测试包 `xiao.bu.tv.channeltest` 验证：
HTTPS 校验下载、原版模块挂载、Java 转发真实搜索 API、本地 MP3/LRC 导入、
浏览器视口、文件选择器扩展 ABI 和 Java 保存 Blob 导出文件。原版播放器已进入
`PLAYING` 状态播放导入曲目。测试曲目是生成的音频夹具，不读取用户音乐文件。
