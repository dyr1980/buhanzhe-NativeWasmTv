# Folia JAR 频道首版

通过 M3U 的 HTTPS `.jar` 地址下载插件，直接显示 JAR 中的 `folia/index.html`。
页面、脚本、样式均随 JAR 分发，不需要部署网页或 Node 服务。Java 获取歌曲信息、
封面及 LRC，音频由 nTv 播放，WebView 接收播放器时间轴。

本版是独立的小型集成原型，包含搜索、基础歌词滚动和高亮；**尚未移植 Folia 原版
React 应用和动画主题**。匹配评分参考 Folia 的算法并作 Java 适配，见 `NOTICE`。

## M3U

```m3u
#EXTM3U
#EXTINF:-1 group-title="音乐",Folia
https://你的服务器/folia.jar?sha256=该JAR的64位SHA256
```

仅填写插件地址和 SHA-256 时，显示 JAR 内的搜索页面。搜索当前使用网易云接口，
选择歌曲后尝试其公开播放地址；账号、付费歌曲和登录未接入，不能保证每首搜索结果可播放。

也可以配置自己可播放的音频，自动查询封面和歌词：

```text
https://你的服务器/folia.jar?sha256=HASH&audio=https%3A%2F%2F你的服务器%2Fsong.mp3&title=歌名&artist=歌手
```

`dist/example.m3u` 由构建脚本生成，包含当前产物的真实校验值；示例域名需要替换。
查询参数的值需 URL 编码，特别是包含 `&`、`?` 或 `#` 的资源地址。

| 参数 | 用途 |
| --- | --- |
| `sha256` | 必填，JAR 的 64 位小写 SHA-256；更新 JAR 后需同步更新 |
| `audio` | 可选，HTTP(S) 音频地址；省略时显示搜索页 |
| `title`、`artist` | 自动匹配歌曲的歌名与歌手 |
| `songId` | 可选，网易云歌曲 ID，跳过自动搜索匹配 |
| `durationMs` | 可选，歌曲时长，毫秒；帮助排除不同版本 |
| `cover` | 可选，显式 HTTP(S) 封面地址 |
| `lyrics` | 可选，显式 HTTP(S) LRC 地址 |

Java 后台获取并缓存数据。显式提供 `cover` 和 `lyrics` 后不需要音乐平台查询。
封面或歌词获取失败时显示原因，音频继续播放。当前按行同步，增强 LRC 的逐字标记
仅作文本清理，不宣称支持逐字动画。

## 显示设置

分辨率和缩放率读取 nTv 的浏览器配置，支持 720p、1080p、2k、4k。
使用与浏览器一致的逻辑视口计算：`布局宽度 = 分辨率宽度 / 缩放率`，
物理 WebView 填满可用区域。更改浏览器设置时同步应用，无需重下 JAR。

## 构建

```powershell
python plugins/folia/build.py
```

可传 `--sdk` 和 `--java-home`。需要 Java 8、Android 30 平台、Build Tools 30.0.3。
输出 `dist/folia.jar`、`dist/folia.sha256`、`dist/example.m3u`。
宿主必须包含新增的 `ChannelPlugin` API 和 JAR 频道加载入口，旧版 nTv 无法识别。

JAR 包含 `classes.dex`、`channel-plugin.json` 和页面资源，不重复包含宿主 API。
下载限额 8 MiB，校验后按哈希保存到应用私有目录，可复用已下载版本。
频道切换会移除页面并销毁 WebView。插件代码具有宿主权限，SHA-256 校验用于固定内容，
不提供执行沙箱；应使用自己信任的插件和频道列表。

## 验证

`FoliaChannelInstrumentation` 覆盖 HTTPS 下载、M3U 分发、JAR 内页面、
Java 获取 LRC、动态切换视口、歌词跳转同步与销毁。
`test-server.py` 提供本机 HTTPS JAR/音频/歌词/封面夹具，证书信任仅在测试进程设置。
需要 Python 的 `cryptography`、`Pillow` 和 FFmpeg。音频测试可用
`http://127.0.0.1:39573/song.mp3`，避免给 IJK 的原生 TLS 层注入测试证书。

```powershell
python plugins/folia/test-server.py
```

已在 Android 7.1.1（API25）独立包 `xiao.bu.tv.channeltest` 上通过：
HTTPS 下载与校验、M3U 频道入口、JAR 内网页、Java 获取歌词、原生 MP3 播放、
1080p/150% 和 4k/200% 视口切换、歌词时钟跳转和页面销毁。

Android 4.x 的编译路径保留，但尚未完成旧 WebView 的实机兼容验证。
