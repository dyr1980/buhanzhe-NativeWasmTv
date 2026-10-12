package xiao.bu.tv;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** HTTP validators and content checksum used by channel-source and EPG caches. */
final class RemoteFileMetadata {
    final String requestUrl;
    final long contentLength;
    final String serverMd5;
    final String localMd5;
    final String etag;
    final long lastModified;

    RemoteFileMetadata(String requestUrl, long contentLength, String serverMd5,
            String localMd5, String etag, long lastModified) {
        this.requestUrl = safe(requestUrl);
        this.contentLength = contentLength;
        this.serverMd5 = normalizeMd5(serverMd5);
        this.localMd5 = normalizeMd5(localMd5);
        this.etag = safe(etag);
        this.lastModified = Math.max(0L, lastModified);
    }

    static RemoteFileMetadata fromJson(String json) {
        if (json == null || json.length() == 0) return null;
        try {
            JSONObject value = new JSONObject(json);
            return new RemoteFileMetadata(value.optString("url", ""),
                    value.optLong("length", -1L), value.optString("serverMd5", ""),
                    value.optString("localMd5", ""), value.optString("etag", ""),
                    value.optLong("lastModified", 0L));
        } catch (JSONException ignored) {
            return null;
        }
    }

    String toJson() {
        try {
            return new JSONObject().put("url", requestUrl)
                    .put("length", contentLength)
                    .put("serverMd5", serverMd5)
                    .put("localMd5", localMd5)
                    .put("etag", etag)
                    .put("lastModified", lastModified).toString();
        } catch (JSONException impossible) {
            return "";
        }
    }

    static RemoteFileMetadata fromResponse(HttpURLConnection connection,
            String requestUrl) {
        return new RemoteFileMetadata(requestUrl, responseLength(connection),
                responseMd5(connection), "", connection.getHeaderField("ETag"),
                connection.getLastModified());
    }

    RemoteFileMetadata verify(byte[] body, String label) throws IOException {
        if (contentLength >= 0L && body.length != contentLength) {
            throw new IOException(label + "大小校验失败：服务器声明 "
                    + contentLength + " 字节，实际 " + body.length + " 字节");
        }
        String digest = md5(body);
        if (serverMd5.length() > 0 && !serverMd5.equals(digest)) {
            throw new IOException(label + " MD5 校验失败");
        }
        return new RemoteFileMetadata(requestUrl, body.length,
                serverMd5, digest, etag, lastModified);
    }

    boolean appliesTo(String url) {
        return requestUrl.equals(safe(url));
    }

    void applyConditionalHeaders(HttpURLConnection connection, String url) {
        if (!appliesTo(url)) return;
        if (etag.length() > 0) connection.setRequestProperty("If-None-Match", etag);
        if (lastModified > 0L) connection.setIfModifiedSince(lastModified);
    }

    boolean matchesRemote(RemoteFileMetadata remote, File cachedFile) {
        if (remote == null || cachedFile == null || !cachedFile.isFile()
                || !requestUrl.equals(remote.requestUrl)) {
            return false;
        }
        long expectedLength = remote.contentLength >= 0L
                ? remote.contentLength : contentLength;
        if (expectedLength < 0L || cachedFile.length() != expectedLength) return false;
        boolean sameValidator = (remote.serverMd5.length() > 0
                && remote.serverMd5.equals(serverMd5))
                || (remote.serverMd5.length() == 0 && remote.etag.length() > 0
                && remote.etag.equals(etag));
        if (!sameValidator || localMd5.length() == 0) return false;
        try {
            String cachedMd5 = md5(cachedFile);
            return localMd5.equals(cachedMd5)
                    && (remote.serverMd5.length() == 0
                            || remote.serverMd5.equals(cachedMd5));
        } catch (IOException error) {
            return false;
        }
    }

    boolean sameContent(RemoteFileMetadata other) {
        return other != null && contentLength == other.contentLength
                && localMd5.length() > 0 && localMd5.equals(other.localMd5);
    }

    boolean matchesLocal(File file) {
        if (file == null || !file.isFile() || contentLength < 0L
                || file.length() != contentLength || localMd5.length() == 0) {
            return false;
        }
        try {
            return localMd5.equals(md5(file));
        } catch (IOException error) {
            return false;
        }
    }

    boolean shouldProbeMd5() {
        return serverMd5.length() > 0 && etag.length() == 0 && lastModified == 0L;
    }

    static void writeAtomically(File target, byte[] bytes, String label) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        recover(target);
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            output.write(bytes);
            output.flush();
        } finally {
            output.close();
        }
        if (backup.exists() && !backup.delete()) {
            temporary.delete();
            throw new IOException("无法准备" + label + "缓存");
        }
        boolean hadTarget = target.exists();
        if (hadTarget && !target.renameTo(backup)) {
            temporary.delete();
            throw new IOException("无法替换" + label + "缓存");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            if (hadTarget) backup.renameTo(target);
            throw new IOException("无法保存" + label + "缓存");
        }
        if (backup.exists()) backup.delete();
    }

    static void recover(File target) {
        if (target == null || target.exists()) return;
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        if (backup.isFile()) backup.renameTo(target);
    }

    private static long responseLength(HttpURLConnection connection) {
        String value = connection.getHeaderField("Content-Length");
        if (value == null) return -1L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String responseMd5(HttpURLConnection connection) {
        String[] headers = { "Content-MD5", "X-Checksum-MD5", "X-Content-MD5",
                "X-File-MD5", "X-MD5", "X-Ms-Blob-Content-Md5",
                "X-Amz-Meta-Md5" };
        for (String header : headers) {
            String normalized = normalizeMd5(connection.getHeaderField(header));
            if (normalized.length() > 0) return normalized;
        }
        String digest = connection.getHeaderField("Digest");
        String normalized = namedBase64Md5(digest, "md5=");
        if (normalized.length() > 0) return normalized;
        normalized = namedBase64Md5(connection.getHeaderField("X-Goog-Hash"), "md5=");
        if (normalized.length() > 0) return normalized;
        // An ETag is an opaque cache validator, even when it looks like an MD5.
        // NAS servers may hash a template or revision rather than these bytes.
        return "";
    }

    private static String namedBase64Md5(String value, String marker) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.US);
        int start = lower.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = value.indexOf(',', start);
        String encoded = (end < 0 ? value.substring(start) : value.substring(start, end)).trim();
        return normalizeMd5(encoded);
    }

    private static String normalizeMd5(String value) {
        String trimmed = safe(value).trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith(":") && trimmed.endsWith(":")
                && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (isHexMd5(trimmed)) return trimmed.toLowerCase(Locale.US);
        try {
            byte[] decoded = Base64.decode(trimmed, Base64.DEFAULT);
            return decoded.length == 16 ? hex(decoded) : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean isHexMd5(String value) {
        if (value == null || value.length() != 32) return false;
        for (int index = 0; index < value.length(); index++) {
            if (Character.digit(value.charAt(index), 16) < 0) return false;
        }
        return true;
    }

    private static String md5(byte[] bytes) throws IOException {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("设备不支持 MD5", impossible);
        }
    }

    private static String md5(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("设备不支持 MD5", impossible);
        } finally {
            input.close();
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
