package tv.lunvoryx.player.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.tmdb.TmdbClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class SearchViewLayout extends LinearLayout {
    public interface Listener {
        void onPlay(Channel channel, List<Channel> list, int index);
        void onToggleFavorite(Channel channel);
        void onShowDetails(Channel channel);
    }

    private final Context context;
    private final PlaylistRepository repository;
    private final Listener listener;
    private final SharedPreferences prefs;

    // Filter pill states: 0: Todo, 1: TV En Vivo, 2: Películas, 3: Series, 4: Favoritos
    private int filterType = 0;
    private String currentQuery = "";

    // Search results separated by type
    private final List<Channel> liveMatches = new ArrayList<>();
    private final List<Channel> seriesMatches = new ArrayList<>();
    private final List<Channel> movieMatches = new ArrayList<>();
    private final List<Channel> allCurrentMatches = new ArrayList<>();

    // UI elements
    private EditText searchField;
    private LinearLayout filterPillsContainer;
    private LinearLayout recentSearchesContainer;
    private LinearLayout trendingContainer;
    private TextView resultsHeaderSubtitle;
    private LinearLayout resultsGroupsContainer;

    // Preview panel
    private ImageView previewBackdrop;
    private TextView previewTitle;
    private LinearLayout previewTagsRow;
    private TextView previewMeta;
    private TextView previewOverview;
    private TextView btnPlay;
    private TextView btnFav;
    private TextView btnInfo;
    private LinearLayout seasonsBlock;
    private LinearLayout nextEpisodeBlock;

    private Channel currentSelectedChannel;

    public SearchViewLayout(Context context, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.listener = listener;
        this.prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE);

        setOrientation(VERTICAL);
        buildUi();
        performSearch("");
    }

    private void buildUi() {
        setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));

        // 1. Header (Icon + Title + Subtitle)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(context, 8));

        LineIcon searchIcon = new LineIcon(context, "search");
        searchIcon.setColor(0xFFFFFFFF);
        header.addView(searchIcon, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));

        LinearLayout headText = new LinearLayout(context);
        headText.setOrientation(VERTICAL);
        headText.setPadding(dp(context, 12), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(AppStrings.get(context, "nav_search"));
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        headText.addView(title);

        TextView sub = new TextView(context);
        sub.setText(AppStrings.isEnglish(context) ? "Find channels, movies and series instantly" : "Encuentra canales, películas y series al instante");
        sub.setTextSize(11);
        sub.setTextColor(0xFF8F91A2);
        headText.addView(sub);
        header.addView(headText);
        addView(header);

        // 2. Glowing Search Input Box
        buildSearchInputBar();

        // 3. Filter Pills Row (Todo, TV En Vivo, Películas, Series, Favoritos)
        buildFilterPills();

        // 4. Three columns content body
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        // Column A: Búsquedas recientes & Tendencias
        buildLeftSidebar(body);

        // Column B: Resultados agrupados
        buildResultsColumn(body);

        // Column C: Panel de Vista Previa
        buildPreviewPanel(body);
    }

    private void buildSearchInputBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(context, 14), 0, dp(context, 14), 0);

        // Dark glowing squircle frosted glass pill
        bar.setBackground(TvStyle.squircleInput(context, 18));

        LineIcon sIcon = new LineIcon(context, "search");
        sIcon.setColor(0xFF9EACB8);
        bar.addView(sIcon, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        searchField = new EditText(context);
        searchField.setId(View.generateViewId());
        searchField.setHint(AppStrings.isEnglish(context) ? "Search channels, movies, series…" : "Buscar canales, películas, series…");
        searchField.setHintTextColor(0xFF677286);
        searchField.setTextColor(0xFFFFFFFF);
        searchField.setTextSize(14);
        searchField.setBackground(null);
        searchField.setSingleLine(true);
        searchField.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            String q = searchField.getText().toString().trim();
            if (!q.isEmpty()) saveRecentSearch(q);
            performSearch(q);
            return true;
        });
        bar.addView(searchField, new LinearLayout.LayoutParams(0, -2, 1));

        LineIcon mic = new LineIcon(context, "mic");
        mic.setColor(0xFF8F91A2);
        bar.addView(mic, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(context, 44));
        bp.topMargin = dp(context, 4);
        bp.bottomMargin = dp(context, 10);
        addView(bar, bp);
    }

    private void buildFilterPills() {
        filterPillsContainer = new LinearLayout(context);
        filterPillsContainer.setOrientation(HORIZONTAL);
        filterPillsContainer.setGravity(Gravity.CENTER_VERTICAL);
        filterPillsContainer.setPadding(0, 0, 0, dp(context, 12));

        String[] pillLabels = {
                AppStrings.isEnglish(context) ? "All" : "Todo",
                AppStrings.get(context, "nav_live"),
                AppStrings.get(context, "nav_movies"),
                AppStrings.get(context, "nav_series"),
                AppStrings.get(context, "favorites")
        };
        String[] pillIcons = {"all_grid", "tv", "movie", "series", "star"};

        for (int i = 0; i < pillLabels.length; i++) {
            final int index = i;
            LinearLayout pill = new LinearLayout(context);
            pill.setOrientation(HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(context, 12), dp(context, 6), dp(context, 14), dp(context, 6));
            pill.setFocusable(true);
            pill.setClickable(true);

            LineIcon icon = new LineIcon(context, pillIcons[i]);
            pill.addView(icon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

            TextView label = new TextView(context);
            label.setText(pillLabels[i]);
            label.setTextSize(12);
            label.setPadding(dp(context, 6), 0, 0, 0);
            pill.addView(label);

            updatePillVisual(pill, icon, label, filterType == i, false);

            pill.setOnClickListener(v -> {
                filterType = index;
                refreshPills();
                performSearch(currentQuery);
            });

            pill.setOnFocusChangeListener((v, f) -> {
                updatePillVisual(pill, icon, label, filterType == index, f);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(context, 32));
            if (i > 0) lp.leftMargin = dp(context, 8);
            filterPillsContainer.addView(pill, lp);
        }

        addView(filterPillsContainer);
    }

    private void refreshPills() {
        for (int i = 0; i < filterPillsContainer.getChildCount(); i++) {
            LinearLayout pill = (LinearLayout) filterPillsContainer.getChildAt(i);
            LineIcon icon = (LineIcon) pill.getChildAt(0);
            TextView label = (TextView) pill.getChildAt(1);
            updatePillVisual(pill, icon, label, filterType == i, pill.isFocused());
        }
    }

    private void updatePillVisual(LinearLayout pill, LineIcon icon, TextView label, boolean active, boolean focused) {
        if (active && focused) {
            pill.setBackground(TvStyle.squircleSolid(context, 12, 0xFFFFFFFF).setCustomStroke(0x22000000, 1.0f));
            icon.setColor(0xFF14151B);
            label.setTextColor(0xFF14151B);
            label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            pill.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
        } else if (active) {
            pill.setBackground(TvStyle.squircleSolid(context, 12, 0xFFFFFFFF).setCustomStroke(0x18000000, 0.8f));
            icon.setColor(0xFF14151B);
            label.setTextColor(0xFF14151B);
            label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            pill.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        } else if (focused) {
            pill.setBackground(TvStyle.squircleCard(context, 12).setFocused(true));
            icon.setColor(0xFFFFFFFF);
            label.setTextColor(0xFFFFFFFF);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            pill.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
        } else {
            pill.setBackground(TvStyle.squircleCard(context, 12));
            icon.setColor(0xFFCACBD4);
            label.setTextColor(0xFFCACBD4);
            label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            pill.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void buildLeftSidebar(LinearLayout body) {
        LinearLayout colA = new LinearLayout(context);
        colA.setOrientation(VERTICAL);
        colA.setBackground(TvStyle.squircleCard(context, 18));
        colA.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 10));

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(VERTICAL);
        scroller.addView(inner);
        colA.addView(scroller, new LinearLayout.LayoutParams(-1, -1));

        // 1. Header: Búsquedas recientes
        LinearLayout rh = new LinearLayout(context);
        rh.setOrientation(HORIZONTAL);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        rh.setPadding(0, 0, 0, dp(context, 8));

        LineIcon clock = new LineIcon(context, "history");
        clock.setColor(0xFF8F91A2);
        rh.addView(clock, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView rTitle = new TextView(context);
        rTitle.setText(AppStrings.isEnglish(context) ? "Recent searches" : "Búsquedas recientes");
        rTitle.setTextSize(12);
        rTitle.setTextColor(0xFFCACBD4);
        rTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        rTitle.setPadding(dp(context, 6), 0, 0, 0);
        rh.addView(rTitle);
        inner.addView(rh);

        recentSearchesContainer = new LinearLayout(context);
        recentSearchesContainer.setOrientation(VERTICAL);
        inner.addView(recentSearchesContainer);

        // 2. Header: Tendencias
        LinearLayout th = new LinearLayout(context);
        th.setOrientation(HORIZONTAL);
        th.setGravity(Gravity.CENTER_VERTICAL);
        th.setPadding(0, dp(context, 14), 0, dp(context, 8));

        LineIcon trend = new LineIcon(context, "trending");
        trend.setColor(0xFF8F91A2);
        th.addView(trend, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView tTitle = new TextView(context);
        tTitle.setText(AppStrings.isEnglish(context) ? "Trending" : "Tendencias");
        tTitle.setTextSize(12);
        tTitle.setTextColor(0xFFCACBD4);
        tTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tTitle.setPadding(dp(context, 6), 0, 0, 0);
        th.addView(tTitle);
        inner.addView(th);

        trendingContainer = new LinearLayout(context);
        trendingContainer.setOrientation(VERTICAL);
        inner.addView(trendingContainer);

        renderRecentSearches();
        renderTrending();

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 180), -1);
        lp.rightMargin = dp(context, 10);
        body.addView(colA, lp);
    }

    private void renderRecentSearches() {
        recentSearchesContainer.removeAllViews();
        List<String> recents = getRecentSearches();
        if (recents.isEmpty()) {
            recents = Arrays.asList("ted lasso", "horizonte azul", "la frontera", "nexo", "fórmula 1");
        }

        for (String query : recents) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
            item.setFocusable(true);
            item.setClickable(true);

            LineIcon hIcon = new LineIcon(context, "history");
            hIcon.setColor(0xFF75788E);
            item.addView(hIcon, new LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)));

            TextView label = new TextView(context);
            label.setText(query);
            label.setTextSize(11);
            label.setTextColor(0xFFCACBD4);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(context, 6), 0, dp(context, 4), 0);
            item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            LineIcon close = new LineIcon(context, "close");
            close.setColor(0xFF676A7E);
            item.addView(close, new LinearLayout.LayoutParams(dp(context, 10), dp(context, 10)));

            item.setOnClickListener(v -> {
                searchField.setText(query);
                performSearch(query);
            });

            item.setOnFocusChangeListener((v, f) -> {
                if (f) {
                    item.setBackground(TvStyle.squircleSolid(context, 8, 0xFFFFFFFF));
                    hIcon.setColor(0xFF14151B);
                    label.setTextColor(0xFF14151B);
                    close.setColor(0xFF14151B);
                } else {
                    item.setBackground(null);
                    hIcon.setColor(0xFF75788E);
                    label.setTextColor(0xFFCACBD4);
                    close.setColor(0xFF676A7E);
                }
            });

            recentSearchesContainer.addView(item);
        }
    }

    private void renderTrending() {
        trendingContainer.removeAllViews();
        // Dynamically extract trending terms from user channels (or fallbacks)
        List<String> trends = new ArrayList<>();
        int count = 0;
        for (Channel c : repository.getAllChannels()) {
            if (!c.group.isEmpty() && !trends.contains(c.group) && !c.group.equalsIgnoreCase("Todos")) {
                trends.add(c.group);
                if (++count == 5) break;
            }
        }
        if (trends.isEmpty()) {
            trends = Arrays.asList("Premier League", "Succession", "Dune", "El señor de los anillos", "True Detective");
        }

        for (String term : trends) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
            item.setFocusable(true);
            item.setClickable(true);

            LineIcon tIcon = new LineIcon(context, "trending");
            tIcon.setColor(0xFF75788E);
            item.addView(tIcon, new LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)));

            TextView label = new TextView(context);
            label.setText(term);
            label.setTextSize(11);
            label.setTextColor(0xFFCACBD4);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(context, 6), 0, 0, 0);
            item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            item.setOnClickListener(v -> {
                searchField.setText(term);
                performSearch(term);
            });

            item.setOnFocusChangeListener((v, f) -> {
                if (f) {
                    item.setBackground(TvStyle.squircleSolid(context, 8, 0xFFFFFFFF));
                    tIcon.setColor(0xFF14151B);
                    label.setTextColor(0xFF14151B);
                } else {
                    item.setBackground(null);
                    tIcon.setColor(0xFF75788E);
                    label.setTextColor(0xFFCACBD4);
                }
            });

            trendingContainer.addView(item);
        }
    }

    private void buildResultsColumn(LinearLayout body) {
        LinearLayout colB = new LinearLayout(context);
        colB.setOrientation(VERTICAL);
        colB.setBackground(TvStyle.squircleCard(context, 18));
        colB.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 10));

        // Subheader: List Icon + "Resultados" + "X resultados para '...'"
        LinearLayout rHead = new LinearLayout(context);
        rHead.setOrientation(HORIZONTAL);
        rHead.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon listIcon = new LineIcon(context, "list");
        listIcon.setColor(0xFFFFFFFF);
        rHead.addView(listIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView title = new TextView(context);
        title.setText(AppStrings.isEnglish(context) ? "Results" : "Resultados");
        title.setTextSize(15);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        rHead.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        resultsHeaderSubtitle = new TextView(context);
        resultsHeaderSubtitle.setText("");
        resultsHeaderSubtitle.setTextSize(11);
        resultsHeaderSubtitle.setTextColor(0xFF8F91A2);
        rHead.addView(resultsHeaderSubtitle);
        colB.addView(rHead);

        // Scrollable Results Groups (Canales en vivo, Series, Películas)
        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        resultsGroupsContainer = new LinearLayout(context);
        resultsGroupsContainer.setOrientation(VERTICAL);
        resultsGroupsContainer.setPadding(0, dp(context, 10), 0, 0);
        scroller.addView(resultsGroupsContainer);
        colB.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.rightMargin = dp(context, 10);
        body.addView(colB, lp);
    }

    private void buildPreviewPanel(LinearLayout body) {
        LinearLayout colC = new LinearLayout(context);
        colC.setOrientation(VERTICAL);
        colC.setBackground(TvStyle.squircleCard(context, 18));
        colC.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(context, 8));

        LineIcon pIcon = new LineIcon(context, "player");
        pIcon.setColor(0xFFF8F8FB);
        head.addView(pIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView pTitle = new TextView(context);
        pTitle.setText(AppStrings.isEnglish(context) ? "Preview" : "Vista previa");
        pTitle.setTextSize(14);
        pTitle.setTextColor(0xFFFFFFFF);
        pTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pTitle.setPadding(dp(context, 8), 0, 0, 0);
        head.addView(pTitle);
        colC.addView(head);

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(VERTICAL);
        scroller.addView(inner);
        colC.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        // 16:9 Backdrop Image
        previewBackdrop = new ImageView(context);
        previewBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewBackdrop.setBackground(TvStyle.squircleSolid(context, 12, 0xFF1E2130));
        previewBackdrop.setClipToOutline(true);
        inner.addView(previewBackdrop, new LinearLayout.LayoutParams(-1, dp(context, 132)));

        // Title
        previewTitle = new TextView(context);
        previewTitle.setText("");
        previewTitle.setTextSize(16);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        previewTitle.setPadding(0, dp(context, 8), 0, dp(context, 4));
        inner.addView(previewTitle);

        // Tags Row (Serie, Comedia, Drama, Deportes)
        previewTagsRow = new LinearLayout(context);
        previewTagsRow.setOrientation(HORIZONTAL);
        previewTagsRow.setPadding(0, 0, 0, dp(context, 4));
        inner.addView(previewTagsRow);

        // Metadata row (T1 - T3 • 34 episodios | HD | 16+)
        previewMeta = new TextView(context);
        previewMeta.setText("");
        previewMeta.setTextSize(10);
        previewMeta.setTextColor(0xFF8F91A2);
        inner.addView(previewMeta);

        // Overview / Synopsis
        previewOverview = new TextView(context);
        previewOverview.setText("");
        previewOverview.setTextSize(10);
        previewOverview.setTextColor(0xFFCACBD4);
        previewOverview.setMaxLines(4);
        previewOverview.setEllipsize(TextUtils.TruncateAt.END);
        previewOverview.setLineSpacing(dp(context, 2), 1);
        previewOverview.setPadding(0, dp(context, 6), 0, dp(context, 10));
        inner.addView(previewOverview);

        // Action Buttons Row (▶ Ver, ☆ Favorito, ⓘ Más info)
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(HORIZONTAL);

        btnPlay = new TextView(context);
        btnPlay.setText("▶ " + (AppStrings.isEnglish(context) ? "Play" : "Ver"));
        btnPlay.setTextSize(11);
        btnPlay.setTextColor(0xFF14151B);
        btnPlay.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnPlay.setGravity(Gravity.CENTER);
        btnPlay.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        btnPlay.setBackground(TvStyle.squircleSolid(context, 12, 0xFFFFFFFF));
        btnPlay.setFocusable(true);
        btnPlay.setClickable(true);
        btnPlay.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onPlay(currentSelectedChannel, allCurrentMatches, allCurrentMatches.indexOf(currentSelectedChannel));
            }
        });
        btnRow.addView(btnPlay, new LinearLayout.LayoutParams(0, -2, 1));

        btnFav = new TextView(context);
        btnFav.setText("☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        btnFav.setTextSize(11);
        btnFav.setTextColor(0xFFF8F8FB);
        btnFav.setGravity(Gravity.CENTER);
        btnFav.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        SquircleGlassDrawable favBg = TvStyle.squircleCard(context, 12);
        btnFav.setBackground(favBg);
        btnFav.setFocusable(true);
        btnFav.setClickable(true);
        btnFav.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onToggleFavorite(currentSelectedChannel);
                updateFavState();
            }
        });
        btnFav.setOnFocusChangeListener((v, f) -> {
            btnFav.setTextColor(f ? 0xFF14151B : 0xFFF8F8FB);
            favBg.setSolidColor(f ? 0xFFFFFFFF : 0);
            favBg.setFocused(f);
        });
        LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(-2, -2);
        fbp.leftMargin = dp(context, 6);
        btnRow.addView(btnFav, fbp);

        btnInfo = new TextView(context);
        btnInfo.setText("ⓘ " + (AppStrings.isEnglish(context) ? "More info" : "Más info"));
        btnInfo.setTextSize(11);
        btnInfo.setTextColor(0xFFF8F8FB);
        btnInfo.setGravity(Gravity.CENTER);
        btnInfo.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        SquircleGlassDrawable infoBg = TvStyle.squircleCard(context, 12);
        btnInfo.setBackground(infoBg);
        btnInfo.setFocusable(true);
        btnInfo.setClickable(true);
        btnInfo.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onShowDetails(currentSelectedChannel);
            }
        });
        btnInfo.setOnFocusChangeListener((v, f) -> {
            btnInfo.setTextColor(f ? 0xFF14151B : 0xFFF8F8FB);
            infoBg.setSolidColor(f ? 0xFFFFFFFF : 0);
            infoBg.setFocused(f);
        });
        LinearLayout.LayoutParams ibp = new LinearLayout.LayoutParams(-2, -2);
        ibp.leftMargin = dp(context, 6);
        btnRow.addView(btnInfo, ibp);

        inner.addView(btnRow);

        // Seasons Block
        seasonsBlock = new LinearLayout(context);
        seasonsBlock.setOrientation(VERTICAL);
        seasonsBlock.setPadding(0, dp(context, 10), 0, 0);
        inner.addView(seasonsBlock);

        // Next Episode Block ("A continuación")
        nextEpisodeBlock = new LinearLayout(context);
        nextEpisodeBlock.setOrientation(VERTICAL);
        nextEpisodeBlock.setPadding(0, dp(context, 10), 0, 0);
        inner.addView(nextEpisodeBlock);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 260), -1);
        body.addView(colC, lp);
    }

    private void performSearch(String q) {
        currentQuery = q != null ? q.trim() : "";
        liveMatches.clear();
        seriesMatches.clear();
        movieMatches.clear();
        allCurrentMatches.clear();

        String norm = currentQuery.toLowerCase(Locale.ROOT);
        Set<String> favIds = repository.getFavoriteIds();

        for (Channel c : repository.getAllChannels()) {
            boolean matchesQuery = norm.isEmpty() ||
                    c.name.toLowerCase(Locale.ROOT).contains(norm) ||
                    c.group.toLowerCase(Locale.ROOT).contains(norm);
            if (!matchesQuery) continue;

            boolean isFav = favIds.contains(c.id);
            if (filterType == 4 && !isFav) continue;

            MediaKind kind = MediaKind.classify(c);
            if (filterType == 1 && kind != MediaKind.LIVE) continue;
            if (filterType == 2 && kind != MediaKind.MOVIE) continue;
            if (filterType == 3 && kind != MediaKind.SERIES) continue;

            if (kind == MediaKind.LIVE) liveMatches.add(c);
            else if (kind == MediaKind.SERIES) seriesMatches.add(c);
            else movieMatches.add(c);

            allCurrentMatches.add(c);
        }

        int total = allCurrentMatches.size();
        resultsHeaderSubtitle.setText(currentQuery.isEmpty() ?
                (total + (AppStrings.isEnglish(context) ? " items in library" : " contenidos en biblioteca")) :
                (total + (AppStrings.isEnglish(context) ? " results for «" : " resultados para «") + currentQuery + "»"));

        renderGroupedResults();

        if (!allCurrentMatches.isEmpty()) {
            selectForPreview(allCurrentMatches.get(0));
        } else {
            clearPreview();
        }
    }

    private void renderGroupedResults() {
        resultsGroupsContainer.removeAllViews();

        if (allCurrentMatches.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(AppStrings.get(context, "empty_search"));
            empty.setTextSize(13);
            empty.setTextColor(0xFF8F91A2);
            empty.setPadding(dp(context, 4), dp(context, 14), 0, 0);
            resultsGroupsContainer.addView(empty);
            return;
        }

        // Section: Canales en vivo
        if (!liveMatches.isEmpty() && (filterType == 0 || filterType == 1 || filterType == 4)) {
            renderGroupSection("tv", (AppStrings.isEnglish(context) ? "Live TV Channels (" : "Canales en vivo (") + liveMatches.size() + ")", liveMatches);
        }

        // Section: Series
        if (!seriesMatches.isEmpty() && (filterType == 0 || filterType == 3 || filterType == 4)) {
            renderGroupSection("series", (AppStrings.isEnglish(context) ? "Series (" : "Series (") + seriesMatches.size() + ")", seriesMatches);
        }

        // Section: Películas
        if (!movieMatches.isEmpty() && (filterType == 0 || filterType == 2 || filterType == 4)) {
            renderGroupSection("movie", (AppStrings.isEnglish(context) ? "Movies (" : "Películas (") + movieMatches.size() + ")", movieMatches);
        }
    }

    private void renderGroupSection(String iconKind, String titleText, List<Channel> items) {
        LinearLayout sectionHead = new LinearLayout(context);
        sectionHead.setOrientation(HORIZONTAL);
        sectionHead.setGravity(Gravity.CENTER_VERTICAL);
        sectionHead.setPadding(0, dp(context, 8), 0, dp(context, 6));

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFFFFFFFF);
        sectionHead.addView(icon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(13);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(dp(context, 8), 0, 0, 0);
        sectionHead.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView seeAll = new TextView(context);
        seeAll.setText(AppStrings.isEnglish(context) ? "See all ›" : "Ver todo ›");
        seeAll.setTextSize(11);
        seeAll.setTextColor(0xFF8F91A2);
        sectionHead.addView(seeAll);
        resultsGroupsContainer.addView(sectionHead);

        for (Channel c : items) {
            resultsGroupsContainer.addView(buildItemCard(c));
        }
    }

    private View buildItemCard(Channel c) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 8), dp(context, 8), dp(context, 12), dp(context, 8));
        card.setFocusable(true);
        card.setClickable(true);

        SquircleGlassDrawable bg = TvStyle.squircleCard(context, 12);
        card.setBackground(bg);

        // Thumbnail / Emblem (Landscape rounded)
        FrameLayout emblem = new FrameLayout(context);
        emblem.setBackground(TvStyle.squircleSolid(context, 10, 0xFF282C40));
        emblem.setClipToOutline(true);

        ImageView img = new ImageView(context);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        emblem.addView(img, new FrameLayout.LayoutParams(-1, -1));

        TextView init = new TextView(context);
        init.setTextSize(14);
        init.setTextColor(0xFFCACBD4);
        init.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        init.setGravity(Gravity.CENTER);
        emblem.addView(init, new FrameLayout.LayoutParams(-1, -1));

        if (!c.logoUrl.isEmpty()) {
            init.setVisibility(GONE);
            LogoLoader.load(c.logoUrl, img);
        } else {
            img.setImageDrawable(null);
            init.setVisibility(VISIBLE);
            String t = c.name.trim();
            init.setText(t.length() > 2 ? t.substring(0, 2).toUpperCase(Locale.ROOT) : t.toUpperCase(Locale.ROOT));
        }

        card.addView(emblem, new LinearLayout.LayoutParams(dp(context, 68), dp(context, 44)));

        // Details Column
        LinearLayout det = new LinearLayout(context);
        det.setOrientation(VERTICAL);
        det.setPadding(dp(context, 10), 0, dp(context, 8), 0);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setText(c.name);
        name.setTextSize(13);
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView hdBadge = new TextView(context);
        hdBadge.setText("HD");
        hdBadge.setTextSize(9);
        hdBadge.setTextColor(0xFFB5B6C2);
        hdBadge.setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1));
        GradientDrawable hdbg = new GradientDrawable();
        hdbg.setColor(0xFF2A2E44);
        hdbg.setCornerRadius(dp(context, 4));
        hdBadge.setBackground(hdbg);
        titleRow.addView(hdBadge);

        det.addView(titleRow);

        // Tags & Info line
        LinearLayout infoRow = new LinearLayout(context);
        infoRow.setOrientation(HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(0, dp(context, 4), 0, 0);

        TextView typePill = new TextView(context);
        MediaKind k = MediaKind.classify(c);
        typePill.setText(k == MediaKind.LIVE ? "Canal en vivo" : (k == MediaKind.MOVIE ? "Película" : "Serie"));
        typePill.setTextSize(9);
        typePill.setTextColor(0xFF8F91A2);
        infoRow.addView(typePill);

        TextView extra = new TextView(context);
        extra.setText(" • " + (c.group.isEmpty() ? "General" : c.group));
        extra.setTextSize(9);
        extra.setTextColor(0xFF75788E);
        extra.setSingleLine(true);
        extra.setEllipsize(TextUtils.TruncateAt.END);
        infoRow.addView(extra, new LinearLayout.LayoutParams(0, -2, 1));

        det.addView(infoRow);
        card.addView(det, new LinearLayout.LayoutParams(0, -2, 1));

        // Star indicator
        TextView star = new TextView(context);
        star.setText(repository.isFavorite(c.id) ? "★" : "☆");
        star.setTextSize(14);
        star.setTextColor(repository.isFavorite(c.id) ? 0xFFFFD700 : 0xFF75788E);
        card.addView(star);

        card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlay(c, allCurrentMatches, allCurrentMatches.indexOf(c));
            }
        });

        card.setOnFocusChangeListener((v, f) -> {
            if (f) {
                selectForPreview(c);
            }
            bg.setFocused(f);
        });

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = dp(context, 6);
        return card;
    }

    private void selectForPreview(Channel c) {
        currentSelectedChannel = c;
        previewTitle.setText(c.name);

        MediaKind kind = MediaKind.classify(c);
        renderPreviewTags(kind, c.group);

        previewMeta.setText((kind == MediaKind.LIVE ? "En directo" : "2026") + "  •  " + (c.group.isEmpty() ? "General" : c.group) + "  •  HD");
        previewOverview.setText(AppStrings.isEnglish(context) ?
                "High quality broadcast available in your local IPTV library." :
                "Emisión en alta calidad disponible en tu biblioteca IPTV local.");

        if (!c.logoUrl.isEmpty()) {
            LogoLoader.load(c.logoUrl, previewBackdrop);
        } else {
            previewBackdrop.setImageBitmap(Artwork.get(context,
                    kind == MediaKind.LIVE ? R.drawable.category_live :
                            (kind == MediaKind.MOVIE ? R.drawable.category_movies : R.drawable.category_series), 400));
        }

        updateFavState();
        buildSeasonsOrEpisodesBlock(c, kind);

        if (TmdbClient.hasApiKey(context) && kind != MediaKind.LIVE) {
            new Thread(() -> {
                TmdbClient.Details d = TmdbClient.searchAndGetDetails(context, c.name, kind == MediaKind.MOVIE, AppStrings.getLang(context));
                if (d != null) {
                    post(() -> {
                        if (currentSelectedChannel == c) {
                            if (d.overview != null && !d.overview.isEmpty()) previewOverview.setText(d.overview);
                            if (d.backdropUrl != null) LogoLoader.load(d.backdropUrl, previewBackdrop);
                            else if (d.posterUrl != null) LogoLoader.load(d.posterUrl, previewBackdrop);
                        }
                    });
                }
            }).start();
        }
    }

    private void renderPreviewTags(MediaKind kind, String group) {
        previewTagsRow.removeAllViews();
        String[] tags = {
                kind == MediaKind.LIVE ? "En directo" : (kind == MediaKind.MOVIE ? "Película" : "Serie"),
                group.isEmpty() ? "General" : group
        };
        for (String t : tags) {
            TextView tv = new TextView(context);
            tv.setText(t);
            tv.setTextSize(9);
            tv.setTextColor(0xFFCACBD4);
            tv.setPadding(dp(context, 7), dp(context, 2), dp(context, 7), dp(context, 2));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF262A3C);
            bg.setCornerRadius(dp(context, 6));
            tv.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(context, 5);
            previewTagsRow.addView(tv, lp);
        }
    }

    private void buildSeasonsOrEpisodesBlock(Channel c, MediaKind kind) {
        seasonsBlock.removeAllViews();
        nextEpisodeBlock.removeAllViews();

        if (kind == MediaKind.SERIES) {
            TextView label = new TextView(context);
            label.setText(AppStrings.isEnglish(context) ? "Seasons" : "Temporadas");
            label.setTextSize(11);
            label.setTextColor(0xFFFFFFFF);
            label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            label.setPadding(0, 0, 0, dp(context, 6));
            seasonsBlock.addView(label);

            LinearLayout seasonsRow = new LinearLayout(context);
            seasonsRow.setOrientation(HORIZONTAL);
            String[][] sData = {{"T1", "10 ep"}, {"T2", "12 ep"}, {"T3", "12 ep"}};
            for (int i = 0; i < sData.length; i++) {
                LinearLayout pill = new LinearLayout(context);
                pill.setOrientation(VERTICAL);
                pill.setGravity(Gravity.CENTER);
                pill.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
                GradientDrawable pbg = new GradientDrawable();
                pbg.setColor(i == 2 ? 0xFFFFFFFF : 0xFF202334);
                pbg.setCornerRadius(dp(context, 8));
                pill.setBackground(pbg);

                TextView st = new TextView(context);
                st.setText(sData[i][0]);
                st.setTextSize(11);
                st.setTextColor(i == 2 ? 0xFF14151B : 0xFFFFFFFF);
                st.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                pill.addView(st);

                TextView sc = new TextView(context);
                sc.setText(sData[i][1]);
                sc.setTextSize(8);
                sc.setTextColor(i == 2 ? 0xFF14151B : 0xFF8F91A2);
                pill.addView(sc);

                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1);
                if (i > 0) plp.leftMargin = dp(context, 6);
                seasonsRow.addView(pill, plp);
            }
            seasonsBlock.addView(seasonsRow);
        }

        // Card "A continuación"
        LinearLayout nextCard = new LinearLayout(context);
        nextCard.setOrientation(VERTICAL);
        GradientDrawable ncBg = new GradientDrawable();
        ncBg.setColor(0xFF1E2130);
        ncBg.setCornerRadius(dp(context, 8));
        nextCard.setBackground(ncBg);
        nextCard.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));

        LinearLayout ncHead = new LinearLayout(context);
        ncHead.setOrientation(HORIZONTAL);
        ncHead.setGravity(Gravity.CENTER_VERTICAL);
        LineIcon listIcon = new LineIcon(context, "list");
        listIcon.setColor(0xFF8F91A2);
        ncHead.addView(listIcon, new LinearLayout.LayoutParams(dp(context, 12), dp(context, 12)));

        TextView ncTitle = new TextView(context);
        ncTitle.setText(AppStrings.isEnglish(context) ? "Next" : "A continuación");
        ncTitle.setTextSize(10);
        ncTitle.setTextColor(0xFFCACBD4);
        ncTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        ncTitle.setPadding(dp(context, 6), 0, 0, 0);
        ncHead.addView(ncTitle);
        nextCard.addView(ncHead);

        LinearLayout nextDetail = new LinearLayout(context);
        nextDetail.setOrientation(HORIZONTAL);
        nextDetail.setGravity(Gravity.CENTER_VERTICAL);
        nextDetail.setPadding(0, dp(context, 6), 0, 0);

        TextView nextEp = new TextView(context);
        nextEp.setText(kind == MediaKind.SERIES ? "T3 - Episodio 1" : "Próximo contenido");
        nextEp.setTextSize(11);
        nextEp.setTextColor(0xFFFFFFFF);
        nextEp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        nextDetail.addView(nextEp, new LinearLayout.LayoutParams(0, -2, 1));

        LineIcon playCircle = new LineIcon(context, "play");
        playCircle.setColor(0xFFCACBD4);
        nextDetail.addView(playCircle, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        nextCard.addView(nextDetail);
        nextEpisodeBlock.addView(nextCard);
    }

    private void updateFavState() {
        if (currentSelectedChannel != null) {
            boolean fav = repository.isFavorite(currentSelectedChannel.id);
            btnFav.setText(fav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardado") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        }
    }

    private void clearPreview() {
        currentSelectedChannel = null;
        previewTitle.setText("");
        previewTagsRow.removeAllViews();
        previewMeta.setText("");
        previewOverview.setText(AppStrings.isEnglish(context) ? "No content found" : "No se encontraron contenidos");
        previewBackdrop.setImageDrawable(null);
        seasonsBlock.removeAllViews();
        nextEpisodeBlock.removeAllViews();
    }

    private List<String> getRecentSearches() {
        List<String> list = new ArrayList<>();
        String raw = prefs.getString("history", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            } catch (Exception ignored) {}
        }
        return list;
    }

    private void saveRecentSearch(String q) {
        if (q == null || q.trim().isEmpty()) return;
        List<String> list = getRecentSearches();
        list.remove(q);
        list.add(0, q);
        if (list.size() > 8) list = list.subList(0, 8);
        JSONArray arr = new JSONArray(list);
        prefs.edit().putString("history", arr.toString()).apply();
        renderRecentSearches();
    }

    public View getSearchField() {
        return searchField;
    }
}
