package tv.lunvoryx.player.epg;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Disk and network methods must be called on a worker. Lookups never perform I/O. */
public final class EpgRepository {
    private static final long MAX_DOWNLOAD = 24L * 1024 * 1024;
    private static final long MAX_EXPANDED = 64L * 1024 * 1024;
    private static final long MAX_DURATION_MS = 120000;
    private final AtomicFile cache;
    private final SharedPreferences preferences;
    private volatile EpgIndex index = new EpgIndex(Collections.emptyList());
    private volatile int programCount;
    private volatile long updatedAt;

    public EpgRepository(Context context) {
        Context app = context.getApplicationContext();
        cache = new AtomicFile(new File(app.getFilesDir(), "guide.xmltv.json"));
        preferences = app.getSharedPreferences("lunvoryx_epg", Context.MODE_PRIVATE);
        updatedAt = preferences.getLong("updated_at", 0);
    }

    public String getSource() { return preferences.getString("source", ""); }
    public long getUpdatedAt() { return updatedAt; }
    public int getProgramCount() { return programCount; }
    public Program now(String tvgId) { return index.now(tvgId, System.currentTimeMillis()); }
    public Program next(String tvgId) { return index.next(tvgId, System.currentTimeMillis()); }

    public synchronized void load() throws IOException {
        List<Program> result = new ArrayList<>();
        long earliest = System.currentTimeMillis() - 6L * 60 * 60 * 1000;
        try (InputStream input = new LimitedInputStream(cache.openRead(), MAX_EXPANDED, Long.MAX_VALUE);
             JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.beginArray();
            while (reader.hasNext()) {
                if (result.size() >= 60000) throw new IOException("La copia de la guía es demasiado grande.");
                String channel = "", title = "", description = "";
                long start = -1, end = -1;
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "channel": channel = reader.nextString(); break;
                        case "title": title = reader.nextString(); break;
                        case "description": description = reader.nextString(); break;
                        case "start": start = reader.nextLong(); break;
                        case "end": end = reader.nextLong(); break;
                        default: reader.skipValue();
                    }
                }
                reader.endObject();
                if (!channel.isEmpty() && !title.isEmpty() && (end > earliest || start >= earliest)) {
                    result.add(new Program(channel, title, description, start, end));
                }
            }
            reader.endArray();
        } catch (FileNotFoundException firstLaunch) {
            return;
        } catch (IllegalStateException | NumberFormatException e) {
            throw new IOException("La copia de la guía no se puede leer. Actualízala para recuperarla.");
        }
        index = new EpgIndex(result);
        programCount = result.size();
    }

    public synchronized void refresh(String address) throws IOException {
        URL url = validatedUrl(address);
        HttpURLConnection connection = null;
        long deadline = System.currentTimeMillis() + MAX_DURATION_MS;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (System.currentTimeMillis() > deadline || Thread.currentThread().isInterrupted()) {
                    throw new IOException("La guía tarda demasiado en responder. Inténtalo de nuevo.");
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "LunvoryxTV/1.0");
                connection.setRequestProperty("Accept", "application/xml,text/xml,application/gzip,*/*");
                connection.setRequestProperty("Accept-Encoding", "gzip");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects == 5) throw new IOException("La fuente XMLTV redirige demasiadas veces.");
                    URL target = new URL(url, location);
                    url = validatedUrl(target.toExternalForm());
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("La fuente XMLTV devuelve un error HTTP " + status + ".");
                if (connection.getContentLength() > MAX_DOWNLOAD) throw new IOException("La descarga de la guía supera los 24 MB.");
                break;
            }
            if (connection == null) throw new IOException("No se pudo abrir la guía.");
            List<Program> programs;
            try (InputStream response = connection.getInputStream();
                 PushbackInputStream compressed = new PushbackInputStream(new BufferedInputStream(new LimitedInputStream(response, MAX_DOWNLOAD, deadline)), 2)) {
                int a = compressed.read();
                int b = compressed.read();
                if (b != -1) compressed.unread(b);
                if (a != -1) compressed.unread(a);
                InputStream decoded = a == 0x1f && b == 0x8b ? new GZIPInputStream(compressed) : compressed;
                try (InputStream bounded = new LimitedInputStream(decoded, MAX_EXPANDED, deadline)) {
                    programs = XmltvParser.parse(bounded, System.currentTimeMillis());
                }
            }
            EpgIndex nextIndex = new EpgIndex(programs);
            save(programs);
            long timestamp = System.currentTimeMillis();
            preferences.edit().putString("source", address.trim()).putLong("updated_at", timestamp).commit();
            index = nextIndex;
            programCount = programs.size();
            updatedAt = timestamp;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void save(List<Program> programs) throws IOException {
        FileOutputStream output = null;
        try {
            output = cache.startWrite();
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.beginArray();
            for (Program p : programs) {
                writer.beginObject();
                writer.name("channel").value(p.channelId);
                writer.name("title").value(p.title);
                writer.name("description").value(p.description);
                writer.name("start").value(p.startMillis);
                writer.name("end").value(p.endMillis);
                writer.endObject();
            }
            writer.endArray();
            writer.flush();
            cache.finishWrite(output);
        } catch (IOException e) {
            if (output != null) cache.failWrite(output);
            throw new IOException("No se pudo guardar la guía. Comprueba el espacio libre.");
        }
    }

    private static URL validatedUrl(String address) throws IOException {
        try {
            URL url = new URL(address.trim());
            if (!("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) || url.getHost().isEmpty()) {
                throw new IOException("Introduce una URL XMLTV que empiece por http:// o https://.");
            }
            return url;
        } catch (java.net.MalformedURLException | NullPointerException e) {
            throw new IOException("Introduce una URL XMLTV válida.");
        }
    }

    static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private final long deadline;
        private long consumed;
        LimitedInputStream(InputStream input, long limit, long deadline) { super(input); this.limit = limit; this.deadline = deadline; }
        private void check(int count) throws IOException {
            if (count > 0) consumed += count;
            if (consumed > limit) throw new IOException("La guía XMLTV supera el tamaño permitido.");
            if (Thread.currentThread().isInterrupted()) throw new IOException("Carga cancelada.");
            if (System.currentTimeMillis() > deadline) throw new IOException("La guía tarda demasiado en responder. Inténtalo de nuevo.");
        }
        @Override public int read() throws IOException { check(0); int value = in.read(); check(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { check(0); int count = in.read(bytes, offset, length); check(count); return count; }
        @Override public long skip(long count) throws IOException { long result = in.skip(Math.min(count, limit - consumed + 1)); check((int) Math.min(Integer.MAX_VALUE, result)); return result; }
    }
}
