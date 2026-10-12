param([string]$NdkRoot = $env:ANDROID_NDK_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$NdkRoot) { throw 'Pass -NdkRoot pointing to Android NDK r14b (API 14 support).' }
$ndkBuild = Join-Path $NdkRoot 'ndk-build.cmd'
if (!(Test-Path -LiteralPath $ndkBuild)) { throw "NDK not found: $ndkBuild" }
$jni = Join-Path $projectRoot 'native/tls'
$buildRoot = Join-Path $projectRoot '.codex-tmp/tls-build'
# Android 4.x needs modern suites; Android 5/6 needs TLS without FD_SET.
foreach ($abi in @('armeabi-v7a', 'x86', 'arm64-v8a')) {
    $minimumApi = if ($abi -eq 'arm64-v8a') { 21 } else { 14 }
    & $ndkBuild "NDK_PROJECT_PATH=$jni" "APP_BUILD_SCRIPT=$jni/Android.mk" "NDK_APPLICATION_MK=$jni/Application.mk" `
        'NDK_TOOLCHAIN_VERSION=clang' "APP_ABI=$abi" "APP_PLATFORM=android-$minimumApi" `
        "NDK_OUT=$buildRoot/$abi/obj" "NDK_LIBS_OUT=$buildRoot/$abi/libs" -j4
    if ($LASTEXITCODE -ne 0) { throw "TLS build failed: $abi" }
    $target = Join-Path $projectRoot "app/src/main/libs/$abi/libntvtls.so"
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath "$buildRoot/$abi/libs/$abi/libntvtls.so" -Destination $target -Force
}
