package tv.lunvoryx.player.data;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Best-effort classification of M3U metadata; the playlist remains the source of truth. */
public enum MediaKind {
    LIVE, MOVIE, SERIES;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SERIES_GROUP = Pattern.compile("(?:^|[^a-z0-9])(?:series?|seriales|temporadas?|episodios?|tv[ -]?shows?)(?:$|[^a-z0-9])");
    // "cine" and "cinema" are intentionally excluded: in European/Spanish IPTV, "ES | Cine", "M+ Cine", "EU- ES M CINE" are live TV channels.
    private static final Pattern MOVIE_GROUP = Pattern.compile("(?:^|[^a-z0-9])(?:peliculas?|pelis|movies?|films?|vod|estrenos?)(?:$|[^a-z0-9])");
    private static final Pattern LIVE_HINTS = Pattern.compile("(?:^|[^a-z0-9])(?:live|en vivo|en directo|directo|24/7|tdt|dazn|deportes?|sports?|noticias|generalistas?|autonomicas?|canales?|radio|laliga|movistar\\+|m\\+)(?:$|[^a-z0-9])");
    private static final Pattern EPISODE = Pattern.compile("(?:^|[^a-z0-9])(?:s\\d{1,3}[ ._-]*(?:e|ep)?\\d{1,3}|\\d{1,2}x\\d{2,3}|temporada\\s*\\d+|episodio\\s*\\d+|cap(?:itulo|\\.)\\s*\\d+)(?:$|[^a-z0-9])");
    private static final Pattern VIDEO_FILE = Pattern.compile(".*\\.(?:mp4|mkv|avi|mov|webm|m4v)$");

    public static MediaKind classify(Channel channel) {
        if (channel == null) return LIVE;
        String path = "";
        try {
            String rawPath = URI.create(channel.url).getPath();
            if (rawPath != null) path = rawPath.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) { }

        // 1. Explicit provider routes are stronger evidence than broad editorial groups
        if (path.contains("/series/")) return SERIES;
        if (path.contains("/movie/") || path.contains("/movies/") || path.contains("/vod/")) return MOVIE;
        if (path.contains("/live/")) return LIVE;

        String name = normalize(channel.name);
        String group = normalize(channel.group);

        // 2. Clear live indicators in group or channel name (e.g. 24/7, TDT, DAZN, Deportes...)
        if (LIVE_HINTS.matcher(group).find() || LIVE_HINTS.matcher(name).find()) {
            if (!EPISODE.matcher(name).find() && !VIDEO_FILE.matcher(path).matches()) {
                return LIVE;
            }
        }

        // 3. Episode marker in name denotes a series episode
        if (EPISODE.matcher(name).find()) return SERIES;

        // 4. Video file extension: .mp4, .mkv, .avi...
        if (VIDEO_FILE.matcher(path).matches()) {
            if (SERIES_GROUP.matcher(group).find()) return SERIES;
            return MOVIE;
        }

        // 5. Group classifications
        if (MOVIE_GROUP.matcher(group).find()) return MOVIE;

        if (SERIES_GROUP.matcher(group).find()) {
            // Linear TV series channels (e.g. "M+ Series", "TNT") streamed via HLS/TS without episode markers
            if (path.endsWith(".m3u8") || path.endsWith(".ts")) {
                return LIVE;
            }
            return SERIES;
        }

        return LIVE;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
