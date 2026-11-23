package tv.lunvoryx.player.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SeriesParser {

    private static final Pattern S_E_PATTERN = Pattern.compile("(?i)(?:s|t|temporada\\s*)(\\d{1,3})[ ._-]*(?:e|ep|episodio\\s*|cap[íi]tulo\\s*|cap\\.?\\s*)(\\d{1,3})");
    private static final Pattern X_PATTERN = Pattern.compile("(?i)(?:^|\\D)(\\d{1,2})x(\\d{1,3})(?:\\D|$)");
    private static final Pattern S_ONLY_PATTERN = Pattern.compile("(?i)(?:s|t|temporada\\s*)(\\d{1,3})(?:\\D|$)");
    private static final Pattern E_ONLY_PATTERN = Pattern.compile("(?i)(?:e|ep|episodio\\s*|cap[íi]tulo\\s*|cap\\.?\\s*)(\\d{1,3})(?:\\D|$)");

    private static final Pattern STRIP_EPISODE_MARKERS = Pattern.compile("(?i)[ ._-]*(?:s\\d{1,3}[ ._-]*(?:e|ep)?\\d{1,3}|\\d{1,2}x\\d{1,3}|t\\d{1,3}[ ._-]*(?:e|ep)?\\d{1,3}|temporada\\s*\\d+.*|episodio\\s*\\d+.*|cap[íi]tulo\\s*\\d+.*).*$");
    private static final Pattern QUALITY_TAGS = Pattern.compile("(?i)\\b(1080p|720p|4k|uhd|fhd|hevc|h264|h265|x264|x265|latino|castellano|sub|subtitulado|dual|multi|esp|en|es)\\b");
    private static final Pattern BRACKETS = Pattern.compile("\\[.*?\\]|\\{.*?\\}");
    private static final Pattern PREFIX_COUNTRY = Pattern.compile("^(?:[A-Z]{2,3}\\s*[:|\\-]\\s*)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19\\d\\d|20\\d\\d)");
    private static final Pattern MULTI_SPACES = Pattern.compile("\\s+");
    private static final Pattern PAREN_YEAR = Pattern.compile("\\(\\s*(?:19|20)\\d{2}\\s*\\)");
    private static final Pattern STANDALONE_YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern TRIM_PUNCT = Pattern.compile("^[ ._\\-:]+|[ ._\\-:]+$");
    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[ ._\\-:]+");
    private static final Pattern PARENS = Pattern.compile("\\(.*?\\)");

    private static final Map<String, String> TITLE_CACHE = new ConcurrentHashMap<>(4096);

    private SeriesParser() {}

    public static ParsedEpisode parseEpisode(Channel channel) {
        String name = channel.name != null ? channel.name : "";
        int season = 1;
        int episode = 1;
        int markerStart = -1;
        int markerEnd = -1;

        Matcher seMatcher = S_E_PATTERN.matcher(name);
        if (seMatcher.find()) {
            try {
                season = Integer.parseInt(seMatcher.group(1));
                episode = Integer.parseInt(seMatcher.group(2));
                markerStart = seMatcher.start();
                markerEnd = seMatcher.end();
            } catch (NumberFormatException ignored) {}
        } else {
            Matcher xMatcher = X_PATTERN.matcher(name);
            if (xMatcher.find()) {
                try {
                    season = Integer.parseInt(xMatcher.group(1));
                    episode = Integer.parseInt(xMatcher.group(2));
                    markerStart = xMatcher.start();
                    markerEnd = xMatcher.end();
                } catch (NumberFormatException ignored) {}
            } else {
                Matcher eMatcher = E_ONLY_PATTERN.matcher(name);
                if (eMatcher.find()) {
                    try {
                        episode = Integer.parseInt(eMatcher.group(1));
                        markerStart = eMatcher.start();
                        markerEnd = eMatcher.end();
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Clean series title (text before marker or stripped)
        String rawSeries;
        if (markerStart > 0) {
            rawSeries = name.substring(0, markerStart);
        } else {
            rawSeries = STRIP_EPISODE_MARKERS.matcher(name).replaceAll("");
        }

        String cleanSeries = cleanSeriesTitle(rawSeries);
        if (cleanSeries.isEmpty()) {
            cleanSeries = cleanSeriesTitle(name);
        }
        if (cleanSeries.isEmpty()) {
            cleanSeries = name.trim();
        }

        // Year extraction
        String year = "";
        Matcher ym = YEAR_PATTERN.matcher(name);
        if (ym.find()) {
            year = ym.group(1);
        }

        // Clean episode subtitle (text after marker)
        String sub = "";
        if (markerEnd > 0 && markerEnd < name.length()) {
            String tail = name.substring(markerEnd).trim();
            tail = LEADING_SEPARATORS.matcher(tail).replaceAll("");
            tail = QUALITY_TAGS.matcher(tail).replaceAll(" ");
            tail = BRACKETS.matcher(tail).replaceAll(" ");
            tail = MULTI_SPACES.matcher(tail).replaceAll(" ").trim();
            // remove trailing parenthesized items
            tail = PARENS.matcher(tail).replaceAll("").trim();
            if (tail.length() > 1) {
                sub = tail;
            }
        }

        String displayTitle;
        if (!sub.isEmpty()) {
            displayTitle = "Episodio " + episode + " · " + sub;
        } else {
            displayTitle = "Episodio " + episode;
            sub = "Episodio " + episode;
        }

        return new ParsedEpisode(cleanSeries, season, episode, displayTitle, sub, "", year);
    }

    public static String cleanSeriesTitle(String text) {
        if (text == null || text.isEmpty()) return "";
        String cached = TITLE_CACHE.get(text);
        if (cached != null) return cached;

        String s = PREFIX_COUNTRY.matcher(text).replaceFirst("");
        s = BRACKETS.matcher(s).replaceAll(" ");
        s = QUALITY_TAGS.matcher(s).replaceAll(" ");
        s = PAREN_YEAR.matcher(s).replaceAll(" ");
        s = STANDALONE_YEAR.matcher(s).replaceAll(" ");
        s = TRIM_PUNCT.matcher(s).replaceAll(" ");
        s = MULTI_SPACES.matcher(s).replaceAll(" ").trim();

        if (TITLE_CACHE.size() < 10000) {
            TITLE_CACHE.put(text, s);
        }
        return s;
    }

    public static List<SeriesItem> groupSeries(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) return Collections.emptyList();
        Map<String, SeriesBuilder> map = new LinkedHashMap<>(Math.min(channels.size(), 4096));

        for (Channel c : channels) {
            ParsedEpisode pe = parseEpisode(c);
            String groupName = c.group != null ? c.group.trim() : "";
            String key = pe.seriesTitle.toLowerCase(Locale.ROOT) + "|||" + groupName.toLowerCase(Locale.ROOT);

            SeriesBuilder b = map.get(key);
            if (b == null) {
                b = new SeriesBuilder(pe.seriesTitle, groupName);
                map.put(key, b);
            }
            b.addEpisode(c, pe);
        }

        List<SeriesItem> result = new ArrayList<>(map.size());
        for (SeriesBuilder b : map.values()) {
            result.add(b.build());
        }

        Collections.sort(result, (a, b) -> a.seriesTitle.compareToIgnoreCase(b.seriesTitle));
        return result;
    }

    public static final class ParsedEpisode {
        public final String seriesTitle;
        public final int season;
        public final int episode;
        public final String displayTitle;
        public final String subtitle;
        public final String duration;
        public final String year;

        public ParsedEpisode(String seriesTitle, int season, int episode, String displayTitle, String subtitle, String duration, String year) {
            this.seriesTitle = seriesTitle;
            this.season = season > 0 ? season : 1;
            this.episode = episode > 0 ? episode : 1;
            this.displayTitle = displayTitle;
            this.subtitle = subtitle;
            this.duration = duration;
            this.year = year;
        }
    }

    private static final class SeriesBuilder {
        final String seriesTitle;
        final String group;
        String logoUrl = "";
        String year = "";
        Channel firstChannel = null;
        final List<Channel> allChannels = new ArrayList<>();
        final Map<Integer, List<EpisodeItem>> seasonsMap = new TreeMap<>();

        SeriesBuilder(String seriesTitle, String group) {
            this.seriesTitle = seriesTitle;
            this.group = group;
        }

        void addEpisode(Channel c, ParsedEpisode pe) {
            if (firstChannel == null) firstChannel = c;
            allChannels.add(c);

            if (logoUrl.isEmpty() && c.logoUrl != null && !c.logoUrl.isEmpty()) {
                logoUrl = c.logoUrl;
            }
            if (year.isEmpty() && pe.year != null && !pe.year.isEmpty()) {
                year = pe.year;
            }

            EpisodeItem epItem = new EpisodeItem(c, pe.season, pe.episode, pe.displayTitle, pe.subtitle, pe.duration);
            List<EpisodeItem> list = seasonsMap.get(pe.season);
            if (list == null) {
                list = new ArrayList<>();
                seasonsMap.put(pe.season, list);
            }
            list.add(epItem);
        }

        SeriesItem build() {
            // Sort episodes in each season by episode number ascending
            for (List<EpisodeItem> epList : seasonsMap.values()) {
                Collections.sort(epList, (a, b) -> Integer.compare(a.episode, b.episode));
            }
            return new SeriesItem(seriesTitle, group, logoUrl, year, firstChannel, allChannels, seasonsMap);
        }
    }
}
