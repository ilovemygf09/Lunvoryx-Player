package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

/** Decode and cache each artwork at the resolution required by its destination. */
public final class Artwork {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    public static Bitmap get(Context context, int resource, int desiredWidth) {
        int width = Math.max(1, desiredWidth);
        String key = resource + "/" + width;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resource, options);
        options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= width) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resource, options);
        if (bitmap != null) CACHE.put(key, bitmap);
        return bitmap;
    }
}
