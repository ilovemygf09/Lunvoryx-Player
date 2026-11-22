package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import tv.lunvoryx.player.R;
import static tv.lunvoryx.player.ui.TvStyle.dp;

/**
 * Top navigation bar for "Modo Intuitivo" matching official mockups.
 * Spans full width with Brand Header, Category Pills, Search and Expiration info.
 */
public final class IntuitiveTopBar extends LinearLayout {
    public interface Listener {
        void onSectionSelected(String section);
        void onSearchClicked();
        void onBackClicked();
    }

    private final Context context;
    private final Listener listener;
    private String currentSection = "Inicio";
    private boolean showBackButton = false;

    private LinearLayout leftContainer;
    private FrameLayout backBtn;
    private LinearLayout tabsContainer;
    private LinearLayout searchPill;
    private LinearLayout expiryBadge;

    public IntuitiveTopBar(Context context, Listener listener) {
        super(context);
        this.context = context;
        this.listener = listener;
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(context, 24), dp(context, 12), dp(context, 24), dp(context, 12));

        // 1. Back button (for details screens)
        backBtn = new FrameLayout(context);
        backBtn.setId(View.generateViewId());
        backBtn.setFocusable(true);
        backBtn.setClickable(true);
        backBtn.setBackground(TvStyle.squircleCard(context, 18));
        LineIcon backIcon = new LineIcon(context, "back");
        backIcon.setColor(0xFFF0F0F5);
        FrameLayout.LayoutParams bip = new FrameLayout.LayoutParams(dp(context, 18), dp(context, 18), Gravity.CENTER);
        backBtn.addView(backIcon, bip);
        backBtn.setOnClickListener(v -> {
            if (listener != null) listener.onBackClicked();
        });
        backBtn.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.08f : 1.0f).scaleY(f ? 1.08f : 1.0f).setDuration(120).start();
            ((SquircleGlassDrawable) v.getBackground()).setFocused(f);
        });
        LinearLayout.LayoutParams bbp = new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38));
        bbp.rightMargin = dp(context, 14);
        backBtn.setVisibility(showBackButton ? VISIBLE : GONE);
        addView(backBtn, bbp);

        // 2. Brand Logo (LUNVORYX PLAYER)
        ImageView brand = new ImageView(context);
        brand.setImageResource(R.drawable.brand_header);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(context, 136), dp(context, 36));
        bp.rightMargin = dp(context, 16);
        addView(brand, bp);

        // Divider
        addView(createDivider());

        // 3. Navigation Tabs (Inicio, TV en vivo, Películas, Series)
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(HORIZONTAL);
        tabsContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(-2, -2);
        tcp.leftMargin = dp(context, 12);
        tcp.rightMargin = dp(context, 12);
        addView(tabsContainer, tcp);

        buildTabs();

        // Divider
        addView(createDivider());

        // Spacer to push search and expiration to the right
        View spacer = new View(context);
        addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        // 4. Search Bar Pill
        searchPill = buildSearchPill();
        addView(searchPill);

        // 5. Expiration / Playlist status Badge
        expiryBadge = buildExpiryBadge();
        LinearLayout.LayoutParams exp = new LinearLayout.LayoutParams(-2, dp(context, 34));
        exp.leftMargin = dp(context, 12);
        addView(expiryBadge, exp);
    }

    private View createDivider() {
        View div = new View(context);
        div.setBackgroundColor(0x338A9BB8);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(context, 1), dp(context, 20));
        dp.leftMargin = dp(context, 8);
        dp.rightMargin = dp(context, 8);
        return div;
    }

    private void buildTabs() {
        tabsContainer.removeAllViews();
        String[] keys = {"Inicio", "TV en vivo", "Películas", "Series"};
        String[] icons = {"home", "tv", "movie", "series"};

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String icon = icons[i];
            boolean isActive = currentSection.equalsIgnoreCase(key);

            LinearLayout tab = new LinearLayout(context);
            tab.setId(View.generateViewId());
            tab.setOrientation(HORIZONTAL);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7));

            LineIcon tabIcon = new LineIcon(context, icon);
            tabIcon.setColor(isActive ? 0xFFFFFFFF : 0xFFB0B7C3);
            tab.addView(tabIcon, new LinearLayout.LayoutParams(dp(context, 17), dp(context, 17)));

            TextView tabText = new TextView(context);
            tabText.setText(key);
            tabText.setTextSize(13.5f);
            tabText.setTextColor(isActive ? 0xFFFFFFFF : 0xFFB0B7C3);
            tabText.setTypeface(Typeface.create("sans-serif-medium", isActive ? Typeface.BOLD : Typeface.NORMAL));
            LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(-2, -2);
            ttp.leftMargin = dp(context, 7);
            tab.addView(tabText, ttp);

            if (isActive) {
                // Glowing blue pill
                tab.setBackground(TvStyle.squircleSolid(context, 16, 0xFF007AFF));
            } else {
                tab.setBackground(TvStyle.squircleCard(context, 16));
            }

            tab.setOnFocusChangeListener((v, f) -> {
                v.animate().scaleX(f ? 1.05f : 1.0f).scaleY(f ? 1.05f : 1.0f).setDuration(120).start();
                if (isActive) {
                    if (f) {
                        tab.setBackground(TvStyle.squircleSolid(context, 16, 0xFF0A84FF));
                    } else {
                        tab.setBackground(TvStyle.squircleSolid(context, 16, 0xFF007AFF));
                    }
                } else {
                    ((SquircleGlassDrawable) tab.getBackground()).setFocused(f);
                    tabIcon.setColor(f ? 0xFFFFFFFF : 0xFFB0B7C3);
                    tabText.setTextColor(f ? 0xFFFFFFFF : 0xFFB0B7C3);
                }
            });

            tab.setOnClickListener(v -> {
                if (listener != null) listener.onSectionSelected(key);
            });

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(context, 34));
            if (i > 0) p.leftMargin = dp(context, 6);
            tabsContainer.addView(tab, p);
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
        pill.setPadding(dp(context, 12), dp(context, 6), dp(context, 16), dp(context, 6));

        LineIcon sIcon = new LineIcon(context, "search");
        sIcon.setColor(0xFF8A9BB8);
        pill.addView(sIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView sText = new TextView(context);
        sText.setText(currentSection.equals("Inicio") ? "Buscar..." : "Buscar canales, películas o series...");
        sText.setTextSize(12.5f);
        sText.setTextColor(0xFF8A9BB8);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(context, 8);
        pill.addView(sText, tp);

        pill.setOnClickListener(v -> {
            if (listener != null) listener.onSearchClicked();
        });

        pill.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.03f : 1.0f).scaleY(f ? 1.03f : 1.0f).setDuration(120).start();
            ((SquircleGlassDrawable) pill.getBackground()).setFocused(f);
            sIcon.setColor(f ? 0xFFFFFFFF : 0xFF8A9BB8);
            sText.setTextColor(f ? 0xFFFFFFFF : 0xFF8A9BB8);
        });

        return pill;
    }

    private LinearLayout buildExpiryBadge() {
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(TvStyle.squircleCard(context, 14));
        badge.setPadding(dp(context, 12), dp(context, 5), dp(context, 14), dp(context, 5));

        LineIcon cal = new LineIcon(context, "calendar");
        cal.setColor(0xFF7A8B9E);
        badge.addView(cal, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView text = new TextView(context);
        text.setText("Tu lista caduca: 06/05/2027");
        text.setTextSize(11.5f);
        text.setTextColor(0xFF9AAEC4);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(context, 6);
        badge.addView(text, tp);

        return badge;
    }

    public void setSection(String section) {
        this.currentSection = section;
        buildTabs();
        if (expiryBadge != null) {
            expiryBadge.setVisibility(section.equalsIgnoreCase("Inicio") ? VISIBLE : GONE);
        }
    }

    public void setShowBackButton(boolean show) {
        this.showBackButton = show;
        if (backBtn != null) {
            backBtn.setVisibility(show ? VISIBLE : GONE);
        }
    }

    public View getActiveTab() {
        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            View child = tabsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                TextView tv = (TextView) ((LinearLayout) child).getChildAt(1);
                if (tv != null && currentSection.equalsIgnoreCase(tv.getText().toString())) {
                    return child;
                }
            }
        }
        return tabsContainer.getChildAt(0);
    }
}
