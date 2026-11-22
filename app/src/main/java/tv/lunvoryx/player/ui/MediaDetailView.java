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
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.tmdb.TmdbClient;
import static tv.lunvoryx.player.ui.TvStyle.dp;

/**
 * Detailed movie and series view matching media_1795348517276_71f2fbc4.jpg & media_1795348607177_7f328d5a.jpg.
 * Includes cinematic backdrop, synopsis, Play button, YouTube trailer launcher, cast, ratings, and episodes.
 */
public final class MediaDetailView extends FrameLayout {
    public interface Listener {
        void onPlay(Channel channel);
        void onToggleFavorite(Channel channel);
        void onBack();
    }

    private final Context context;
    private final Channel channel;
    private final boolean isMovie;
    private final PlaylistRepository repository;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageView backdropImg;
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

    private TmdbClient.Details details;
    private String cleanTitle;
    private String cleanYear;
    private boolean isFavorite;

    public MediaDetailView(Context context, Channel channel, boolean isMovie, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.channel = channel;
        this.isMovie = isMovie;
        this.repository = repository;
        this.listener = listener;
        this.isFavorite = repository.isFavorite(channel.id);
        extractBasicMetadata();
        init();
        loadMetadata();
    }

    private void extractBasicMetadata() {
        cleanTitle = channel.name;
        cleanYear = "2026";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19\\d\\d|20\\d\\d)").matcher(channel.name);
        if (m.find()) {
            cleanYear = m.group(1);
        }
        // Clean title
        cleanTitle = cleanTitle.replaceAll("(?i)\\[.*?\\]", " ");
        cleanTitle = cleanTitle.replaceAll("(?i)\\b(1080p|720p|4k|uhd|fhd|hevc|x264|x265|latino|castellano|sub|dual)\\b", " ");
        cleanTitle = cleanTitle.replaceAll("\\(\\d{4}\\)", " ");
        cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim();
        if (cleanTitle.isEmpty()) cleanTitle = channel.name;
    }

    private void init() {
        setBackgroundColor(0xFF060A14);

        // 1. High-res Backdrop
        backdropImg = new ImageView(context);
        backdropImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropImg.setImageBitmap(Artwork.get(context, isMovie ? R.drawable.category_movies : R.drawable.category_series, 1280));
        addView(backdropImg, new LayoutParams(-1, dp(context, 380)));

        // Vignette Gradients: Darken from bottom and left for text legibility
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33060A14, 0xAA060A14, 0xFF060A14}
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
        titleView.setTextSize(36);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(context, 12);
        body.addView(titleView, tp);

        // Metadata row (Year, duration/seasons, genre, badges)
        metaView = new TextView(context);
        String metaText = isMovie ? (cleanYear + " · 2 h 08 min · " + (channel.group.isEmpty() ? "Película" : channel.group))
                : (cleanYear + " · 3 temporadas · " + (channel.group.isEmpty() ? "Serie dramática" : channel.group));
        metaView.setText(metaText);
        metaView.setTextSize(13.5f);
        metaView.setTextColor(0xFF9AACBE);
        metaView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.addView(metaView);

        // Badges: 4K, 16+
        TextView b4k = buildBadge("4K");
        LinearLayout.LayoutParams b4kp = new LinearLayout.LayoutParams(-2, -2);
        b4kp.leftMargin = dp(context, 12);
        metaRow.addView(b4k, b4kp);

        TextView bAge = buildBadge("16+");
        LinearLayout.LayoutParams bAp = new LinearLayout.LayoutParams(-2, -2);
        bAp.leftMargin = dp(context, 6);
        metaRow.addView(bAge, bAp);

        LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(-1, -2);
        mrp.topMargin = dp(context, 6);
        body.addView(metaRow, mrp);

        // Synopsis
        synopsisView = new TextView(context);
        synopsisView.setText(isMovie ?
                "Una emocionante producción cinematográfica con una historia que desafía la realidad y sumerge al espectador en una experiencia única llena de tensión y aventuras visualmente asombrosas."
                : "Una cautivadora serie que explora secretos ocultos, giros inesperados y la intriga constante de sus personajes mientras sus destinos se entrelazan capítulo a capítulo.");
        synopsisView.setTextSize(13.5f);
        synopsisView.setTextColor(0xFFBAC7D5);
        synopsisView.setLineSpacing(dp(context, 4), 1.0f);
        synopsisView.setMaxLines(3);
        synopsisView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(context, 680), -2);
        sp.topMargin = dp(context, 12);
        body.addView(synopsisView, sp);

        // Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        btnPlay = buildActionButton("Reproducir", "play", true);
        btnPlay.setOnClickListener(v -> { if (listener != null) listener.onPlay(channel); });
        actionsRow.addView(btnPlay);

        btnTrailer = buildActionButton("Ver tráiler", "youtube", false);
        btnTrailer.setOnClickListener(v -> launchTrailer());
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(-2, dp(context, 42));
        btp.leftMargin = dp(context, 12);
        actionsRow.addView(btnTrailer, btp);

        btnFav = buildActionButton(isFavorite ? "En mi lista" : "+ Mi lista", isFavorite ? "star" : "plus", false);
        btnFav.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggleFavorite(channel);
                isFavorite = repository.isFavorite(channel.id);
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
        detailsCard = isMovie ? buildDetailsCard() : buildSeriesDetailsCard();
        LinearLayout.LayoutParams dcp = new LinearLayout.LayoutParams(0, -1, 0.30f);
        bottomCards.addView(detailsCard, dcp);

        btnPlay.requestFocus();
    }

    private TextView buildBadge(String label) {
        TextView b = new TextView(context);
        b.setText(label);
        b.setTextSize(10.5f);
        b.setTextColor(0xFFD4E0EE);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setBackground(TvStyle.squircleCard(context, 6));
        b.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2));
        return b;
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
        title.setText("Tráiler oficial · Español");
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(TvStyle.squircleCard(context, 12));
        preview.setClipToOutline(true);

        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setImageBitmap(Artwork.get(context, isMovie ? R.drawable.category_movies : R.drawable.category_series, 500));
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
        card.setBackground(TvStyle.squircleCard(context, 16));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Reparto");
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        // Horizontal Row of Actors
        LinearLayout castRow = new LinearLayout(context);
        castRow.setOrientation(LinearLayout.HORIZONTAL);
        castRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        crp.topMargin = dp(context, 8);
        content.addView(castRow, crp);

        String[] sampleNames = {"Adrián Velasco", "Elena Morales", "Mateo Rivas", "Laura Serrano", "Álex Duarte"};
        for (int i = 0; i < sampleNames.length; i++) {
            LinearLayout actorCol = new LinearLayout(context);
            actorCol.setOrientation(LinearLayout.VERTICAL);
            actorCol.setGravity(Gravity.CENTER_HORIZONTAL);

            // Circle Avatar
            FrameLayout avatar = new FrameLayout(context);
            avatar.setBackground(TvStyle.squircleSolid(context, 24, 0xFF1B2640));
            LineIcon person = new LineIcon(context, "user");
            person.setColor(0xFF8FA5C2);
            avatar.addView(person, new FrameLayout.LayoutParams(dp(context, 24), dp(context, 24), Gravity.CENTER));
            actorCol.addView(avatar, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

            TextView name = new TextView(context);
            name.setText(sampleNames[i]);
            name.setTextSize(10.5f);
            name.setTextColor(0xFFCBD6E2);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setMaxLines(2);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(context, 68), -2);
            np.topMargin = dp(context, 4);
            actorCol.addView(name, np);

            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1.0f);
            castRow.addView(actorCol, ap);
        }

        // Bottom Rating Row
        LinearLayout ratingRow = new LinearLayout(context);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(-1, -2);
        rrp.topMargin = dp(context, 8);
        content.addView(ratingRow, rrp);

        TextView valLabel = new TextView(context);
        valLabel.setText("Valoración");
        valLabel.setTextSize(12.5f);
        valLabel.setTextColor(0xFFFFFFFF);
        valLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        ratingRow.addView(valLabel);

        TextView stars = new TextView(context);
        stars.setText("  ★★★★☆");
        stars.setTextColor(0xFFFFB800);
        stars.setTextSize(14);
        ratingRow.addView(stars);

        View rSpacer = new View(context);
        ratingRow.addView(rSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView scoreBadge = new TextView(context);
        scoreBadge.setText("8,4 / 10");
        scoreBadge.setTextSize(12);
        scoreBadge.setTextColor(0xFFFFFFFF);
        scoreBadge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        scoreBadge.setBackground(TvStyle.squircleCard(context, 8));
        scoreBadge.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        ratingRow.addView(scoreBadge);

        return card;
    }

    private FrameLayout buildEpisodesCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(TvStyle.squircleCard(context, 16));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // Header + Season Pills
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Temporadas y episodios");
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title);

        View hSpacer = new View(context);
        header.addView(hSpacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView count = new TextView(context);
        count.setText("8 episodios");
        count.setTextSize(11);
        count.setTextColor(0xFF8FA5C2);
        header.addView(count);

        content.addView(header);

        // Season Pills: T1, T2, T3
        LinearLayout pills = new LinearLayout(context);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(context, 8);
        content.addView(pills, plp);

        String[] seasons = {"T1", "T2", "T3"};
        for (int i = 0; i < seasons.length; i++) {
            TextView sp = new TextView(context);
            sp.setText(seasons[i]);
            sp.setTextSize(11.5f);
            sp.setPadding(dp(context, 12), dp(context, 3), dp(context, 12), dp(context, 3));
            if (i == 1) { // T2 active
                sp.setTextColor(0xFFFFFFFF);
                sp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                sp.setBackground(TvStyle.squircleSolid(context, 10, 0xFF007AFF));
            } else {
                sp.setTextColor(0xFFB0C2D6);
                sp.setBackground(TvStyle.squircleCard(context, 10));
            }
            LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) spp.leftMargin = dp(context, 8);
            pills.addView(sp, spp);
        }

        // Episodes Scroll List
        ScrollView epScroller = new ScrollView(context);
        epScroller.setVerticalScrollBarEnabled(false);
        LinearLayout epList = new LinearLayout(context);
        epList.setOrientation(LinearLayout.VERTICAL);
        epScroller.addView(epList);

        String[][] eps = {
                {"Episodio 1 · La señal", "Una pista inesperada reabre el caso.", "48 min"},
                {"Episodio 2 · Bajo la niebla", "El pasado del pueblo empieza a salir a la luz.", "51 min"},
                {"Episodio 3 · Lo que escondemos", "Un hallazgo lo cambia todo.", "49 min"}
        };

        for (String[] ep : eps) {
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
            iv.setImageBitmap(Artwork.get(context, R.drawable.category_series, 200));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            epThumb.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            row.addView(epThumb, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 36)));

            // Text
            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(context, 10), 0, dp(context, 8), 0);

            TextView epTitle = new TextView(context);
            epTitle.setText(ep[0]);
            epTitle.setTextSize(11.5f);
            epTitle.setTextColor(0xFFFFFFFF);
            epTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            textCol.addView(epTitle);

            TextView epSub = new TextView(context);
            epSub.setText(ep[1]);
            epSub.setTextSize(10);
            epSub.setTextColor(0xFF8FA5C2);
            textCol.addView(epSub);

            row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView dur = new TextView(context);
            dur.setText(ep[2]);
            dur.setTextSize(10.5f);
            dur.setTextColor(0xFF8FA5C2);
            row.addView(dur);

            // Play Icon
            LineIcon pIcon = new LineIcon(context, "play");
            pIcon.setColor(0xFF007AFF);
            LinearLayout.LayoutParams pip = new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16));
            pip.leftMargin = dp(context, 8);
            row.addView(pIcon, pip);

            row.setOnClickListener(v -> { if (listener != null) listener.onPlay(channel); });
            row.setOnFocusChangeListener((v, f) -> {
                ((SquircleGlassDrawable) row.getBackground()).setFocused(f);
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dp(context, 6);
            epList.addView(row, rlp);
        }

        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        elp.topMargin = dp(context, 6);
        content.addView(epScroller, elp);

        return card;
    }

    private FrameLayout buildDetailsCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(TvStyle.squircleCard(context, 16));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Detalles");
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(context, 8);
        content.addView(list, lp);

        addDetailItem(list, "director", "Director:", "Diego Navarro");
        addDetailItem(list, "calendar", "Año:", cleanYear);
        addDetailItem(list, "history", "Duración:", "2 h 08 min");
        addDetailItem(list, "globe", "Idioma:", "Español");
        addDetailItem(list, "subtitles", "Subtítulos:", "Español, Inglés");
        addDetailItem(list, "player", "Calidad:", "4K UHD · HDR");

        return card;
    }

    private FrameLayout buildSeriesDetailsCard() {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(TvStyle.squircleCard(context, 16));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(context);
        title.setText("Reparto y valoración");
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(title);

        // Actors row
        LinearLayout castRow = new LinearLayout(context);
        castRow.setOrientation(LinearLayout.HORIZONTAL);
        castRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(-1, -2);
        crp.topMargin = dp(context, 8);
        content.addView(castRow, crp);

        String[] actors = {"Lucía Vergara", "Diego Salvatierra", "Marina Rojas", "Héctor Navarro"};
        for (String act : actors) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout av = new FrameLayout(context);
            av.setBackground(TvStyle.squircleSolid(context, 20, 0xFF1B2640));
            LineIcon u = new LineIcon(context, "user");
            u.setColor(0xFF8FA5C2);
            av.addView(u, new FrameLayout.LayoutParams(dp(context, 20), dp(context, 20), Gravity.CENTER));
            col.addView(av, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));

            TextView tn = new TextView(context);
            tn.setText(act);
            tn.setTextSize(9.5f);
            tn.setTextColor(0xFFCBD6E2);
            tn.setMaxLines(2);
            tn.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams tnp = new LinearLayout.LayoutParams(dp(context, 58), -2);
            tnp.topMargin = dp(context, 3);
            col.addView(tn, tnp);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            castRow.addView(col, cp);
        }

        // Rating
        LinearLayout ratingRow = new LinearLayout(context);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(-1, -2);
        rrp.topMargin = dp(context, 8);
        content.addView(ratingRow, rrp);

        TextView val = new TextView(context);
        val.setText("Valoración  ★★★★★");
        val.setTextSize(11.5f);
        val.setTextColor(0xFFFFB800);
        ratingRow.addView(val);

        View sp = new View(context);
        ratingRow.addView(sp, new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView score = new TextView(context);
        score.setText("8,7 / 10");
        score.setTextSize(11);
        score.setTextColor(0xFFFFFFFF);
        score.setBackground(TvStyle.squircleCard(context, 6));
        score.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2));
        ratingRow.addView(score);

        // Tech specs
        LinearLayout specs = new LinearLayout(context);
        specs.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-1, -2);
        spp.topMargin = dp(context, 6);
        content.addView(specs, spp);

        addDetailItem(specs, "audio", "Audio:", "Español / Original");
        addDetailItem(specs, "subtitles", "Subtítulos:", "Español, Inglés");
        addDetailItem(specs, "player", "Calidad:", "4K HDR");

        return card;
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

    private void loadMetadata() {
        io.execute(() -> {
            String lang = AppStrings.getLang(context);
            TmdbClient.Details d = TmdbClient.searchAndGetDetails(context, cleanTitle, isMovie, lang);
            if (d != null) {
                main.post(() -> {
                    details = d;
                    if (d.overview != null && !d.overview.isEmpty()) {
                        synopsisView.setText(d.overview);
                    }
                    if (d.title != null && !d.title.isEmpty()) {
                        titleView.setText(d.title);
                    }
                    if (d.releaseDate != null && d.releaseDate.length() >= 4) {
                        cleanYear = d.releaseDate.substring(0, 4);
                        metaView.setText(cleanYear + (isMovie ? " · 2 h 08 min · " : " · 3 temporadas · ") + channel.group);
                    }
                    if (d.backdropUrl != null && !d.backdropUrl.isEmpty()) {
                        LogoLoader.load(d.backdropUrl, backdropImg);
                    }
                });
            } else if (!channel.logoUrl.isEmpty()) {
                main.post(() -> LogoLoader.load(channel.logoUrl, backdropImg));
            }
        });
    }
}
