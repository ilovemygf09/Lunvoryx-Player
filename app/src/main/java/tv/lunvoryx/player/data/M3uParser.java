package tv.lunvoryx.player.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Streaming line parser for extended M3U. Android-independent so edge cases can be tested on the JVM. */
public final class M3uParser {
    public static final int MAX_CHANNELS = 150_000;
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
    private M3uParser() { }

    public static List<Channel> parse(String text, String sourceUrl) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("La lista está vacía.");
        String clean = text.startsWith("\uFEFF") ? text.substring(1) : text;
        if (isHls(clean)) {
            String source = resolve(sourceUrl, null);
            if (source == null) throw new IllegalArgumentException("Una emisión HLS debe importarse mediante su URL.");
            List<Channel> single = new ArrayList<>();
            single.add(Channel.create(defaultName(source), source, "Emisiones", "", "", "", ""));
            return single;
        }
        Map<String, Channel> channels = new LinkedHashMap<>();
        String name = "", group = "", logo = "", tvgId = "", userAgent = "", referer = "";
        String extGroup = "";
        try (BufferedReader reader = new BufferedReader(new StringReader(clean))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("#EXTINF:")) {
                    int comma = outsideQuoteComma(line);
                    String metadata = comma < 0 ? line : line.substring(0, comma);
                    Map<String, String> attributes = attributes(metadata);
                    name = comma < 0 ? "" : line.substring(comma + 1).trim();
                    if (name.isEmpty()) name = get(attributes, "tvg-name", "");
                    group = get(attributes, "group-title", "");
                    logo = get(attributes, "tvg-logo", "");
                    tvgId = get(attributes, "tvg-id", "");
                    userAgent = "";
                    referer = "";
                } else if (upper.startsWith("#EXTGRP:")) {
                    extGroup = line.substring(8).trim();
                } else if (upper.startsWith("#EXTVLCOPT:HTTP-USER-AGENT=")) {
                    userAgent = line.substring(line.indexOf('=') + 1).trim();
                } else if (upper.startsWith("#EXTVLCOPT:HTTP-REFERRER=") || upper.startsWith("#EXTVLCOPT:HTTP-REFERER=")) {
                    referer = line.substring(line.indexOf('=') + 1).trim();
                } else if (upper.startsWith("#KODIPROP:INPUTSTREAM.ADAPTIVE.STREAM_HEADERS=") ||
                        upper.startsWith("#KODIPROP:INPUTSTREAM.ADAPTIVE.MANIFEST_HEADERS=")) {
                    Map<String, String> headers = headers(line.substring(line.indexOf('=') + 1));
                    userAgent = get(headers, "user-agent", userAgent);
                    referer = get(headers, "referer", get(headers, "referrer", referer));
                } else if (!line.startsWith("#")) {
                    int pipe = line.indexOf('|');
                    String url = resolve(pipe < 0 ? line : line.substring(0, pipe), sourceUrl);
                    if (pipe >= 0) {
                        Map<String, String> headers = headers(line.substring(pipe + 1));
                        userAgent = get(headers, "user-agent", userAgent);
                        referer = get(headers, "referer", get(headers, "referrer", referer));
                    }
                    if (url != null) {
                        String resolvedLogo = resolve(logo, sourceUrl);
                        Channel channel = Channel.create(name.isEmpty() ? defaultName(url) : name, url,
                                group.isEmpty() ? extGroup : group, resolvedLogo == null ? "" : resolvedLogo,
                                tvgId, userAgent, referer);
                        if (!channels.containsKey(channel.id)) channels.put(channel.id, channel);
                        if (channels.size() > MAX_CHANNELS) throw new IllegalArgumentException("La lista supera el límite de 150.000 canales.");
                    }
                    name = ""; group = ""; logo = ""; tvgId = ""; userAgent = ""; referer = ""; extGroup = "";
                }
            }
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        if (channels.isEmpty()) throw new IllegalArgumentException("No se han encontrado canales compatibles en esta lista M3U.");
        return new ArrayList<>(channels.values());
    }

    private static boolean isHls(String text) {
        return Pattern.compile("(?im)^\\s*#EXT-X-(?:TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE|PLAYLIST-TYPE|I-FRAME-STREAM-INF):").matcher(text).find();
    }

    private static int outsideQuoteComma(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '\'' || c == '"') && (quote == 0 || quote == c)) quote = quote == 0 ? c : 0;
            else if (c == ',' && quote == 0) return i;
        }
        return -1;
    }

    private static Map<String, String> attributes(String metadata) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(metadata);
        while (matcher.find()) result.put(matcher.group(1).toLowerCase(Locale.ROOT),
                matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
        return result;
    }

    private static String get(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    private static Map<String, String> headers(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            if (equals < 1) continue;
            try {
                result.put(URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8.name()).toLowerCase(Locale.ROOT),
                        URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8.name()));
            } catch (Exception ignored) { /* Malformed optional headers do not discard a valid stream. */ }
        }
        return result;
    }

    private static String resolve(String candidate, String base) {
        if (candidate == null || candidate.trim().isEmpty()) return null;
        try {
            URI uri = new URI(candidate.trim().replace(" ", "%20"));
            if (!uri.isAbsolute()) {
                if (base == null || base.isEmpty()) return null;
                uri = new URI(base).resolve(uri);
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            // Arbitrary local paths and transports without a bundled playback module are never imported.
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
            return uri.normalize().toString();
        } catch (Exception invalid) { return null; }
    }

    private static String defaultName(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isEmpty() && !path.endsWith("/")) {
                String file = path.substring(path.lastIndexOf('/') + 1);
                if (!file.equalsIgnoreCase("index.m3u8") && !file.equalsIgnoreCase("master.m3u8")) return file;
            }
            return URI.create(url).getHost();
        } catch (Exception ignored) { return "Emisión"; }
    }
}
