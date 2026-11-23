package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.PlaylistRepository;
import static tv.lunvoryx.player.ui.TvStyle.dp;

/**
 * Intuitive Home Screen matching media_1795347990938_52fcabe8.jpg:
 * - Large Left Live TV Card with "● EN DIRECTO"
 * - Center Column: Movies Card (top) + Reload List Card (bottom)
 * - Right Column: Series Card (top) + Settings Card (bottom)
 * - Footer: Version info
 */
public final class IntuitiveHomeScreen extends LinearLayout {
    public interface Listener {
        void onNavigate(String section);
        void onReloadPlaylist();
        void onOpenSettings();
    }

    private final Context context;
    private final PlaylistRepository repository;
    private final Listener listener;
    private FrameLayout liveCard;

    public IntuitiveHomeScreen(Context context, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.listener = listener;
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(context, 24), dp(context, 10), dp(context, 24), dp(context, 16));

        // Main Grid Row
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(HORIZONTAL);
        grid.setClipChildren(false);
        setClipChildren(false);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        gp.topMargin = dp(context, 40);
        gp.bottomMargin = dp(context, 30);
        addView(grid, gp);

        // 1. Left Giant Card: TV en vivo (44% width)
        liveCard = buildLiveTvCard();
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(0, -1, 0.46f);
        lcp.rightMargin = dp(context, 16);
        grid.addView(liveCard, lcp);

        // 2. Middle Column: Peliculas (top) + Recargar lista (bottom) (28% width)
        LinearLayout midCol = new LinearLayout(context);
        midCol.setOrientation(VERTICAL);
        midCol.setClipChildren(false);
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(0, -1, 0.27f);
        mcp.rightMargin = dp(context, 16);
        grid.addView(midCol, mcp);

        FrameLayout moviesCard = buildImageCard("Películas", "movie", R.drawable.category_movies);
        moviesCard.setOnClickListener(v -> { if (listener != null) listener.onNavigate("Películas"); });
        midCol.addView(moviesCard, new LinearLayout.LayoutParams(-1, 0, 0.72f));

        View midSpacer = new View(context);
        midCol.addView(midSpacer, new LinearLayout.LayoutParams(-1, dp(context, 14)));

        FrameLayout reloadCard = buildActionCard("Recargar lista", "refresh", 0xFF0E1A34);
        reloadCard.setOnClickListener(v -> { if (listener != null) listener.onReloadPlaylist(); });
        midCol.addView(reloadCard, new LinearLayout.LayoutParams(-1, 0, 0.28f));

        // 3. Right Column: Series (top) + Ajustes (bottom) (28% width)
        LinearLayout rightCol = new LinearLayout(context);
        rightCol.setOrientation(VERTICAL);
        rightCol.setClipChildren(false);
        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(0, -1, 0.27f);
        grid.addView(rightCol, rcp);

        FrameLayout seriesCard = buildImageCard("Series", "series", R.drawable.category_live);
        seriesCard.setOnClickListener(v -> { if (listener != null) listener.onNavigate("Series"); });
        rightCol.addView(seriesCard, new LinearLayout.LayoutParams(-1, 0, 0.72f));

        View rightSpacer = new View(context);
        rightCol.addView(rightSpacer, new LinearLayout.LayoutParams(-1, dp(context, 14)));

        FrameLayout settingsCard = buildActionCard("Ajustes", "settings", 0xFF0E1A34);
        settingsCard.setOnClickListener(v -> { if (listener != null) listener.onOpenSettings(); });
        rightCol.addView(settingsCard, new LinearLayout.LayoutParams(-1, 0, 0.28f));

        // Setup D-pad focus flow
        liveCard.setId(View.generateViewId());
        moviesCard.setId(View.generateViewId());
        reloadCard.setId(View.generateViewId());
        seriesCard.setId(View.generateViewId());
        settingsCard.setId(View.generateViewId());

        liveCard.setNextFocusRightId(moviesCard.getId());
        moviesCard.setNextFocusLeftId(liveCard.getId());
        moviesCard.setNextFocusRightId(seriesCard.getId());
        moviesCard.setNextFocusDownId(reloadCard.getId());

        reloadCard.setNextFocusLeftId(liveCard.getId());
        reloadCard.setNextFocusUpId(moviesCard.getId());
        reloadCard.setNextFocusRightId(settingsCard.getId());

        seriesCard.setNextFocusLeftId(moviesCard.getId());
        seriesCard.setNextFocusDownId(settingsCard.getId());

        settingsCard.setNextFocusLeftId(reloadCard.getId());
        settingsCard.setNextFocusUpId(seriesCard.getId());

        // Footer at bottom
        TextView footer = new TextView(context);
        String vName = "1.1.1";
        try {
            vName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        footer.setText("Lunvoryx Player · v" + vName);
        footer.setTextSize(11.5f);
        footer.setTextColor(0xFF6A7B95);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, -2);
        fp.topMargin = dp(context, 12);
        addView(footer, fp);

        post(() -> liveCard.requestFocus());
    }

    public View getFirstFocusable() {
        return liveCard;
    }

    private FrameLayout buildLiveTvCard() {
        FrameLayout card = new FrameLayout(context);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(TvStyle.squircleCard(context, 20));
        IntuitivePanel.outline(card, 14);
        IntuitivePanel.focusRing(card, false);

        // Background photo (stadium or category_live)
        ImageView bg = new ImageView(context);
        bg.setImageBitmap(Artwork.get(context, R.drawable.intu_stadium, 900));
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // Vignette gradient
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xAF031022, 0x33081428, 0x050A1628}
        ));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // Top-right "● EN DIRECTO" badge
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(TvStyle.squircleSolid(context, 12, 0xFFE50914));
        badge.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));

        TextView dot = new TextView(context);
        dot.setText("● ");
        dot.setTextColor(0xFFFFFFFF);
        dot.setTextSize(9);
        badge.addView(dot);

        TextView badgeText = new TextView(context);
        badgeText.setText("EN DIRECTO");
        badgeText.setTextColor(0xFFFFFFFF);
        badgeText.setTextSize(11);
        badgeText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        badge.addView(badgeText);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bp.topMargin = dp(context, 18);
        bp.rightMargin = dp(context, 18);
        card.addView(badge, bp);

        // Center content: Big TV icon + "TV en vivo"
        LinearLayout center = new LinearLayout(context);
        center.setOrientation(VERTICAL);
        center.setGravity(Gravity.CENTER);

        LineIcon tvIcon = new LineIcon(context, "tv");
        tvIcon.setColor(0xFFFFFFFF);
        center.addView(tvIcon, new LinearLayout.LayoutParams(dp(context, 68), dp(context, 68)));

        TextView title = new TextView(context);
        title.setText("TV en vivo");
        title.setTextSize(38);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(context, 10);
        center.addView(title, tp);

        FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        label.leftMargin = dp(context, 22);
        card.addView(center, label);

        card.setOnClickListener(v -> {
            if (listener != null) listener.onNavigate("TV en vivo");
        });

        card.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.025f : 1.0f).scaleY(f ? 1.025f : 1.0f).setDuration(130).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
            IntuitivePanel.focusRing(v, f);
        });

        return card;
    }

    private FrameLayout buildImageCard(String titleText, String iconKind, int resId) {
        FrameLayout card = new FrameLayout(context);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(TvStyle.squircleCard(context, 18));
        IntuitivePanel.outline(card, 14);
        IntuitivePanel.focusRing(card, false);

        ImageView bg = new ImageView(context);
        bg.setImageBitmap(Artwork.get(context, resId, 700));
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xA3061020, 0x44061222, 0x10060B18}
        ));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(VERTICAL);
        center.setGravity(Gravity.CENTER);

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFFFFFFFF);
        center.addView(icon, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(23);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(context, 8);
        center.addView(title, tp);

        FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        label.leftMargin = dp(context, 22);
        card.addView(center, label);

        card.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.035f : 1.0f).scaleY(f ? 1.035f : 1.0f).setDuration(130).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
            IntuitivePanel.focusRing(v, f);
        });

        return card;
    }

    private FrameLayout buildActionCard(String titleText, String iconKind, int bgColor) {
        FrameLayout card = new FrameLayout(context);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(TvStyle.squircleCard(context, 18));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(HORIZONTAL);
        center.setGravity(Gravity.CENTER);

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFFFFFFFF);
        center.addView(icon, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(context, 12);
        center.addView(title, tp);

        card.addView(center, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        card.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.04f : 1.0f).scaleY(f ? 1.04f : 1.0f).setDuration(130).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
            IntuitivePanel.focusRing(v, f);
        });

        return card;
    }
}
