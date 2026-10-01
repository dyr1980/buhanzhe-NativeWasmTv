package xiao.bu.tv;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class ScreenshotGallery {
    static void save(Context context, byte[] image) throws IOException {
        boolean jpeg = image != null && image.length >= 4
                && image[0] == (byte) 0xff && image[1] == (byte) 0xd8;
        boolean png = image != null && image.length >= 8
                && image[0] == (byte) 137 && image[1] == 80
                && image[2] == 78 && image[3] == 71;
        if (!jpeg && !png) throw new IOException("截图格式无效");
        String mime = jpeg ? "image/jpeg" : "image/png";
        String name = "nTv-" + System.currentTimeMillis() + (jpeg ? ".jpg" : ".png");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/nTv");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建相册图片");
            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("无法写入相册");
                    out.write(image);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
            } catch (Exception error) {
                context.getContentResolver().delete(uri, null, null);
                throw new IOException("保存相册失败", error);
            }
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "nTv");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建截图目录");
            File file = new File(directory, name);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(image);
            } catch (IOException error) {
                file.delete();
                throw error;
            }
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                    new String[]{mime}, null);
        }
    }
}
