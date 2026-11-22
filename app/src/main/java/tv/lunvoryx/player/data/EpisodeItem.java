package tv.lunvoryx.player.data;

public final class EpisodeItem {
    public final Channel channel;
    public final int season;
    public final int episode;
    public final String displayTitle; // e.g. "Episodio 1 · La señal" or "Episodio 1"
    public final String subtitle;     // e.g. "La señal" or "Episodio 1"
    public final String duration;     // e.g. "48 min"

    public EpisodeItem(Channel channel, int season, int episode, String displayTitle, String subtitle, String duration) {
        this.channel = channel;
        this.season = season;
        this.episode = episode;
        this.displayTitle = displayTitle;
        this.subtitle = subtitle;
        this.duration = duration;
    }
}
