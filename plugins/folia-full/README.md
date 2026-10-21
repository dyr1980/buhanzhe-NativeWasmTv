# Folia 源码构建 JAR 频道

前端从 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)
官方源码自行编译，不采集第三方网站的 HTML、JS、CSS 或图片。
固定版本：`0.7.21`，提交 `851af3478bfa98b68dbd21fbc89120cc11b7ea96`。

## 安装和交互

需要支持 32 MiB JAR 的宿主 APK：新 JAR 约 12.5 MiB，旧宿主的 8 MiB 上限无法加载。
要求 Android 7.0+、**Android System WebView / Chrome 61 或以上**。推荐较新的内核。
兼容构建在 Android 7.1.1 / 出厂 WebView 61.0.3163.98 真机和 Android 9 / WebView 68.0.3440.70 模拟器测试。
系统版本与 WebView 内核版本是两回事，Android 7 自带的旧内核仍可能不满足要求。
插件启动时检查原始内核 UA，低于 61 会明确提示更新，不受宿主伪装 UA 的设置影响。

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
- 返回键优先回退 WebView 网页历史，每次回退不累计退出计时；没有可回退页面时，连续两次返回关闭插件、
  停止页面及本地服务并回到频道主页。文件夹弹窗内返回仍优先退上一级目录。
  网页历史优先的行为需要更新宿主 APK，JAR 无需重打包。
- 宿主频道列表、设置和返回提示显示时，先由对应界面处理输入。
- 修复代理音频地址核对，点选歌曲及自动切歌保留原版自动播放意图，无需再手动点一次播放。
- Android 的本地「导入文件夹」入口选择整个文件夹，不再逐项勾选文件。
  Android 7–9 使用独立的 Android 5 Material 深色主题：圆角弹窗、路径、文件夹图标、
  子文件夹内的文件数/音乐数、当前目录的文件总数/音乐数/可导入数，以及「导入此文件夹」按钮。
  统计和导入只包含当前目录的文件，不递归包含子文件夹；没有音乐的目录不能确认。
  遥控器：上下选择目录，确定进入目录，右键移动到导入按钮，再按确定导入；
  左键/返回回到上一级并保留原目录的位置，存储根目录再次返回才关闭。
  顶部上一级、目录列表、底部取消/导入可用方向键切换，焦点使用高亮描边。
  所选文件会复制到应用的 WebView IndexedDB 库，重新打开页面后仍可读取；不会修改原文件。
  这是只读导入，不提供桌面的目录监控或文件就地写回。请预留导入文件所需存储空间。
  Android 7–9 首次需要允许读取存储空间，更新 JAR 即可；Android 10+ 使用系统 SAF 文件夹授权，
  授权后显示相同 Material 主题的统计/导入确认弹窗，系统授权窗口保持系统自身主题。
  此现代授权路径需要支持可选 `ChannelPlugin.DirectoryHost.chooseDirectory` 的新版宿主 APK；
  旧宿主会提示更新，不会退回逐项选文件。该能力不改变 API 1，旧 JAR 的 BrowserHost 仍兼容。
  普通歌单/文件上传入口继续使用原系统文件选择器，只有本地音乐文件夹入口使用文件夹导入。
- Java 保存 Blob 导出、媒体键桥接仍保留。

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

构建适配全部写在脚本中：目标 Chrome 61、使用 classic worker 并提供上游虚拟模块插件、
禁用替换内置页面的 PWA 插件、使用相对 API 地址。酷狗依赖使用同一固定版本的
GitHub codeload 下载地址，未替换依赖实现。官方源码文件保持原样；Vite 适配增加 Android 文件夹桥接入口，
并按内核能力选择原版书库布局、转换旧内核不支持的轮播内联样式。

样式使用 Lightning CSS 1.32.0，将 4/8 位十六进制透明色降级为传统 rgba()，
同时处理颜色语法和 CSS 嵌套。宿主 targetSdk 25 下，即使内核 119 也会禁用
CSS hex-alpha，因此仅设置 Chrome 构建目标不能解决白色按钮问题。
compat-assets.mjs 使用 Babel parser 处理生成 JS 中的完整颜色字符串/模板常量，
避免对整个脚本直接替换。core-js 3.51.0 为页面和 worker 补齐 Promise.withResolvers、
Object/Map.groupBy 等较新 API。兼容依赖只在构建时打包，不在手机启动时下载。

Chrome 61 兼容构建还包含：

- Babel 将 async/await 和异步迭代降为 generator，避免旧 Chromium 中 Dexie 事务提前提交。
- regexpu-core 展开 Unicode 属性正则，避免旧引擎不认识 Extended_Pictographic 等属性。
- FormatJS 补齐 Intl.Locale/Segmenter，保留歌词分词能力；core-js 补齐其余标准库 API。
- PostCSS Preset Env 降级 CSS 层和选择器，补齐 Tailwind 的属性初始值、弹窗高度和旧式颜色。
  根变量和 reset 的选择器单独输出普通文档分支，避免旧解析器丢弃含 :host/::backdrop 的整组规则。
  所有 CSS 分包统一计算层级优先级，避免 reset 压过其他分包的组件规则。
- 同样转换 JS 中运行时注入的 shader CSS，保证背景画布填满容器；为封面补齐正方形比例，
  将轮播的 paddingInline 改为左右 padding，保持首张卡片居中。
- 不支持容器查询或 color-mix 的内核使用上游原有 Grid 书库，隐藏依赖这些能力的 Bravais 布局。
  不覆盖保存的布局偏好，升级内核后仍可使用 Bravais。
- 小型 DOM 适配补齐 Blob.text/arrayBuffer、ResizeObserver 尺寸和 focus-visible。
  旧内核上的内联主题 color-mix 在样式变化时解析为 rgba；不在每个动画帧扫描页面。
- SystemJS 保留分包与动态加载，转换 import()/import.meta；其 resolve 适配遵循 Vite 所需的同步约定。
  页面样式只进入主线程，classic worker 不包含 DOM 样式注入器。
- ResizeObserver、AbortController 和 toggleAttribute 提供旧内核缺少的实现。
- 音频元数据模块使用受限的源码转换和 JSBI，补齐 BigInt 运算与 DataView 的 64 位读写；
  有原生 BigInt 的内核继续使用原生实现。没有将 64 位值直接转为可能丢失精度的 Number。

最低版本是实际 WebView 61，不是把 UA 改成 61。低于此版本未承诺兼容。
旧内核上的渐变使用 sRGB 近似；部分新式布局与动效可能有差异。WebGPU、GPU 模型分析、
高级特效和桌面专属功能不会由这些补丁提供；在线服务及登录仍取决于外部服务。

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
连续回退两层网页历史，保持插件打开且不触发退出计时；根页面双击返回关闭插件并显示频道主页。
音频桥接将媒体 `src` 的 DOM 属性读取还原为原始地址，修复原版播放器核对地址后跳过自动播放的问题，
不强行恢复用户主动暂停。`ntv-files.js` 提供原版导入器使用的只读文件句柄，原生多选结果可序列化保存，
从 IndexedDB 读回时仅恢复本适配器标记的句柄。取消选择也会结束 Promise，允许再次打开。

2026-10-20 在 Android 7.1.1 / WebView 119 实机复测新版文件夹弹窗：
取消后再次打开、进入目录、显示「3 个文件 · 2 首音乐 · 可导入 3 个文件」、
子文件夹显示「1 个文件 · 1 首音乐」、点击一次导入当前目录、子文件夹音频不混入本次导入均通过。
原版导入器识别两个 WAV 与匹配 LRC、页面重载后读取文件、所选本地音频播放和时间推进也通过。
此路径不使用 ActivityMonitor 模拟选择结果。Android 10+ SAF 授权分支未在本轮完成实机验证。
另外保留无需额外播放手势的 HTTP 代理音频播放检查。只使用测试自建文件，
不读取用户音乐，也不改变系统选择器的禁用状态。
测试代码见 `FoliaFullInstrumentation` 和 `app/src/androidTest/assets/folia-native-import.js`。本次没有宣称逐项验证所有账号、主题和在线服务。

2026-10-21 Chrome 68 兼容回归包含：原版页面启动、原版更新与首次引导按钮、
100%/150% 的真实触摸和飞鼠点击、深色设置页、HTTP 音频自动播放、文件夹取消/再次打开、
遥控器选择/进入/返回/导入、文件数量、本地 WAV 与 LRC 导入、IndexedDB 重载恢复、
本地音频播放时间推进，以及网页历史优先返回、根页双返回退到频道主页。
设备系统 WebView 未升级，测试使用独立包 `xiao.bu.tv.channeltest`。
首页额外检查背景画布与容器尺寸一致、轮播当前卡片居中、封面保持正方形；
在 Android 9 / WebView 68 和 Android 7.1.1 / WebView 119 上通过。
构建侧回归可运行 `node plugins/folia-full/tests/css-compat.mjs`，检查运行时 shader 样式和跨分包层级。

同日将 Android 7.1.1 真机恢复为出厂 WebView 61.0.3163.98 后，使用独立包复测：
真实元数据 worker 解析 WAV 时长、本地 WAV/LRC 导入与重载恢复、本地播放、HTTP 自动播放、
首页背景铺满、正方形封面、轮播居中、歌单进入/返回、100%/150% 触摸及飞鼠点击、
文件夹遥控器操作、深色设置、网页历史及双返回均通过。未升级或切换系统 WebView。
`node plugins/folia-full/tests/metadata-bigint.mjs` 检查无原生 BigInt 时的精确 64 位读写与运算。
在线账号登录、全部音频格式和高级 GPU 特效未逐项验证。
