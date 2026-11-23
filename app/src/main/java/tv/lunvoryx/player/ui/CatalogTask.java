package tv.lunvoryx.player.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One pending catalogue query per view. Submit and cancel on the main thread. */
final class CatalogTask {
    interface Query<T> { T run(); }
    interface Result<T> { void accept(T result); }

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private Future<?> pending;
    private int generation;

    CatalogTask() { this(WORKERS); }

    CatalogTask(ExecutorService executor) { this.executor = executor; }

    <T> void submit(Query<T> query, Result<T> callback) {
        cancel();
        int request = generation;
        pending = executor.submit(() -> {
            T result = query.run();
            if (Thread.currentThread().isInterrupted()) return;
            MAIN.post(() -> {
                if (generation == request) {
                    pending = null;
                    callback.accept(result);
                }
            });
        });
    }

    void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
    }
}
