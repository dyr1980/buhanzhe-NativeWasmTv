package xiao.bu.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Small site artwork, independent of bitmap density and the tab's click area. */
final class BrowserFaviconView extends View {
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect bounds = new Rect();
    private final int maxSide;

    BrowserFaviconView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;
        maxSide = Math.round(14f * getResources().getDisplayMetrics().density);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        int limit = Math.max(0, Math.min(maxSide, Math.min(width, height)));
        if (limit == 0) { bounds.setEmpty(); return; }
        float scale = Math.min(1f, limit / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
        int drawingWidth = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int drawingHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
        int left = (width - drawingWidth) / 2;
        int top = (height - drawingHeight) / 2;
        bounds.set(left, top, left + drawingWidth, top + drawingHeight);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!bitmap.isRecycled() && !bounds.isEmpty()) {
            // Explicit pixel bounds bypass BitmapDrawable's automatic density
            // enlargement. Filter only when shrinking; never stretch a favicon.
            canvas.drawBitmap(bitmap, null, bounds, paint);
        }
    }
}
