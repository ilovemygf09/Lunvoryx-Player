package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import tv.lunvoryx.player.R;
import static tv.lunvoryx.player.ui.TvStyle.dp;

/**
 * Top navigation bar for Modo Intuitivo matching user mockups.
 * Left: Brand logo at the very top-left.
 * Center: Horizontal tabs (Inicio, TV en vivo, Películas, Series).
 * Right: Search pill + Playlist Expiry status badge (on Inicio).
 */
public final class IntuitiveTopBar extends LinearLayout {
    public interface Listener {
        void onSectionSelected(String section);
        void onSearchClicked();
        void onBackClicked();
    }

    private static final class TabItem {
        final String key;
        final LinearLayout layout;
        final LineIcon icon;
        final TextView text;

        TabItem(String key, LinearLayout layout, LineIcon icon, TextView text) {
            this.key = key;
            this.layout = layout;
            this.icon = icon;
            this.text = text;
        }
    }

    private final Context context;
    private final Listener listener;
    private String currentSection = "Inicio";

    private LinearLayout tabsContainer;
    private LinearLayout searchPill;
    private TextView searchHint;
    private LinearLayout expiryBadge;
    private final List<TabItem> tabs = new ArrayList<>();

    public IntuitiveTopBar(Context context, Listener listener) {
        super(context);
        this.context = context;
        this.listener = listener;
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        // 32dp horizontal padding protects against TV overscan / bezel clipping
        setPadding(dp(context, 32), dp(context, 6), dp(context, 32), dp(context, 6));

        // 1. Brand Logo (LUNVORYX PLAYER) at the very top-left
        ImageView brand = new ImageView(context);
        brand.setImageResource(R.drawable.brand_header);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(context, 130), dp(context, 34));
        bp.rightMargin = dp(context, 14);
        addView(brand, bp);

        // Divider 1
        addView(createDivider(), createDividerLayoutParams());

        // 2. Navigation Tabs (Inicio, TV en vivo, Películas, Series)
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(HORIZONTAL);
        tabsContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(-2, -2);
        tcp.leftMargin = dp(context, 10);
        tcp.rightMargin = dp(context, 10);
        addView(tabsContainer, tcp);

        buildTabsOnce();

        // Divider 2
        addView(createDivider(), createDividerLayoutParams());

        // Spacer to push search and expiration to the right
        View spacer = new View(context);
        addView(spacer, new LinearLayout.LayoutParams(0, dp(context, 1), 1.0f));

        // 3. Search Bar Pill
        searchPill = buildSearchPill();
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-2, dp(context, 34));
        addView(searchPill, spp);

        // 4. Expiration / Playlist status Badge
        expiryBadge = buildExpiryBadge();
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(-2, dp(context, 34));
        exp.leftMargin = dp(context, 10);
        addView(expiryBadge, exp);

        updateTabStyles();
    }

    private View createDivider() {
        View div = new View(context);
        div.setBackgroundColor(0x338A9BB8);
        return div;
    }

    private LinearLayout.LayoutParams createDividerLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 1), dp(context, 20));
        lp.leftMargin = dp(context, 6);
        lp.rightMargin = dp(context, 6);
        return lp;
    }

    private void buildTabsOnce() {
        tabsContainer.removeAllViews();
        tabs.clear();

        String[] keys = {"Inicio", "TV en vivo", "Películas", "Series"};
        String[] icons = {"home", "tv", "movie", "series"};

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String iconName = icons[i];

            LinearLayout tab = new LinearLayout(context);
            tab.setId(View.generateViewId());
            tab.setOrientation(HORIZONTAL);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setPadding(dp(context, 15), dp(context, 6), dp(context, 15), dp(context, 6));

            LineIcon tabIcon = new LineIcon(context, iconName);
            tab.addView(tabIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

            TextView tabText = new TextView(context);
            tabText.setText(key);
            tabText.setTextSize(13f);
            LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(-2, -2);
            ttp.leftMargin = dp(context, 6);
            tab.addView(tabText, ttp);

            tab.setOnFocusChangeListener((v, f) -> {
                v.animate().scaleX(f ? 1.05f : 1.0f).scaleY(f ? 1.05f : 1.0f).setDuration(120).start();
                boolean isActive = currentSection.equalsIgnoreCase(key);
                if (isActive) {
                    tab.setBackground(TvStyle.squircleSolid(context, 16, f ? 0xFF0A84FF : 0xFF007AFF));
                } else {
                    if (tab.getBackground() instanceof SquircleGlassDrawable) {
                        ((SquircleGlassDrawable) tab.getBackground()).setFocused(f);
                    }
                    tabIcon.setColor(f ? 0xFFFFFFFF : 0xFFB0B7C3);
                    tabText.setTextColor(f ? 0xFFFFFFFF : 0xFFB0B7C3);
                }
            });

            tab.setOnClickListener(v -> {
                setSection(key);
                if (listener != null) listener.onSectionSelected(key);
            });

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(context, 34));
            if (i > 0) p.leftMargin = dp(context, 6);
            tabsContainer.addView(tab, p);

            tabs.add(new TabItem(key, tab, tabIcon, tabText));
        }
    }

    private void updateTabStyles() {
        for (TabItem t : tabs) {
            boolean isActive = currentSection.equalsIgnoreCase(t.key);
            if (isActive) {
                t.layout.setBackground(TvStyle.squircleSolid(context, 16, 0xFF007AFF));
                t.icon.setColor(0xFFFFFFFF);
                t.text.setTextColor(0xFFFFFFFF);
                t.text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            } else {
                t.layout.setBackground(TvStyle.squircleCard(context, 16));
                t.icon.setColor(0xFFB0B7C3);
                t.text.setTextColor(0xFFB0B7C3);
                t.text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
        }
        if (searchHint != null) {
            searchHint.setText(currentSection.equalsIgnoreCase("Inicio") ?
                    "Buscar…" : "Buscar canales, películas o series…");
        }
        if (expiryBadge != null) {
            expiryBadge.setVisibility(currentSection.equalsIgnoreCase("Inicio") ? VISIBLE : GONE);
        }
    }

    private LinearLayout buildSearchPill() {
        LinearLayout pill = new LinearLayout(context);
        pill.setId(View.generateViewId());
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setBackground(TvStyle.squircleInput(context, 16));
        pill.setPadding(dp(context, 12), dp(context, 6), dp(context, 14), dp(context, 6));

        LineIcon sIcon = new LineIcon(context, "search");
        sIcon.setColor(0xFF8A9BB8);
        pill.addView(sIcon, new LinearLayout.LayoutParams(dp(context, 15), dp(context, 15)));

        searchHint = new TextView(context);
        searchHint.setText("Buscar canales, películas o series…");
        searchHint.setTextSize(11.5f);
        searchHint.setTextColor(0xFF8A9BB8);
        searchHint.setSingleLine(true);
        searchHint.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(context, 8);
        pill.addView(searchHint, tp);

        pill.setOnClickListener(v -> {
            if (listener != null) listener.onSearchClicked();
        });

        pill.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.03f : 1.0f).scaleY(f ? 1.03f : 1.0f).setDuration(120).start();
            ((SquircleGlassDrawable) pill.getBackground()).setFocused(f);
            sIcon.setColor(f ? 0xFFFFFFFF : 0xFF8A9BB8);
            searchHint.setTextColor(f ? 0xFFFFFFFF : 0xFF8A9BB8);
        });

        return pill;
    }

    private LinearLayout buildExpiryBadge() {
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(TvStyle.squircleCard(context, 14));
        badge.setPadding(dp(context, 10), dp(context, 5), dp(context, 12), dp(context, 5));

        LineIcon cal = new LineIcon(context, "calendar");
        cal.setColor(0xFF7A8B9E);
        badge.addView(cal, new LinearLayout.LayoutParams(dp(context, 13), dp(context, 13)));

        TextView text = new TextView(context);
        text.setText("Tu lista caduca: 06/05/2027");
        text.setTextSize(11f);
        text.setTextColor(0xFF9AAEC4);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(context, 6);
        badge.addView(text, tp);

        return badge;
    }

    public void setSection(String section) {
        if (section == null) return;
        this.currentSection = section;
        updateTabStyles();
    }

    public void setShowBackButton(boolean show) {
        // In intuitive mode, the back button is inside the detail hero view, matching the mockups.
    }

    public View getActiveTab() {
        for (TabItem t : tabs) {
            if (currentSection.equalsIgnoreCase(t.key)) {
                return t.layout;
            }
        }
        return tabs.isEmpty() ? null : tabs.get(0).layout;
    }
}
