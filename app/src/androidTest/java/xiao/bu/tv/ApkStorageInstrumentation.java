package xiao.bu.tv;

import android.app.Instrumentation;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;

/** Regression: APK receiving must work without any external storage access. */
public final class ApkStorageInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int status = -1;
        File file = null;
        try {
            ContextWrapper context = new ContextWrapper(getTargetContext()) {
                @Override public File getExternalFilesDir(String type) {
                    throw new SecurityException("External storage denied");
                }
            };
            File directory = ApkFileProvider.updateDirectory(context);
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new AssertionError("Cannot create private receiving directory");
            file = new File(directory, "received-" + System.currentTimeMillis() + ".apk");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(new byte[] {'P', 'K', 3, 4});
            }
            android.net.Uri uri = ApkFileProvider.uriForFile(context, file);
            try (ParcelFileDescriptor descriptor = context.getContentResolver()
                    .openFileDescriptor(uri, "r")) {
                if (descriptor == null || descriptor.getStatSize() != 4)
                    throw new AssertionError("Provider cannot read private APK");
            }
            try {
                ApkFileProvider.uriForFile(context, new File(context.getFilesDir(), "received-1.apk"));
                throw new AssertionError("Provider accepted a file outside the receiving directory");
            } catch (java.io.FileNotFoundException expected) { }
            if (android.os.Build.VERSION.SDK_INT < 24) {
                File staged = ApkTransferInstaller.legacyInstallerFile(context, file);
                try {
                    if (!staged.isFile() || staged.length() != file.length()
                            || staged.getCanonicalPath().equals(file.getCanonicalPath()))
                        throw new AssertionError("Legacy installer did not get a dedicated copy");
                } finally { staged.delete(); }
            }
            result.putString("stream", "PASS APK storage and provider access with external storage denied\n");
        } catch (Throwable error) {
            status = 0;
            result.putString("stream", android.util.Log.getStackTraceString(error));
        } finally { if (file != null) file.delete(); }
        finish(status, result);
    }
}
