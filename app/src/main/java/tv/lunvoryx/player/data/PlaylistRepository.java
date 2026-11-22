package tv.lunvoryx.player.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** A local-first catalogue. Call load/import/refresh/remove on a worker, never the UI thread. */
public final class PlaylistRepository {
    public static final int MAX_IMPORT_BYTES = 250 * 1024 * 1024; // 250 MB max playlist size
    private static final String DEMO_ID = "lunvoryx-demo";
    private static final Object PREFERENCES_LOCK = new Object();
    private final Context context;
    private final SharedPreferences preferences;
    private final AtomicFile storage;
    // Readers use immutable snapshots and never wait behind a download or disk write.
    private volatile List<Playlist> playlists = Collections.emptyList();
    private boolean loaded;

    // Media snapshot caches
    private volatile List<Channel> cachedAllChannels;
    private volatile List<Channel> cachedLiveChannels;
    private volatile List<Channel> cachedMovieChannels;
    private volatile List<Channel> cachedSeriesChannels;
    private volatile List<SeriesItem> cachedGroupedSeries;
    private final Object mediaCacheLock = new Object();
    private final ExecutorService mediaCacheExecutor = Executors.newSingleThreadExecutor();

    public PlaylistRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("lunvoryx_preferences", Context.MODE_PRIVATE);
        this.storage = new AtomicFile(new File(this.context.getFilesDir(), "playlists.json"));
    }

    public synchronized List<Playlist> load() throws IOException {
        if (loaded) return getPlaylists();
        List<Playlist> restored = new ArrayList<>();
        try (InputStream stream = storage.openRead()) {
            JSONObject root = new JSONObject(readText(stream, (int) Math.min(Integer.MAX_VALUE - 8, (long) MAX_IMPORT_BYTES * 2)));
            JSONArray lists = root.optJSONArray("playlists");
            if (lists != null) for (int i = 0; i < lists.length(); i++) {
                JSONObject item = lists.getJSONObject(i);
                JSONArray channelArray = item.optJSONArray("channels");
                List<Channel> channels = new ArrayList<>();
                if (channelArray != null) for (int j = 0; j < channelArray.length(); j++) {
                    JSONObject c = channelArray.getJSONObject(j);
                    String url = c.optString("url");
                    if (!url.isEmpty()) channels.add(new Channel(c.optString("id"), c.optString("name"), url,
                            c.optString("group"), c.optString("logo"), c.optString("tvgId"),
                            c.optString("userAgent"), c.optString("referer")));
                }
                restored.add(new Playlist(item.getString("id"), item.optString("name"),
                        item.optString("source"), channels, item.optLong("updatedAt")));
            }
        } catch (FileNotFoundException firstLaunch) {
            // An empty library is the correct first-run state.
        } catch (JSONException damaged) {
            throw new IOException("No se ha podido leer la biblioteca guardada.");
        }
        playlists = Collections.unmodifiableList(restored);
        loaded = true;
        if (restored.isEmpty() || (restored.size() == 1 && DEMO_ID.equals(restored.get(0).id) && restored.get(0).channels.size() <= 3)) {
            try {
                addDemoPlaylist();
            } catch (Exception ignored) {}
        }
        invalidateMediaCache();
        precomputeMediaCacheAsync();
        return getPlaylists();
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }

    public void invalidateMediaCache() {
        synchronized (mediaCacheLock) {
            cachedAllChannels = null;
            cachedLiveChannels = null;
            cachedMovieChannels = null;
            cachedSeriesChannels = null;
            cachedGroupedSeries = null;
        }
    }

    public List<Channel> getAllChannels() {
        List<Channel> cached = cachedAllChannels;
        if (cached != null) return cached;
        synchronized (mediaCacheLock) {
            if (cachedAllChannels != null) return cachedAllChannels;
            Map<String, Channel> unique = new LinkedHashMap<>();
            for (Playlist playlist : playlists) for (Channel channel : playlist.channels) {
                if (!unique.containsKey(channel.id)) unique.put(channel.id, channel);
            }
            cachedAllChannels = Collections.unmodifiableList(new ArrayList<>(unique.values()));
            return cachedAllChannels;
        }
    }

    public List<Channel> getLiveChannels() {
        List<Channel> cached = cachedLiveChannels;
        if (cached != null) return cached;
        synchronized (mediaCacheLock) {
            if (cachedLiveChannels != null) return cachedLiveChannels;
            List<Channel> list = new ArrayList<>();
            for (Channel c : getAllChannels()) {
                if (c.getKind() == MediaKind.LIVE) list.add(c);
            }
            cachedLiveChannels = Collections.unmodifiableList(list);
            return cachedLiveChannels;
        }
    }

    public List<Channel> getMovieChannels() {
        List<Channel> cached = cachedMovieChannels;
        if (cached != null) return cached;
        synchronized (mediaCacheLock) {
            if (cachedMovieChannels != null) return cachedMovieChannels;
            List<Channel> list = new ArrayList<>();
            for (Channel c : getAllChannels()) {
                if (c.getKind() == MediaKind.MOVIE) list.add(c);
            }
            cachedMovieChannels = Collections.unmodifiableList(list);
            return cachedMovieChannels;
        }
    }

    public List<Channel> getSeriesChannels() {
        List<Channel> cached = cachedSeriesChannels;
        if (cached != null) return cached;
        synchronized (mediaCacheLock) {
            if (cachedSeriesChannels != null) return cachedSeriesChannels;
            List<Channel> list = new ArrayList<>();
            for (Channel c : getAllChannels()) {
                if (c.getKind() == MediaKind.SERIES) list.add(c);
            }
            cachedSeriesChannels = Collections.unmodifiableList(list);
            return cachedSeriesChannels;
        }
    }

    public List<SeriesItem> peekGroupedSeries() {
        return cachedGroupedSeries;
    }

    public List<SeriesItem> getGroupedSeries() {
        List<SeriesItem> cached = cachedGroupedSeries;
        if (cached != null) return cached;
        synchronized (mediaCacheLock) {
            if (cachedGroupedSeries != null) return cachedGroupedSeries;
            List<Channel> seriesChannels = getSeriesChannels();
            List<SeriesItem> grouped = SeriesParser.groupSeries(seriesChannels);
            cachedGroupedSeries = Collections.unmodifiableList(grouped);
            return cachedGroupedSeries;
        }
    }

    public interface SeriesCallback {
        void onLoaded(List<SeriesItem> series);
    }

    public void loadGroupedSeriesAsync(SeriesCallback callback) {
        List<SeriesItem> cached = cachedGroupedSeries;
        if (cached != null) {
            if (callback != null) callback.onLoaded(cached);
            return;
        }
        mediaCacheExecutor.execute(() -> {
            List<SeriesItem> result = getGroupedSeries();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onLoaded(result);
            });
        });
    }

    public void precomputeMediaCacheAsync() {
        mediaCacheExecutor.execute(() -> {
            try {
                getAllChannels();
                getLiveChannels();
                getMovieChannels();
                getSeriesChannels();
                getGroupedSeries();
            } catch (Exception ignored) {}
        });
    }

    public Channel findChannel(String id) {
        for (Playlist playlist : playlists) for (Channel channel : playlist.channels) if (channel.id.equals(id)) return channel;
        return null;
    }

    public Playlist importUrl(String name, String url) throws IOException {
        ensureLoaded();
        String source = validateHttpUrl(url);
        Download download = download(source);
        List<Channel> channels = parse(download.text, download.finalUrl);
        synchronized (this) {
            Playlist existing = findBySource(source);
            Playlist result = new Playlist(existing == null ? UUID.randomUUID().toString() : existing.id,
                    name, source, channels, System.currentTimeMillis());
            put(result);
            return result;
        }
    }

    public Playlist importFile(String name, Uri uri) throws IOException {
        ensureLoaded();
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) throw new IOException("Selecciona un archivo M3U válido.");
        String text;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("No se ha podido abrir el archivo.");
            text = readText(stream, MAX_IMPORT_BYTES);
        } catch (SecurityException denied) {
            throw new IOException("La aplicación no tiene permiso para leer este archivo. Vuelve a seleccionarlo.");
        }
        List<Channel> channels = parse(text, "");
        synchronized (this) {
            Playlist existing = findBySource(uri.toString());
            Playlist result = new Playlist(existing == null ? UUID.randomUUID().toString() : existing.id,
                    name, uri.toString(), channels, System.currentTimeMillis());
            put(result);
            return result;
        }
    }

    public Playlist importContent(String name, String content) throws IOException {
        ensureLoaded();
        if (content == null || content.trim().isEmpty()) throw new IOException("El contenido de la lista está vacío.");
        List<Channel> channels = parse(content, "");
        synchronized (this) {
            Playlist result = new Playlist(UUID.randomUUID().toString(),
                    name.isEmpty() ? "Lista móvil" : name, "text:" + System.currentTimeMillis(), channels, System.currentTimeMillis());
            put(result);
            return result;
        }
    }

    public synchronized Playlist addDemoPlaylist() throws IOException {
        ensureLoaded();
        List<Channel> channels = new ArrayList<>();

        // 1. Live TV Channels (matching user reference mockup)
        channels.add(Channel.create("DAZN LaLiga", "https://test-streams.mux.dev/live/dazn_laliga.m3u8", "EU-ES DAZN", "", "dazn.es", "", ""));
        channels.add(Channel.create("La 1 HD", "https://test-streams.mux.dev/live/la1.m3u8", "ES | GENERALISTAS", "", "la1.es", "", ""));
        channels.add(Channel.create("Antena 3", "https://test-streams.mux.dev/live/antena3.m3u8", "ES | GENERALISTAS", "", "antena3.es", "", ""));
        channels.add(Channel.create("Movistar Cine", "https://test-streams.mux.dev/live/movistar_cine.ts", "ES | CINE", "", "mcine.es", "", ""));
        channels.add(Channel.create("Eurosport", "https://test-streams.mux.dev/live/eurosport.ts", "ES | DEPORTES", "", "eurosport.es", "", ""));
        channels.add(Channel.create("National Geographic", "https://test-streams.mux.dev/live/natgeo.ts", "ES | DOCUMENTALES", "", "natgeo.es", "", ""));
        channels.add(Channel.create("laSexta", "https://test-streams.mux.dev/live/lasexta.m3u8", "ES | GENERALISTAS", "", "lasexta.es", "", ""));
        channels.add(Channel.create("Cuatro", "https://test-streams.mux.dev/live/cuatro.m3u8", "ES | GENERALISTAS", "", "cuatro.es", "", ""));

        // 2. Movies (matching user reference mockup)
        channels.add(Channel.create("La mangosta", "https://test-streams.mux.dev/movie/mangosta.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("El vestido", "https://test-streams.mux.dev/movie/vestido.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Colisión", "https://test-streams.mux.dev/movie/colision.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Kraken", "https://test-streams.mux.dev/movie/kraken.mkv", "ES | PELÍCULAS 2026 (MKV)", "", "", "", ""));
        channels.add(Channel.create("Recreación de un asesinato", "https://test-streams.mux.dev/movie/recreacion.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Insidious: Fuera del límite", "https://test-streams.mux.dev/movie/insidious.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("La última chispa de esperanza", "https://test-streams.mux.dev/movie/chispa.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("¿Por qué me casé otra vez?", "https://test-streams.mux.dev/movie/porque.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("En la ruta del crimen", "https://test-streams.mux.dev/movie/crimen.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Sintel", "https://test-streams.mux.dev/movie/sintel.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Luces en la catedral", "https://test-streams.mux.dev/movie/luces.mp4", "ES | PELÍCULAS 2026 (MP4)", "", "", "", ""));
        channels.add(Channel.create("Cómo entrenar a tu dragón 2", "https://test-streams.mux.dev/movie/dragon.mp4", "ES | PELÍCULAS ANIMACIÓN", "", "", "", ""));

        // 3. Series (multiple episodes per series to demonstrate 1-cover-per-series grouping and episode sheets)
        // Dark (3 seasons, 6 episodes)
        channels.add(Channel.create("Dark S01 E01 - Secretos", "https://test-streams.mux.dev/series/dark_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Dark S01 E02 - Mentiras", "https://test-streams.mux.dev/series/dark_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Dark S01 E03 - Pasados y presentes", "https://test-streams.mux.dev/series/dark_s1e3.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Dark S02 E01 - Principios y finales", "https://test-streams.mux.dev/series/dark_s2e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Dark S02 E02 - Materia oscura", "https://test-streams.mux.dev/series/dark_s2e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Dark S03 E01 - Déjà-vu", "https://test-streams.mux.dev/series/dark_s3e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Stranger Things
        channels.add(Channel.create("Stranger Things S01 E01", "https://test-streams.mux.dev/series/st_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Stranger Things S01 E02", "https://test-streams.mux.dev/series/st_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Stranger Things S02 E01", "https://test-streams.mux.dev/series/st_s2e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Stranger Things S02 E04 - El espía", "https://test-streams.mux.dev/series/st_s2e4.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Miércoles
        channels.add(Channel.create("Miércoles S01 E01", "https://test-streams.mux.dev/series/wed_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("Miércoles S01 E02", "https://test-streams.mux.dev/series/wed_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // La casa de papel
        channels.add(Channel.create("La casa de papel S01 E01", "https://test-streams.mux.dev/series/lcdp_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("La casa de papel S01 E02", "https://test-streams.mux.dev/series/lcdp_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("La casa de papel S02 E01", "https://test-streams.mux.dev/series/lcdp_s2e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // El juego del calamar
        channels.add(Channel.create("El juego del calamar S01 E01", "https://test-streams.mux.dev/series/squid_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("El juego del calamar S01 E02", "https://test-streams.mux.dev/series/squid_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // The Crown
        channels.add(Channel.create("The Crown S01 E01", "https://test-streams.mux.dev/series/crown_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));
        channels.add(Channel.create("The Crown S01 E02", "https://test-streams.mux.dev/series/crown_s1e2.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Black Mirror
        channels.add(Channel.create("Black Mirror S01 E01", "https://test-streams.mux.dev/series/bm_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // The Witcher
        channels.add(Channel.create("The Witcher S01 E01", "https://test-streams.mux.dev/series/witcher_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // One Piece
        channels.add(Channel.create("One Piece S01 E01", "https://test-streams.mux.dev/series/op_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Lupin
        channels.add(Channel.create("Lupin S01 E01", "https://test-streams.mux.dev/series/lupin_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Élite
        channels.add(Channel.create("Élite S01 E01", "https://test-streams.mux.dev/series/elite_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // You
        channels.add(Channel.create("You S01 E01", "https://test-streams.mux.dev/series/you_s1e1.mp4", "ES | SERIES NETFLIX", "", "", "", ""));

        // Additional providers
        channels.add(Channel.create("The Boys S01 E01", "https://test-streams.mux.dev/series/theboys_s1e1.mp4", "ES | SERIES AMAZON", "", "", "", ""));
        channels.add(Channel.create("Succession S01 E01", "https://test-streams.mux.dev/series/succession_s1e1.mp4", "ES | SERIES HBO MAX", "", "", "", ""));

        Playlist result = new Playlist(DEMO_ID, "Lista de prueba", "demo:", channels, System.currentTimeMillis());
        put(result);
        return result;
    }

    public Playlist refresh(String playlistId) throws IOException {
        ensureLoaded();
        Playlist current = findPlaylist(playlistId);
        if (current == null) throw new IOException("Esta lista ya no está disponible.");
        if (current.source.equals("demo:")) return addDemoPlaylist();
        String scheme = Uri.parse(current.source).getScheme();
        if ("content".equalsIgnoreCase(scheme)) return importFile(current.name, Uri.parse(current.source));
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return importUrl(current.name, current.source);
        throw new IOException("Vuelve a importar el archivo para actualizar esta lista.");
    }

    public synchronized void removePlaylist(String id) throws IOException {
        ensureLoaded();
        List<Playlist> next = new ArrayList<>(playlists);
        for (int i = next.size() - 1; i >= 0; i--) if (next.get(i).id.equals(id)) next.remove(i);
        save(next);
        playlists = Collections.unmodifiableList(next);
        invalidateMediaCache();
        precomputeMediaCacheAsync();
    }

    public boolean isFavorite(String channelId) { return getFavoriteIds().contains(channelId); }

    public boolean toggleFavorite(String channelId) {
        synchronized (PREFERENCES_LOCK) {
            Set<String> favorites = getFavoriteIds();
            boolean added;
            if (favorites.contains(channelId)) { favorites.remove(channelId); added = false; }
            else { favorites.add(channelId); added = true; }
            preferences.edit().putStringSet("favorites", favorites).apply();
            return added;
        }
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(preferences.getStringSet("favorites", Collections.emptySet()));
    }

    public void recordPlayed(String channelId) {
        synchronized (PREFERENCES_LOCK) {
            List<String> recents = getRecentIds();
            recents.remove(channelId);
            recents.add(0, channelId);
            if (recents.size() > 40) recents = new ArrayList<>(recents.subList(0, 40));
            JSONArray json = new JSONArray();
            for (String id : recents) json.put(id);
            preferences.edit().putString("recents", json.toString()).apply();
        }
    }

    public List<String> getRecentIds() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(preferences.getString("recents", "[]"));
            for (int i = 0; i < json.length(); i++) result.add(json.getString(i));
        } catch (JSONException ignored) { }
        return result;
    }

    private synchronized void ensureLoaded() throws IOException { if (!loaded) load(); }
    private Playlist findPlaylist(String id) {
        for (Playlist playlist : playlists) if (playlist.id.equals(id)) return playlist;
        return null;
    }
    private Playlist findBySource(String source) {
        for (Playlist playlist : playlists) if (playlist.source.equals(source)) return playlist;
        return null;
    }

    private synchronized void put(Playlist playlist) throws IOException {
        List<Playlist> next = new ArrayList<>(playlists);
        int existingIndex = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(playlist.id)) { existingIndex = i; break; }
        if (existingIndex >= 0) next.set(existingIndex, playlist); else next.add(playlist);
        save(next);
        playlists = Collections.unmodifiableList(next);
        invalidateMediaCache();
        precomputeMediaCacheAsync();
    }

    private void save(List<Playlist> data) throws IOException {
        FileOutputStream stream = null;
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            JSONArray lists = new JSONArray();
            for (Playlist playlist : data) {
                JSONObject item = new JSONObject();
                item.put("id", playlist.id).put("name", playlist.name).put("source", playlist.source).put("updatedAt", playlist.updatedAt);
                JSONArray channels = new JSONArray();
                for (Channel channel : playlist.channels) {
                    channels.put(new JSONObject().put("id", channel.id).put("name", channel.name).put("url", channel.url)
                            .put("group", channel.group).put("logo", channel.logoUrl).put("tvgId", channel.tvgId)
                            .put("userAgent", channel.userAgent).put("referer", channel.referer));
                }
                item.put("channels", channels);
                lists.put(item);
            }
            root.put("playlists", lists);
            byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
            if (encoded.length > (long) MAX_IMPORT_BYTES * 2) throw new IOException("La biblioteca supera el tamaño máximo permitido.");
            stream = storage.startWrite();
            stream.write(encoded);
            storage.finishWrite(stream);
        } catch (Exception failure) {
            if (stream != null) storage.failWrite(stream);
            throw new IOException("No se han podido guardar los cambios. Comprueba el espacio disponible.");
        }
    }

    private static List<Channel> parse(String text, String source) throws IOException {
        try { return M3uParser.parse(text, source); }
        catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage()); }
    }

    private static String validateHttpUrl(String raw) throws IOException {
        if (raw == null) throw new IOException("Introduce una URL HTTP o HTTPS.");
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception invalid) { throw new IOException("Introduce una URL HTTP o HTTPS válida."); }
    }

    private static Download download(String source) throws IOException {
        String current = source;
        long deadline = System.nanoTime() + 180_000_000_000L;
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpURLConnection connection = null;
            try {
                long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= 0) throw new java.net.SocketTimeoutException();
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setConnectTimeout((int) Math.min(20_000, Math.max(1, remainingMillis)));
                connection.setReadTimeout((int) Math.min(60_000, Math.max(1, remainingMillis)));
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "LunvoryxTV/1.0");
                connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, text/plain, */*");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("El servidor ha devuelto una redirección no válida.");
                    current = validateHttpUrl(new URL(new URL(current), location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("El servidor ha respondido con el código " + status + ".");
                long size = connection.getContentLength();
                if (size > MAX_IMPORT_BYTES) throw new IOException("La lista supera el límite de 250 MB.");
                InputStream input = connection.getInputStream();
                if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) input = new GZIPInputStream(input);
                try (InputStream stream = input) { return new Download(readText(stream, MAX_IMPORT_BYTES, deadline), current); }
            } catch (java.net.SocketTimeoutException timeout) {
                throw new IOException("El servidor ha tardado demasiado en responder. Inténtalo de nuevo.");
            } catch (java.net.UnknownHostException unavailable) {
                throw new IOException("No se encuentra el servidor. Comprueba la dirección y la conexión.");
            } catch (javax.net.ssl.SSLException tls) {
                throw new IOException("No se ha podido establecer una conexión segura con el servidor.");
            } finally { if (connection != null) connection.disconnect(); }
        }
        throw new IOException("El servidor ha devuelto demasiadas redirecciones.");
    }

    private static String readText(InputStream stream, int limit) throws IOException {
        return readText(stream, limit, Long.MAX_VALUE);
    }

    private static String readText(InputStream stream, int limit, long deadline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(limit, 1024 * 1024));
        byte[] buffer = new byte[32 * 1024];
        int count;
        int total = 0;
        while ((count = stream.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Importación cancelada.");
            if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException();
            total += count;
            if (total > limit) throw new IOException("El archivo supera el tamaño máximo permitido (250 MB).");
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }

    private static final class Download {
        final String text, finalUrl;
        Download(String text, String finalUrl) { this.text = text; this.finalUrl = finalUrl; }
    }
}
