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
    private static final Pattern MOVIE_GROUP = Pattern.compile("(?:^|[^a-z0-9])(?:peliculas?|cine|cinema|movies?|films?|vod)(?:$|[^a-z0-9])");
    private static final Pattern EPISODE = Pattern.compile("(?:^|[^a-z0-9])(?:s\\d{1,3}[ ._-]*e\\d{1,3}|\\d{1,2}x\\d{2,3})(?:$|[^a-z0-9])");
    private static final Pattern VIDEO_FILE = Pattern.compile(".*\\.(?:mp4|mkv|avi|mov|webm|m4v)$");

    public static MediaKind classify(Channel channel) {
        if (channel == null) return LIVE;
        String path = "";
        try {
            String rawPath = URI.create(channel.url).getPath();
            if (rawPath != null) path = rawPath.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) { }
        // Explicit provider routes are stronger evidence than broad editorial groups such as “Cine”.
        if (path.contains("/series/")) return SERIES;
        if (path.contains("/movie/") || path.contains("/movies/") || path.contains("/vod/")) return MOVIE;
        if (path.contains("/live/")) return LIVE;
        String group = normalize(channel.group);
        if (SERIES_GROUP.matcher(group).find() || EPISODE.matcher(normalize(channel.name)).find()) return SERIES;
        if (MOVIE_GROUP.matcher(group).find() || VIDEO_FILE.matcher(path).matches()) return MOVIE;
        return LIVE;
    }

    private static String normalize(String value) {
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
