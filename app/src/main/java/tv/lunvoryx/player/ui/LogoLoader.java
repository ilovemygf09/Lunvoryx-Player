package tv.lunvoryx.player.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.ref.WeakReference;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded image downloads, decoded off the UI thread. Never holds an Activity. */
public final class LogoLoader {
    private static final int MAX_PENDING=32;
    private static final long FAILURE_BACKOFF_MS=30_000;
    // Visible cards win over earlier off-screen work; both queue length and concurrency are bounded.
    private static final LinkedBlockingDeque<Runnable> PENDING=new LinkedBlockingDeque<Runnable>(MAX_PENDING){
        @Override public boolean offer(Runnable request){return offerFirst(request);}
    };
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,PENDING,(request,pool)->{
        Runnable obsolete=PENDING.pollLast();
        if(obsolete instanceof Request)((Request)obsolete).cancelled=true;
        if(!pool.isShutdown()&&!PENDING.offerFirst(request)&&request instanceof Request)((Request)request).cancelled=true;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String k,Bitmap b){ return b.getByteCount(); }
    };
    private static final LruCache<String,Long> FAILURES=new LruCache<>(128);
    static {IO.allowCoreThreadTimeOut(true);}

    public static void load(String url,ImageView view){
        Object previous=view.getTag();
        if(previous instanceof Request)((Request)previous).cancelled=true;
        view.setTag(null);view.setImageDrawable(null);
        if(url==null||(!url.regionMatches(true,0,"https://",0,8)&&!url.regionMatches(true,0,"http://",0,7))) return;
        Request request=new Request(url,view);view.setTag(request);
        Bitmap cached=CACHE.get(url); if(cached!=null){view.setImageBitmap(cached);return;}
        if(recentlyFailed(url))return;
        IO.execute(request);
    }

    private static boolean recentlyFailed(String url){
        Long until=FAILURES.get(url);
        return until!=null&&SystemClock.elapsedRealtime()<until;
    }

    private static final class Request implements Runnable {
        final String url;
        final WeakReference<ImageView> target;
        volatile boolean cancelled;
        Request(String url,ImageView view){this.url=url;target=new WeakReference<>(view);}

        // The tag is only an identity token. No layout, attachment or drawing properties are read off-thread.
        private boolean current(){ImageView view=target.get();return !cancelled&&view!=null&&view.getTag()==this;}
        private void deliver(Bitmap bitmap){
            MAIN.post(()->{ImageView view=target.get();if(!cancelled&&view!=null&&view.getTag()==this)view.setImageBitmap(bitmap);});
        }
        private void fail(){FAILURES.put(url,SystemClock.elapsedRealtime()+FAILURE_BACKOFF_MS);}

        @Override public void run(){
            if(!current())return;
            Bitmap cached=CACHE.get(url);if(cached!=null){deliver(cached);return;}
            if(recentlyFailed(url))return;
            HttpURLConnection connection=null;
            try{
                long deadline=SystemClock.elapsedRealtime()+8_000;
                connection=(HttpURLConnection)new URL(url).openConnection(); connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
                if(connection.getResponseCode()!=200){fail();return;}
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] buf=new byte[4096];int n;
                    while((n=in.read(buf))!=-1){
                        if(!current())return;
                        if(SystemClock.elapsedRealtime()>deadline||out.size()+n>2*1024*1024){fail();return;}
                        out.write(buf,0,n);
                    }
                }
                if(!current())return;
                byte[] data=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(o.outWidth<=0||o.outHeight<=0){fail();return;}
                o.inSampleSize=1;while(o.outWidth/o.inSampleSize>256||o.outHeight/o.inSampleSize>256)o.inSampleSize*=2;
                o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(b!=null){CACHE.put(url,b);FAILURES.remove(url);deliver(b);}else fail();
            }catch(Exception ignored){if(!cancelled)fail();}finally{if(connection!=null)connection.disconnect();}
        }
    }
}
