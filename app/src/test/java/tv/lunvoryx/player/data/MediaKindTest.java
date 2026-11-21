package tv.lunvoryx.player.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaKindTest {
    private Channel channel(String name, String url, String group) {
        return Channel.create(name, url, group, "", "", "", "");
    }

    @Test public void recognisesProviderRoutesBeforeGroups() {
        assertEquals(MediaKind.MOVIE, MediaKind.classify(channel("Film", "https://tv.example/movie/user/pass/11", "Otros")));
        assertEquals(MediaKind.SERIES, MediaKind.classify(channel("Show", "https://tv.example/series/user/pass/12.mp4", "Películas")));
        assertEquals(MediaKind.LIVE, MediaKind.classify(channel("Canal cine", "https://tv.example/live/user/pass/13.m3u8", "Cine")));
    }

    @Test public void classifiesAccentsAndWholeGroupWords() {
        assertEquals(MediaKind.MOVIE, MediaKind.classify(channel("Film", "https://tv.example/1.m3u8", "Películas de prueba")));
        assertEquals(MediaKind.MOVIE, MediaKind.classify(channel("Film", "https://tv.example/1.m3u8", "ES | Cine")));
        assertEquals(MediaKind.SERIES, MediaKind.classify(channel("Show", "https://tv.example/1.m3u8", "Series españolas")));
        assertEquals(MediaKind.LIVE, MediaKind.classify(channel("Channel", "https://tv.example/1.m3u8", "Cinemática")));
    }

    @Test public void checksExtensionWithoutQueryString() {
        assertEquals(MediaKind.MOVIE, MediaKind.classify(channel("Film", "https://tv.example/file.MKV?token=xyz", "Otros")));
        assertEquals(MediaKind.LIVE, MediaKind.classify(channel("Channel", "https://tv.example/list.m3u8?redirect=/movie/1.mp4", "Otros")));
    }

    @Test public void detectsEpisodesBeforeGenericVideoFiles() {
        assertEquals(MediaKind.SERIES, MediaKind.classify(channel("Mi serie S02E03", "https://tv.example/file.mp4", "Otros")));
        assertEquals(MediaKind.SERIES, MediaKind.classify(channel("Mi serie 2x03", "https://tv.example/file.m3u8", "Otros")));
        assertEquals(MediaKind.SERIES, MediaKind.classify(channel("Episodio", "https://tv.example/file.mov", "Series")));
    }

    @Test public void defaultsToLiveWhenMetadataDoesNotSay() {
        assertEquals(MediaKind.LIVE, MediaKind.classify(channel("Noticias", "https://tv.example/channel", "Información")));
        assertEquals(MediaKind.LIVE, MediaKind.classify(null));
    }
}
