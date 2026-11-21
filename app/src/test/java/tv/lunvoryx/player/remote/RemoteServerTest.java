package tv.lunvoryx.player.remote;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteServerTest {

    @Test
    public void testTokenGenerationAndUrl() {
        RemoteServer server = new RemoteServer((name, url, content) -> {});
        String token = server.getSessionToken();

        assertNotNull(token);
        assertEquals(32, token.length()); // 16 bytes in hex = 32 characters (128 bits entropy)

        String url = server.getUrl();
        assertTrue(url.contains("token=" + token));

        // Test uniqueness between instances
        RemoteServer server2 = new RemoteServer((n, u, c) -> {});
        assertNotEquals(server.getSessionToken(), server2.getSessionToken());
    }

    @Test
    public void testValidHttpUrlValidation() {
        assertTrue(RemoteServer.isValidHttpUrl("http://example.com/playlist.m3u"));
        assertTrue(RemoteServer.isValidHttpUrl("https://stream.server.io:8080/live.m3u8"));

        assertFalse(RemoteServer.isValidHttpUrl("file:///sdcard/malicious.apk"));
        assertFalse(RemoteServer.isValidHttpUrl("content://media/external/file"));
        assertFalse(RemoteServer.isValidHttpUrl("javascript:evil()"));
        assertFalse(RemoteServer.isValidHttpUrl("ftp://test.org"));
        assertFalse(RemoteServer.isValidHttpUrl(""));
        assertFalse(RemoteServer.isValidHttpUrl(null));
        assertFalse(RemoteServer.isValidHttpUrl("http://"));
    }
}
