package tv.lunvoryx.player.epg;

/** An XMLTV broadcast. All times are absolute UTC milliseconds. */
public final class Program {
    public final String channelId;
    public final String title;
    public final String description;
    public final long startMillis;
    public final long endMillis;

    public Program(String channelId, String title, String description, long startMillis, long endMillis) {
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public boolean isOnAir(long nowMillis) {
        return startMillis <= nowMillis && endMillis > nowMillis;
    }
}
