package tv.lunvoryx.player.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SeriesItem {
    public final String seriesTitle;
    public final String group;
    public final String logoUrl;
    public final String year;
    public final Channel representativeChannel;
    public final List<Channel> allEpisodeChannels;
    public final Map<Integer, List<EpisodeItem>> seasonsMap;

    public SeriesItem(String seriesTitle, String group, String logoUrl, String year,
                      Channel representativeChannel, List<Channel> allEpisodeChannels,
                      Map<Integer, List<EpisodeItem>> seasonsMap) {
        this.seriesTitle = seriesTitle;
        this.group = group != null ? group : "";
        this.logoUrl = logoUrl != null ? logoUrl : "";
        this.year = year != null && !year.isEmpty() ? year : "2026";
        this.representativeChannel = representativeChannel;
        this.allEpisodeChannels = allEpisodeChannels;
        this.seasonsMap = seasonsMap;
    }

    public int getTotalEpisodes() {
        return allEpisodeChannels.size();
    }

    public int getSeasonCount() {
        return Math.max(1, seasonsMap.size());
    }

    public List<Integer> getSeasons() {
        return new ArrayList<>(seasonsMap.keySet());
    }

    public List<EpisodeItem> getEpisodesForSeason(int season) {
        List<EpisodeItem> list = seasonsMap.get(season);
        return list != null ? list : Collections.emptyList();
    }

    public Channel getFirstEpisode() {
        if (!seasonsMap.isEmpty()) {
            for (List<EpisodeItem> list : seasonsMap.values()) {
                if (!list.isEmpty()) return list.get(0).channel;
            }
        }
        return representativeChannel;
    }

    public boolean isFavorite(PlaylistRepository repository) {
        if (representativeChannel != null && repository.isFavorite(representativeChannel.id)) return true;
        for (Channel c : allEpisodeChannels) {
            if (repository.isFavorite(c.id)) return true;
        }
        return false;
    }

    public boolean hasRecent(PlaylistRepository repository) {
        List<String> recents = repository.getRecentIds();
        if (representativeChannel != null && recents.contains(representativeChannel.id)) return true;
        for (Channel c : allEpisodeChannels) {
            if (recents.contains(c.id)) return true;
        }
        return false;
    }
}
