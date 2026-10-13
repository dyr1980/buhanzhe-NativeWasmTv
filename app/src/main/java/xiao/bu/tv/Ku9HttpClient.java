package xiao.bu.tv;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal HTTP adapter exposed to Ku9 scripts. */
final class Ku9HttpClient {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.0; TV) AppleWebKit/537.36 "
                    + "Chrome/120.0 Mobile Safari/537.36";

    private Ku9HttpClient() {
    }

    static String getText(String url, JSONObject headers, int maxBytes) throws IOException {
        HttpURLConnection connection = open(url, "GET", headers, true);
        boolean consumed = false;
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("酷9脚本请求失败: HTTP " + code);
            }
            String text = readUtf8(connection.getInputStream(), maxBytes);
            consumed = true;
            return text;
        } finally {
            if (!consumed) connection.disconnect();
        }
    }

    static String postText(String url, String body, String headersJson, int maxBytes) {
        try {
            return new JSONObject(requestJson(url, "POST", headersJson, body, true, maxBytes))
                    .optString("body", "");
        } catch (JSONException error) {
            return "";
        }
    }

    static String requestJson(String url, String method, String headersJson, String body,
            boolean followRedirects, int maxBytes) {
        return requestJson(url, method, headersJson, body, followRedirects, maxBytes, 0);
    }

    static String requestJson(String url, String method, String headersJson, String body,
            boolean followRedirects, int maxBytes, int timeoutMs) {
        JSONObject result = new JSONObject();
        HttpURLConnection connection = null;
        boolean consumed = false;
        try {
            String requestMethod = TextUtils.isEmpty(method) ? "GET"
                    : method.toUpperCase(Locale.US);
            JSONObject requestHeaders = parseHeaders(headersJson);
            // Host-only option: a probe must not return a whole ignored-Range TS to JS.
            int responseLimit = Math.min(maxBytes, Math.max(0, requestHeaders.optInt("X-Ku9-Response-Limit")));
            requestHeaders.remove("X-Ku9-Response-Limit");
            boolean binaryRequest = "base64".equals(requestHeaders.optString("X-Ku9-Request-Encoding"));
            boolean binaryResponse = "base64".equals(requestHeaders.optString("X-Ku9-Response-Encoding"));
            requestHeaders.remove("X-Ku9-Request-Encoding");
            requestHeaders.remove("X-Ku9-Response-Encoding");
            byte[] requestBody = body == null ? null : binaryRequest
                    ? Base64.decode(body, Base64.DEFAULT) : body.getBytes("UTF-8");
            connection = open(url, requestMethod, requestHeaders, followRedirects, timeoutMs);
            if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod) && requestBody != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(requestBody);
                connection.getOutputStream().close();
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            if (input != null && "gzip".equalsIgnoreCase(connection.getContentEncoding()))
                input = new GZIPInputStream(input);
            result.put("code", code);
            result.put("body", input == null ? "" : binaryResponse
                    ? Base64.encodeToString(responseLimit > 0 ? readPrefixBytes(input, responseLimit)
                            : readBytes(input, maxBytes), Base64.NO_WRAP) : responseLimit > 0
                    ? readPrefix(input, responseLimit) : readUtf8(input, maxBytes));
            if (binaryResponse) result.put("bodyEncoding", "base64");
            consumed = responseLimit == 0;
            String finalUrl = connection.getURL().toString();
            String location = connection.getHeaderField("Location");
            if (!followRedirects && !TextUtils.isEmpty(location)) {
                try {
                    finalUrl = connection.getURL().toURI().resolve(location).toString();
                } catch (Exception ignored) {
                    finalUrl = location;
                }
            }
            result.put("url", finalUrl);
            // Ku9 names the effective/follow-up URL "furl".
            result.put("furl", finalUrl);
            JSONObject headers = new JSONObject();
            for (Map.Entry<String, List<String>> entry
                    : connection.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    headers.put(entry.getKey(), entry.getValue().get(0));
                }
            }
            result.put("headers", headers);
        } catch (Exception error) {
            try {
                result.put("code", 0);
                result.put("body", "");
                result.put("error", message(error));
            } catch (JSONException ignored) {
            }
        } finally {
            if (connection != null && !consumed) {
                connection.disconnect();
            }
        }
        return result.toString();
    }

    static JSONObject parseHeaders(String json) {
        if (!TextUtils.isEmpty(json)) {
            try {
                return new JSONObject(json);
            } catch (JSONException ignored) {
            }
        }
        return new JSONObject();
    }

    private static String readPrefix(InputStream input, int limit) throws IOException {
        return new String(readPrefixBytes(input, limit), "UTF-8");
    }

    private static byte[] readPrefixBytes(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] block = new byte[Math.min(limit, 4096)];
        while (output.size() < limit) {
            int count = input.read(block, 0, Math.min(block.length, limit - output.size()));
            if (count < 0) break;
            output.write(block, 0, count);
        }
        return output.toByteArray();
    }

    static String readUtf8(InputStream input, int maxBytes) throws IOException {
        return new String(readBytes(input, maxBytes), "UTF-8");
    }

    private static byte[] readBytes(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int count;
        try {
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IOException("酷9脚本响应超过 " + maxBytes + " 字节限制");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            // Release a completely consumed response to OkHttp's shared pool.
            input.close();
        }
    }

    private static HttpURLConnection open(String url, String method, JSONObject headers,
            boolean followRedirects) throws IOException {
        return open(url, method, headers, followRedirects, 0);
    }

    private static HttpURLConnection open(String url, String method, JSONObject headers,
            boolean followRedirects, int timeoutMs) throws IOException {
        java.net.URL target = URI.create(url.trim()).toURL();
        // Connection: close is an HTTP/1.1 request; HTTP/2 cannot carry it.
        // Honor the script's transport choice rather than silently dropping it.
        boolean http1 = false;
        if (headers != null) {
            Iterator<String> headerNames = headers.keys();
            while (headerNames.hasNext()) {
                String name = headerNames.next();
                if ("Connection".equalsIgnoreCase(name)
                        && "close".equalsIgnoreCase(headers.optString(name).trim())) http1 = true;
            }
        }
        HttpURLConnection connection = http1 ? NetworkClient.openHttp1(target, timeoutMs) : timeoutMs > 0
                ? NetworkClient.openBounded(target, timeoutMs) : NetworkClient.open(target);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(followRedirects);
        connection.setRequestMethod(method == null ? "GET" : method.toUpperCase(Locale.US));
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        Iterator<String> names = (headers == null ? new JSONObject() : headers).keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!TextUtils.isEmpty(name)) {
                connection.setRequestProperty(name, headers.optString(name, ""));
            }
        }
        return connection;
    }

    private static String message(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return TextUtils.isEmpty(message) ? "未知错误" : message;
    }
}
