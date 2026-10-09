package xiao.bu.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Validates a received APK, stores it atomically, and hands it to Package Installer. */
final class ApkTransferInstaller {
    static final int MAX_APK_BYTES = 64 * 1024 * 1024;

    static final class ReceivedApk {
        final File file;
        final String originalName;
        final String packageName;
        final String label;
        final String versionName;
        final int versionCode;

        ReceivedApk(File file, String originalName, String packageName, String label,
                String versionName, int versionCode) {
            this.file = file;
            this.originalName = originalName;
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    private ApkTransferInstaller() {}

    static synchronized ReceivedApk save(Activity activity, String suppliedName, byte[] body)
            throws IOException {
        String originalName = safeOriginalName(suppliedName);
        if (body == null || body.length < 4 || body.length > MAX_APK_BYTES) {
            throw new IOException(body != null && body.length > MAX_APK_BYTES
                    ? "APK 不能超过 64 MB" : "APK 文件为空或不完整");
        }
        if (body[0] != 'P' || body[1] != 'K') {
            throw new IOException("所选文件不是有效的 APK");
        }
        File directory = ApkFileProvider.updateDirectory(activity);
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            throw new IOException("设备无法创建 APK 接收目录");
        }
        ReceivedApkCleanup.cleanup(activity, null);
        long id = System.currentTimeMillis();
        while (new File(directory, "received-" + id + ".apk").exists()
                || new File(directory, "received-" + id + ".apk.part").exists()) id++;
        File partial = new File(directory, "received-" + id + ".apk.part");
        File destination = new File(directory,
                partial.getName().substring(0, partial.getName().length() - ".part".length()));
        FileOutputStream output = new FileOutputStream(partial);
        try {
            output.write(body);
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!partial.renameTo(destination)) {
            partial.delete();
            throw new IOException("设备保存 APK 失败");
        }

        PackageManager manager = activity.getPackageManager();
        PackageInfo info = manager.getPackageArchiveInfo(destination.getAbsolutePath(), 0);
        if (info == null || info.applicationInfo == null || info.packageName == null) {
            destination.delete();
            throw new IOException("APK 无法解析，文件可能已损坏");
        }
        ApplicationInfo application = info.applicationInfo;
        application.sourceDir = destination.getAbsolutePath();
        application.publicSourceDir = destination.getAbsolutePath();
        String label;
        try {
            CharSequence loaded = manager.getApplicationLabel(application);
            label = loaded == null ? info.packageName : loaded.toString();
        } catch (RuntimeException error) {
            label = info.packageName;
        }
        try { ReceivedApkCleanup.track(activity, destination, info); }
        catch (IOException error) { destination.delete(); throw error; }
        return new ReceivedApk(destination, originalName, info.packageName, label,
                info.versionName == null ? "" : info.versionName, info.versionCode);
    }

    static void launchInstaller(Activity activity, File apk) throws IOException {
        Uri uri = ApkFileProvider.uriForFile(activity, apk);
        if (Build.VERSION.SDK_INT < 24) {
            // Several 4.x OEM installers register only file://, even for
            // ACTION_INSTALL_PACKAGE. Give them a dedicated readable copy;
            // never make the private received APK or other app files public.
            uri = Uri.fromFile(legacyInstallerFile(activity, apk));
        }
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }

    static File legacyInstallerFile(android.content.Context context, File apk) throws IOException {
        ApkFileProvider.uriForFile(context, apk); // Validate the narrowly scoped source.
        File cache = context.getCacheDir();
        File directory = new File(cache, "apk-installer");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("无法创建安装临时目录");
        File destination = new File(directory, apk.getName());
        try (FileInputStream input = new FileInputStream(apk);
                FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        } catch (IOException error) {
            destination.delete();
            throw error;
        }
        // Old file-URI installers need traversal (not directory listing) through
        // the app/cache parents. Only the dedicated APK copy is world-readable.
        if (!new File(context.getApplicationInfo().dataDir).setExecutable(true, false)
                || !cache.setExecutable(true, false)
                || !directory.setExecutable(true, false)
                || !destination.setReadable(true, false)) {
            destination.delete();
            throw new IOException("无法授权系统安装器读取 APK");
        }
        return destination;
    }

    private static String safeOriginalName(String suppliedName) throws IOException {
        String name = suppliedName == null ? "" : suppliedName.trim();
        if (name.length() == 0 || name.length() > 180
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || !name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            throw new IOException("请选择有效的 .apk 文件");
        }
        return name;
    }

}
