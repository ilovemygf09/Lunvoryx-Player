package tv.lunvoryx.player.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
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
    private boolean loaded;
    private static final ExecutorService MEDIA_WORKER = Executors.newSingleThreadExecutor();
    private volatile Catalog catalog = new Catalog(Collections.emptyList());

    // Publish all lookup structures together, so readers can finish with their existing snapshot.
    private static final class Catalog {
        final List<Playlist> playlists;
        final Map<String, Channel> byId = new LinkedHashMap<>();
        final List<Channel> all;
        List<Channel> live, movies, episodes;
        private List<SeriesItem> series;

        Catalog(List<Playlist> playlists) {
            this.playlists = Collections.unmodifiableList(playlists);
            for (Playlist playlist : playlists) {
                for (Channel channel : playlist.channels) {
                    if (!byId.containsKey(channel.id)) byId.put(channel.id, channel);
                }
            }
            all = Collections.unmodifiableList(new ArrayList<>(byId.values()));
        }

        synchronized void classify() {
            if (live != null) return;
            List<Channel> liveItems = new ArrayList<>(), movieItems = new ArrayList<>(), seriesItems = new ArrayList<>();
            for (Channel channel : all) {
                switch (channel.getKind()) {
                    case LIVE: liveItems.add(channel); break;
                    case MOVIE: movieItems.add(channel); break;
                    case SERIES: seriesItems.add(channel); break;
                }
            }
            live = Collections.unmodifiableList(liveItems);
            movies = Collections.unmodifiableList(movieItems);
            episodes = Collections.unmodifiableList(seriesItems);
        }

        private final Object seriesLock = new Object();
        List<SeriesItem> groupedSeries() {
            synchronized (seriesLock) {
                if (series == null) {
                    classify();
                    series = Collections.unmodifiableList(SeriesParser.groupSeries(episodes));
                }
                return series;
            }
        }
    }

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
                    if (DEMO_ID.equals(item.optString("id")) && url.startsWith("https://test-streams.mux.dev/"))
                        url = demoUrl(url.substring("https://test-streams.mux.dev/".length()), c.optString("id"));
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
        catalog = new Catalog(restored);
        loaded = true;
        if (restored.isEmpty() || (restored.size() == 1 && DEMO_ID.equals(restored.get(0).id) && restored.get(0).channels.size() <= 3)) {
            try {
                addDemoPlaylist();
                return getPlaylists();
            } catch (Exception ignored) {}
        }
        precomputeMediaCacheAsync();
        return getPlaylists();
    }

    public List<Playlist> getPlaylists() {
        return catalog.playlists;
    }

    public List<Channel> getAllChannels() { return catalog.all; }

    public List<Channel> getLiveChannels() {
        Catalog snapshot = catalog;
        snapshot.classify();
        return snapshot.live;
    }

    public List<Channel> getMovieChannels() {
        Catalog snapshot = catalog;
        snapshot.classify();
        return snapshot.movies;
    }

    public List<Channel> getSeriesChannels() {
        Catalog snapshot = catalog;
        snapshot.classify();
        return snapshot.episodes;
    }

    public List<SeriesItem> getGroupedSeries() { return catalog.groupedSeries(); }

    private void precomputeMediaCacheAsync() {
        Catalog snapshot = catalog;
        MEDIA_WORKER.execute(() -> {
            if (snapshot == catalog) snapshot.groupedSeries();
        });
    }

    public Channel findChannel(String id) { return catalog.byId.get(id); }

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

    private static String demoUrl(String path, String id) {
        int slash = path.indexOf('/');
        if (slash < 0) return "https://test-streams.mux.dev/" + path;
        String kind = path.substring(0, slash);
        if (!kind.equals("live") && !kind.equals("series") && !kind.equals("movie"))
            return "https://test-streams.mux.dev/" + path;
        return "asset:///demo/" + kind + "/sample.mp4#" + id;
    }

    private static Channel demoChannel(String name, String legacyPath, String group, String tvgId) {
        // Retain favorites and watch positions from earlier versions of the built-in list.
        String id = Channel.stableId("https://test-streams.mux.dev/" + legacyPath);
        return new Channel(id, name, demoUrl(legacyPath, id), group, "", tvgId, "", "");
    }

    public synchronized Playlist addDemoPlaylist() throws IOException {
        ensureLoaded();
        List<Channel> channels = new ArrayList<>();

        // 1. Live TV Channels (matching user reference mockup)
        channels.add(demoChannel("DAZN LaLiga", "live/dazn_laliga.m3u8", "EU-ES DAZN", "dazn.es"));
        channels.add(demoChannel("La 1 HD", "live/la1.m3u8", "ES | GENERALISTAS", "la1.es"));
        channels.add(demoChannel("Antena 3", "live/antena3.m3u8", "ES | GENERALISTAS", "antena3.es"));
        channels.add(demoChannel("Movistar Cine", "live/movistar_cine.ts", "ES | CINE", "mcine.es"));
        channels.add(demoChannel("Eurosport", "live/eurosport.ts", "ES | DEPORTES", "eurosport.es"));
        channels.add(demoChannel("National Geographic", "live/natgeo.ts", "ES | DOCUMENTALES", "natgeo.es"));
        channels.add(demoChannel("laSexta", "live/lasexta.m3u8", "ES | GENERALISTAS", "lasexta.es"));
        channels.add(demoChannel("Cuatro", "live/cuatro.m3u8", "ES | GENERALISTAS", "cuatro.es"));

        // 2. Movies (matching user reference mockup)
        channels.add(demoChannel("La mangosta", "movie/mangosta.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("El vestido", "movie/vestido.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Colisión", "movie/colision.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Kraken", "movie/kraken.mkv", "ES | PELÍCULAS 2026 (MKV)", ""));
        channels.add(demoChannel("Recreación de un asesinato", "movie/recreacion.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Insidious: Fuera del límite", "movie/insidious.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("La última chispa de esperanza", "movie/chispa.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("¿Por qué me casé otra vez?", "movie/porque.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("En la ruta del crimen", "movie/crimen.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Sintel", "movie/sintel.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Luces en la catedral", "movie/luces.mp4", "ES | PELÍCULAS 2026 (MP4)", ""));
        channels.add(demoChannel("Cómo entrenar a tu dragón 2", "movie/dragon.mp4", "ES | PELÍCULAS ANIMACIÓN", ""));

        // 3. Series (multiple episodes per series to demonstrate 1-cover-per-series grouping and episode sheets)
        // Dark (3 seasons, 6 episodes)
        channels.add(demoChannel("Dark S01 E01 - Secretos", "series/dark_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Dark S01 E02 - Mentiras", "series/dark_s1e2.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Dark S01 E03 - Pasados y presentes", "series/dark_s1e3.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Dark S02 E01 - Principios y finales", "series/dark_s2e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Dark S02 E02 - Materia oscura", "series/dark_s2e2.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Dark S03 E01 - Déjà-vu", "series/dark_s3e1.mp4", "ES | SERIES NETFLIX", ""));

        // Stranger Things
        channels.add(demoChannel("Stranger Things S01 E01", "series/st_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Stranger Things S01 E02", "series/st_s1e2.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Stranger Things S02 E01", "series/st_s2e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Stranger Things S02 E04 - El espía", "series/st_s2e4.mp4", "ES | SERIES NETFLIX", ""));

        // Miércoles
        channels.add(demoChannel("Miércoles S01 E01", "series/wed_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("Miércoles S01 E02", "series/wed_s1e2.mp4", "ES | SERIES NETFLIX", ""));

        // La casa de papel
        channels.add(demoChannel("La casa de papel S01 E01", "series/lcdp_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("La casa de papel S01 E02", "series/lcdp_s1e2.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("La casa de papel S02 E01", "series/lcdp_s2e1.mp4", "ES | SERIES NETFLIX", ""));

        // El juego del calamar
        channels.add(demoChannel("El juego del calamar S01 E01", "series/squid_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("El juego del calamar S01 E02", "series/squid_s1e2.mp4", "ES | SERIES NETFLIX", ""));

        // The Crown
        channels.add(demoChannel("The Crown S01 E01", "series/crown_s1e1.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(demoChannel("The Crown S01 E02", "series/crown_s1e2.mp4", "ES | SERIES NETFLIX", ""));

        // Black Mirror
        channels.add(demoChannel("Black Mirror S01 E01", "series/bm_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // The Witcher
        channels.add(demoChannel("The Witcher S01 E01", "series/witcher_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // One Piece
        channels.add(demoChannel("One Piece S01 E01", "series/op_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // Lupin
        channels.add(demoChannel("Lupin S01 E01", "series/lupin_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // Élite
        channels.add(demoChannel("Élite S01 E01", "series/elite_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // You
        channels.add(demoChannel("You S01 E01", "series/you_s1e1.mp4", "ES | SERIES NETFLIX", ""));

        // Additional providers
        channels.add(demoChannel("The Boys S01 E01", "series/theboys_s1e1.mp4", "ES | SERIES AMAZON", ""));
        channels.add(demoChannel("Succession S01 E01", "series/succession_s1e1.mp4", "ES | SERIES HBO MAX", ""));

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
        List<Playlist> next = new ArrayList<>(getPlaylists());
        for (int i = next.size() - 1; i >= 0; i--) if (next.get(i).id.equals(id)) next.remove(i);
        save(next);
        catalog = new Catalog(next);
        precomputeMediaCacheAsync();
    }

    public boolean isFavorite(String channelId) { return preferences.getStringSet("favorites", Collections.emptySet()).contains(channelId); }

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
        for (Playlist playlist : getPlaylists()) if (playlist.id.equals(id)) return playlist;
        return null;
    }
    private Playlist findBySource(String source) {
        for (Playlist playlist : getPlaylists()) if (playlist.source.equals(source)) return playlist;
        return null;
    }

    private synchronized void put(Playlist playlist) throws IOException {
        List<Playlist> next = new ArrayList<>(getPlaylists());
        int existingIndex = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(playlist.id)) { existingIndex = i; break; }
        if (existingIndex >= 0) next.set(existingIndex, playlist); else next.add(playlist);
        save(next);
        catalog = new Catalog(next);
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
