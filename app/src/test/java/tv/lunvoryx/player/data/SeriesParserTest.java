package tv.lunvoryx.player.data;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SeriesParserTest {
    private Channel channel(String name, String url, String group, String logo) {
        return Channel.create(name, url, group, logo, "", "", "");
    }

    @Test
    public void parsesEpisodeFormatsCorrectly() {
        SeriesParser.ParsedEpisode pe1 = SeriesParser.parseEpisode(channel("Dark S01 E02 - Mentiras", "http://tv/1.mp4", "Series", "http://logo/dark.png"));
        assertEquals("Dark", pe1.seriesTitle);
        assertEquals(1, pe1.season);
        assertEquals(2, pe1.episode);
        assertEquals("Episodio 2 · Mentiras", pe1.displayTitle);

        SeriesParser.ParsedEpisode pe2 = SeriesParser.parseEpisode(channel("ES | Stranger Things 2x04", "http://tv/2.mp4", "Series", ""));
        assertEquals("Stranger Things", pe2.seriesTitle);
        assertEquals(2, pe2.season);
        assertEquals(4, pe2.episode);

        SeriesParser.ParsedEpisode pe3 = SeriesParser.parseEpisode(channel("The Crown (2016) S03E01", "http://tv/3.mp4", "Series", ""));
        assertEquals("The Crown", pe3.seriesTitle);
        assertEquals(3, pe3.season);
        assertEquals(1, pe3.episode);
    }

    @Test
    public void groupsEpisodesIntoSingleSeries() {
        List<Channel> channels = new ArrayList<>();
        channels.add(channel("Dark S01 E01", "http://tv/1.mp4", "ES | SERIES NETFLIX", "http://logo/dark.png"));
        channels.add(channel("Dark S01 E02", "http://tv/2.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(channel("Dark S02 E01", "http://tv/3.mp4", "ES | SERIES NETFLIX", ""));
        channels.add(channel("Dark S03 E08", "http://tv/4.mp4", "ES | SERIES NETFLIX", ""));

        // Add another series
        channels.add(channel("Stranger Things S01 E01", "http://tv/5.mp4", "ES | SERIES NETFLIX", "http://logo/st.png"));

        List<SeriesItem> series = SeriesParser.groupSeries(channels);

        assertEquals(2, series.size());

        SeriesItem dark = series.get(0).seriesTitle.equals("Dark") ? series.get(0) : series.get(1);
        assertEquals("Dark", dark.seriesTitle);
        assertEquals("ES | SERIES NETFLIX", dark.group);
        assertEquals("http://logo/dark.png", dark.logoUrl);
        assertEquals(4, dark.getTotalEpisodes());
        assertEquals(3, dark.getSeasonCount());

        List<EpisodeItem> s1Eps = dark.getEpisodesForSeason(1);
        assertEquals(2, s1Eps.size());
        assertEquals(1, s1Eps.get(0).episode);
        assertEquals(2, s1Eps.get(1).episode);

        List<EpisodeItem> s2Eps = dark.getEpisodesForSeason(2);
        assertEquals(1, s2Eps.size());
        assertEquals(1, s2Eps.get(0).episode);

        List<EpisodeItem> s3Eps = dark.getEpisodesForSeason(3);
        assertEquals(1, s3Eps.size());
        assertEquals(8, s3Eps.get(0).episode);
    }
}
