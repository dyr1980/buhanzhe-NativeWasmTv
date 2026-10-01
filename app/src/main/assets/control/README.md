# 局域网管理网站

此目录整体打入 Debug 和 Release APK，无需在线下载管理页面。

```text
index.html             一级入口
pages/*.html           独立二级页面（groups 是频道配置的子页面）
css/common.css         全站公共样式
js/common.js           HTTP 请求、连接状态、页面生命周期
js/navigation.js       多页来源记录、弹层历史与统一返回（不依赖 referrer）
js/pointer-queue.js    飞鼠串行事件队列，逐帧合并移动、保留按键边界
js/pages/*.js          对应页面的渲染和交互逻辑
```

- 页面通过真实 HTML 地址跳转，不使用 hash 路由。内部链接与按钮统一经过 `NtvNavigation.go` / `navigateTo`。一次性来源令牌通过当前标签页的 sessionStorage 传递，接收后去掉 URL 参数；页面编号、可信上一页、安全首页兜底与界面状态保存在 history.state。不依赖 referrer、history.length 或“历史必须含首页”。禁用存储时安全降级回首页。
- 返回按钮和 APP 系统返回共用 `NtvNavigation.back`：先关闭弹层，再返回可信管理页，无来源则 replace 到首页。弹层使用一个同文档历史项，浏览器返回关闭顶部弹层；手动关闭、弹层切换、编辑取消和刷新均清理/恢复该项，避免幽灵返回。新增弹层必须调用 overlayOpen/overlayClosed，关闭函数返回 false 可阻止丢弃编辑。
- 首页不拦截浏览器离站；APP 首页系统返回只 finish 管理 Activity，绝不调用播放器返回或退出。脚本不可用时原生仅回紧邻的本站管理页，或替换为首页。
- 飞鼠离页保存模式、输入草稿、键盘展开状态，停止连发和传感器，按输入队列顺序取消鼠标按下后跳转（网络故障最多等待 1.5 秒）。返回保留界面但不会自动重启陀螺仪。普通页面滚动位置按历史项恢复；媒体控制离页只停止控制端轮询，不控制电视播放。
- `ControlSite.java` 是本地 HTTP 服务的静态资源白名单，新增页面或依赖时同步登记。所有 API 保持 `/api/…` 原地址。
- 每个页面只加载公共脚本和自己的脚本。飞鼠传感器与媒体轮询在各自页面退出/隐藏时暂停；浏览器返回后恢复。
- 频道配置中未保存的源和 EPG 输入保存在当前标签页的 sessionStorage；电视端配置发生变化时，不用旧草稿覆盖新配置。
- 保持 ES5 语法，管理网站仍兼容旧 Android WebView。Ku9 的 Android 版本要求不受此拆分影响。
- 原有独立在线页面 `/video-recorder.html`、`/flymouse.html` 继续走原来的分发方式；`/pages/flymouse.html` 是管理站内置的飞鼠页面。

从仓库根目录运行回归测试（Node.js 22.13+）：

```powershell
npm ci --prefix scripts
npm test --prefix scripts
```

测试覆盖页面独立初始化、资源/事件依赖、ES5 语法、返回接口、频道草稿、10000 频道解析合并、媒体轮询及视频截屏逻辑。

飞鼠移动、按下、松开、滚轮和键盘事件使用同一个队列。超时不重放旧点击，先取消旧拖动。
`docs/pointer-queue.js` 为在线页面使用的同一份脚本；修改后同步，回归测试会检查两份文件一致。

静态资源白名单和 MIME 测试（JDK 8+，仓库根目录）：

```powershell
New-Item -ItemType Directory -Force .codex-tmp/control-site-tests
javac -encoding UTF-8 -d .codex-tmp/control-site-tests app/src/main/java/xiao/bu/tv/ControlSite.java scripts/TestControlSite.java
java -cp .codex-tmp/control-site-tests xiao.bu.tv.TestControlSite app/src/main/assets/control
```
