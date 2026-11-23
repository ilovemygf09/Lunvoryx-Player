package tv.lunvoryx.player.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.EpisodeItem;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.data.SeriesItem;
import tv.lunvoryx.player.tmdb.TmdbClient;
import static tv.lunvoryx.player.ui.TvStyle.dp;

/**
 * Detailed movie and series view matching media_1795348517276_71f2fbc4.jpg & media_1795348607177_7f328d5a.jpg.
 * Includes cinematic backdrop, synopsis, Play button, YouTube trailer launcher, cast, ratings, and seasons/episodes.
 */
public final class MediaDetailView extends FrameLayout {
    public interface Listener {
        void onPlay(Channel channel);
        void onToggleFavorite(Channel channel);
        void onBack();
    }

    private final Context context;
    private final Channel channel;
    private final SeriesItem seriesItem;
    private final boolean isMovie;
    private final PlaylistRepository repository;
    private final Listener listener;
    private final PreviewMetadata metadata = new PreviewMetadata();
    private TextView castText, ratingText;

    private ImageView backdropImg, trailerThumb;
    private LinearLayout portraits, detailRows;
    private TextView titleView;
    private TextView metaView;
    private TextView synopsisView;
    private TextView btnPlay;
    private TextView btnTrailer;
    private TextView btnFav;

    // Bottom cards
    private FrameLayout trailerCard;
    private FrameLayout middleCard;
    private FrameLayout detailsCard;

    // Episodes Card UI elements
    private LinearLayout seasonsPillsRow;
    private TextView episodesCountView;
    private LinearLayout epListContainer;
    private int activeSeason = 1;

    private TmdbClient.Details details;
    private String cleanTitle;
    private String cleanYear;
    private boolean isFavorite;

    public MediaDetailView(Context context, Channel channel, boolean isMovie, PlaylistRepository repository, Listener listener) {
        this(context, channel, null, isMovie, repository, listener);
    }

    public MediaDetailView(Context context, SeriesItem seriesItem, PlaylistRepository repository, Listener listener) {
        this(context, seriesItem != null ? seriesItem.representativeChannel : null, seriesItem, false, repository, listener);
    }

    public MediaDetailView(Context context, Channel channel, SeriesItem seriesItem, boolean isMovie, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.channel = channel;
        this.seriesItem = seriesItem;
        this.isMovie = isMovie;
        this.repository = repository;
        this.listener = listener;
        this.isFavorite = seriesItem != null ? seriesItem.isFavorite(repository) : (channel != null && repository.isFavorite(channel.id));
        extractBasicMetadata();
        init();
    }

    private void extractBasicMetadata() {
        if (seriesItem != null) {
            cleanTitle = seriesItem.seriesTitle;
            cleanYear = seriesItem.year;
            return;
        }
        cleanTitle = channel != null ? channel.name : "Título";
        cleanYear = "";
        if (channel != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19\\d\\d|20\\d\\d)").matcher(channel.name);
            if (m.find()) {
                cleanYear = m.group(1);
            }
        }
        cleanTitle = cleanTitle.replaceAll("(?i)\\[.*?\\]", " ");
        cleanTitle = cleanTitle.replaceAll("(?i)\\b(1080p|720p|4k|uhd|fhd|hevc|x264|x265|latino|castellano|sub|dual)\\b", " ");
        cleanTitle = cleanTitle.replaceAll("\\(\\d{4}\\)", " ");
        cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim();
        if (cleanTitle.isEmpty() && channel != null) cleanTitle = channel.name;
    }

    private void init() {
        setBackgroundColor(0xFF060A14);

        // 1. High-res Backdrop
        backdropImg = new ImageView(context);
        backdropImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropImg.setImageBitmap(Artwork.get(context, isMovie ? R.drawable.intu_movie_scene : R.drawable.intu_series_scene, 1280));
        addView(backdropImg, new LayoutParams(-1, dp(context, 380)));

        // Vignette Gradients: Darken from bottom and left for text legibility
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00060A14, 0x33060A14, 0xFF060A14}
        ));
        addView(shade, new LayoutParams(-1, dp(context, 385)));

        View leftShade = new View(context);
        leftShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE0060A14, 0x88060A14, 0x00060A14}
        ));
        addView(leftShade, new LayoutParams(dp(context, 750), dp(context, 385)));

        // 2. Main Scrollable Container
        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        addView(scroller, new LayoutParams(-1, -1));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context, 32), dp(context, 20), dp(context, 32), dp(context, 32));
        scroller.addView(body, new LayoutParams(-1, -2));

        // Top Back Row
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout backBtn = new FrameLayout(context);
        backBtn.setFocusable(true);
        backBtn.setClickable(true);
        backBtn.setBackground(TvStyle.squircleCard(context, 18));
        LineIcon backIcon = new LineIcon(context, "back");
        backIcon.setColor(0xFFF0F0F5);
        backBtn.addView(backIcon, new FrameLayout.LayoutParams(dp(context, 18), dp(context, 18), Gravity.CENTER));
        backBtn.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
        backBtn.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.08f : 1.0f).scaleY(f ? 1.08f : 1.0f).setDuration(120).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
        });
        topRow.addView(backBtn, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
        body.addView(topRow);

        // Hero Info: Title
        titleView = new TextView(context);
        titleView.setText(cleanTitle);
        titleView.setTextSize(40);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(context, 12);
        body.addView(titleView, tp);

        // Metadata row (Year, duration/seasons, genre, badges)
        metaView = new TextView(context);
        updateMetaLine();
        metaView.setTextSize(13.5f);
        metaView.setTextColor(0xFF9AACBE);
        metaView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.addView(metaView);



        LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(-1, -2);
        mrp.topMargin = dp(context, 6);
        body.addView(metaRow, mrp);

        // Synopsis
        synopsisView = new TextView(context);
        synopsisView.setText("Sinopsis no disponible");
        synopsisView.setTextSize(13.5f);
        synopsisView.setTextColor(0xFFBAC7D5);
        synopsisView.setLineSpacing(dp(context, 4), 1.0f);
        synopsisView.setMaxLines(3);
        synopsisView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(context, 440), -2);
        sp.topMargin = dp(context, 12);
        body.addView(synopsisView, sp);

        // Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        btnPlay = buildActionButton("Reproducir", "play", true);
        btnPlay.setOnClickListener(v -> {
            if (listener != null) {
                if (seriesItem != null) {
                    listener.onPlay(seriesItem.getFirstEpisode());
                } else if (channel != null) {
                    listener.onPlay(channel);
                }
            }
        });
        actionsRow.addView(btnPlay);

        btnTrailer = buildActionButton("Ver tráiler", "youtube", false);
        btnTrailer.setOnClickListener(v -> launchTrailer());
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(-2, dp(context, 42));
        btp.leftMargin = dp(context, 12);
        actionsRow.addView(btnTrailer, btp);

        btnFav = buildActionButton(isFavorite ? "En mi lista" : "+ Mi lista", isFavorite ? "star" : "plus", false);
        btnFav.setOnClickListener(v -> {
            if (listener != null) {
                if (seriesItem != null && seriesItem.representativeChannel != null) {
                    listener.onToggleFavorite(seriesItem.representativeChannel);
                    isFavorite = seriesItem.isFavorite(repository);
                } else if (channel != null) {
                    listener.onToggleFavorite(channel);
                    isFavorite = repository.isFavorite(channel.id);
                }
                btnFav.setText(isFavorite ? "En mi lista" : "+ Mi lista");
            }
        });
        LinearLayout.LayoutParams bfp = new LinearLayout.LayoutParams(-2, dp(context, 42));
        bfp.leftMargin = dp(context, 12);
        actionsRow.addView(btnFav, bfp);

        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(-1, -2);
        arp.topMargin = dp(context, 18);
        body.addView(actionsRow, arp);

        // Bottom 3 Cards Container
        LinearLayout bottomCards = new LinearLayout(context);
        bottomCards.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bcp = new LinearLayout.LayoutParams(-1, dp(context, 225));
        bcp.topMargin = dp(context, 28);
        body.addView(bottomCards, bcp);

        // Card 1: Trailer card (28% width)
        trailerCard = buildTrailerCard();
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, -1, 0.28f);
        tcp.rightMargin = dp(context, 16);
        bottomCards.addView(trailerCard, tcp);

        // Card 2: Reparto / Temporadas (42% width)
        middleCard = isMovie ? buildCastCard() : buildEpisodesCard();
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(0, -1, 0.42f);
        mcp.rightMargin = dp(context, 16);
        bottomCards.addView(middleCard, mcp);

        // Card 3: Detalles / Reparto y valoración (30% width)
        detailsCard = isMovie ? buildDetailsCard() : buildCastCard();
        LinearLayout.LayoutParams dcp = new LinearLayout.LayoutParams(0, -1, 0.30f);
        bottomCards.addView(detailsCard, dcp);

        btnPlay.requestFocus();
    }

    private void updateMetaLine() {
        List<String> values = new java.util.ArrayList<>();
        if (!cleanYear.isEmpty()) values.add(cleanYear);
        if (seriesItem != null) {
            int seasons = seriesItem.getSeasonCount();
            values.add(seasons + (seasons == 1 ? " temporada" : " temporadas"));
        }
        if (channel != null && !channel.group.isEmpty()) values.add(channel.group);
        metaView.setText(TextUtils.join(" · ", values));
    }

    private TextView buildActionButton(String label, String iconKind, boolean isPrimary) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setTextSize(14);
        b.setFocusable(true);
        b.setClickable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(context, 22), 0, dp(context, 22), 0);
        b.setHeight(dp(context, 42));

        if (isPrimary) {
            b.setTextColor(0xFFFFFFFF);
            b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            b.setBackground(TvStyle.squircleSolid(context, 21, 0xFF007AFF));
        } else {
            b.setTextColor(0xFFD2DEEB);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setBackground(TvStyle.squircleCard(context, 21));
        }

        b.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.05f : 1.0f).scaleY(f ? 1.05f : 1.0f).setDuration(120).start();
            if (isPrimary) {
                b.setBackground(TvStyle.squircleSolid(context, 21, f ? 0xFF0A84FF : 0xFF007AFF));
            } else {
                ((SquircleGlassDrawable) b.getBackground()).setFocused(f);
            }
        });

        return b;
    }

    private FrameLayout buildTrailerCard() {
        FrameLayout card = new FrameLayout(context);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(TvStyle.squircleCard(context, 16));
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Tráiler en YouTube");
        title.setTextSize(16);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(TvStyle.squircleCard(context, 12));
        preview.setClipToOutline(true);

        ImageView thumb = trailerThumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setImageBitmap(Artwork.get(context, isMovie ? R.drawable.intu_movie_scene : R.drawable.intu_series_scene, 500));
        preview.addView(thumb, new FrameLayout.LayoutParams(-1, -1));

        // Dark gradient
        View overlay = new View(context);
        overlay.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x22000000, 0x88000000}
        ));
        preview.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // YouTube badge
        LinearLayout ytBadge = new LinearLayout(context);
        ytBadge.setOrientation(LinearLayout.HORIZONTAL);
        ytBadge.setGravity(Gravity.CENTER_VERTICAL);
        LineIcon ytIcon = new LineIcon(context, "youtube");
        ytIcon.setColor(0xFFE50914);
        ytBadge.addView(ytIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
        TextView ytText = new TextView(context);
        ytText.setText("YouTube");
        ytText.setTextColor(0xFFFFFFFF);
        ytText.setTextSize(10.5f);
        ytText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        ytText.setPadding(dp(context, 4), 0, 0, 0);
        ytBadge.addView(ytText);
        FrameLayout.LayoutParams ytp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        ytp.topMargin = dp(context, 8);
        ytp.rightMargin = dp(context, 8);
        preview.addView(ytBadge, ytp);

        // Center Play Circle
        FrameLayout playCircle = new FrameLayout(context);
        playCircle.setBackground(TvStyle.squircleSolid(context, 20, 0xCC111827));
        LineIcon playIcon = new LineIcon(context, "play");
        playIcon.setColor(0xFFFFFFFF);
        playCircle.addView(playIcon, new FrameLayout.LayoutParams(dp(context, 16), dp(context, 16), Gravity.CENTER));
        preview.addView(playCircle, new FrameLayout.LayoutParams(dp(context, 40), dp(context, 40), Gravity.CENTER));

        // Title at bottom
        TextView bTitle = new TextView(context);
        bTitle.setText(cleanTitle.toUpperCase(Locale.ROOT));
        bTitle.setTextSize(11);
        bTitle.setTextColor(0xFFFFFFFF);
        bTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        bTitle.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 6));
        preview.addView(bTitle, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        pp.topMargin = dp(context, 8);
        content.addView(preview, pp);

        card.setOnClickListener(v -> launchTrailer());
        card.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.03f : 1.0f).scaleY(f ? 1.03f : 1.0f).setDuration(120).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
        });

        return card;
    }

    private FrameLayout buildCastCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(IntuitivePanel.create(context));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Reparto");
        title.setTextSize(16);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        portraits = new LinearLayout(context);
        content.addView(portraits);
        castText = new TextView(context);
        castText.setTextColor(0xFFBAC7D5);
        castText.setText("Reparto no disponible");
        content.addView(castText);
        ratingText = new TextView(context);
        ratingText.setTextColor(0xFFFFFFFF);
        content.addView(ratingText);

        return card;
    }

    private FrameLayout buildEpisodesCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(IntuitivePanel.create(context));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // Header + Episodes count
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Temporadas y episodios");
        title.setTextSize(16);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title);

        View hSpacer = new View(context);
        header.addView(hSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        episodesCountView = new TextView(context);
        episodesCountView.setTextSize(11);
        episodesCountView.setTextColor(0xFF8FA5C2);
        header.addView(episodesCountView);

        content.addView(header);

        // Season Pills: T1, T2, T3...
        seasonsPillsRow = new LinearLayout(context);
        seasonsPillsRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonsPillsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(context, 8);
        android.widget.HorizontalScrollView seasonsScroll = new android.widget.HorizontalScrollView(context);
        seasonsScroll.setHorizontalScrollBarEnabled(false);
        seasonsScroll.addView(seasonsPillsRow);
        content.addView(seasonsScroll, plp);

        // Episodes Scroll List
        ScrollView epScroller = new ScrollView(context);
        epScroller.setVerticalScrollBarEnabled(false);
        epListContainer = new LinearLayout(context);
        epListContainer.setOrientation(LinearLayout.VERTICAL);
        epScroller.addView(epListContainer, new LayoutParams(-1, -2));

        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        elp.topMargin = dp(context, 6);
        content.addView(epScroller, elp);

        List<Integer> seasons = seriesItem == null ? Collections.emptyList() : seriesItem.getSeasons();
        if (!seasons.isEmpty()) activeSeason = seasons.get(0);

        renderSeasonPills(seasons);
        renderEpisodesForSeason(activeSeason);

        return card;
    }

    private void renderSeasonPills(List<Integer> seasons) {
        seasonsPillsRow.removeAllViews();
        for (int i = 0; i < seasons.size(); i++) {
            final int sNum = seasons.get(i);
            TextView sp = new TextView(context);
            sp.setText("T" + sNum);
            sp.setTextSize(11.5f);
            sp.setFocusable(true);
            sp.setClickable(true);
            sp.setPadding(dp(context, 12), dp(context, 3), dp(context, 12), dp(context, 3));

            boolean isActive = (sNum == activeSeason);
            applySeasonPillStyle(sp, isActive, false);

            sp.setOnClickListener(v -> {
                activeSeason = sNum;
                updateSeasonPillStyles(seasons);
                renderEpisodesForSeason(activeSeason);
            });

            sp.setOnFocusChangeListener((v, f) -> {
                if (f) {
                    activeSeason = sNum;
                    renderEpisodesForSeason(activeSeason);
                }
                updateSeasonPillStyles(seasons);
            });

            LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) spp.leftMargin = dp(context, 8);
            seasonsPillsRow.addView(sp, spp);
        }
    }

    private void updateSeasonPillStyles(List<Integer> seasons) {
        for (int i = 0; i < seasonsPillsRow.getChildCount(); i++) {
            TextView pill = (TextView) seasonsPillsRow.getChildAt(i);
            applySeasonPillStyle(pill, seasons.get(i) == activeSeason, pill.hasFocus());
        }
    }

    private void applySeasonPillStyle(TextView sp, boolean isActive, boolean isFocused) {
        if (isActive || isFocused) {
            sp.setTextColor(0xFFFFFFFF);
            sp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            sp.setBackground(TvStyle.squircleSolid(context, 10, isActive ? 0xFF007AFF : 0xFF1E2D4A));
        } else {
            sp.setTextColor(0xFFB0C2D6);
            sp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            sp.setBackground(TvStyle.squircleCard(context, 10));
        }
    }

    private void renderEpisodesForSeason(int seasonNum) {
        epListContainer.removeAllViews();
        List<EpisodeItem> list = seriesItem != null ? seriesItem.getEpisodesForSeason(seasonNum) : null;

        if (list == null || list.isEmpty()) {
            episodesCountView.setText("Sin episodios en la lista");
            return;
        }

        episodesCountView.setText(list.size() + (list.size() == 1 ? " episodio" : " episodios"));
        for (EpisodeItem ep : list) {
            View row = buildEpisodeRow(ep.displayTitle, ep.subtitle, ep.duration, ep.channel.logoUrl, ep.channel);
            epListContainer.addView(row);
        }
    }

    private View buildEpisodeRow(String title, String subtitle, String duration, String logoUrl, Channel targetChannel) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(TvStyle.squircleCard(context, 10));
        row.setPadding(dp(context, 8), dp(context, 6), dp(context, 10), dp(context, 6));

        // Thumbnail
        FrameLayout epThumb = new FrameLayout(context);
        epThumb.setBackground(TvStyle.squircleSolid(context, 8, 0xFF1B2640));
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (logoUrl != null && !logoUrl.isEmpty()) {
            LogoLoader.load(logoUrl, iv);
        } else if (seriesItem != null && !seriesItem.logoUrl.isEmpty()) {
            LogoLoader.load(seriesItem.logoUrl, iv);
        } else {
            iv.setImageBitmap(Artwork.get(context, R.drawable.intu_series_scene, 320));
        }
        epThumb.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        row.addView(epThumb, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 36)));

        // Text Info
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(context, 10), 0, dp(context, 8), 0);

        TextView epTitle = new TextView(context);
        epTitle.setText(title);
        epTitle.setTextSize(11.5f);
        epTitle.setTextColor(0xFFFFFFFF);
        epTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        epTitle.setSingleLine(true);
        epTitle.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(epTitle);

        TextView epSub = new TextView(context);
        epSub.setText(subtitle);
        epSub.setTextSize(10);
        epSub.setTextColor(0xFF8FA5C2);
        epSub.setSingleLine(true);
        epSub.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(epSub);

        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

        TextView dur = new TextView(context);
        dur.setText(duration);
        dur.setTextSize(10.5f);
        dur.setTextColor(0xFF8FA5C2);
        row.addView(dur);

        // Play Icon
        LineIcon pIcon = new LineIcon(context, "play");
        pIcon.setColor(0xFF007AFF);
        LinearLayout.LayoutParams pip = new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16));
        pip.leftMargin = dp(context, 8);
        row.addView(pIcon, pip);

        row.setOnClickListener(v -> {
            if (listener != null) {
                if (targetChannel != null) {
                    listener.onPlay(targetChannel);
                } else if (channel != null) {
                    listener.onPlay(channel);
                }
            }
        });

        row.setOnFocusChangeListener((v, f) -> {
            ((SquircleGlassDrawable) row.getBackground()).setFocused(f);
            row.animate().scaleX(f ? 1.02f : 1.0f).scaleY(f ? 1.02f : 1.0f).setDuration(100).start();
        });

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(context, 6);
        row.setLayoutParams(rlp);
        return row;
    }

    private FrameLayout buildDetailsCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(IntuitivePanel.create(context));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Detalles");
        title.setTextSize(16);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        LinearLayout list = detailRows = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(context, 8);
        content.addView(list, lp);

        fillDetailRows();

        return card;
    }

    private void fillDetailRows() {
        if (detailRows == null) return;
        detailRows.removeAllViews();
        if (details != null && !TextUtils.isEmpty(details.director))
            addDetailItem(detailRows, "movie", "Dirección:", details.director);
        if (!cleanYear.isEmpty()) addDetailItem(detailRows, "calendar", "Año:", cleanYear);
        if (details != null && details.runtime > 0)
            addDetailItem(detailRows, "clock", "Duración:", details.runtime + " min");
        if (details != null && !TextUtils.isEmpty(details.language))
            addDetailItem(detailRows, "audio", "Idioma original:", new Locale(details.language).getDisplayLanguage());
        if (channel != null) addDetailItem(detailRows, "folder", "Grupo:", channel.group);
    }

    private void addDetailItem(LinearLayout parent, String iconKind, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 2), 0, dp(context, 2));

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFF7B8FA6);
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView l = new TextView(context);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(0xFF8B9EB3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(context, 8);
        row.addView(l, lp);

        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(11);
        v.setTextColor(0xFFFFFFFF);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.leftMargin = dp(context, 6);
        row.addView(v, vp);

        parent.addView(row);
    }

    private void launchTrailer() {
        try {
            String trailerUrl = null;
            if (details != null && details.trailerUrl != null && !details.trailerUrl.isEmpty()) {
                trailerUrl = details.trailerUrl;
            } else {
                boolean isEn = AppStrings.isEnglish(context);
                String q = cleanTitle + (isEn ? " official trailer" : " trailer oficial espanol");
                trailerUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8");
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "No se pudo abrir el tráiler.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        String logo = seriesItem != null ? seriesItem.logoUrl : channel != null ? channel.logoUrl : "";
        if (!logo.isEmpty()) LogoLoader.load(logo, backdropImg);
        metadata.load(context, cleanTitle, isMovie, d -> {
            details = d;
            if (!TextUtils.isEmpty(d.title)) titleView.setText(d.title);
            if (!TextUtils.isEmpty(d.overview)) synopsisView.setText(d.overview);
            if (!d.cast.isEmpty()) {
                castText.setVisibility(GONE);
                portraits.removeAllViews();
                int count = Math.min(isMovie ? 5 : 4, d.cast.size());
                for (int i = 0; i < count; i++) {
                    LinearLayout person = new LinearLayout(context);
                    person.setOrientation(LinearLayout.VERTICAL);
                    person.setGravity(Gravity.CENTER_HORIZONTAL);
                    portraits.addView(person, new LinearLayout.LayoutParams(0, -2, 1));
                    ImageView photo = new ImageView(context);
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(0xFF172C46);
                    photo.setBackground(circle);
                    photo.setClipToOutline(true);
                    photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    int size = dp(context, isMovie ? 44 : 36);
                    if (i < d.castPhotos.size() && !d.castPhotos.get(i).isEmpty()) {
                        person.addView(photo, new LinearLayout.LayoutParams(size, size));
                        LogoLoader.load(d.castPhotos.get(i), photo);
                    }
                    TextView name = new TextView(context);
                    name.setText(d.cast.get(i).split(" \\(", 2)[0]);
                    name.setTextSize(11);
                    name.setGravity(Gravity.CENTER);
                    name.setTextColor(0xFFF2F6FF);
                    person.addView(name);
                }
            }
            if (d.rating > 0) ratingText.setText(String.format(java.util.Locale.getDefault(), "★  %.1f / 10", d.rating));
            if (d.releaseDate != null && d.releaseDate.length() >= 4) {
                cleanYear = d.releaseDate.substring(0, 4);
                updateMetaLine();
            }
            fillDetailRows();
            if (!TextUtils.isEmpty(d.backdropUrl)) {
                LogoLoader.load(d.backdropUrl, backdropImg, 1280);
                LogoLoader.load(d.backdropUrl, trailerThumb, 512);
            }
        });
    }

    @Override protected void onDetachedFromWindow() {
        metadata.cancel();
        LogoLoader.clear(backdropImg);
        LogoLoader.clear(trailerThumb);
        super.onDetachedFromWindow();
    }
}
