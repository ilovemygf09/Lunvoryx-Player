package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;

/** Decode fixed UI artwork at display size rather than its export resolution. */
public final class Artwork {
    private static final LruCache<Integer,Bitmap> CACHE=new LruCache<Integer,Bitmap>(16*1024*1024){
        @Override protected int sizeOf(Integer key,Bitmap value){return value.getByteCount();}
    };
    public static Bitmap get(Context context,int resource,int desiredWidth){
        Bitmap bitmap=CACHE.get(resource);if(bitmap!=null)return bitmap;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeResource(context.getResources(),resource,o);
        o.inSampleSize=1;while(o.outWidth/(o.inSampleSize*2)>=desiredWidth)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inScaled=false;bitmap=BitmapFactory.decodeResource(context.getResources(),resource,o);CACHE.put(resource,bitmap);return bitmap;
    }
}
