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
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.data.PlaylistRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private static final class Matches {
        final List<Channel> all = new ArrayList<>();
        final List<Channel> live = new ArrayList<>();
        final List<Channel> series = new ArrayList<>();
        final List<Channel> movies = new ArrayList<>();
        final List<String> trends = new ArrayList<>();
    }

    private final PreviewMetadata metadata = new PreviewMetadata();
    private final CatalogTask searchTask = new CatalogTask();
    private List<Channel> allCurrentMatches = Collections.emptyList();
    private ResultSection liveSection, seriesSection, movieSection;
    private RecyclerView resultsList;

    // UI elements
    private EditText searchField;
    private LinearLayout filterPillsContainer;
    private LinearLayout recentSearchesContainer;
    private LinearLayout trendingContainer;
    private TextView resultsHeaderSubtitle;

    // Preview panel
    private ImageView previewBackdrop;
    private TextView previewTitle;
    private LinearLayout previewTagsRow;
    private TextView previewMeta;
    private TextView previewOverview;
    private TextView btnPlay;
    private TextView btnFav;
    private TextView btnInfo;

    private Channel currentSelectedChannel;

    public SearchViewLayout(Context context, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.listener = listener;
        this.prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE);

        setOrientation(VERTICAL);
        buildUi();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        performSearch(currentQuery);
    }

    @Override protected void onDetachedFromWindow() {
        searchTask.cancel();
        metadata.cancel();
        super.onDetachedFromWindow();
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


        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 180), -1);
        lp.rightMargin = dp(context, 10);
        body.addView(colA, lp);
    }

    private void renderRecentSearches() {
        recentSearchesContainer.removeAllViews();
        List<String> recents = getRecentSearches();

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

    private void renderTrending(List<String> trends) {
        trendingContainer.removeAllViews();

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

        boolean english = AppStrings.isEnglish(context);
        liveSection = new ResultSection("tv", english ? "Live TV Channels" : "Canales en vivo");
        seriesSection = new ResultSection("series", "Series");
        movieSection = new ResultSection("movie", english ? "Movies" : "Películas");
        resultsList = new RecyclerView(context);
        resultsList.setLayoutManager(new LinearLayoutManager(context));
        resultsList.setItemAnimator(null);
        resultsList.setPadding(0, dp(context, 10), 0, 0);
        resultsList.setAdapter(new ConcatAdapter(liveSection, seriesSection, movieSection));
        colB.addView(resultsList, new LinearLayout.LayoutParams(-1, 0, 1));

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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 260), -1);
        body.addView(colC, lp);
    }

    private void performSearch(String q) {
        currentQuery = q != null ? q.trim() : "";
        String norm = currentQuery.toLowerCase(Locale.ROOT);
        int filter = filterType;
        boolean needTrends = trendingContainer.getChildCount() == 0;
        resultsHeaderSubtitle.setText(AppStrings.isEnglish(context) ? "Searching…" : "Buscando…");
        searchTask.submit(() -> {
            Matches result = new Matches();
            Set<String> favorites = filter == 4 ? repository.getFavoriteIds() : Collections.emptySet();
            for (Channel c : repository.getAllChannels()) {
                if (Thread.currentThread().isInterrupted()) break;
                if (needTrends && result.trends.size() < 5 && !c.group.isEmpty()
                        && !c.group.equalsIgnoreCase("Todos") && !result.trends.contains(c.group)) {
                    result.trends.add(c.group);
                }
                if (filter == 4 && !favorites.contains(c.id)) continue;
                if (!norm.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(norm)
                        && !c.group.toLowerCase(Locale.ROOT).contains(norm)) continue;
                MediaKind kind = c.getKind();
                if (filter == 1 && kind != MediaKind.LIVE || filter == 2 && kind != MediaKind.MOVIE
                        || filter == 3 && kind != MediaKind.SERIES) continue;
                result.all.add(c);
                if (kind == MediaKind.LIVE) result.live.add(c);
                else if (kind == MediaKind.SERIES) result.series.add(c);
                else result.movies.add(c);
            }
            return result;
        }, result -> {
            allCurrentMatches = result.all;
            liveSection.update(result.live);
            seriesSection.update(result.series);
            movieSection.update(result.movies);
            if (needTrends) renderTrending(result.trends);
            int total = result.all.size();
            boolean english = AppStrings.isEnglish(context);
            resultsHeaderSubtitle.setText(currentQuery.isEmpty()
                    ? total + (english ? " items in library" : " contenidos en biblioteca")
                    : total + (english ? " results for «" : " resultados para «") + currentQuery + "»");
            if (total > 0) {
                resultsList.scrollToPosition(0);
                selectForPreview(result.all.get(0));
            } else clearPreview();
        });
    }

    private final class ResultSection extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final String icon, label;
        List<Channel> items = Collections.emptyList();

        ResultSection(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        void update(List<Channel> matches) {
            items = matches;
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return items.isEmpty() ? 0 : items.size() + 1; }
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return type == 0 ? new GroupHeader(icon) : new ResultCard();
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == 0) ((GroupHeader) holder).title.setText(label + " (" + items.size() + ")");
            else ((ResultCard) holder).bind(items.get(position - 1));
        }
    }

    private final class GroupHeader extends RecyclerView.ViewHolder {
        final TextView title;

        GroupHeader(String iconKind) {
            super(new LinearLayout(context));
            LinearLayout sectionHead = (LinearLayout) itemView;
            sectionHead.setOrientation(HORIZONTAL);
            sectionHead.setGravity(Gravity.CENTER_VERTICAL);
            sectionHead.setPadding(0, dp(context, 8), 0, dp(context, 6));

            LineIcon icon = new LineIcon(context, iconKind);
            icon.setColor(0xFFFFFFFF);
            sectionHead.addView(icon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

            title = new TextView(context);
            title.setTextSize(13);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            title.setPadding(dp(context, 8), 0, 0, 0);
            sectionHead.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        }
    }

    private final class ResultCard extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView name, init, typePill, extra, star;
        Channel item;

        ResultCard() {
            super(new LinearLayout(context));
            LinearLayout card = (LinearLayout) itemView;
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

            img = new ImageView(context);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            emblem.addView(img, new FrameLayout.LayoutParams(-1, -1));

            init = new TextView(context);
            init.setTextSize(14);
            init.setTextColor(0xFFCACBD4);
            init.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            init.setGravity(Gravity.CENTER);
            emblem.addView(init, new FrameLayout.LayoutParams(-1, -1));

            card.addView(emblem, new LinearLayout.LayoutParams(dp(context, 68), dp(context, 44)));

            // Details Column
            LinearLayout det = new LinearLayout(context);
            det.setOrientation(VERTICAL);
            det.setPadding(dp(context, 10), 0, dp(context, 8), 0);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            name = new TextView(context);
            name.setTextSize(13);
            name.setTextColor(0xFFFFFFFF);
            name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));


            det.addView(titleRow);

            // Tags & Info line
            LinearLayout infoRow = new LinearLayout(context);
            infoRow.setOrientation(HORIZONTAL);
            infoRow.setGravity(Gravity.CENTER_VERTICAL);
            infoRow.setPadding(0, dp(context, 4), 0, 0);

            typePill = new TextView(context);
            typePill.setTextSize(9);
            typePill.setTextColor(0xFF8F91A2);
            infoRow.addView(typePill);

            extra = new TextView(context);
            extra.setTextSize(9);
            extra.setTextColor(0xFF75788E);
            extra.setSingleLine(true);
            extra.setEllipsize(TextUtils.TruncateAt.END);
            infoRow.addView(extra, new LinearLayout.LayoutParams(0, -2, 1));

            det.addView(infoRow);
            card.addView(det, new LinearLayout.LayoutParams(0, -2, 1));

            // Star indicator
            star = new TextView(context);
            star.setTextSize(14);
            card.addView(star);

            card.setOnClickListener(v -> {
                if (listener != null && item != null) {
                    int index = allCurrentMatches.indexOf(item);
                    if (index >= 0) listener.onPlay(item, allCurrentMatches, index);
                }
            });

            card.setOnFocusChangeListener((v, f) -> {
                if (f && item != null) {
                    selectForPreview(item);
                }
                bg.setFocused(f);
            });

        }

        void bind(Channel c) {
            item = c;
            name.setText(c.name);
            MediaKind kind = c.getKind();
            typePill.setText(kind == MediaKind.LIVE ? "Canal en vivo" : kind == MediaKind.MOVIE ? "Película" : "Serie");
            extra.setText(" • " + (c.group.isEmpty() ? "General" : c.group));
            boolean favorite = repository.isFavorite(c.id);
            star.setText(favorite ? "★" : "☆");
            star.setTextColor(favorite ? 0xFFFFD700 : 0xFF75788E);
            LogoLoader.load(c.logoUrl, img);
            init.setVisibility(c.logoUrl.isEmpty() ? VISIBLE : GONE);
            if (c.logoUrl.isEmpty()) init.setText(c.name.substring(0, Math.min(2, c.name.length())).toUpperCase(Locale.ROOT));
            if (itemView.hasFocus()) selectForPreview(c);
        }
    }

    private void selectForPreview(Channel c) {
        currentSelectedChannel = c;
        previewTitle.setText(c.name);

        MediaKind kind = c.getKind();
        renderPreviewTags(kind, c.group);

        previewMeta.setText(c.group);
        previewOverview.setText(AppStrings.isEnglish(context) ?
                "High quality broadcast available in your local IPTV library." :
                "Emisión en alta calidad disponible en tu biblioteca IPTV local.");

        if (!c.logoUrl.isEmpty()) {
            LogoLoader.load(c.logoUrl, previewBackdrop);
        } else {
            LogoLoader.clear(previewBackdrop);
            previewBackdrop.setImageBitmap(Artwork.get(context,
                    kind == MediaKind.LIVE ? R.drawable.category_live :
                            (kind == MediaKind.MOVIE ? R.drawable.category_movies : R.drawable.category_series), 400));
        }

        updateFavState();

        if (kind == MediaKind.LIVE) metadata.cancel();
        else metadata.load(context, c.name, kind == MediaKind.MOVIE, details -> {
            if (details.overview != null && !details.overview.isEmpty()) previewOverview.setText(details.overview);
            if (details.backdropUrl != null) LogoLoader.load(details.backdropUrl, previewBackdrop);
            else if (details.posterUrl != null) LogoLoader.load(details.posterUrl, previewBackdrop);
        });
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

    private void updateFavState() {
        if (currentSelectedChannel != null) {
            boolean fav = repository.isFavorite(currentSelectedChannel.id);
            btnFav.setText(fav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardado") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        }
    }

    private void clearPreview() {
        metadata.cancel();
        currentSelectedChannel = null;
        previewTitle.setText("");
        previewTagsRow.removeAllViews();
        previewMeta.setText("");
        previewOverview.setText(AppStrings.isEnglish(context) ? "No content found" : "No se encontraron contenidos");
        LogoLoader.clear(previewBackdrop);
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
