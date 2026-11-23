package tv.lunvoryx.player.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.data.SeriesItem;

/** Computes category counts and the visible list on a worker. */
abstract class BrowseFilter<T> {
    static final BrowseFilter<Channel> CHANNELS = new BrowseFilter<Channel>() {
        String name(Channel c) { return c.name; }
        String group(Channel c) { return c.group; }
        boolean contains(Channel c, Set<String> ids) { return ids.contains(c.id); }
    };
    static final BrowseFilter<SeriesItem> SERIES = new BrowseFilter<SeriesItem>() {
        String name(SeriesItem s) { return s.seriesTitle; }
        String group(SeriesItem s) { return s.group; }
        boolean contains(SeriesItem s, Set<String> ids) { return s.isFavorite(ids); }
    };

    abstract String name(T item);
    abstract String group(T item);
    abstract boolean contains(T item, Set<String> ids);

    static final class Counts {
        final Map<String, Integer> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int total, favorites, recent;
    }

    static final class Result<T> {
        final Counts counts;
        final List<T> items;

        Result(Counts counts, List<T> items) {
            this.counts = counts;
            this.items = items;
        }
    }

    Result<T> run(List<T> source, PlaylistRepository repo, String category, String query,
                  int sort, boolean countCategories) {
        Counts counts = countCategories ? new Counts() : null;
        boolean favoritesOnly = "favorites".equals(category);
        boolean recentOnly = "resume".equals(category);
        String selectedGroup = category.startsWith("group:") ? category.substring(6) : null;
        String needle = query.toLowerCase(Locale.ROOT);
        boolean useSource = "all".equals(category) && needle.isEmpty() && sort == 0;
        if (useSource && counts == null) return new Result<>(null, source);
        Set<String> favorites = countCategories || favoritesOnly ? repo.getFavoriteIds() : Collections.emptySet();
        Set<String> recent = countCategories || recentOnly ? new HashSet<>(repo.getRecentIds()) : Collections.emptySet();
        List<T> items = useSource ? source : new ArrayList<>();
        for (T item : source) {
            if (Thread.currentThread().isInterrupted()) break;
            String group = group(item);
            if (group.isEmpty()) group = "General";
            boolean favorite = !favorites.isEmpty() && contains(item, favorites);
            boolean played = !recent.isEmpty() && contains(item, recent);
            if (counts != null) {
                counts.total++;
                if (favorite) counts.favorites++;
                if (played) counts.recent++;
                Integer count = counts.groups.get(group);
                counts.groups.put(group, count == null ? 1 : count + 1);
            }
            if (useSource || favoritesOnly && !favorite || recentOnly && !played) continue;
            if (selectedGroup != null && !group.equalsIgnoreCase(selectedGroup)) continue;
            if (!needle.isEmpty() && !name(item).toLowerCase(Locale.ROOT).contains(needle)) continue;
            items.add(item);
        }
        if (sort != 0 && !Thread.currentThread().isInterrupted()) {
            Comparator<T> byName = (a, b) -> name(a).compareToIgnoreCase(name(b));
            Collections.sort(items, sort == 1 ? byName : Collections.reverseOrder(byName));
        }
        return new Result<>(counts, items);
    }
}
