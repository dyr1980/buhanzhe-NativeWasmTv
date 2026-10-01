package xiao.bu.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads a public HTTPS userscript without exposing the television as an SSRF proxy. */
final class UserScriptImporter {
    private static final int MAX_SCRIPT_BYTES = 262144;
    private static final int MAX_METADATA_BYTES = 524288;
    private static final int MAX_REDIRECTS = 5;
    private static final Pattern GREASY_PATH = Pattern.compile(
            "^/(?:([A-Za-z-]+)/)?scripts/(\\d+)(?:-[^/]*)?/?$");
    private static final Pattern OPEN_USER_JS_PATH = Pattern.compile(
            "^/scripts/([^/]+)/([^/]+)/?$");
    private static final Pattern METADATA_LINE = Pattern.compile(
            "^\\s*//\\s*@([^\\s]+)\\s*(.*)$");
    private static final Pattern GM_CALL = Pattern.compile("\\b(?:GM_[A-Za-z]|GM\\.[A-Za-z])");

    private UserScriptImporter() { }

    static JSONObject importScript(String requestedUrl) throws Exception {
        URL sourceUrl = normalizeSourceUrl(requestedUrl);
        String host = sourceUrl.getHost().toLowerCase(Locale.US);
        if ("home.greasyfork.org.cn".equals(host) && sourceUrl.getRef() != null
                && sourceUrl.getRef().startsWith("https://")) {
            sourceUrl = normalizeSourceUrl(sourceUrl.getRef());
            host = sourceUrl.getHost().toLowerCase(Locale.US);
        }
        Matcher greasy = GREASY_PATH.matcher(sourceUrl.getPath());
        if ((isGreasyHost(host) || isGreasyMirror(host)) && greasy.matches()) {
            if (isGreasyMirror(host)) {
                // The mirror keeps Greasy Fork's numeric ids and accepts any filename after it.
                sourceUrl = new URL("https://update.greasyfork.org.cn/scripts/"
                        + greasy.group(2) + "/script.user.js");
            } else {
                String locale = greasy.group(1);
                if (locale == null || locale.length() == 0) locale = "en";
                String apiHost = host.contains("sleazyfork")
                        ? "api.sleazyfork.org" : "api.greasyfork.org";
                Download metadata = download(new URL("https://" + apiHost + "/" + locale
                        + "/scripts/" + greasy.group(2) + ".json"), MAX_METADATA_BYTES,
                        "application/json");
                JSONObject data = new JSONObject(metadata.text());
                String codeUrl = data.optString("code_url", "");
                if (codeUrl.length() == 0) throw new IOException("脚本网站没有提供安装地址");
                sourceUrl = new URL(codeUrl);
            }
        } else if (isGreasyMirror(host) && sourceUrl.getPath().matches(
                "^/(?:[A-Za-z-]+/)?scripts/?$")) {
            throw new IOException("这是脚本搜索页，请先打开一个具体脚本的详情页再导入");
        } else if ("openuserjs.org".equals(host)) {
            Matcher openUserJs = OPEN_USER_JS_PATH.matcher(sourceUrl.getPath());
            if (openUserJs.matches()) {
                sourceUrl = new URL("https://openuserjs.org/install/" + openUserJs.group(1)
                        + "/" + openUserJs.group(2) + ".user.js");
            }
        } else if ("github.com".equals(host)) {
            String path = sourceUrl.getPath();
            int blob = path.indexOf("/blob/");
            if (blob > 0) {
                sourceUrl = new URL("https://raw.githubusercontent.com"
                        + path.substring(0, blob) + "/" + path.substring(blob + 6));
            }
        }

        // Keep the stable install endpoint instead of a versioned CDN redirect so a
        // later click can update the existing entry rather than creating a duplicate.
        URL installUrl = sourceUrl;
        Download script = download(sourceUrl, MAX_SCRIPT_BYTES, "text/javascript");
        return parseScript(script.text(), nameFromUrl(script.url), installUrl.toString());
    }

    static JSONObject importLocalScript(String source, String fileName) throws Exception {
        if (fileName == null || !fileName.toLowerCase(Locale.US).endsWith(".js")) {
            throw new IOException("请选择 .user.js 或 .js 脚本文件");
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceFirst("(?i)(?:\\.user)?\\.js$", "");
        return parseScript(source, name.length() == 0 ? "本地脚本"
                : name.substring(0, Math.min(80, name.length())), "");
    }

    private static JSONObject parseScript(String source, String fallbackName, String installUrl)
            throws Exception {
        if (source == null || source.length() > MAX_SCRIPT_BYTES
                || source.getBytes("UTF-8").length > MAX_SCRIPT_BYTES) {
            throw new IOException("脚本文件超过 256KB");
        }
        if (source.startsWith("\ufeff")) source = source.substring(1);
        int metadataStart = source.indexOf("// ==UserScript==");
        int metadataEnd = source.indexOf("// ==/UserScript==");
        if (metadataStart < 0 || metadataEnd <= metadataStart) {
            throw new IOException("不是有效的 UserScript，请检查脚本元数据头");
        }
        Metadata parsed = parseMetadata(source.substring(metadataStart, metadataEnd));
        if (parsed.name.length() == 0) parsed.name = fallbackName;
        JSONArray warnings = compatibilityWarnings(source, parsed);
        return new JSONObject().put("ok", true).put("script", new JSONObject()
                .put("name", parsed.name)
                .put("enabled", true)
                .put("source", source)
                .put("installUrl", installUrl)
                .put("version", parsed.version)
                .put("description", parsed.description)
                .put("matches", new JSONArray(parsed.matches)))
                .put("warnings", warnings);
    }

    private static URL normalizeSourceUrl(String value) throws Exception {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0 || text.length() > 4096) throw new IOException("请输入脚本安装地址");
        URL url = new URL(text);
        validatePublicHttps(url);
        return url;
    }

    private static boolean isGreasyHost(String host) {
        return "greasyfork.org".equals(host) || "www.greasyfork.org".equals(host)
                || "api.greasyfork.org".equals(host) || "sleazyfork.org".equals(host)
                || "www.sleazyfork.org".equals(host) || "api.sleazyfork.org".equals(host);
    }

    private static boolean isGreasyMirror(String host) {
        return "gfork.zh-tw.eu.org".equals(host) || "www.gfork.zh-tw.eu.org".equals(host);
    }

    private static Download download(URL initial, int limit, String accept) throws Exception {
        URL current = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validatePublicHttps(current);
            HttpURLConnection connection = NetworkClient.open(current);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(18000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", accept + ", text/plain;q=0.9, */*;q=0.2");
            connection.setRequestProperty("User-Agent", "nTv UserScript/" + BuildConfig.VERSION_NAME);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().length() == 0) {
                        throw new IOException("脚本下载重定向无效");
                    }
                    current = new URL(current, location);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("脚本下载失败：HTTP " + status);
                }
                int length = connection.getContentLength();
                if (length > limit) throw new IOException("脚本文件超过 256KB");
                InputStream input = connection.getInputStream();
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(
                            length > 0 ? Math.min(length, limit) : 8192);
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > limit) throw new IOException("脚本文件超过 256KB");
                        output.write(buffer, 0, count);
                    }
                    return new Download(current, output.toByteArray(), connection.getContentType());
                } finally {
                    input.close();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("脚本下载重定向次数过多");
    }

    private static void validatePublicHttps(URL url) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null
                || url.getPort() != -1 && url.getPort() != 443 || url.getHost().length() == 0) {
            throw new IOException("仅支持公开网站的 HTTPS 脚本地址");
        }
        List<InetAddress> addresses = NetworkClient.sharedClient().dns().lookup(url.getHost());
        if (addresses.isEmpty()) throw new IOException("无法解析脚本网站地址");
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) throw new IOException("不允许从局域网或本机地址导入脚本");
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254) && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && b == 168);
        }
        return bytes.length == 16 && (bytes[0] & 0xfe) != 0xfc
                && !((bytes[0] & 255) == 0xfe && (bytes[1] & 0xc0) == 0x80);
    }

    private static Metadata parseMetadata(String block) {
        Metadata result = new Metadata();
        for (String raw : block.replace("\r", "").split("\n")) {
            Matcher matcher = METADATA_LINE.matcher(raw);
            if (!matcher.matches()) continue;
            String key = matcher.group(1), value = matcher.group(2).trim();
            if ("name:zh-CN".equalsIgnoreCase(key)) result.localizedName = value;
            else if ("name:zh".equalsIgnoreCase(key) && result.localizedName.length() == 0)
                result.localizedName = value;
            else if ("name".equalsIgnoreCase(key)) result.name = value;
            else if ("version".equalsIgnoreCase(key)) result.version = value;
            else if ("description:zh-CN".equalsIgnoreCase(key)) result.localizedDescription = value;
            else if ("description".equalsIgnoreCase(key)) result.description = value;
            else if ("match".equalsIgnoreCase(key) || "include".equalsIgnoreCase(key))
                result.matches.add(value);
            else if ("grant".equalsIgnoreCase(key)) result.grants.add(value);
            else if ("require".equalsIgnoreCase(key)) result.requires.add(value);
            else if ("resource".equalsIgnoreCase(key)) result.resources.add(value);
        }
        if (result.localizedName.length() > 0) result.name = result.localizedName;
        if (result.localizedDescription.length() > 0) result.description = result.localizedDescription;
        if (result.name.length() > 80) result.name = result.name.substring(0, 80);
        return result;
    }

    private static JSONArray compatibilityWarnings(String source, Metadata metadata) {
        Set<String> warnings = new LinkedHashSet<String>();
        for (String grant : metadata.grants) {
            if (grant.length() > 0 && !"none".equalsIgnoreCase(grant)) {
                warnings.add("脚本声明了 GM_* 权限，nTv 暂不提供这些 API");
                break;
            }
        }
        if (!metadata.requires.isEmpty()) warnings.add("脚本使用 @require，外部依赖不会自动加载");
        if (!metadata.resources.isEmpty()) warnings.add("脚本使用 @resource，外部资源不会自动加载");
        if (GM_CALL.matcher(source).find())
            warnings.add("检测到 GM_* 调用，脚本可能无法完整运行");
        return new JSONArray(warnings);
    }

    private static String nameFromUrl(URL url) {
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        name = name.replaceFirst("(?i)\\.user\\.js$", "").replace('_', ' ');
        return name.length() == 0 ? "导入的脚本" : name.substring(0, Math.min(80, name.length()));
    }

    private static final class Download {
        final URL url;
        final byte[] body;
        final String contentType;
        Download(URL url, byte[] body, String contentType) {
            this.url = url; this.body = body; this.contentType = contentType;
        }
        String text() {
            return new String(body, Charset.forName("UTF-8"));
        }
    }

    private static final class Metadata {
        String name = "";
        String localizedName = "";
        String version = "";
        String description = "";
        String localizedDescription = "";
        final Set<String> matches = new LinkedHashSet<String>();
        final Set<String> grants = new LinkedHashSet<String>();
        final Set<String> requires = new LinkedHashSet<String>();
        final Set<String> resources = new LinkedHashSet<String>();
    }
}
