package xiao.bu.tv;

import android.util.Base64;
import android.os.Build;
import android.os.SystemClock;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Loads a channel catalog from another nTv device without copying its settings. */
final class RemoteCatalogClient {
    private final java.util.Map<String, String> playbackRoutes = new java.util.HashMap<String, String>();

    synchronized void changePlaybackRoute(String previous, String next) {
        for (java.util.Map.Entry<String, String> route : playbackRoutes.entrySet()) {
            route.setValue(next);
        }
        playbackRoutes.put(previous, next);
        playbackRoutes.remove(next);
    }

    synchronized void clearPlaybackRoutes() { playbackRoutes.clear(); }

    private synchronized String playbackHost(String original) {
        String next = playbackRoutes.get(original);
        return next == null ? original : next;
    }
    static final int TAKEOVER_PROTOCOL = 1;
    static final int APK_TRANSFER_PROTOCOL = 1;

    private static final String SOURCE_PREFIX = "ntvremote:";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int POINTER_CONNECT_TIMEOUT_MS = 1500;
    private static final int POINTER_READ_TIMEOUT_MS = 2500;
    private static final long RESOLVE_TIMEOUT_MS = 30000L;

    private static volatile int[][] hardwareAvcCastProfiles;
    private static volatile int[][] hardwareHevcCastProfiles;
    private static volatile Boolean hardwareHevcDecoder;

    /** Maximum decoder size for each useful frame rate, independent of screen size. */
    private static int[][] hardwareCastProfiles(String mimeType) {
        int[][] cached = "video/hevc".equals(mimeType)
                ? hardwareHevcCastProfiles : hardwareAvcCastProfiles;
        if (cached != null) return cached;
        int[] rates = new int[] {120, 60, 30};
        int[][] best = new int[rates.length][3];
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                    String name = codec.getName().toLowerCase(java.util.Locale.US);
                    if (codec.isEncoder() || name.contains(".google.") || name.startsWith("c2.android.")
                            || name.contains(".secure") || name.contains(".sw.")) continue;
                    for (String type : codec.getSupportedTypes()) {
                        if (!mimeType.equalsIgnoreCase(type)) continue;
                        MediaCodecInfo.VideoCapabilities video = codec.getCapabilitiesForType(type).getVideoCapabilities();
                        for (int rateIndex = 0; rateIndex < rates.length; rateIndex++) {
                            int rate = rates[rateIndex];
                            for (int[] size : new int[][] {{3840,2160},{2560,1440},
                                    {1920,1080},{1280,720},{960,540},{640,360}}) {
                                if (!video.areSizeAndRateSupported(size[0], size[1], rate)) continue;
                                if ((long) size[0] * size[1]
                                        > (long) best[rateIndex][0] * best[rateIndex][1]) {
                                    best[rateIndex] = new int[] {size[0], size[1], rate};
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) { /* Keep the conservative display fallback. */ }
        }
        if ("video/hevc".equals(mimeType)) hardwareHevcCastProfiles = best;
        else hardwareAvcCastProfiles = best;
        return best;
    }

    private static int[] hardwareCastLimit() {
        int[] best = new int[] {0, 0, 30};
        for (int[] profile : hardwareCastProfiles("video/avc")) {
            if (profile[0] > best[0]
                    || profile[0] == best[0] && profile[2] > best[2]) best = profile;
        }
        return best;
    }

    private static JSONArray hardwareCastProfilesJson() throws JSONException {
        JSONArray result = new JSONArray();
        appendHardwareCastProfiles(result, "h264", hardwareCastProfiles("video/avc"));
        appendHardwareCastProfiles(result, "h265", hardwareCastProfiles("video/hevc"));
        return result;
    }

    private static void appendHardwareCastProfiles(JSONArray result, String codec,
            int[][] profiles) throws JSONException {
        for (int[] profile : profiles) {
            if (profile[0] <= 0 || profile[1] <= 0 || profile[2] <= 0) continue;
            result.put(new JSONObject().put("codec", codec).put("width", profile[0])
                    .put("height", profile[1]).put("fps", profile[2]));
        }
    }

    private static boolean hasHardwareHevcDecoder() {
        Boolean cached = hardwareHevcDecoder;
        if (cached != null) return cached;
        boolean supported = false;
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for (MediaCodecInfo codec
                        : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                    String name = codec.getName().toLowerCase(java.util.Locale.US);
                    if (codec.isEncoder() || name.contains(".google.")
                            || name.startsWith("c2.android.") || name.contains(".secure")
                            || name.contains(".sw.")) continue;
                    for (String type : codec.getSupportedTypes()) {
                        if ("video/hevc".equalsIgnoreCase(type)) {
                            supported = true;
                            break;
                        }
                    }
                    if (supported) break;
                }
            } catch (RuntimeException ignored) { }
        }
        hardwareHevcDecoder = supported;
        return supported;
    }

    ChannelCatalog.Group[] loadCatalog(String serverUrl) throws IOException, JSONException {
        String baseUrl = normalizeServerUrl(serverUrl);
        JSONObject state;
        try {
            state = getJson(baseUrl + "/api/catalog");
        } catch (IOException unavailable) {
            state = getJson(baseUrl + "/api/state");
        }
        String upstream = state.optString("remoteCatalogUrl", "").trim();
        if (upstream.length() == 0) {
            JSONObject settings = state.optJSONObject("settings");
            upstream = settings == null ? ""
                    : settings.optString("remoteCatalogUrl", "").trim();
        }
        if (upstream.length() > 0) {
            throw new IOException("所选手机正在接管另一台设备，请先退出接管模式");
        }
        JSONArray jsonGroups = state.optJSONArray("groups");
        if (jsonGroups == null) {
            throw new IOException("手机没有返回频道目录");
        }
        List<ChannelCatalog.Group> groups = new ArrayList<ChannelCatalog.Group>();
        int fallbackNumber = 1;
        for (int groupIndex = 0; groupIndex < jsonGroups.length(); groupIndex++) {
            JSONObject jsonGroup = jsonGroups.optJSONObject(groupIndex);
            if (jsonGroup == null) {
                continue;
            }
            String groupName = jsonGroup.optString("name", "").trim();
            // Favorites are deliberately local to each device. The TV builds its own
            // fixed favorites entry when setCustomGroups() is called.
            if (groupName.length() == 0 || "我的收藏".equals(groupName)) {
                continue;
            }
            JSONArray jsonChannels = jsonGroup.optJSONArray("channels");
            if (jsonChannels == null || jsonChannels.length() == 0) {
                continue;
            }
            List<Channel> channels = new ArrayList<Channel>();
            for (int channelIndex = 0; channelIndex < jsonChannels.length(); channelIndex++) {
                JSONObject jsonChannel = jsonChannels.optJSONObject(channelIndex);
                if (jsonChannel == null) {
                    continue;
                }
                String name = jsonChannel.optString("name", "").trim();
                if (name.length() == 0) {
                    continue;
                }
                String number = jsonChannel.optString("number", "").trim();
                if (number.length() == 0) {
                    number = String.valueOf(fallbackNumber);
                }
                fallbackNumber++;
                int sourceCount = Math.max(1, jsonChannel.optInt("sourceCount", 1));
                String[] sources = new String[sourceCount];
                for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
                    sources[sourceIndex] = encodeSource(baseUrl, groupIndex,
                            channelIndex, sourceIndex);
                }
                channels.add(new Channel(number, name,
                        "remote_" + groupIndex + "_" + channelIndex,
                        sources, null, null, null,
                        jsonChannel.optString("epgId", null)).withLogo(jsonChannel.optString("logoUrl", "")).withSubtitles(jsonChannel.optString("subtitleUrls", "")).withRadio(jsonChannel.optBoolean("radio", false)));
            }
            if (!channels.isEmpty()) {
                groups.add(new ChannelCatalog.Group(groupName,
                        ChannelCatalog.SOURCE_CUSTOM,
                        channels.toArray(new Channel[channels.size()])));
            }
        }
        if (groups.isEmpty()) {
            throw new IOException("手机频道目录为空");
        }
        return groups.toArray(new ChannelCatalog.Group[groups.size()]);
    }

    Result resolve(String encodedSource, int receiverWidth, int receiverHeight,
            boolean lowResourceReceiver, String receiverUrl)
            throws IOException, JSONException {
        Source source = decodeSource(encodedSource);
        // A retained catalog still contains its original LAN URLs. Resolve them
        // against the authenticated session route without rebuilding the catalog.
        source = new Source(playbackHost(source.baseUrl), source.groupIndex,
                source.channelIndex, source.sourceIndex);
        JSONObject command = new JSONObject();
        command.put("action", "play");
        command.put("group", source.groupIndex);
        command.put("channel", source.channelIndex);
        command.put("source", source.sourceIndex);
        command.put("receiver", true);
        String normalizedReceiverUrl = normalizeServerUrl(receiverUrl);
        if (normalizedReceiverUrl.length() > 0) {
            command.put("receiverUrl", normalizedReceiverUrl);
        }
        command.put("castH265", hasHardwareHevcDecoder());
        checkResolveCancelled();
        JSONObject accepted = postJson(source.baseUrl + "/api/control", command);
        if (!accepted.optBoolean("ok", false)) {
            throw new IOException(accepted.optString("message", "手机拒绝播放频道"));
        }
        final int acceptedRequestId = accepted.optInt("playRequestId", -1);

        long deadline = SystemClock.elapsedRealtime() + RESOLVE_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            checkResolveCancelled();
            JSONObject state;
            try {
                state = requestJson(source.baseUrl + "/api/playback", "GET", null, 1500, 2500);
            } catch (IOException unavailable) {
                checkResolveCancelled();
                state = requestJson(source.baseUrl + "/api/state", "GET", null, 1500, 2500);
            }
            checkResolveCancelled();
            if (acceptedRequestId >= 0 && state.optInt("playRequestId", -1) > acceptedRequestId) {
                throw new IOException("Remote playback request superseded");
            }
            JSONObject current = state.optJSONObject("current");
            JSONObject playback = state.optJSONObject("remotePlayback");
            if (current != null && playback != null
                    && (acceptedRequestId < 0
                        || state.optInt("playRequestId", -1) == acceptedRequestId)
                    && current.optInt("groupIndex", -1) == source.groupIndex
                    && current.optInt("channelIndex", -1) == source.channelIndex
                    && current.optInt("sourceIndex", -1) == source.sourceIndex
                    && playback.optBoolean("available", false)
                    && playback.optInt("sourceIndex", -1) == source.sourceIndex) {
                String mode = playback.optString("sourceMode", "proxy");
                if ("cast".equals(mode)) {
                    String castUrl = playback.optString("sourceUrl", "").trim();
                    if (castUrl.length() > 0) {
                        return new Result(castUrl, true, playback.optString("castTransport", "tcp"),
                                playback.optString("castSessionId", ""));
                    }
                }
                if ("direct".equals(mode)) {
                    String directUrl = playback.optString("sourceUrl", "").trim();
                    if (directUrl.length() > 0) {
                        detachPhonePlayer(source.baseUrl);
                        return new Result(directUrl, true);
                    }
                }
                String path = playback.optString(
                        "playlistPath", "/api/recording/playlist");
                detachPhonePlayer(source.baseUrl);
                return new Result(absoluteUrl(source.baseUrl, path), true);
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("等待手机解析时已取消");
            }
        }
        throw new IOException("等待手机解析频道超时");
    }

    private static void checkResolveCancelled() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted())
            throw new java.io.InterruptedIOException("Remote channel resolve cancelled");
    }

    private static void detachPhonePlayer(String baseUrl) {
        try {
            postJson(baseUrl + "/api/control",
                    new JSONObject().put("action", "detachRemote"));
        } catch (Exception ignored) {
            // The returned stream remains usable even when talking to an older host.
        }
    }

    void release(String serverUrl, String sessionId) {
        try {
            String baseUrl = normalizeServerUrl(serverUrl);
            if (baseUrl.length() > 0) {
                postJson(baseUrl + "/api/control",
                        new JSONObject().put("action", "releaseReceiver")
                                .put("sessionId", sessionId));
            }
        } catch (Exception ignored) {
            // The receiver can still return to its local catalog when the source
            // device is already offline.
        }
    }

    /** Return receiver remote-control input to the current controlling device. */

    JSONObject pointer(String serverUrl, JSONObject request)
            throws IOException, JSONException {
        return requestJson(normalizeServerUrl(serverUrl) + "/api/pointer", "POST",
                request.toString().getBytes("UTF-8"), POINTER_CONNECT_TIMEOUT_MS,
                POINTER_READ_TIMEOUT_MS);
    }

    JSONObject controlReceiver(String receiverUrl, JSONObject request)
            throws IOException, JSONException {
        return postJson(normalizeServerUrl(receiverUrl) + "/api/control", request);
    }

    JSONObject pushApk(String receiverUrl, String fileName, byte[] apk)
            throws IOException, JSONException {
        String receiverBase = normalizeServerUrl(receiverUrl);
        if (receiverBase.length() == 0) {
            throw new IOException("请填写电视 IP，再发送 APK");
        }
        JSONObject receiverState;
        try {
            receiverState = getJson(receiverBase + "/api/state");
        } catch (IOException error) {
            throw new IOException("无法连接电视，请确认电视端 nTv 正在运行");
        }
        if (receiverState.optInt("apkTransferProtocol", 0) < APK_TRANSFER_PROTOCOL) {
            throw new IOException("电视端版本较旧，不支持 APK 投送，请先更新电视端 nTv");
        }
        int remoteLimit = receiverState.optInt("apkTransferMaxBytes", 0);
        if (remoteLimit > 0 && apk.length > remoteLimit) {
            throw new IOException("APK 超过电视可接收的 "
                    + Math.max(1, remoteLimit / 1024 / 1024) + " MB");
        }
        String url = receiverBase + "/api/apk/upload?name="
                + URLEncoder.encode(fileName == null ? "" : fileName, "UTF-8");
        return requestApk(url, apk);
    }

    private static JSONObject requestApk(String url, byte[] body)
            throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(120000);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/vnd.android.package-archive");
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream output = connection.getOutputStream();
        try {
            output.write(body);
        } finally {
            output.close();
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        byte[] response;
        try {
            response = readFully(input);
        } finally {
            if (input != null) input.close();
            connection.disconnect();
        }
        String text = new String(response, "UTF-8");
        if (status < 200 || status >= 300) {
            try {
                throw new IOException(new JSONObject(text).optString("message", "电视拒绝接收 APK"));
            } catch (JSONException ignored) {
                throw new IOException("电视接口返回 " + status);
            }
        }
        return new JSONObject(text);
    }

    static boolean isRemoteSource(String sourceUrl) {
        return sourceUrl != null && sourceUrl.startsWith(SOURCE_PREFIX);
    }

    static String normalizeServerUrl(String value) throws IOException {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.length() == 0) {
            return "";
        }
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            throw new IOException("手机地址仅支持 HTTP 或 HTTPS");
        }
        if (result.endsWith("index.html")) {
            result = result.substring(0, result.length() - "index.html".length());
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String encodeSource(String baseUrl, int groupIndex,
            int channelIndex, int sourceIndex) {
        String encodedBase = Base64.encodeToString(baseUrl.getBytes(),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return SOURCE_PREFIX + encodedBase + ":" + groupIndex + ":"
                + channelIndex + ":" + sourceIndex;
    }

    private static Source decodeSource(String encodedSource) throws IOException {
        if (!isRemoteSource(encodedSource)) {
            throw new IOException("远程频道描述无效");
        }
        String[] parts = encodedSource.substring(SOURCE_PREFIX.length()).split(":", -1);
        if (parts.length != 4) {
            throw new IOException("远程频道描述不完整");
        }
        try {
            String baseUrl = new String(Base64.decode(parts[0],
                    Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            return new Source(normalizeServerUrl(baseUrl),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (Exception error) {
            throw new IOException("无法读取远程频道描述");
        }
    }

    private static JSONObject getJson(String url) throws IOException, JSONException {
        return requestJson(url, "GET", null);
    }

    private static JSONObject postJson(String url, JSONObject body)
            throws IOException, JSONException {
        return requestJson(url, "POST", body.toString().getBytes("UTF-8"));
    }

    private static JSONObject requestJson(String url, String method, byte[] body)
            throws IOException, JSONException {
        return requestJson(url, method, body, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private static JSONObject requestJson(String url, String method, byte[] body,
            int connectTimeoutMs, int readTimeoutMs) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
            } finally {
                output.close();
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        byte[] response;
        try {
            response = readFully(input);
        } finally {
            if (input != null) {
                input.close();
            }
            connection.disconnect();
        }
        String text = new String(response, "UTF-8");
        if (status < 200 || status >= 300) {
            throw new IOException("手机接口返回 " + status + "：" + text);
        }
        return new JSONObject(text);
    }

    private static byte[] readFully(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String absoluteUrl(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    static final class Result {
        final String url;
        final boolean directDataSource;
        final String castTransport;
        final String castSessionId;

        Result(String url, boolean directDataSource) {
            this(url, directDataSource, "tcp");
        }

        Result(String url, boolean directDataSource, String transport) {
            this(url, directDataSource, transport, "");
        }

        Result(String url, boolean directDataSource, String transport, String session) {
            this.castSessionId = session;
            this.castTransport = "udp".equals(transport) ? "udp" : "tcp";
            this.url = url;
            this.directDataSource = directDataSource;
        }
    }

    private static final class Source {
        final String baseUrl;
        final int groupIndex;
        final int channelIndex;
        final int sourceIndex;

        Source(String baseUrl, int groupIndex, int channelIndex, int sourceIndex) {
            this.baseUrl = baseUrl;
            this.groupIndex = groupIndex;
            this.channelIndex = channelIndex;
            this.sourceIndex = sourceIndex;
        }
    }
}
