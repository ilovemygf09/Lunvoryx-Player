package tv.lunvoryx.player.data;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class M3uParserTest {
    @Test public void parsesBomQuotedCommasAndGroups() {
        List<Channel> channels = M3uParser.parse("\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"news\" tvg-name=\"Fallback\" group-title=\"Noticias, España\" tvg-logo=\"/logos/a.png\",Noticias, 24 horas\r\nhttps://tv.example/live.m3u8\r\n", "https://tv.example/list/index.m3u");
        assertEquals(1, channels.size());
        Channel c = channels.get(0);
        assertEquals("Noticias, 24 horas", c.name);
        assertEquals("Noticias, España", c.group);
        assertEquals("news", c.tvgId);
        assertEquals("https://tv.example/logos/a.png", c.logoUrl);
    }

    @Test public void resolvesRelativeUrlsAndKeepsCredentials() {
        Channel c = M3uParser.parse("#EXTM3U\n#EXTINF:-1,Canal\n../live/a.m3u8?token=abc%2B123\n", "https://tv.example/lists/main.m3u").get(0);
        assertEquals("https://tv.example/live/a.m3u8?token=abc%2B123", c.url);
    }

    @Test public void parsesVlcKodiAndPipeHeadersWithRightPrecedence() {
        Channel c = M3uParser.parse("#EXTM3U\n#EXTINF:-1,Canal\n#EXTGRP:Deportes\n#EXTVLCOPT:http-user-agent=VLC\n#EXTVLCOPT:http-referrer=https://player.example/\n#KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Kodi%2F1\nhttps://tv.example/a|User-Agent=Lunvoryx%2F1&Referer=https%3A%2F%2Fportal.example%2F\n", "").get(0);
        assertEquals("Deportes", c.group);
        assertEquals("Lunvoryx/1", c.userAgent);
        assertEquals("https://portal.example/", c.referer);
        assertEquals("https://tv.example/a", c.url);
    }

    @Test public void headersAndGroupsDoNotLeakIntoNextChannel() {
        List<Channel> list = M3uParser.parse("#EXTINF:-1,Uno\n#EXTGRP:Uno\n#EXTVLCOPT:http-user-agent=Custom\nhttps://tv.example/a\n#EXTINF:-1,Dos\nhttps://tv.example/b", "");
        assertEquals("", list.get(1).userAgent);
        assertEquals("Sin grupo", list.get(1).group);
    }

    @Test public void deduplicatesStableStreamIdsAcrossMetadataChanges() {
        List<Channel> list = M3uParser.parse("#EXTINF:-1,Uno\nhttps://tv.example/a\n#EXTINF:-1,Duplicado\nhttps://tv.example/a", "");
        assertEquals(1, list.size());
        assertEquals("Uno", list.get(0).name);
        assertEquals(list.get(0).id, Channel.create("Otro", "https://tv.example/a", "", "", "", "", "").id);
    }

    @Test public void mediaHlsIsOneChannelNotIndividualSegments() {
        List<Channel> list = M3uParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg1.ts\n#EXTINF:10,\nseg2.ts\n#EXT-X-ENDLIST", "https://tv.example/live.m3u8");
        assertEquals(1, list.size());
        assertEquals("https://tv.example/live.m3u8", list.get(0).url);
    }

    @Test public void masterHlsIsOneChannelNotIndividualVariants() {
        List<Channel> list = M3uParser.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2560000\nhigh.m3u8", "https://tv.example/master.m3u8");
        assertEquals(1, list.size());
    }

    @Test public void skipsUnsupportedSchemesAndMalformedUrls() {
        List<Channel> list = M3uParser.parse("#EXTM3U\n#EXTINF:-1,Unsafe\nfile:///data/private\n#EXTINF:-1,Valid\nhttps://tv.example/a\nnot a url", "");
        assertEquals(1, list.size());
        assertEquals("Valid", list.get(0).name);
    }

    @Test public void supportsSingleQuotedAndUnquotedAttributes() {
        Channel c = M3uParser.parse("#EXTINF:-1 tvg-id=news tvg-name='La señal' group-title='Noticias',\nhttps://tv.example/a", "").get(0);
        assertEquals("La señal", c.name);
        assertEquals("Noticias", c.group);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsHtmlErrorPages() {
        M3uParser.parse("<!doctype html><html><body>Unavailable</body></html>", "https://tv.example/list");
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsEmptyLists() { M3uParser.parse("#EXTM3U\n", ""); }

    @Test(expected = IllegalArgumentException.class) public void rejectsHlsFilesWithoutRemoteBase() {
        M3uParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\nsegment.ts", "");
    }

    @Test public void supportsIpv6AndProtocolRelativeChannels() {
        List<Channel> list = M3uParser.parse("#EXTINF:-1,Local\nhttp://[2001:db8::1]:8080/a.ts\n#EXTINF:-1,CDN\n//cdn.example/b.m3u8", "https://tv.example/list.m3u");
        assertEquals("http://[2001:db8::1]:8080/a.ts", list.get(0).url);
        assertEquals("https://cdn.example/b.m3u8", list.get(1).url);
    }

    @Test public void rejectsUnbundledTransportsAndStripsHeaderNewlines() {
        List<Channel> list = M3uParser.parse("rtsp://tv.example/a\nudp://tv.example/b\nhttps://tv.example/c|User-Agent=Hello%0D%0AInjected", "");
        assertEquals(1, list.size());
        assertEquals("HelloInjected", list.get(0).userAgent);
    }

    @Test(timeout = 15_000) public void parsesTenThousandChannelsWithoutQuadraticDeduplication() {
        StringBuilder input = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 10_000; i++) input.append("#EXTINF:-1 group-title=\"Grupo ").append(i % 20)
                .append("\",Canal ").append(i).append("\nhttps://tv.example/").append(i).append(".m3u8\n");
        List<Channel> channels = M3uParser.parse(input.toString(), "");
        assertEquals(10_000, channels.size());
        assertEquals("Canal 9999", channels.get(9999).name);
    }
}
