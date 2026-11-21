package tv.lunvoryx.player.ui;

import android.content.Context;
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
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.tmdb.TmdbClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class MediaBrowseView extends LinearLayout {
    public interface Listener {
        void onPlay(Channel channel, List<Channel> list, int index);
        void onToggleFavorite(Channel channel);
    }

    private static class CategoryItem {
        final String id;
        final String label;
        final String icon;
        int count;
        CategoryItem(String id, String label, String icon, int count) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.count = count;
        }
    }

    private final Context context;
    private final PlaylistRepository repository;
    private final boolean isMovies;
    private final Listener listener;

    private final List<Channel> fullItems = new ArrayList<>();
    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<Channel> filteredItems = new ArrayList<>();

    private String selectedCatId = "all";
    private String selectedCatLabel = "Todas";
    private String searchQuery = "";
    private int sortMode = 0; // 0: Añadidos (default), 1: A-Z, 2: Z-A

    // UI elements
    private LinearLayout categoriesContainer;
    private TextView catHeaderTitle;
    private TextView catHeaderBadge;
    private RecyclerView posterGrid;
    private PosterAdapter posterAdapter;

    // Preview Panel
    private ImageView previewPoster;
    private TextView previewTitle;
    private TextView previewMeta;
    private TextView previewOverview;
    private TextView btnPlay;
    private TextView btnFav;
    private TextView previewDirector;
    private TextView previewCast;
    private TextView previewAudio;
    private TextView previewSubtitles;

    private Channel currentSelectedChannel;
    private final Map<String, TmdbClient.Details> tmdbCache = new HashMap<>();

    public MediaBrowseView(Context context, PlaylistRepository repository, boolean isMovies, Listener listener) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.isMovies = isMovies;
        this.listener = listener;

        setOrientation(VERTICAL);
        buildUi();
        loadData();
    }

    private void buildUi() {
        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

        // 1. Header: Icon + Title + Subtitle
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(context, 10));

        LineIcon sectionIcon = new LineIcon(context, isMovies ? "movie" : "series");
        sectionIcon.setColor(0xFFF8F8FB);
        header.addView(sectionIcon, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(VERTICAL);
        titleCol.setPadding(dp(context, 14), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(isMovies ? (AppStrings.isEnglish(context) ? "Movies" : "Películas") : (AppStrings.isEnglish(context) ? "Series" : "Series"));
        title.setTextSize(26);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText(isMovies ?
                (AppStrings.isEnglish(context) ? "Thousands of movies in high quality, organized by categories" : "Miles de películas en alta calidad, organizadas por categorías") :
                (AppStrings.isEnglish(context) ? "Thousands of series in high quality, organized by categories" : "Miles de series en alta calidad, organizadas por categorías"));
        sub.setTextSize(12);
        sub.setTextColor(0xFF8F91A2);
        sub.setPadding(0, dp(context, 2), 0, 0);
        titleCol.addView(sub);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
        addView(header);

        // 2. Three columns body
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        // COLUMN A: Categorías
        buildCategoriesColumn(body);

        // COLUMN B: Grid de contenido
        buildGridColumn(body);

        // COLUMN C: Panel Vista Previa
        buildPreviewColumn(body);
    }

    private void buildCategoriesColumn(LinearLayout body) {
        LinearLayout colA = new LinearLayout(context);
        colA.setOrientation(VERTICAL);
        colA.setBackground(TvStyle.squircleCard(context, 18));
        colA.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 14));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(context, 4), 0, 0, dp(context, 12));

        LineIcon catIcon = new LineIcon(context, "categories");
        catIcon.setColor(0xFFF8F8FB);
        head.addView(catIcon, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        TextView catTitle = new TextView(context);
        catTitle.setText(AppStrings.isEnglish(context) ? "Categories" : "Categorías");
        catTitle.setTextSize(15);
        catTitle.setTextColor(0xFFFFFFFF);
        catTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        catTitle.setPadding(dp(context, 10), 0, 0, 0);
        head.addView(catTitle);
        colA.addView(head);

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        categoriesContainer = new LinearLayout(context);
        categoriesContainer.setOrientation(VERTICAL);
        scroller.addView(categoriesContainer);
        colA.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 200), -1);
        lp.rightMargin = dp(context, 10);
        body.addView(colA, lp);
    }

    private void buildGridColumn(LinearLayout body) {
        LinearLayout colB = new LinearLayout(context);
        colB.setOrientation(VERTICAL);
        colB.setBackground(TvStyle.squircleCard(context, 18));
        colB.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 10));

        // Subheader: Folder Icon + Category Name + Pill Count
        LinearLayout catHeader = new LinearLayout(context);
        catHeader.setOrientation(HORIZONTAL);
        catHeader.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon fIcon = new LineIcon(context, "folder");
        fIcon.setColor(0xFFFFFFFF);
        catHeader.addView(fIcon, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        catHeaderTitle = new TextView(context);
        catHeaderTitle.setText("Todas");
        catHeaderTitle.setTextSize(15);
        catHeaderTitle.setTextColor(0xFFFFFFFF);
        catHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        catHeaderTitle.setSingleLine(true);
        catHeaderTitle.setEllipsize(TextUtils.TruncateAt.END);
        catHeaderTitle.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        catHeader.addView(catHeaderTitle, new LinearLayout.LayoutParams(0, -2, 1));

        catHeaderBadge = new TextView(context);
        catHeaderBadge.setText("0");
        catHeaderBadge.setTextSize(11);
        catHeaderBadge.setTextColor(0xFFB5B6C2);
        catHeaderBadge.setPadding(dp(context, 10), dp(context, 3), dp(context, 10), dp(context, 3));
        catHeaderBadge.setBackground(TvStyle.squircleCard(context, 10));
        catHeader.addView(catHeaderBadge);
        colB.addView(catHeader);

        // Search bar + Sort pill
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(context, 10), 0, dp(context, 10));

        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        searchBox.setBackground(TvStyle.squircleInput(context, 12));

        LineIcon sIcon = new LineIcon(context, "search");
        sIcon.setColor(0xFF8F91A2);
        searchBox.addView(sIcon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        EditText sInput = new EditText(context);
        sInput.setHint(AppStrings.isEnglish(context) ? "Search in category…" : "Buscar en esta categoría…");
        sInput.setHintTextColor(0xFF75788E);
        sInput.setTextColor(0xFFFFFFFF);
        sInput.setTextSize(12);
        sInput.setBackground(null);
        sInput.setSingleLine(true);
        sInput.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        sInput.setOnEditorActionListener((v, actionId, event) -> {
            searchQuery = sInput.getText().toString().trim();
            applyFilterAndSort();
            return true;
        });
        searchBox.addView(sInput, new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(searchBox, new LinearLayout.LayoutParams(0, dp(context, 34), 1));

        TextView sortBtn = new TextView(context);
        sortBtn.setText("⇅ Orden: Añadidos ▾");
        sortBtn.setTextSize(11);
        sortBtn.setTextColor(0xFFCACBD4);
        sortBtn.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        SquircleGlassDrawable sortBg = TvStyle.squircleCard(context, 12);
        sortBtn.setBackground(sortBg);
        sortBtn.setFocusable(true);
        sortBtn.setClickable(true);
        sortBtn.setOnClickListener(v -> toggleSort(sortBtn));
        sortBtn.setOnFocusChangeListener((v, f) -> {
            sortBtn.setTextColor(f ? 0xFF14151B : 0xFFCACBD4);
            sortBg.setSolidColor(f ? 0xFFFFFFFF : 0);
            sortBg.setFocused(f);
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(context, 34));
        sp.leftMargin = dp(context, 8);
        tools.addView(sortBtn, sp);
        colB.addView(tools);

        // Content Grid (5 columns of vertical movie posters)
        posterGrid = new RecyclerView(context);
        posterGrid.setLayoutManager(new GridLayoutManager(context, 4));
        posterGrid.setItemAnimator(null);
        posterGrid.setClipToPadding(false);
        posterAdapter = new PosterAdapter();
        posterGrid.setAdapter(posterAdapter);
        colB.addView(posterGrid, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.rightMargin = dp(context, 10);
        body.addView(colB, lp);
    }

    private void toggleSort(TextView sortBtn) {
        sortMode = (sortMode + 1) % 3;
        String label = "⇅ Orden: Añadidos ▾";
        if (sortMode == 1) label = "⇅ Orden: A-Z ▾";
        if (sortMode == 2) label = "⇅ Orden: Z-A ▾";
        sortBtn.setText(label);
        applyFilterAndSort();
    }

    private void buildPreviewColumn(LinearLayout body) {
        LinearLayout colC = new LinearLayout(context);
        colC.setOrientation(VERTICAL);
        colC.setBackground(TvStyle.squircleCard(context, 18));
        colC.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(context, 10));

        LineIcon pIcon = new LineIcon(context, "player");
        pIcon.setColor(0xFFF8F8FB);
        head.addView(pIcon, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        TextView pTitle = new TextView(context);
        pTitle.setText(AppStrings.isEnglish(context) ? "Preview" : "Vista previa");
        pTitle.setTextSize(15);
        pTitle.setTextColor(0xFFFFFFFF);
        pTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pTitle.setPadding(dp(context, 8), 0, 0, 0);
        head.addView(pTitle);
        colC.addView(head);

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        scroller.addView(content);
        colC.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        // Poster / Backdrop 16:9 rounded image
        previewPoster = new ImageView(context);
        previewPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewPoster.setBackground(TvStyle.squircleSolid(context, 12, 0xFF202332));
        previewPoster.setClipToOutline(true);
        content.addView(previewPoster, new LinearLayout.LayoutParams(-1, dp(context, 130)));

        // Title
        previewTitle = new TextView(context);
        previewTitle.setText("");
        previewTitle.setTextSize(17);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        previewTitle.setPadding(0, dp(context, 8), 0, dp(context, 4));
        content.addView(previewTitle);

        // Tags / Metadata (2026  4K  2 h 46 min  Ciencia ficción)
        previewMeta = new TextView(context);
        previewMeta.setText("");
        previewMeta.setTextSize(11);
        previewMeta.setTextColor(0xFF9EA0B2);
        content.addView(previewMeta);

        // Synopsis
        previewOverview = new TextView(context);
        previewOverview.setText("");
        previewOverview.setTextSize(11);
        previewOverview.setTextColor(0xFFB5B6C4);
        previewOverview.setMaxLines(4);
        previewOverview.setEllipsize(TextUtils.TruncateAt.END);
        previewOverview.setLineSpacing(dp(context, 2), 1);
        previewOverview.setPadding(0, dp(context, 6), 0, dp(context, 10));
        content.addView(previewOverview);

        // Buttons row (Ver película  /  Favorito)
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(HORIZONTAL);

        btnPlay = new TextView(context);
        btnPlay.setText("▶  " + (isMovies ? (AppStrings.isEnglish(context) ? "Play movie" : "Ver película") : (AppStrings.isEnglish(context) ? "Play series" : "Ver serie")));
        btnPlay.setTextSize(12);
        btnPlay.setTextColor(0xFF14151B);
        btnPlay.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnPlay.setGravity(Gravity.CENTER);
        btnPlay.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        btnPlay.setBackground(TvStyle.squircleSolid(context, 12, 0xFFFFFFFF));
        btnPlay.setFocusable(true);
        btnPlay.setClickable(true);
        btnPlay.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onPlay(currentSelectedChannel, filteredItems, filteredItems.indexOf(currentSelectedChannel));
            }
        });
        btnRow.addView(btnPlay, new LinearLayout.LayoutParams(0, -2, 1));

        btnFav = new TextView(context);
        btnFav.setText("☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        btnFav.setTextSize(12);
        btnFav.setTextColor(0xFFF8F8FB);
        btnFav.setGravity(Gravity.CENTER);
        btnFav.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        SquircleGlassDrawable favBg = TvStyle.squircleCard(context, 12);
        btnFav.setBackground(favBg);
        btnFav.setFocusable(true);
        btnFav.setClickable(true);
        btnFav.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onToggleFavorite(currentSelectedChannel);
                updateFavButtonState();
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
        content.addView(btnRow);

        // Technical details metadata rows
        content.addView(buildMetaRow("director", AppStrings.isEnglish(context) ? "Director:" : "Director:", previewDirector = createMetaValue()));
        content.addView(buildMetaRow("cast", AppStrings.isEnglish(context) ? "Cast:" : "Reparto:", previewCast = createMetaValue()));
        content.addView(buildMetaRow("audio", AppStrings.isEnglish(context) ? "Audio:" : "Audio:", previewAudio = createMetaValue()));
        content.addView(buildMetaRow("subtitles", AppStrings.isEnglish(context) ? "Subtitles:" : "Subtítulos:", previewSubtitles = createMetaValue()));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 248), -1);
        body.addView(colC, lp);
    }

    private TextView createMetaValue() {
        TextView tv = new TextView(context);
        tv.setTextSize(11);
        tv.setTextColor(0xFFCACBD4);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private LinearLayout buildMetaRow(String iconKind, String labelText, TextView valueView) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 6), 0, 0);

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFF8F91A2);
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(11);
        label.setTextColor(0xFF8F91A2);
        label.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        row.addView(label);

        row.addView(valueView, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void loadData() {
        fullItems.clear();
        for (Channel c : repository.getAllChannels()) {
            MediaKind kind = MediaKind.classify(c);
            if (isMovies && kind == MediaKind.MOVIE) fullItems.add(c);
            else if (!isMovies && kind == MediaKind.SERIES) fullItems.add(c);
        }

        buildCategoryList();
        renderCategories();
        applyFilterAndSort();
    }

    private void buildCategoryList() {
        categories.clear();
        Set<String> favIds = repository.getFavoriteIds();
        List<String> recents = repository.getRecentIds();

        // 1. Todas
        categories.add(new CategoryItem("all", AppStrings.isEnglish(context) ? "All" : "Todas", "all_grid", fullItems.size()));

        // 2. Favoritos
        int favCount = 0;
        for (Channel c : fullItems) if (favIds.contains(c.id)) favCount++;
        categories.add(new CategoryItem("favorites", AppStrings.isEnglish(context) ? "Favorites" : "Favoritos", "star", favCount));

        // 3. Seguir viendo (Reanudar para ver)
        int resumeCount = 0;
        for (Channel c : fullItems) if (recents.contains(c.id)) resumeCount++;
        categories.add(new CategoryItem("resume", AppStrings.isEnglish(context) ? "Keep watching" : "Seguir viendo", "resume", resumeCount));

        // Group counts from playlist
        Map<String, Integer> groupCounts = new HashMap<>();
        for (Channel c : fullItems) {
            String g = (c.group == null || c.group.trim().isEmpty()) ? (AppStrings.isEnglish(context) ? "General" : "General") : c.group.trim();
            Integer cnt = groupCounts.get(g);
            groupCounts.put(g, (cnt != null ? cnt : 0) + 1);
        }

        TreeSet<String> sortedGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedGroups.addAll(groupCounts.keySet());

        for (String g : sortedGroups) {
            categories.add(new CategoryItem("group:" + g, g, "folder", groupCounts.get(g)));
        }
    }

    private void renderCategories() {
        categoriesContainer.removeAllViews();
        for (CategoryItem item : categories) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
            row.setFocusable(true);
            row.setClickable(true);

            LineIcon icon = new LineIcon(context, item.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

            TextView label = new TextView(context);
            label.setText(item.label);
            label.setTextSize(12);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(context, 8), 0, dp(context, 6), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            TextView count = new TextView(context);
            count.setText(String.valueOf(item.count));
            count.setTextSize(10);
            count.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(0xFF282A3A);
            cbg.setCornerRadius(dp(context, 10));
            count.setBackground(cbg);
            row.addView(count);

            boolean isSelected = selectedCatId.equals(item.id);
            applyCategoryRowStyle(row, icon, label, count, isSelected, false);

            row.setOnClickListener(v -> {
                selectedCatId = item.id;
                selectedCatLabel = item.label;
                renderCategories();
                applyFilterAndSort();
            });

            row.setOnFocusChangeListener((v, focused) -> {
                applyCategoryRowStyle(row, icon, label, count, selectedCatId.equals(item.id), focused);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(context, 4);
            categoriesContainer.addView(row, lp);
        }
    }

    private void applyCategoryRowStyle(LinearLayout row, LineIcon icon, TextView label, TextView count, boolean selected, boolean focused) {
        if (selected || focused) {
            row.setBackground(TvStyle.squircleSolid(context, 12, selected ? (focused ? 0xFFFFFFFF : 0xFFE0E0E6) : 0xFFFFFFFF));
            icon.setColor(0xFF14151B);
            label.setTextColor(0xFF14151B);
            label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            count.setTextColor(0xFF14151B);
        } else {
            row.setBackground(null);
            icon.setColor(0xFF8F91A2);
            label.setTextColor(0xFFCACBD4);
            label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            count.setTextColor(0xFFB5B6C2);
        }
    }

    private void applyFilterAndSort() {
        filteredItems.clear();
        Set<String> favIds = repository.getFavoriteIds();
        List<String> recents = repository.getRecentIds();

        for (Channel c : fullItems) {
            if ("favorites".equals(selectedCatId) && !favIds.contains(c.id)) continue;
            if ("resume".equals(selectedCatId) && !recents.contains(c.id)) continue;
            if (selectedCatId.startsWith("group:")) {
                String groupName = selectedCatId.substring(6);
                if (!groupName.equalsIgnoreCase(c.group)) continue;
            }
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase(Locale.ROOT);
                if (!c.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            }
            filteredItems.add(c);
        }

        if (sortMode == 1) {
            Collections.sort(filteredItems, (a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 2) {
            Collections.sort(filteredItems, (a, b) -> b.name.compareToIgnoreCase(a.name));
        }

        catHeaderTitle.setText(selectedCatLabel);
        catHeaderBadge.setText(String.valueOf(filteredItems.size()) + (isMovies ? (AppStrings.isEnglish(context) ? " movies" : " películas") : (AppStrings.isEnglish(context) ? " series" : " series")));
        posterAdapter.notifyDataSetChanged();

        if (!filteredItems.isEmpty()) {
            selectForPreview(filteredItems.get(0));
        } else {
            clearPreview();
        }
    }

    private void selectForPreview(Channel c) {
        currentSelectedChannel = c;
        previewTitle.setText(c.name);

        String year = extractYear(c.name);
        previewMeta.setText((year.isEmpty() ? "2026" : year) + "  •  " + (c.group.isEmpty() ? (isMovies ? "Película" : "Serie") : c.group));
        previewOverview.setText(AppStrings.isEnglish(context) ? "Available in your local library." : "Disponible en tu biblioteca local.");
        previewDirector.setText("—");
        previewCast.setText("—");
        previewAudio.setText("Español / Original");
        previewSubtitles.setText("Español, Inglés");

        updateFavButtonState();

        if (!c.logoUrl.isEmpty()) {
            LogoLoader.load(c.logoUrl, previewPoster);
        } else {
            previewPoster.setImageBitmap(Artwork.get(context, isMovies ? R.drawable.category_movies : R.drawable.category_series, 400));
        }

        // Enriquecer con TMDB si hay API Key
        if (TmdbClient.hasApiKey(context)) {
            fetchTmdbData(c);
        }
    }

    private void fetchTmdbData(Channel c) {
        if (tmdbCache.containsKey(c.id)) {
            applyTmdbDetails(tmdbCache.get(c.id));
            return;
        }

        new Thread(() -> {
            TmdbClient.Details d = TmdbClient.searchAndGetDetails(context, c.name, isMovies, AppStrings.getLang(context));
            if (d != null) {
                tmdbCache.put(c.id, d);
                post(() -> {
                    if (currentSelectedChannel == c) {
                        applyTmdbDetails(d);
                    }
                });
            }
        }).start();
    }

    private void applyTmdbDetails(TmdbClient.Details d) {
        if (d.overview != null && !d.overview.isEmpty()) {
            previewOverview.setText(d.overview);
        }
        if (d.backdropUrl != null) {
            LogoLoader.load(d.backdropUrl, previewPoster);
        } else if (d.posterUrl != null) {
            LogoLoader.load(d.posterUrl, previewPoster);
        }
        if (d.releaseDate != null && !d.releaseDate.isEmpty()) {
            previewMeta.setText(d.releaseDate.substring(0, Math.min(4, d.releaseDate.length())) + "  •  ★ " + String.format(Locale.ROOT, "%.1f", d.rating) + "  •  " + (isMovies ? "Película" : "Serie"));
        }
        if (!d.cast.isEmpty()) {
            previewCast.setText(android.text.TextUtils.join(", ", d.cast));
        }
    }

    private void updateFavButtonState() {
        if (currentSelectedChannel != null) {
            boolean isFav = repository.isFavorite(currentSelectedChannel.id);
            btnFav.setText(isFav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardada") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        }
    }

    private void clearPreview() {
        currentSelectedChannel = null;
        previewTitle.setText("");
        previewMeta.setText("");
        previewOverview.setText(AppStrings.isEnglish(context) ? "No content found in this category." : "No hay contenido en esta categoría.");
        previewPoster.setImageDrawable(null);
    }

    private static String extractYear(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19\\d\\d|20\\d\\d)").matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    // ADAPTER & VIEW HOLDER FOR VERTICAL POSTERS (5 per row)
    private final class PosterAdapter extends RecyclerView.Adapter<PosterHolder> {
        @Override public PosterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PosterHolder();
        }
        @Override public void onBindViewHolder(PosterHolder holder, int position) {
            holder.bind(filteredItems.get(position));
        }
        @Override public int getItemCount() {
            return filteredItems.size();
        }
    }

    private final class PosterHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView posterImage;
        final TextView initialsText;
        final TextView titleText;
        final TextView yearText;
        Channel item;

        PosterHolder() {
            super(new FrameLayout(context));
            card = (FrameLayout) itemView;
            card.setFocusable(true);
            card.setClickable(true);
            int margin = dp(context, 4);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, dp(context, 142));
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);

            SquircleGlassDrawable cardBg = TvStyle.squircleCard(context, 12);
            card.setBackground(cardBg);
            card.setClipToOutline(true);

            posterImage = new ImageView(context);
            posterImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(posterImage, new FrameLayout.LayoutParams(-1, -1));

            // Gradient shadow on bottom of poster
            View shade = new View(context);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xEE0B0D18});
            shade.setBackground(g);
            card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            initialsText = new TextView(context);
            initialsText.setTextSize(22);
            initialsText.setTextColor(0xFFCACBD4);
            initialsText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            initialsText.setGravity(Gravity.CENTER);
            card.addView(initialsText, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout caption = new LinearLayout(context);
            caption.setOrientation(VERTICAL);
            caption.setPadding(dp(context, 6), 0, dp(context, 6), dp(context, 6));

            titleText = new TextView(context);
            titleText.setTextSize(11);
            titleText.setTextColor(0xFFFFFFFF);
            titleText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            titleText.setSingleLine(true);
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            caption.addView(titleText);

            yearText = new TextView(context);
            yearText.setTextSize(9);
            yearText.setTextColor(0xFF8F91A2);
            yearText.setSingleLine(true);
            caption.addView(yearText);

            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            card.addView(caption, cp);

            card.setOnClickListener(v -> {
                if (item != null && listener != null) {
                    listener.onPlay(item, filteredItems, filteredItems.indexOf(item));
                }
            });

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && item != null) {
                    selectForPreview(item);
                }
                cardBg.setFocused(hasFocus);
                if (hasFocus) {
                    card.setScaleX(1.05f);
                    card.setScaleY(1.05f);
                } else {
                    card.setScaleX(1.0f);
                    card.setScaleY(1.0f);
                }
            });
        }

        void bind(Channel c) {
            this.item = c;
            titleText.setText(c.name);
            String y = extractYear(c.name);
            yearText.setText(y.isEmpty() ? (c.group != null ? c.group : "") : y);

            if (c.logoUrl != null && !c.logoUrl.isEmpty()) {
                initialsText.setVisibility(GONE);
                LogoLoader.load(c.logoUrl, posterImage);
            } else {
                posterImage.setImageDrawable(null);
                initialsText.setVisibility(VISIBLE);
                String trimmed = c.name.trim();
                initialsText.setText(trimmed.length() > 2 ? trimmed.substring(0, 2).toUpperCase(Locale.ROOT) : trimmed.toUpperCase(Locale.ROOT));
            }
        }
    }
}
