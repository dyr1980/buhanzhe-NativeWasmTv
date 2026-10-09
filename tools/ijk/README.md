# nTv IJK / Dolby changes

## Playback policy

- H.264, HEVC and MPEG-2 prefer MediaCodec. The user's explicit software decoder setting remains authoritative. Failed ordinary hardware video decoding retains IJK's FFmpeg fallback.
- AAC keeps its existing PCM path. FFmpeg now includes AC3, EAC3, TrueHD and MLP decoders, AC3/EAC3/TrueHD/MLP demuxers and parsers, Matroska demuxing, and MPEG-2 decoding/parsing.
- AC3/EAC3: API 21+ may use compressed AudioTrack output when the connected HDMI output advertises the format/channel count. API 29+ also checks direct playback support. Headphone/Bluetooth routing, unsupported devices and unknown capabilities use FFmpeg PCM. API <21 never loads the new AudioTrack bridge.
- Atmos: EAC3/TrueHD bitstreams are not decoded or stripped on the passthrough path. TrueHD passthrough additionally requires API 23+, an advertised TrueHD output, and 16-frame batching. Software fallback plays the channel-based mix; it does **not** reproduce Atmos object rendering.
- A route change, failed/stalled write, unexpected playback-head reset, application mute/volume adjustment or non-1x speed returns the current audio stream to FFmpeg PCM. No restart of the video is required. Stop/seek discard the old stream's pending encoded bytes. Re-enabling passthrough is deferred to the next stream open.
- The HLS proxy still emits only one selected video/audio rendition group. It prefers EAC3/AC3 when direct output is available and AAC on speakers; Dolby-only playlists can use software decoding. It does not reintroduce probing of unused HLS groups.
- Dolby Vision is **not** identified by HEVC alone. MP4/fMP4 `dvcC`/`dvvC` and DV sample-entry tags, plus explicit HLS DV CODECS declarations, reach the native video pipeline. It requests `video/dolby-vision`, matching the advertised profile and level. This revision accepts single-layer profiles 5/8/9. Unsupported profiles, dual-layer content, missing metadata or unavailable DV decoders emit a clear unsupported-format error instead of falling through to ordinary HEVC with wrong colours. Matroska DV metadata and DRM/dual-layer DV are not implemented.

Audio capability APIs can describe a disconnected or inactive output on older Android releases; that is why a MIME name or `isDirectPlaybackSupported()` alone is not sufficient. See [Android TV audio capabilities](https://developer.android.com/training/tv/playback/audio-capabilities) and [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack).

## Rebuilding native libraries

The app packages prebuilt libraries, so running Gradle alone does not compile these changes.

The source overlays target the existing `.codex-tmp/ijkplayer-0.8.8` checkout, its configured FFmpeg 3.4 `android/contrib/ffmpeg-arm64` and `ffmpeg-armv7a` trees, NDK r14b and the unchanged `libijksdl.so` in the app. `module-ntv.sh` is the feature profile for a fresh FFmpeg configuration. SoundTouch is the upstream `Bilibili/soundtouch` branch `ijk-r0.1.2-dev`, placed at `ijkmedia/ijksoundtouch`.

1. Run `node tools/ijk/apply-ntv-patches.cjs` from the repository root. Exact-match checks reject unexpected source versions, and running it twice is safe.
2. In Git Bash, run `bash tools/ijk/build-ffmpeg-dolby.sh arm64` and then `bash tools/ijk/build-ffmpeg-dolby.sh armv7a`. These scripts reuse the existing standalone cross-toolchain configuration. Set `NTV_SKIP_CONFIGURE=1` only for subsequent source-only rebuilds. Git Bash's `/usr/bin/awk` must precede the old Windows NDK awk.
3. Generate `ijkmedia/ijkplayer/ijkversion.h` with upstream `version.sh` if absent. Invoke `ndk-build.cmd` from the repo root with `NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=tools/ijk/Android.mk NDK_APPLICATION_MK=tools/ijk/Application.mk NDK_OUT=.codex-tmp/dolby-native/obj NDK_LIBS_OUT=.codex-tmp/dolby-native/libs`, once for `APP_ABI=arm64-v8a APP_PLATFORM=android-21`, once for `APP_ABI=armeabi-v7a APP_PLATFORM=android-14`.
4. After successful linking, copy `libijkplayer.so`, `libijksdl.so` and (when FFmpeg changed) `libijkffmpeg.so` for each ABI from `.codex-tmp/dolby-native/libs` into `app/src/main/libs`. Original libraries from this change are backed up under `.codex-tmp/dolby-original-libs` locally.
5. Run the normal Gradle or release packaging script.

The IJK overlays are LGPL-2.1-or-later, like their host sources. Keep these source changes alongside the redistributed native libraries.

## Interactive cast latency

For nTv's realtime RTSP session, the player uses a three-frame decoded audio queue instead of IJK's default nine-frame queue. Its AudioTrack uses Android's reported minimum safe buffer instead of the upstream 2x-playback buffer, and the AudioTrack worker uses normal priority so it cannot starve video/UI on small CPUs. Video-only casting still renders the newest decoded picture immediately. When system audio is present, audio remains the master clock, while decoded video pictures more than 80 ms behind that clock are discarded until playback catches up. This bounds pointer lag without dropping compressed H.264/H.265 reference packets. Ordinary live streams retain the normal audio queue and priority; their hardware video synchronization policy is described below.

When building ABIs in parallel, use different `NDK_LIBS_OUT` directories: NDK's install task may remove the other ABI's generated outputs. Copy each successful result into the corresponding app ABI directory and verify hashes.

## Track-switch stability update

The overlay also validates and deduplicates native track selection before closing a working decoder, restores the previous stream on an open failure, and supports explicit initial stream indices for recovery. Text subtitle decoders are included; `ntv_subtitle_text.h` safely converts both compact FFmpeg ASS events and legacy Dialogue events to bounded plain text. See [Android 9 regression report](../../reports/2026-09-02-track-switch-android9.md) for playback, pause/progress, failed-selection and overlay tests.

## Validation (2026-09-02)

Passed:

- ARM32 and ARM64 FFmpeg and IJK native compilation/linking.
- Both Android application variants and the dedicated instrumentation APKs compile.
- `TestDolbyFormats`: DV vs ordinary HEVC distinction, codec declarations, HDMI/speaker rendition priority, TrueHD batch/reset behaviour.
- Existing HLS metadata, subtitle timing/placement, URL-scoped track preference tests.
- `npm test --prefix scripts`: all web control/media/recording regression tests.

Not verified on hardware:

- PCM playback instrumentation on Android 7/4.4. The Android 7 app update succeeded, but installing the separate test APK returned `INSTALL_FAILED_USER_RESTRICTED` / installation cancelled. No test result is claimed from that attempt, and no installation restriction was bypassed.
- HDMI AC3/EAC3/TrueHD passthrough, Atmos receiver indication, hot-plug transitions, AV sync and Dolby Vision output require a compatible TV/receiver and representative media. The connected phones do not establish these capabilities.

### Offline speaker regression

`DolbyPlaybackInstrumentation` uses small offline Apple HLS audio segments, so this test isolates decoding from network speed. Download `a2/fileSequence0.ac3` and `a3/fileSequence0.ec3` under [Apple's TS example](https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8) to `/sdcard/Download/ntv-dolby/sample.ac3` and `sample.ec3`. Both fixtures were copied to the Android 7 phone during preparation.

Build with `:app:assembleArm64DebugAndroidTest` (or `Arm32`), install the matching app/test APKs with device approval, then run:

```text
adb -s DEVICE shell am instrument -w xiao.bu.tv.test/xiao.bu.tv.DolbyPlaybackInstrumentation
```

It requires a no-HDMI speaker route, checks prepare/render callbacks, codec/module identity and advancing audio clock, and does not modify channel settings. Check `nTvDolby` and `nTvDolbyTest` logcat tags. An instrumentation failure is not a passed playback test.

## High frame rate playback and stream statistics

`ntv-frame-sync` keeps audio as master and tolerates 80 ms of hardware scheduling jitter before discarding decoded pictures. Consecutive early drops are bounded; compressed reference packets are never discarded. An empty picture queue wakes on decoder production instead of waiting for another fixed refresh interval.

On API 23+, `IjkCodecPerformance` requests a supported operating rate for 45+ fps streams (60 for a supported 50 fps stream), and realtime codec priority. Capability checks bound the request to the selected decoder's size/rate range. A rejected configuration recreates the codec and retries without these optional hints. API 14–22 retain their existing codec configuration. The JNI helper class and entry point must be preserved in release builds.

Live HLS prefetch is separate from its first-frame water mark. Queues stay bounded to 48 MiB on ordinary devices (64 MiB in stable mode) and 15 MiB on low-memory devices; the native option ceiling is 128 MiB. Background packet targets are 360/480/720 for low/balanced/stable modes; low-memory devices use 360. Stable refill marks respect IJK's actual 5000 ms option limit. Infinite buffering stays disabled.

Finite HLS uses `ntv-vod-buffer-mode` (0 low / 1 balanced / 2 stable).
`apply-vod-buffer-patches.cjs` verifies the HLS demuxer and a positive finite
duration before enabling the reserve. Live streams, ordinary MP4 and casting
retain their existing policy. During startup and genuine rebuffering, the target
uses the actual remaining clip duration:
`remainingMediaMs * max(0, 1 - cachedMediaMs / elapsedDownloadMs) + safetyMs`.
Safety is 2000 ms balanced / 5000 ms stable, bounded by EOF and the native
60-second packet target ceiling; opening/probing time is included in the initial
estimate. Startup explicitly enters native buffering before preparation instead
of relying on a later queue underrun. Seeks use the ordinary short refill mark.
Compressed packets continue downloading during playback, with a 64 MiB balanced
or 96 MiB stable ceiling on ordinary devices. Existing small-device byte limits
are preserved. Queue saturation or EOF releases buffering rather than waiting
forever for an unreachable duration. These are estimated reserves, not a promise
against indefinite bandwidth deficits, outages or insufficient memory.

At a sustained 5 seconds of media per 6 seconds downloaded, a 20-second clip needs
about 8.3 seconds of stable reserve; a 150-second clip needs 30 seconds. Fast
downloads require only the safety margin. Targets shrink towards EOF.
Startup/probe overhead can require more. The finite-HLS recovery watchdog
allows up to 150 seconds for this intentional refill, instead of restarting at
30 seconds. Live and low-delay recovery remain unchanged.

The custom-source proxy also reserves plain finite HLS fragments on disk, beyond
the low-memory device's unchanged 15 MiB compressed-packet ceiling. Balanced
uses at most 128 MiB and stable 256 MiB, including partial downloads, with two
64 KiB streaming workers. Live, encrypted and byte-range playlists bypass this
cache. Files are committed only after complete download; consumed fragments and
closed/crashed sessions are removed. Storage limits fall back to ordinary HTTP.
Startup measures one fragment for clips up to 30 seconds and two for longer clips,
includes partial progress when the
HTTP length is known, and computes the same deficit from the actual EXTINF sum.
The UI displays `网速太慢，稳定播放预计还需 XX 秒` when measured delivery is slower
than playback, updating the estimate as downloads progress. Whole-fragment
rounding and disk quota can limit the reserve; a 0.25-second tolerance avoids
adding an entire fragment for measurement noise. Channel switches cancel waiting,
and download/storage failures stop prefetch. Startup follows the required reserve
rather than starting automatically after a fixed two-minute delay.
This is an estimate under the observed throughput, not a guarantee against later
network changes. There is no fixed two-minute playback horizon.

Requests start in playback order: the next request waits for its predecessor's
headers, then their bodies can overlap. Known-length downloads reserve disk space
before writing, so later fragments cannot steal the pending fragment's quota.
Properties 22040/22041 atomically communicate proxy warmup time and a satisfied
disk reserve to the native player. Initial decoder priming needs only 1 second
when the proxy reserve is sufficient, while genuine subsequent rebuffering
restores the remaining-duration policy. A satisfied reserve or near-full packet
queue is checked on packet arrival, before blocking for another fragment.

The proxy resumes an interrupted HTTP 200 full media fragment as well as an
existing HTTP 206 range. It requires a strong ETag and an exact matching HTTP 206
offset/end/total on resumption, with at most two body retries. A changed entity,
ignored range or missing validator closes the stream instead of corrupting media.
`tests/hls-stream-failure.test.py` fault-injects the production handler and checks
payload offsets, counts and all rejection cases. `test-vod-buffer.c` checks the
reserve calculation independently of Android playback.

FFmpeg 3.4 misidentifies `av3a` audio as MP3. Before opening decoders, the player marks it unsupported and asks best-stream selection to require a decoder, selecting the accompanying AAC track. Explicit supported track choices retain priority. This is an audio selection fix, not an AVS3 decoder; FFmpeg may still log a probe error before the stream's registration is inspected.

Float property `11001` measures video frame cadence from packet media timestamps. A header-only H.264 parser distinguishes separately coded PAFF fields from complete MBAFF/progressive pictures, so 1080i50 reports 25 frames/s. Output property `10002` counts successful submissions of distinct pictures over wall time, excludes retained repaints, and returns zero on pause/stall. Long property `22001` exposes the submission counter for independent interval checks. Statistics never replace zero actual output with nominal FPS.

`tools/ijk/test-stream-timing.c` runs on a host C99 compiler. `LocalStreamFrameInstrumentation` exercises production playback, records source/output FPS, output counts, audio choice, buffering events and AV difference, and restores settings. It rejects loss of foreground focus and intercepts touch events only during the fixture. ARM32/ARM64 player and SDL libraries were rebuilt against the existing, unchanged FFmpeg libraries; a Gradle build alone does not rebuild native code.

## HDR colour output

The debug overlay now shows the source HDR/SDR classification, component bit depth,
colour primaries/matrix and transfer function (including BT.2020, PQ and HLG), plus
the active decoder implementation name. The channel card includes HDR/SDR. Missing
metadata is labelled unknown; HEVC, 10-bit and the selected HDR output mode alone
are never used to classify HDR. Android MediaPlayer cannot expose its implementation
name through this API and reports that limitation explicitly.

`apply-video-info-patches.cjs` adds long property `22010`: one atomic 32-bit source
snapshot with depth [0..4], primaries [5..9], transfer [10..14], matrix [15..19], range
[20..21], Dolby Vision [22], available [24], and software-frame authoritative [25].
The existing compressed-packet probe refreshes stream/header metadata; software
frames update it before upload/conversion. Reading the HUD never decodes or copies
pixels and never borrows a live parser pointer from another thread. Decoder names
come from IJK's current native codec information, independently of the requested
hardware/software setting. `currentSourceStats` in `/api/media` exposes the same values.

`apply-hdr-patches.cjs` runs from the playback overlay script. MediaCodec receives the stream's colour standard, range, transfer, HEVC Main10 profile and available CTA-861.3 static metadata. HDR10/HLG are distinguished by PQ/HLG transfer metadata; HEVC or 10-bit alone does not imply HDR. Dolby Vision keeps its separate decoder policy.

Advanced settings expose the persisted `hdrMode` (`mapping`, `hardware`, `bt709`, `hardware_sdr`) through `/api/settings` and `/api/state?view=advanced`. Changing it restarts the current channel. Each player receives `ntv-hdr-mode` (0/1/2/3); policy is carried in that player's MediaFormat rather than a process-global colour switch.

* **HDR 映射 / mapping** explicitly converts HDR10/HLG to SDR even if the display advertises HDR. API 17–30 use a hardware decoder -> SurfaceTexture -> one GLES tone-mapping pass -> SurfaceView. Rendering applies the PQ/HLG EOTF, BT.2020 -> BT.709 conversion in linear light, a luminance shoulder and gamut compression. Decoder output explicitly tagged SDR bypasses the extra tone mapping. Codec-provided static metadata updates the peak; the bounded default for missing metadata is 1000 nits. API 31+ requests codec HDR -> SDR conversion and verifies acknowledgement before playback, otherwise retaining the existing FFmpeg fallback.
* **HDR 硬解 / hardware (default)** keeps original HDR SurfaceView output only when the display owning the SurfaceView advertises the corresponding format: HDR10/HDR10+ for PQ, HLG for HLG. Unsupported, unavailable or pre-API-24 display capabilities select HDR-to-SDR mapping instead, using the same path as mapping mode. Main10 decoder support alone does not qualify a display. Capability queries use an API-isolated helper for legacy compatibility and inspect the actual output display, including external displays. Correct passthrough still requires a compatible decoder/display/HDMI chain. The separate decode-mode setting remains authoritative: explicit software decoding or a codec fallback still maps HDR to SDR because the software renderer cannot emit native HDR Surface buffers.
* **硬解软显 / hardware_sdr** forces the hardware decoder -> SurfaceTexture -> GPU HDR-to-SDR pass -> SurfaceView path on API 17+, including API 31+ where it bypasses the decoder tone-mapping request. This is a manual alternative for devices that advertise HDR but cannot display native HDR correctly. Explicit software decoding and hardware-decoder failure retain the software fallback; ordinary SDR uses direct output without an extra GPU bridge.
* **HDR BT.709 / bt709** overrides MediaCodec colour standard/transfer to BT.709/SDR, omits HDR static metadata, and uses the BT.709 YUV matrix with tone mapping disabled for software frames. For hardware streams originally tagged HDR or a different colour standard, a GPU pass produces an SDR RGB Surface even if the vendor continues to attach HDR tags to its decoded buffers. It reinterprets the decoder's reported YUV matrix as BT.709 in the encoded domain, with no HDR luminance mapping. Correctly tagged BT.709 SDR keeps direct output. The mode preserves actual bit depth and range. This is a manual compatibility mode for incorrectly tagged SDR sources, not HDR-to-SDR conversion; true PQ/HLG sources can look washed out in this mode. Vendor behaviour still needs validation on each device. Dolby Vision retains its separate decoder policy.

Mapping/hardware/hardware_sdr leave normal SDR colour metadata unchanged. New installs and absent/invalid preferences default to hardware; saved valid selections are retained. Java, the web selector and the native player's default use the same policy. The hardware-mode name describes the HDR output path, not a replacement for the separate decoder selection. Software/SDR behaviour is explicit in the settings help. `test-hdr-display-policy.java` verifies format-specific capabilities, unknown display fallback, the forced GPU policy and preference compatibility on the host.

Software decoding uses a YUV GLES output from the first frame, including on API 14–16, and preserves per-frame matrix/range/transfer/static light levels unless BT.709 is explicitly selected. Its YUV420 10-bit -> 8-bit upload conversion remains in FFmpeg; tone mapping runs on the GPU. This does not make old CPUs capable of software 4K playback. Replacement IJK preparation waits asynchronously for the preceding player release (bounded to two seconds), and HDR bridge replacement also waits for its old EGL producer to disconnect. These handoffs prevent a new output connecting to a Surface still owned by the old codec. GPU output statistics count distinct texture timestamps after successful swaps, return zero after a stall, and do not count retained repaints.

The source `app/src/main/res/raw/hdr_tonemap.glsl` is shared with the native shaders; keep it with the LGPL native overlays when redistributing. Both `libijkplayer.so` and `libijksdl.so` must be rebuilt together because overlay colour fields extend their shared structure. The current ARM32/ARM64 IJK/SDL builds use NDK r17c against the existing FFmpeg binaries; Gradle alone cannot rebuild them. The format-prefix migration also removes previously duplicated performance/DV blocks and is idempotent.

The real test source is [Apple's advanced HLS example](https://developer.apple.com/streaming/examples/advanced-stream-dv-atmos.html). The pinned [1080p HDR + AAC playlist](fixtures/hdr-1080p.m3u8) selects PQ HEVC instead of letting the mixed master choose SDR or Dolby Vision. The current sample contains HDR10+ dynamic metadata; this revision uses the HDR10 base/static metadata and does not implement dynamic HDR10+ scene grading. The master also offers SDR companion renditions for comparison.
