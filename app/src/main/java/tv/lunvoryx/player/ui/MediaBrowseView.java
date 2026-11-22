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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashSet;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.data.SeriesItem;
import tv.lunvoryx.player.data.SeriesParser;
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
        default void onOpenDetails(Channel channel, List<Channel> list, int index) {
            onPlay(channel, list, index);
        }
        default void onOpenSeriesDetails(SeriesItem series) {}
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
    private final boolean isIntuitive;

    private final List<Channel> fullItems = new ArrayList<>();
    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<Channel> filteredItems = new ArrayList<>();

    // Series items (grouped 1 per series)
    private final List<SeriesItem> fullSeries = new ArrayList<>();
    private final List<SeriesItem> filteredSeries = new ArrayList<>();

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
    private LinearLayout loadingLayout;
    private TextView loadingText;

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
    private SeriesItem currentSelectedSeries;
    private final Map<String, TmdbClient.Details> tmdbCache = new HashMap<>();

    public MediaBrowseView(Context context, PlaylistRepository repository, boolean isMovies, Listener listener) {
        this(context, repository, isMovies, listener, false);
    }

    public MediaBrowseView(Context context, PlaylistRepository repository, boolean isMovies, Listener listener, boolean isIntuitive) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.isMovies = isMovies;
        this.listener = listener;
        this.isIntuitive = isIntuitive;

        initUI();
        loadData();
    }

    private void initUI() {
        setOrientation(HORIZONTAL);
        setBackgroundColor(0xFF060A14);

        int leftW = isIntuitive ? dp(context, 260) : dp(context, 240);
        addView(buildCategoriesColumn(), new LayoutParams(leftW, -1));

        if (isIntuitive) {
            addView(buildGridColumn(), new LayoutParams(0, -1, 1.0f));
        } else {
            addView(buildGridColumn(), new LayoutParams(0, -1, 0.62f));
            addView(buildPreviewColumn(), new LayoutParams(0, -1, 0.38f));
        }
    }

    private View buildCategoriesColumn() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(VERTICAL);
        col.setBackground(TvStyle.squircleCard(context, 16));
        col.setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 16));

        TextView heading = new TextView(context);
        heading.setText(AppStrings.isEnglish(context) ? "Categories" : "Categorías");
        heading.setTextSize(16);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        heading.setPadding(dp(context, 8), 0, 0, dp(context, 12));
        col.addView(heading);

        ScrollView scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        categoriesContainer = new LinearLayout(context);
        categoriesContainer.setOrientation(VERTICAL);
        scroller.addView(categoriesContainer, new LayoutParams(-1, -2));
        col.addView(scroller, new LayoutParams(-1, 0, 1));

        return col;
    }

    private View buildGridColumn() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(VERTICAL);
        col.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));

        // Subheader: Folder icon, Category Title, Count Badge, Search Box, Sort Dropdown
        LinearLayout subHeader = new LinearLayout(context);
        subHeader.setOrientation(HORIZONTAL);
        subHeader.setGravity(Gravity.CENTER_VERTICAL);
        subHeader.setPadding(0, 0, 0, dp(context, 12));

        LineIcon folderIcon = new LineIcon(context, isMovies ? "movies" : "series");
        folderIcon.setColor(0xFF007AFF);
        subHeader.addView(folderIcon, new LayoutParams(dp(context, 20), dp(context, 20)));

        catHeaderTitle = new TextView(context);
        catHeaderTitle.setText(selectedCatLabel);
        catHeaderTitle.setTextSize(16);
        catHeaderTitle.setTextColor(0xFFFFFFFF);
        catHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        catHeaderTitle.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        subHeader.addView(catHeaderTitle);

        catHeaderBadge = new TextView(context);
        catHeaderBadge.setText("0 " + (isMovies ? "películas" : "series"));
        catHeaderBadge.setTextSize(11);
        catHeaderBadge.setTextColor(0xFF8FA5C2);
        catHeaderBadge.setBackground(TvStyle.squircleCard(context, 8));
        catHeaderBadge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        subHeader.addView(catHeaderBadge);

        View spacer = new View(context);
        subHeader.addView(spacer, new LayoutParams(0, 1, 1));

        // Search Bar in Subheader
        FrameLayout searchCard = new FrameLayout(context);
        searchCard.setBackground(TvStyle.squircleCard(context, 14));
        searchCard.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        LinearLayout searchInner = new LinearLayout(context);
        searchInner.setOrientation(HORIZONTAL);
        searchInner.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon sIcon = new LineIcon(context, "search");
        sIcon.setColor(0xFF8FA5C2);
        searchInner.addView(sIcon, new LayoutParams(dp(context, 14), dp(context, 14)));

        EditText searchEdit = new EditText(context);
        searchEdit.setHint(AppStrings.isEnglish(context) ? "Search in category…" : "Buscar en esta categoría…");
        searchEdit.setHintTextColor(0xFF6B7280);
        searchEdit.setTextColor(0xFFFFFFFF);
        searchEdit.setTextSize(12);
        searchEdit.setBackground(null);
        searchEdit.setSingleLine(true);
        searchEdit.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        searchEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                applyFilterAndSort();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        searchInner.addView(searchEdit, new LayoutParams(dp(context, 160), -2));
        searchCard.addView(searchInner);
        subHeader.addView(searchCard);

        // Sort Pill / Button
        TextView sortBtn = new TextView(context);
        sortBtn.setText(AppStrings.isEnglish(context) ? "Sort: Added ▾" : "Ordenar: Añadidas ▾");
        sortBtn.setTextSize(11.5f);
        sortBtn.setTextColor(0xFFD2DEEB);
        sortBtn.setBackground(TvStyle.squircleCard(context, 14));
        sortBtn.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        sortBtn.setFocusable(true);
        sortBtn.setClickable(true);
        sortBtn.setOnClickListener(v -> {
            sortMode = (sortMode + 1) % 3;
            if (sortMode == 0) sortBtn.setText(AppStrings.isEnglish(context) ? "Sort: Added ▾" : "Ordenar: Añadidas ▾");
            else if (sortMode == 1) sortBtn.setText(AppStrings.isEnglish(context) ? "Sort: A-Z ▾" : "Ordenar: A-Z ▾");
            else sortBtn.setText(AppStrings.isEnglish(context) ? "Sort: Z-A ▾" : "Ordenar: Z-A ▾");
            applyFilterAndSort();
        });
        sortBtn.setOnFocusChangeListener((v, f) -> {
            ((SquircleGlassDrawable) sortBtn.getBackground()).setFocused(f);
        });
        LayoutParams sbp = new LayoutParams(-2, -2);
        sbp.leftMargin = dp(context, 10);
        subHeader.addView(sortBtn, sbp);

        col.addView(subHeader);

        FrameLayout gridContainer = new FrameLayout(context);

        // Grid of Posters: 6 columns in Intuitive Mode for full screen, 5 columns in Classic Mode
        int spanCount = isIntuitive ? 6 : 5;
        posterGrid = new RecyclerView(context);
        posterGrid.setLayoutManager(new GridLayoutManager(context, spanCount));
        posterGrid.setHasFixedSize(true);
        posterGrid.setItemViewCacheSize(24);
        posterGrid.getRecycledViewPool().setMaxRecycledViews(0, 36);
        posterAdapter = new PosterAdapter();
        posterGrid.setAdapter(posterAdapter);
        posterGrid.setVerticalScrollBarEnabled(false);
        gridContainer.addView(posterGrid, new FrameLayout.LayoutParams(-1, -1));

        loadingLayout = new LinearLayout(context);
        loadingLayout.setOrientation(VERTICAL);
        loadingLayout.setGravity(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        if (spinner.getIndeterminateDrawable() != null) {
            spinner.getIndeterminateDrawable().setColorFilter(0xFF007AFF, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        loadingLayout.addView(spinner, new LayoutParams(dp(context, 48), dp(context, 48)));

        loadingText = new TextView(context);
        loadingText.setText(AppStrings.isEnglish(context) ? "Loading series…" : "Cargando catálogo de series…");
        loadingText.setTextSize(14);
        loadingText.setTextColor(0xFF8FA5C2);
        loadingText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LayoutParams tlp = new LayoutParams(-2, -2);
        tlp.topMargin = dp(context, 12);
        loadingLayout.addView(loadingText, tlp);
        loadingLayout.setVisibility(GONE);
        gridContainer.addView(loadingLayout, new FrameLayout.LayoutParams(-1, -1));

        col.addView(gridContainer, new LayoutParams(-1, 0, 1));

        return col;
    }

    private View buildPreviewColumn() {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(VERTICAL);
        col.setBackground(TvStyle.squircleCard(context, 16));
        col.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));

        previewPoster = new ImageView(context);
        previewPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LayoutParams plp = new LayoutParams(-1, dp(context, 160));
        previewPoster.setBackground(TvStyle.squircleCard(context, 12));
        previewPoster.setClipToOutline(true);
        col.addView(previewPoster, plp);

        previewTitle = new TextView(context);
        previewTitle.setTextSize(18);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        previewTitle.setPadding(0, dp(context, 12), 0, dp(context, 4));
        col.addView(previewTitle);

        previewMeta = new TextView(context);
        previewMeta.setTextSize(12);
        previewMeta.setTextColor(0xFF8FA5C2);
        previewMeta.setPadding(0, 0, 0, dp(context, 8));
        col.addView(previewMeta);

        previewOverview = new TextView(context);
        previewOverview.setTextSize(11.5f);
        previewOverview.setTextColor(0xFFBAC7D5);
        previewOverview.setMaxLines(3);
        previewOverview.setEllipsize(TextUtils.TruncateAt.END);
        previewOverview.setPadding(0, 0, 0, dp(context, 12));
        col.addView(previewOverview);

        // Action Buttons
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(HORIZONTAL);

        btnPlay = new TextView(context);
        btnPlay.setText(AppStrings.isEnglish(context) ? "▶  Play" : "▶  Ver ahora");
        btnPlay.setTextColor(0xFFFFFFFF);
        btnPlay.setTextSize(12);
        btnPlay.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnPlay.setBackground(TvStyle.squircleSolid(context, 12, 0xFF007AFF));
        btnPlay.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        btnPlay.setFocusable(true);
        btnPlay.setClickable(true);
        btnPlay.setOnClickListener(v -> {
            if (isMovies) {
                if (currentSelectedChannel != null && listener != null) {
                    listener.onPlay(currentSelectedChannel, filteredItems, filteredItems.indexOf(currentSelectedChannel));
                }
            } else {
                if (currentSelectedSeries != null && listener != null) {
                    listener.onOpenSeriesDetails(currentSelectedSeries);
                }
            }
        });
        btnRow.addView(btnPlay);

        btnFav = new TextView(context);
        btnFav.setText(AppStrings.isEnglish(context) ? "☆ Favorite" : "☆ Favorito");
        btnFav.setTextColor(0xFFCACBD4);
        btnFav.setTextSize(12);
        btnFav.setBackground(TvStyle.squircleCard(context, 12));
        btnFav.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        btnFav.setFocusable(true);
        btnFav.setClickable(true);
        btnFav.setOnClickListener(v -> {
            if (isMovies && currentSelectedChannel != null) {
                listener.onToggleFavorite(currentSelectedChannel);
                updateFavButtonState();
            } else if (!isMovies && currentSelectedSeries != null && currentSelectedSeries.representativeChannel != null) {
                listener.onToggleFavorite(currentSelectedSeries.representativeChannel);
                updateFavButtonState();
            }
        });
        LayoutParams bfp = new LayoutParams(-2, -2);
        bfp.leftMargin = dp(context, 8);
        btnRow.addView(btnFav, bfp);

        col.addView(btnRow);

        // Metadata grid
        LinearLayout metaGrid = new LinearLayout(context);
        metaGrid.setOrientation(VERTICAL);
        metaGrid.setPadding(0, dp(context, 14), 0, 0);

        previewDirector = new TextView(context);
        metaGrid.addView(buildMetaRow("director", AppStrings.isEnglish(context) ? "Director:" : "Director:", previewDirector));

        previewCast = new TextView(context);
        metaGrid.addView(buildMetaRow("user", AppStrings.isEnglish(context) ? "Cast:" : "Reparto:", previewCast));

        previewAudio = new TextView(context);
        metaGrid.addView(buildMetaRow("audio", AppStrings.isEnglish(context) ? "Audio:" : "Audio:", previewAudio));

        previewSubtitles = new TextView(context);
        metaGrid.addView(buildMetaRow("subtitles", AppStrings.isEnglish(context) ? "Subtitles:" : "Subtítulos:", previewSubtitles));

        col.addView(metaGrid, new LayoutParams(-1, 0, 1));

        return col;
    }

    private View buildMetaRow(String iconKind, String labelText, TextView valueView) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 3), 0, dp(context, 3));

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFF7B8FA6);
        row.addView(icon, new LayoutParams(dp(context, 14), dp(context, 14)));

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(10.5f);
        label.setTextColor(0xFF8B9EB3);
        label.setPadding(dp(context, 6), 0, dp(context, 6), 0);
        row.addView(label);

        valueView.setTextSize(10.5f);
        valueView.setTextColor(0xFFFFFFFF);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView, new LayoutParams(0, -2, 1));
        return row;
    }

    public void requestFocusFirstItem() {
        post(() -> {
            if (posterGrid != null) {
                View first = posterGrid.getChildAt(0);
                if (first != null) first.requestFocus();
                else posterGrid.requestFocus();
            }
        });
    }

    private void loadData() {
        if (isMovies) {
            if (loadingLayout != null) loadingLayout.setVisibility(GONE);
            if (posterGrid != null) posterGrid.setVisibility(VISIBLE);
            fullItems.clear();
            fullItems.addAll(repository.getMovieChannels());
            buildCategoryList();
            renderCategories();
            applyFilterAndSort();
        } else {
            List<SeriesItem> cached = repository.peekGroupedSeries();
            if (cached != null) {
                if (loadingLayout != null) loadingLayout.setVisibility(GONE);
                if (posterGrid != null) posterGrid.setVisibility(VISIBLE);
                fullSeries.clear();
                fullSeries.addAll(cached);
                buildCategoryList();
                renderCategories();
                applyFilterAndSort();
            } else {
                if (loadingLayout != null) loadingLayout.setVisibility(VISIBLE);
                if (posterGrid != null) posterGrid.setVisibility(GONE);
                if (catHeaderBadge != null) catHeaderBadge.setText(AppStrings.isEnglish(context) ? "Loading…" : "Cargando…");
                repository.loadGroupedSeriesAsync(seriesList -> {
                    if (loadingLayout != null) loadingLayout.setVisibility(GONE);
                    if (posterGrid != null) posterGrid.setVisibility(VISIBLE);
                    fullSeries.clear();
                    fullSeries.addAll(seriesList);
                    buildCategoryList();
                    renderCategories();
                    applyFilterAndSort();
                    requestFocusFirstItem();
                });
            }
        }
    }

    private void buildCategoryList() {
        categories.clear();
        Set<String> favIds = repository.getFavoriteIds();
        Set<String> recentIds = new HashSet<>(repository.getRecentIds());

        if (isMovies) {
            // 1. Todas
            categories.add(new CategoryItem("all", AppStrings.isEnglish(context) ? "All" : "Todas", "all_grid", fullItems.size()));

            // 2. Favoritos
            int favCount = 0;
            for (Channel c : fullItems) if (favIds.contains(c.id)) favCount++;
            categories.add(new CategoryItem("favorites", AppStrings.isEnglish(context) ? "Favorites" : "Favoritos", "star", favCount));

            // 3. Seguir viendo (Reanudar para ver)
            int resumeCount = 0;
            for (Channel c : fullItems) if (recentIds.contains(c.id)) resumeCount++;
            categories.add(new CategoryItem("resume", AppStrings.isEnglish(context) ? "Keep watching" : "Seguir viendo", "resume", resumeCount));

            // Group counts from playlist
            Map<String, Integer> groupCounts = new HashMap<>();
            for (Channel c : fullItems) {
                String g = (c.group == null || c.group.trim().isEmpty()) ? "General" : c.group.trim();
                Integer cnt = groupCounts.get(g);
                groupCounts.put(g, (cnt != null ? cnt : 0) + 1);
            }

            TreeSet<String> sortedGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            sortedGroups.addAll(groupCounts.keySet());

            for (String g : sortedGroups) {
                categories.add(new CategoryItem("group:" + g, g, "folder", groupCounts.get(g)));
            }
        } else {
            // 1. Todas (Series count)
            categories.add(new CategoryItem("all", AppStrings.isEnglish(context) ? "All" : "Todas", "all_grid", fullSeries.size()));

            // 2. Favoritos
            int favCount = 0;
            for (SeriesItem s : fullSeries) if (s.isFavorite(favIds)) favCount++;
            categories.add(new CategoryItem("favorites", AppStrings.isEnglish(context) ? "Favorites" : "Favoritos", "star", favCount));

            // 3. Seguir viendo
            int resumeCount = 0;
            for (SeriesItem s : fullSeries) if (s.hasRecent(recentIds)) resumeCount++;
            categories.add(new CategoryItem("resume", AppStrings.isEnglish(context) ? "Keep watching" : "Seguir viendo", "resume", resumeCount));

            // Group counts from playlist
            Map<String, Integer> groupCounts = new HashMap<>();
            for (SeriesItem s : fullSeries) {
                String g = (s.group == null || s.group.trim().isEmpty()) ? "General" : s.group.trim();
                Integer cnt = groupCounts.get(g);
                groupCounts.put(g, (cnt != null ? cnt : 0) + 1);
            }

            TreeSet<String> sortedGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            sortedGroups.addAll(groupCounts.keySet());

            for (String g : sortedGroups) {
                categories.add(new CategoryItem("group:" + g, g, "folder", groupCounts.get(g)));
            }
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
            row.addView(icon, new LayoutParams(dp(context, 16), dp(context, 16)));

            TextView label = new TextView(context);
            label.setText(item.label);
            label.setTextSize(12);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(context, 8), 0, dp(context, 6), 0);
            row.addView(label, new LayoutParams(0, -2, 1));

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

            LayoutParams lp = new LayoutParams(-1, -2);
            lp.bottomMargin = dp(context, 4);
            categoriesContainer.addView(row, lp);
        }
    }

    private void applyCategoryRowStyle(LinearLayout row, LineIcon icon, TextView label, TextView count, boolean selected, boolean focused) {
        if (isIntuitive) {
            if (selected || focused) {
                row.setBackground(TvStyle.squircleSolid(context, 12, selected ? 0xFF007AFF : 0xFF1E2D4A));
                icon.setColor(0xFFFFFFFF);
                label.setTextColor(0xFFFFFFFF);
                label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                count.setTextColor(0xFFFFFFFF);
            } else {
                row.setBackground(null);
                icon.setColor(0xFF8F91A2);
                label.setTextColor(0xFFCACBD4);
                label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                count.setTextColor(0xFFB5B6C2);
            }
            return;
        }
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
        if (isMovies) {
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
            catHeaderBadge.setText(String.valueOf(filteredItems.size()) + (AppStrings.isEnglish(context) ? " movies" : " películas"));
            posterAdapter.notifyDataSetChanged();

            if (!filteredItems.isEmpty()) {
                selectForPreview(filteredItems.get(0));
            } else {
                clearPreview();
            }
        } else {
            filteredSeries.clear();
            Set<String> favIds = repository.getFavoriteIds();
            Set<String> recentIds = new HashSet<>(repository.getRecentIds());
            for (SeriesItem s : fullSeries) {
                if ("favorites".equals(selectedCatId) && !s.isFavorite(favIds)) continue;
                if ("resume".equals(selectedCatId) && !s.hasRecent(recentIds)) continue;
                if (selectedCatId.startsWith("group:")) {
                    String groupName = selectedCatId.substring(6);
                    if (!groupName.equalsIgnoreCase(s.group)) continue;
                }
                if (!searchQuery.isEmpty()) {
                    String q = searchQuery.toLowerCase(Locale.ROOT);
                    if (!s.seriesTitle.toLowerCase(Locale.ROOT).contains(q)) continue;
                }
                filteredSeries.add(s);
            }

            if (sortMode == 1) {
                Collections.sort(filteredSeries, (a, b) -> a.seriesTitle.compareToIgnoreCase(b.seriesTitle));
            } else if (sortMode == 2) {
                Collections.sort(filteredSeries, (a, b) -> b.seriesTitle.compareToIgnoreCase(a.seriesTitle));
            }

            catHeaderTitle.setText(selectedCatLabel);
            catHeaderBadge.setText(String.valueOf(filteredSeries.size()) + (AppStrings.isEnglish(context) ? " series" : " series"));
            posterAdapter.notifyDataSetChanged();

            if (!filteredSeries.isEmpty()) {
                selectForPreviewSeries(filteredSeries.get(0));
            } else {
                clearPreview();
            }
        }
    }

    private void selectForPreview(Channel c) {
        currentSelectedChannel = c;
        currentSelectedSeries = null;
        if (previewTitle == null) return;
        previewTitle.setText(c.name);

        String year = extractYear(c.name);
        if (previewMeta != null) {
            previewMeta.setText((year.isEmpty() ? "2026" : year) + "  •  " + (c.group.isEmpty() ? "Película" : c.group));
        }
        if (previewOverview != null) {
            previewOverview.setText(AppStrings.isEnglish(context) ? "Available in your local library." : "Disponible en tu biblioteca local.");
        }
        if (previewDirector != null) previewDirector.setText("—");
        if (previewCast != null) previewCast.setText("—");
        if (previewAudio != null) previewAudio.setText("Español / Original");
        if (previewSubtitles != null) previewSubtitles.setText("Español, Inglés");

        updateFavButtonState();

        if (previewPoster != null) {
            if (!c.logoUrl.isEmpty()) {
                LogoLoader.load(c.logoUrl, previewPoster);
            } else {
                previewPoster.setImageBitmap(Artwork.get(context, R.drawable.category_movies, 400));
            }
        }

        if (TmdbClient.hasApiKey(context)) {
            fetchTmdbData(c);
        }
    }

    private void selectForPreviewSeries(SeriesItem s) {
        currentSelectedSeries = s;
        currentSelectedChannel = s.representativeChannel;
        if (previewTitle == null) return;
        previewTitle.setText(s.seriesTitle);

        int sc = s.getSeasonCount();
        if (previewMeta != null) {
            previewMeta.setText(s.year + "  •  " + sc + (sc == 1 ? " temporada" : " temporadas") + "  •  " + (s.group.isEmpty() ? "Serie" : s.group));
        }
        if (previewOverview != null) {
            previewOverview.setText(AppStrings.isEnglish(context) ? "Available in your local library." : "Disponible en tu biblioteca local.");
        }
        if (previewDirector != null) previewDirector.setText("—");
        if (previewCast != null) previewCast.setText("—");
        if (previewAudio != null) previewAudio.setText("Español / Original");
        if (previewSubtitles != null) previewSubtitles.setText("Español, Inglés");

        updateFavButtonState();

        if (previewPoster != null) {
            if (!s.logoUrl.isEmpty()) {
                LogoLoader.load(s.logoUrl, previewPoster);
            } else {
                previewPoster.setImageBitmap(Artwork.get(context, R.drawable.category_series, 400));
            }
        }

        if (TmdbClient.hasApiKey(context)) {
            fetchTmdbDataForSeries(s);
        }
    }

    private void fetchTmdbData(Channel c) {
        if (tmdbCache.containsKey(c.id)) {
            applyTmdbDetails(tmdbCache.get(c.id));
            return;
        }

        new Thread(() -> {
            TmdbClient.Details d = TmdbClient.searchAndGetDetails(context, c.name, true, AppStrings.getLang(context));
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

    private void fetchTmdbDataForSeries(SeriesItem s) {
        String cacheKey = "series:" + s.seriesTitle;
        if (tmdbCache.containsKey(cacheKey)) {
            applyTmdbDetails(tmdbCache.get(cacheKey));
            return;
        }

        new Thread(() -> {
            TmdbClient.Details d = TmdbClient.searchAndGetDetails(context, s.seriesTitle, false, AppStrings.getLang(context));
            if (d != null) {
                tmdbCache.put(cacheKey, d);
                post(() -> {
                    if (currentSelectedSeries == s) {
                        applyTmdbDetails(d);
                    }
                });
            }
        }).start();
    }

    private void applyTmdbDetails(TmdbClient.Details d) {
        if (previewOverview == null) return;
        if (d.overview != null && !d.overview.isEmpty()) {
            previewOverview.setText(d.overview);
        }
        if (previewPoster != null) {
            if (d.backdropUrl != null) {
                LogoLoader.load(d.backdropUrl, previewPoster);
            } else if (d.posterUrl != null) {
                LogoLoader.load(d.posterUrl, previewPoster);
            }
        }
        if (previewMeta != null && d.releaseDate != null && !d.releaseDate.isEmpty()) {
            previewMeta.setText(d.releaseDate.substring(0, Math.min(4, d.releaseDate.length())) + "  •  ★ " + String.format(Locale.ROOT, "%.1f", d.rating) + "  •  " + (isMovies ? "Película" : "Serie"));
        }
        if (previewCast != null && !d.cast.isEmpty()) {
            previewCast.setText(android.text.TextUtils.join(", ", d.cast));
        }
    }

    private void updateFavButtonState() {
        if (btnFav == null) return;
        if (isMovies && currentSelectedChannel != null) {
            boolean isFav = repository.isFavorite(currentSelectedChannel.id);
            btnFav.setText(isFav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardada") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        } else if (!isMovies && currentSelectedSeries != null) {
            boolean isFav = currentSelectedSeries.isFavorite(repository);
            btnFav.setText(isFav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardada") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        }
    }

    private void clearPreview() {
        currentSelectedChannel = null;
        currentSelectedSeries = null;
        if (previewTitle == null) return;
        previewTitle.setText("");
        if (previewMeta != null) previewMeta.setText("");
        if (previewOverview != null) previewOverview.setText(AppStrings.isEnglish(context) ? "No content found in this category." : "No hay contenido en esta categoría.");
        if (previewPoster != null) previewPoster.setImageDrawable(null);
    }

    private static String extractYear(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19\\d\\d|20\\d\\d)").matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    // ADAPTER & VIEW HOLDER FOR VERTICAL POSTERS (5 or 6 per row)
    private final class PosterAdapter extends RecyclerView.Adapter<PosterHolder> {
        PosterAdapter() {
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) {
            if (isMovies) {
                if (position >= 0 && position < filteredItems.size()) {
                    return filteredItems.get(position).id.hashCode();
                }
            } else {
                if (position >= 0 && position < filteredSeries.size()) {
                    return filteredSeries.get(position).seriesTitle.hashCode();
                }
            }
            return position;
        }

        @Override public PosterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new PosterHolder();
        }
        @Override public void onBindViewHolder(PosterHolder holder, int position) {
            if (isMovies) {
                holder.bind(filteredItems.get(position));
            } else {
                holder.bindSeries(filteredSeries.get(position));
            }
        }
        @Override public int getItemCount() {
            return isMovies ? filteredItems.size() : filteredSeries.size();
        }
    }

    private final class PosterHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView posterImage;
        final TextView initialsText;
        final TextView titleText;
        final TextView yearText;
        Channel item;
        SeriesItem series;

        PosterHolder() {
            super(new FrameLayout(context));
            card = (FrameLayout) itemView;
            card.setFocusable(true);
            card.setClickable(true);
            int margin = dp(context, 4);
            int h = isIntuitive ? dp(context, 168) : dp(context, 142);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, h);
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);

            SquircleGlassDrawable cardBg = TvStyle.squircleCard(context, 12);
            card.setBackground(cardBg);
            card.setClipToOutline(true);

            posterImage = new ImageView(context);
            posterImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(posterImage, new FrameLayout.LayoutParams(-1, -1));

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
                if (series != null && listener != null) {
                    listener.onOpenSeriesDetails(series);
                } else if (item != null && listener != null) {
                    listener.onOpenDetails(item, filteredItems, filteredItems.indexOf(item));
                }
            });

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    if (series != null) selectForPreviewSeries(series);
                    else if (item != null) selectForPreview(item);
                }
                if (isIntuitive) {
                    cardBg.setCustomStroke(hasFocus ? 0xFF00BFFF : 0x22FFFFFF, hasFocus ? 2.0f : 1.0f);
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
            this.series = null;
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

        void bindSeries(SeriesItem s) {
            this.series = s;
            this.item = null;
            titleText.setText(s.seriesTitle);
            int sc = s.getSeasonCount();
            yearText.setText(s.year + " · " + sc + (sc == 1 ? " temp." : " temps."));

            if (s.logoUrl != null && !s.logoUrl.isEmpty()) {
                initialsText.setVisibility(GONE);
                LogoLoader.load(s.logoUrl, posterImage);
            } else {
                posterImage.setImageDrawable(null);
                initialsText.setVisibility(VISIBLE);
                String trimmed = s.seriesTitle.trim();
                initialsText.setText(trimmed.length() > 2 ? trimmed.substring(0, 2).toUpperCase(Locale.ROOT) : trimmed.toUpperCase(Locale.ROOT));
            }
        }
    }
}
