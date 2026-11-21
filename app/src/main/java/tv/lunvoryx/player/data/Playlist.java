package tv.lunvoryx.player.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Playlist {
    public final String id;
    public final String name;
    /** Original URL or persisted content URI, or demo: for the built-in test list. */
    public final String source;
    public final List<Channel> channels;
    public final long updatedAt;

    public Playlist(String id, String name, String source, List<Channel> channels, long updatedAt) {
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "Mi lista" : name.trim();
        this.source = source == null ? "" : source;
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.updatedAt = updatedAt;
    }
}
