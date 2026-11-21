package tv.lunvoryx.player.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable channel metadata; stream credentials must never be written to logs. */
public final class Channel {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    public final String id;
    public final String name;
    public final String url;
    public final String group;
    public final String logoUrl;
    public final String tvgId;
    public final String userAgent;
    public final String referer;

    public Channel(String id, String name, String url, String group, String logoUrl,
                   String tvgId, String userAgent, String referer) {
        this.id = empty(id) ? stableId(url) : id;
        this.name = empty(name) ? "Canal sin nombre" : name.trim();
        this.url = Objects.requireNonNull(url, "url");
        this.group = empty(group) ? "Sin grupo" : group.trim();
        this.logoUrl = value(logoUrl);
        this.tvgId = value(tvgId);
        this.userAgent = safeHeader(userAgent);
        this.referer = safeHeader(referer);
    }

    public static Channel create(String name, String url, String group, String logoUrl,
                                 String tvgId, String userAgent, String referer) {
        return new Channel(stableId(url), name, url, group, logoUrl, tvgId, userAgent, referer);
    }

    public static String stableId(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            char[] result = new char[32];
            for (int i = 0; i < 16; i++) {
                result[i * 2] = HEX[(bytes[i] & 0xff) >>> 4];
                result[i * 2 + 1] = HEX[bytes[i] & 15];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static boolean empty(String text) { return text == null || text.trim().isEmpty(); }
    private static String safeHeader(String text) { return value(text).replace("\r", "").replace("\n", ""); }

    @Override public boolean equals(Object other) { return other instanceof Channel && id.equals(((Channel) other).id); }
    @Override public int hashCode() { return id.hashCode(); }
}
