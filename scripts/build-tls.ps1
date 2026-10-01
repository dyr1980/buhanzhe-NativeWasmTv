param([string]$NdkRoot = $env:ANDROID_NDK_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$NdkRoot) { throw 'Pass -NdkRoot pointing to Android NDK r14b (API 14 support).' }
$ndkBuild = Join-Path $NdkRoot 'ndk-build.cmd'
if (!(Test-Path -LiteralPath $ndkBuild)) { throw "NDK not found: $ndkBuild" }
$jni = Join-Path $projectRoot 'native/tls'
$buildRoot = Join-Path $projectRoot '.codex-tmp/tls-build'
# Android 4.0 processes are 32-bit; newer systems use their existing TLS provider.
foreach ($abi in @('armeabi-v7a', 'x86')) {
    & $ndkBuild "NDK_PROJECT_PATH=$jni" "APP_BUILD_SCRIPT=$jni/Android.mk" "NDK_APPLICATION_MK=$jni/Application.mk" `
        'NDK_TOOLCHAIN_VERSION=clang' "APP_ABI=$abi" 'APP_PLATFORM=android-14' `
        "NDK_OUT=$buildRoot/$abi/obj" "NDK_LIBS_OUT=$buildRoot/$abi/libs" -j4
    if ($LASTEXITCODE -ne 0) { throw "TLS build failed: $abi" }
    $target = Join-Path $projectRoot "app/src/main/libs/$abi/libntvtls.so"
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath "$buildRoot/$abi/libs/$abi/libntvtls.so" -Destination $target -Force
}
