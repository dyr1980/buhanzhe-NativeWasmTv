#!/usr/bin/env bash
set -euo pipefail
export PATH="/usr/bin:/bin:$PATH"
repo="$(cd "$(dirname "$0")/../.." && pwd)"
tree="${IJK_ROOT:-$repo/.codex-tmp/ijkplayer-0.8.8}"
arch="${1:-arm64}"
case "$arch" in arm64) cross=aarch64-linux-android;; armv7a) cross=arm-linux-androideabi;; x86) cross=i686-linux-android;; *) exit 2;; esac
export ANDROID_NDK="${ANDROID_NDK:-$repo/.codex-tmp/android-ndk-r14b}"
export PATH="/usr/bin:$tree/android/contrib/build/ffmpeg-$arch/toolchain/bin:$ANDROID_NDK/prebuilt/windows-x86_64/bin:$PATH"
cd "$tree/android/contrib/ffmpeg-$arch"
test -f ffbuild/config.mak
node "$repo/tools/ijk/apply-ffmpeg-buffer-patches.cjs" "$PWD"
node "$repo/tools/ijk/apply-ffmpeg-seek-patches.cjs" "$PWD"
node "$repo/tools/ijk/apply-adaptive-probe-patches.cjs" "$PWD"
# Reuse the already configured cross-toolchain, retaining all prior IPTV flags.
flags="$(sed -n 's/^FFMPEG_CONFIGURATION=//p' ffbuild/config.mak)"
extra_flags=""
# FFmpeg 3.4's 32-bit x86 assembly emits text relocations forbidden by Android 6+.
# Match upstream Android x86 builds; compiler-generated SSE remains available.
if [ "$arch" = x86 ]; then extra_flags="--disable-asm"; fi
if [ "${NTV_SKIP_CONFIGURE:-0}" != 1 ]; then
    eval "./configure $flags --enable-decoder=ac3 --enable-decoder=eac3 --enable-decoder=truehd --enable-decoder=mlp --enable-parser=ac3 --enable-parser=mlp --enable-demuxer=ac3 --enable-demuxer=eac3 --enable-demuxer=truehd --enable-demuxer=mlp --enable-demuxer=matroska --enable-decoder=mpeg2video --enable-parser=mpegvideo --enable-decoder=movtext --enable-decoder=ass --enable-decoder=ssa --enable-decoder=subrip --enable-decoder=srt --enable-decoder=webvtt $extra_flags"
fi
grep -q '^#define CONFIG_EAC3_DECODER 1' config.h
make -j4
make install
prefix="$tree/android/contrib/build/ffmpeg-$arch/output"
mkdir -p "$prefix/include/libffmpeg"
cp config.h "$prefix/include/libffmpeg/config.h"
libraries=()
# Archives contain exactly the configured objects. Globbing *.o also includes
# stale objects left over after disabling a decoder or changing assembly flags.
for module in avcodec avfilter avformat avutil swresample swscale; do
    libraries+=("lib$module/lib$module.a")
done
"$cross-gcc" -shared -Wl,-Bsymbolic -Wl,--no-undefined -Wl,-z,text -Wl,-z,noexecstack -Wl,-soname,libijkffmpeg.so \
    -Wl,--whole-archive "${libraries[@]}" -Wl,--no-whole-archive -lm -lz -o "$prefix/libijkffmpeg.so"
echo "Built $prefix/libijkffmpeg.so"
