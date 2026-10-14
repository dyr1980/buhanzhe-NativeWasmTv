function updatePlaybackPlugins() {
  var button = document.getElementById("updatePlaybackPlugins");
  if (button.disabled) return;
  button.disabled = true;
  button.textContent = "正在检查更新…";
  api("/api/settings", {updateCjsPlugin: true}, function(error, result) {
    button.disabled = false;
    button.textContent = "更新播放插件";
    if (error) { toast(error.message, true); return; }
    toast((result.message || "插件更新检查完成") + "；正在使用的插件重启后生效");
    refresh();
  }, 120000);
}
function toggleRadioUiOptions() {
  var panel = document.getElementById("radioUiOptions");
  var open = panel.style.display === "none";
  panel.style.display = open ? "block" : "none";
  var button = document.getElementById("radioUiToggle");
  button.textContent = open ? "收起" : "设置";
  button.setAttribute("aria-expanded", open ? "true" : "false");
}
function resetRadioUiUrl() {
  document.getElementById("radioUiUrl").value="";
  saveRadioUiUrl(false);
}
function saveRadioUiUrl(download) {
  api("/api/radio-ui", {url: document.getElementById("radioUiUrl").value, download: download}, function(error, result) {
    if(error){toast(error.message,true);return;}
    toast(download ? "电台界面已更新" : "界面地址已保存");
    loadRadioUiUrl();
  }, download ? 120000 : 15000);
}
function loadRadioUiUrl() {
  api("/api/radio-ui", null, function(error,result) {
    var status = document.getElementById("radioUiStatus");
    if(error){status.textContent=error.message;return;}
    document.getElementById("radioUiUrl").value=result.url || result.defaultUrl || "";
    var styles = result.styles || ["classic"], select = document.getElementById("radioUiStyle");
    for(var i=0;i<select.options.length;i++) select.options[i].disabled=styles.indexOf(select.options[i].value)<0;
    select.value=result.style || "classic";
    status.textContent=(result.loaded ? "已安装 · 版本 " + result.version : "未安装") + (result.error ? " · " + result.error : "");
  });
}
function saveRadioUiStyle() {
  var select=document.getElementById("radioUiStyle");
  select.disabled=true;
  api("/api/radio-ui", {style:select.value}, function(error) {
    select.disabled=false;
    if(error)toast(error.message,true);else toast("唱片样式已保存");
    loadRadioUiUrl();
  });
}
function installRadioUi() {
  var file = document.getElementById("radioUiJar").files[0];
  if (!file) { toast("请选择电台界面 JAR", true); return; }
  if (file.size > 2 * 1024 * 1024) { toast("JAR 不能超过 2 MB", true); return; }
  var xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/radio-ui", true);
  xhr.timeout = 30000;
  xhr.setRequestHeader("Content-Type", "application/octet-stream");
  xhr.onload = function () {
    try {
      var result = JSON.parse(xhr.responseText);
      if (xhr.status !== 200 || !result.ok) throw new Error(result.error || "导入失败");
      toast("电台界面已替换，版本 " + result.version);
      loadRadioUiUrl();
    } catch (error) { toast(error.message, true); }
  };
  xhr.onerror = xhr.ontimeout = function () { toast("电台界面导入失败", true); };
  toast("正在导入电台界面…");
  xhr.send(file);
}
function saveReverse() {
  var reverse = document.getElementById("reverse");
  api("/api/settings", { reverseKeys: reverse.checked }, function (error) {
    if (error) {
      reverse.checked = !reverse.checked;
      toast(error.message, true);
      return;
    }
    toast("设置已保存");
  });
}
function saveAutoStart() {
  var input = document.getElementById("autoStart");
  api("/api/settings", { autoStart: input.checked }, function (error) {
    if (error) {
      input.checked = !input.checked;
      toast(error.message, true);
      return;
    }
    toast(input.checked ? "已开启开机自启动" : "已关闭开机自启动");
  });
}
function saveDecoder() {
  var decoder = document.getElementById("decoder"),
    hardware = document.getElementById("hardwareDecoder"),
    surface = document.getElementById("surfaceMode"),
    transport = document.getElementById("rtspTransport"),
    sps = document.getElementById("spsCompatibility");
  toast("正在切换解码配置…");
  api(
    "/api/settings",
    {
      decodeMode: decoder.value,
      hardwareDecoder: hardware.value,
      surfaceMode: surface.value,
      rtspTransport: transport.value,
      h264SpsCompatibility: sps.value === "true"
    },
    function (error) {
      if (error) {
        toast(error.message, true);
        refresh();
        return;
      }
      toast("解码配置已保存，正在重新播放");
    }
  );
}
function saveHdrMode() {
  var select = document.getElementById("hdrMode");
  select.disabled = true;
  api("/api/settings", { hdrMode: select.value }, function (error) {
    select.disabled = false;
    if (error) { toast(error.message, true); refresh(); return; }
    toast("HDR 模式已保存，正在重新播放");
    refresh();
  });
}
function renderHardwareDecoders() {
  var select = document.getElementById("hardwareDecoder"),
    selected = state.settings.hardwareDecoder || "auto",
    items = state.settings.hardwareDecoders || [];
  select.innerHTML = "";
  var automatic = document.createElement("option");
  automatic.value = "auto";
  automatic.textContent = "自动选择";
  select.appendChild(automatic);
  for (var i = 0; i < items.length; i++) {
    var option = document.createElement("option");
    option.value = items[i];
    option.textContent = items[i];
    select.appendChild(option);
  }
  select.value = selected;
  if (select.value !== selected) {
    var saved = document.createElement("option");
    saved.value = selected;
    saved.textContent = selected + "（当前）";
    select.appendChild(saved);
    select.value = selected;
  }
}
function renderPageState() {
  var s = state.settings;
  var proxy = document.getElementById("githubProxyUrl"), mode = document.getElementById("githubConnectionMode");
  if (!mode._dirty) mode.value = s.githubProxyEnabled === false ? "direct"
    : githubProxyChoice(s.githubProxyBaseUrl || "https://gh-proxy.org/");
  if (document.activeElement !== proxy && !proxy._dirty) {
    proxy.value = s.githubProxyBaseUrl || "https://gh-proxy.org/";
  }
  renderGithubProxyFields();
  document.getElementById("dnsMode").value = s.dnsMode || "ali";
  document.getElementById("reverse").checked = s.reverseKeys === true;
  document.getElementById("autoStart").checked = s.autoStart === true;
  document.getElementById("decoder").value = s.decodeMode || "auto";
  document.getElementById("hdrMode").value = s.hdrMode === "passthrough" ? "hardware" : s.hdrMode || "hardware";
  document.getElementById("surfaceMode").value = s.surfaceMode || "normal";
  document.getElementById("rtspTransport").value = s.rtspTransport || "tcp";
  document.getElementById("spsCompatibility").value = String(s.h264SpsCompatibility !== false);
  renderHardwareDecoders();
}
function saveDns(){var dns=document.getElementById('dnsMode');toast('正在切换 DNS…');api('/api/settings',{dnsMode:dns.value},function(error){if(error){toast(error.message,true);refresh();return}toast('DNS 配置已保存')})}
var githubProxySaving = false;
function githubProxyChoice(url) {
  var value = String(url).replace(/\/+$/, "") + "/";
  return /^(https:\/\/)(gh-proxy\.org|gh-proxy\.com|ghfile\.geekertao\.top|github-proxy\.memory-echoes\.cn|github\.tbap\.top)\/$/.test(value)
    ? value : "custom";
}
function renderGithubProxyFields() {
  var mode = document.getElementById("githubConnectionMode"), custom = mode.value === "custom";
  document.getElementById("githubProxyCustom").style.display = custom ? "" : "none";
  document.getElementById("githubProxyUrl").disabled = !custom || githubProxySaving;
  document.getElementById("githubProxySave").disabled = githubProxySaving;
  mode.disabled = githubProxySaving;
}
function changeGithubConnectionMode() {
  var mode = document.getElementById("githubConnectionMode");
  mode._dirty = true;
  renderGithubProxyFields();
  if (mode.value !== "custom") saveGithubProxy();
}
function saveGithubProxy() {
  if (githubProxySaving) return;
  var input = document.getElementById("githubProxyUrl");
  var value = input.value.replace(/^\s+|\s+$/g, ""),
    mode = document.getElementById("githubConnectionMode"), enabled = mode.value !== "direct",
    settings = { githubProxyEnabled: enabled };
  if (enabled) settings.githubProxyBaseUrl = mode.value === "custom" ? value : mode.value;
  githubProxySaving = true;
  renderGithubProxyFields();
  api("/api/settings", settings, function (error) {
    githubProxySaving = false;
    renderGithubProxyFields();
    if (error) {
      if (mode.value !== "custom") { mode._dirty = false; renderPageState(); }
      toast(error.message, true); return;
    }
    input._dirty = false;
    mode._dirty = false;
    state.settings.githubProxyEnabled = enabled;
    if (enabled) state.settings.githubProxyBaseUrl = settings.githubProxyBaseUrl || "https://gh-proxy.org/";
    renderPageState();
    toast(enabled ? "GitHub 加速已保存，新请求生效" : "已切换为 GitHub 直连，新请求生效");
    refresh();
  });
}
document.getElementById("githubProxyUrl").oninput = function () { this._dirty = true; };
startPage();

loadRadioUiUrl();
