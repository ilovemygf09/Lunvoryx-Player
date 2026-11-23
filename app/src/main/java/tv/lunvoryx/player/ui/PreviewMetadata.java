package tv.lunvoryx.player.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.lunvoryx.player.tmdb.TmdbClient;

/** Wait for D-pad focus to settle before looking up the preview. */
final class PreviewMetadata {
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);
    private final CatalogTask task = new CatalogTask(NETWORK);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LruCache<String, TmdbClient.Details> cache = new LruCache<>(64);

    void load(Context context, String title, boolean movie, CatalogTask.Result<TmdbClient.Details> show) {
        cancel();
        if (!TmdbClient.hasApiKey(context)) return;
        String language = AppStrings.getLang(context);
        String key = language + (movie ? "/movie/" : "/series/") + title;
        TmdbClient.Details cached = cache.get(key);
        if (cached != null) {
            show.accept(cached);
            return;
        }
        Context app = context.getApplicationContext();
        handler.postDelayed(() -> task.submit(() -> TmdbClient.searchAndGetDetails(app, title, movie, language),
                details -> {
                    if (details != null) {
                        cache.put(key, details);
                        show.accept(details);
                    }
                }), 250);
    }

    void cancel() {
        handler.removeCallbacksAndMessages(null);
        task.cancel();
    }
}
