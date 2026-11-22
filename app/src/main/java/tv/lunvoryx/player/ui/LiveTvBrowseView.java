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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.epg.EpgRepository;
import tv.lunvoryx.player.epg.Program;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class LiveTvBrowseView extends LinearLayout {
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
    private final EpgRepository epgRepository;
    private final Listener listener;
    private final boolean isIntuitive;

    private final List<Channel> fullChannels = new ArrayList<>();
    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();

    private String selectedCatId = "all";
    private String selectedCatLabel = "Todas";
    private String searchQuery = "";
    private int sortMode = 0; // 0: Original, 1: A-Z, 2: Z-A

    // UI elements
    private LinearLayout categoriesContainer;
    private TextView catHeaderTitle;
    private TextView catHeaderBadge;
    private RecyclerView channelList;
    private ChannelRowAdapter channelAdapter;

    // Preview Panel
    private ImageView previewLogo;
    private TextView previewLogoText;
    private TextView previewTitle;
    private TextView previewProgram;
    private TextView previewSchedule;
    private TextView previewRemaining;
    private ProgressBar previewProgress;
    private TextView btnPlay;
    private TextView btnFav;
    private TextView nextTime;
    private TextView nextTitle;

    private Channel currentSelectedChannel;

    public LiveTvBrowseView(Context context, PlaylistRepository repository, Listener listener) {
        this(context, repository, listener, false);
    }

    public LiveTvBrowseView(Context context, PlaylistRepository repository, Listener listener, boolean isIntuitive) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.epgRepository = new EpgRepository(context);
        this.listener = listener;
        this.isIntuitive = isIntuitive;

        setOrientation(VERTICAL);
        buildUi();
        loadData();
    }

    private void buildUi() {
        if (!isIntuitive) {
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

            // 1. Header: Icon + Title + Subtitle
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(0, 0, 0, dp(context, 10));

            LineIcon tvIcon = new LineIcon(context, "tv");
            tvIcon.setColor(0xFFF8F8FB);
            header.addView(tvIcon, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)));

            LinearLayout titleCol = new LinearLayout(context);
            titleCol.setOrientation(VERTICAL);
            titleCol.setPadding(dp(context, 14), 0, 0, 0);

            TextView title = new TextView(context);
            title.setText(AppStrings.get(context, "nav_live"));
            title.setTextSize(26);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            titleCol.addView(title);

            TextView sub = new TextView(context);
            sub.setText(AppStrings.isEnglish(context) ?
                    "Thousands of live channels, organized by categories" :
                    "Miles de canales en directo, organizados por categorías");
            sub.setTextSize(12);
            sub.setTextColor(0xFF8F91A2);
            sub.setPadding(0, dp(context, 2), 0, 0);
            titleCol.addView(sub);

            header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));
            addView(header);
        } else {
            setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 12));
        }

        // 2. Three Columns Body
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        // COLUMN A: Categorías
        buildCategoriesColumn(body);

        // COLUMN B: Canales List
        buildChannelsColumn(body);

        // COLUMN C: Vista Previa
        buildPreviewColumn(body);
    }

    private void buildCategoriesColumn(LinearLayout body) {
        LinearLayout colA = new LinearLayout(context);
        colA.setOrientation(VERTICAL);
        colA.setBackground(TvStyle.squircleCard(context, 18));
        colA.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 8));

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

        // Down scroll arrow hint
        LineIcon down = new LineIcon(context, "down");
        down.setColor(0xFF75788E);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14));
        dp.gravity = Gravity.CENTER_HORIZONTAL;
        dp.topMargin = dp(context, 4);
        colA.addView(down, dp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 190), -1);
        lp.rightMargin = dp(context, 10);
        body.addView(colA, lp);
    }

    private void buildChannelsColumn(LinearLayout body) {
        LinearLayout colB = new LinearLayout(context);
        colB.setOrientation(VERTICAL);
        colB.setBackground(TvStyle.squircleCard(context, 18));
        colB.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 8));

        // Subheader: TV Icon + "Canales • [Category]" + Badge
        LinearLayout chHeader = new LinearLayout(context);
        chHeader.setOrientation(HORIZONTAL);
        chHeader.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon tvI = new LineIcon(context, "tv");
        tvI.setColor(0xFFFFFFFF);
        chHeader.addView(tvI, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

        catHeaderTitle = new TextView(context);
        catHeaderTitle.setText("Canales • Todas");
        catHeaderTitle.setTextSize(15);
        catHeaderTitle.setTextColor(0xFFFFFFFF);
        catHeaderTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        catHeaderTitle.setSingleLine(true);
        catHeaderTitle.setEllipsize(TextUtils.TruncateAt.END);
        catHeaderTitle.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        chHeader.addView(catHeaderTitle, new LinearLayout.LayoutParams(0, -2, 1));

        catHeaderBadge = new TextView(context);
        catHeaderBadge.setText("0 canales");
        catHeaderBadge.setTextSize(11);
        catHeaderBadge.setTextColor(0xFFB5B6C2);
        catHeaderBadge.setPadding(dp(context, 10), dp(context, 3), dp(context, 10), dp(context, 3));
        catHeaderBadge.setBackground(TvStyle.squircleCard(context, 10));
        chHeader.addView(catHeaderBadge);
        colB.addView(chHeader);

        // Search + Sort tools row
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(context, 10), 0, dp(context, 8));

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
        sortBtn.setText("⇅ Orden: Original ▾");
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

        // Vertical Channels List
        channelList = new RecyclerView(context);
        channelList.setLayoutManager(new LinearLayoutManager(context));
        channelList.setItemAnimator(null);
        channelAdapter = new ChannelRowAdapter();
        channelList.setAdapter(channelAdapter);
        colB.addView(channelList, new LinearLayout.LayoutParams(-1, 0, 1));

        // Down scroll arrow hint
        LineIcon down = new LineIcon(context, "down");
        down.setColor(0xFF75788E);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14));
        dp.gravity = Gravity.CENTER_HORIZONTAL;
        dp.topMargin = dp(context, 4);
        colB.addView(down, dp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.rightMargin = dp(context, 10);
        body.addView(colB, lp);
    }

    private void toggleSort(TextView sortBtn) {
        sortMode = (sortMode + 1) % 3;
        String label = "⇅ Orden: Original ▾";
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

        // Preview Screen Container with live badge
        FrameLayout screenFrame = new FrameLayout(context);
        screenFrame.setBackground(TvStyle.squircleSolid(context, 12, 0xFF1F2233));
        screenFrame.setClipToOutline(true);

        previewLogo = new ImageView(context);
        previewLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewLogo.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        screenFrame.addView(previewLogo, new FrameLayout.LayoutParams(-1, -1));

        previewLogoText = new TextView(context);
        previewLogoText.setTextSize(28);
        previewLogoText.setTextColor(0xFFFFFFFF);
        previewLogoText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        previewLogoText.setGravity(Gravity.CENTER);
        screenFrame.addView(previewLogoText, new FrameLayout.LayoutParams(-1, -1));

        // Live badge: "● EN DIRECTO"
        TextView liveBadge = new TextView(context);
        liveBadge.setText("● " + (AppStrings.isEnglish(context) ? "LIVE" : "EN DIRECTO"));
        liveBadge.setTextSize(10);
        liveBadge.setTextColor(0xFFFFFFFF);
        liveBadge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        liveBadge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        liveBadge.setBackground(TvStyle.squircleSolid(context, 10, 0xFFE50914));
        FrameLayout.LayoutParams lbp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lbp.setMargins(dp(context, 10), dp(context, 10), 0, 0);
        screenFrame.addView(liveBadge, lbp);

        colC.addView(screenFrame, new LinearLayout.LayoutParams(-1, dp(context, 140)));

        // Channel info row: Emblem/number + Name + Program
        LinearLayout chInfo = new LinearLayout(context);
        chInfo.setOrientation(HORIZONTAL);
        chInfo.setGravity(Gravity.CENTER_VERTICAL);
        chInfo.setPadding(0, dp(context, 12), 0, dp(context, 4));

        LinearLayout names = new LinearLayout(context);
        names.setOrientation(VERTICAL);
        previewTitle = new TextView(context);
        previewTitle.setText("La 1 HD");
        previewTitle.setTextSize(16);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        names.addView(previewTitle);

        previewProgram = new TextView(context);
        previewProgram.setText("Telediario 2");
        previewProgram.setTextSize(12);
        previewProgram.setTextColor(0xFF8F91A2);
        names.addView(previewProgram);
        chInfo.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        colC.addView(chInfo);

        // Schedule and remaining time row
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(HORIZONTAL);
        timeRow.setPadding(0, dp(context, 6), 0, dp(context, 4));

        previewSchedule = new TextView(context);
        previewSchedule.setText("20:00 - 21:00");
        previewSchedule.setTextSize(11);
        previewSchedule.setTextColor(0xFFCACBD4);
        timeRow.addView(previewSchedule, new LinearLayout.LayoutParams(0, -2, 1));

        previewRemaining = new TextView(context);
        previewRemaining.setText("Quedan 25 min");
        previewRemaining.setTextSize(11);
        previewRemaining.setTextColor(0xFF8F91A2);
        timeRow.addView(previewRemaining);
        colC.addView(timeRow);

        // Cyan progress bar
        previewProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        previewProgress.setMax(100);
        previewProgress.setProgress(60);
        previewProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF38B2FF));
        previewProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF282C40));
        colC.addView(previewProgress, new LinearLayout.LayoutParams(-1, dp(context, 4)));

        // Buttons row (Ver canal / Favorito)
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(HORIZONTAL);
        btnRow.setPadding(0, dp(context, 14), 0, dp(context, 14));

        btnPlay = new TextView(context);
        btnPlay.setText("▶  " + (AppStrings.isEnglish(context) ? "Watch channel" : "Ver canal"));
        btnPlay.setTextSize(12);
        btnPlay.setTextColor(isIntuitive ? 0xFFFFFFFF : 0xFF14151B);
        btnPlay.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        btnPlay.setGravity(Gravity.CENTER);
        btnPlay.setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9));
        btnPlay.setBackground(TvStyle.squircleSolid(context, 12, isIntuitive ? 0xFF007AFF : 0xFFFFFFFF));
        btnPlay.setFocusable(true);
        btnPlay.setClickable(true);
        if (isIntuitive) {
            btnPlay.setOnFocusChangeListener((v, f) -> {
                btnPlay.setBackground(TvStyle.squircleSolid(context, 12, f ? 0xFF0A84FF : 0xFF007AFF));
                v.animate().scaleX(f ? 1.04f : 1.0f).scaleY(f ? 1.04f : 1.0f).setDuration(120).start();
            });
        }
        btnPlay.setOnClickListener(v -> {
            if (currentSelectedChannel != null && listener != null) {
                listener.onPlay(currentSelectedChannel, filteredChannels, filteredChannels.indexOf(currentSelectedChannel));
            }
        });
        btnRow.addView(btnPlay, new LinearLayout.LayoutParams(0, -2, 1));

        btnFav = new TextView(context);
        btnFav.setText("☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        btnFav.setTextSize(12);
        btnFav.setTextColor(0xFFF8F8FB);
        btnFav.setGravity(Gravity.CENTER);
        btnFav.setPadding(dp(context, 12), dp(context, 9), dp(context, 12), dp(context, 9));
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
        fbp.leftMargin = dp(context, 8);
        btnRow.addView(btnFav, fbp);
        colC.addView(btnRow);

        // Next program card: "A continuación"
        LinearLayout nextCard = new LinearLayout(context);
        nextCard.setOrientation(VERTICAL);
        nextCard.setBackground(TvStyle.squircleCard(context, 12));
        nextCard.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        LinearLayout ncHead = new LinearLayout(context);
        ncHead.setOrientation(HORIZONTAL);
        ncHead.setGravity(Gravity.CENTER_VERTICAL);
        LineIcon listIcon = new LineIcon(context, "list");
        listIcon.setColor(0xFF8F91A2);
        ncHead.addView(listIcon, new LinearLayout.LayoutParams(dp(context, 13), dp(context, 13)));

        TextView ncTitle = new TextView(context);
        ncTitle.setText(AppStrings.isEnglish(context) ? "Next" : "A continuación");
        ncTitle.setTextSize(11);
        ncTitle.setTextColor(0xFFCACBD4);
        ncTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        ncTitle.setPadding(dp(context, 6), 0, 0, 0);
        ncHead.addView(ncTitle);
        nextCard.addView(ncHead);

        LinearLayout nextDetail = new LinearLayout(context);
        nextDetail.setOrientation(HORIZONTAL);
        nextDetail.setPadding(0, dp(context, 6), 0, 0);

        nextTime = new TextView(context);
        nextTime.setText("21:00");
        nextTime.setTextSize(11);
        nextTime.setTextColor(0xFF8F91A2);
        nextDetail.addView(nextTime);

        nextTitle = new TextView(context);
        nextTitle.setText("El tiempo");
        nextTitle.setTextSize(11);
        nextTitle.setTextColor(0xFFF8F8FB);
        nextTitle.setPadding(dp(context, 10), 0, 0, 0);
        nextTitle.setSingleLine(true);
        nextTitle.setEllipsize(TextUtils.TruncateAt.END);
        nextDetail.addView(nextTitle, new LinearLayout.LayoutParams(0, -2, 1));
        nextCard.addView(nextDetail);

        colC.addView(nextCard);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 252), -1);
        body.addView(colC, lp);
    }

    private void loadData() {
        fullChannels.clear();
        for (Channel c : repository.getAllChannels()) {
            if (MediaKind.classify(c) == MediaKind.LIVE) {
                fullChannels.add(c);
            }
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
        categories.add(new CategoryItem("all", AppStrings.isEnglish(context) ? "All" : "Todas", "all_grid", fullChannels.size()));

        // 2. Favoritos
        int favCount = 0;
        for (Channel c : fullChannels) if (favIds.contains(c.id)) favCount++;
        categories.add(new CategoryItem("favorites", AppStrings.isEnglish(context) ? "Favorites" : "Favoritos", "star", favCount));

        // 3. Seguir viendo (Recientes)
        int recentCount = 0;
        for (Channel c : fullChannels) if (recents.contains(c.id)) recentCount++;
        categories.add(new CategoryItem("recents", AppStrings.isEnglish(context) ? "Keep watching" : "Seguir viendo", "resume", recentCount));

        // Groups from playlist
        Map<String, Integer> groupCounts = new HashMap<>();
        for (Channel c : fullChannels) {
            String g = (c.group == null || c.group.trim().isEmpty()) ? (AppStrings.isEnglish(context) ? "General" : "General") : c.group.trim();
            Integer cnt = groupCounts.get(g);
            groupCounts.put(g, (cnt != null ? cnt : 0) + 1);
        }

        TreeSet<String> sortedGroups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedGroups.addAll(groupCounts.keySet());

        for (String g : sortedGroups) {
            String icon = chooseIconForGroup(g);
            categories.add(new CategoryItem("group:" + g, g, icon, groupCounts.get(g)));
        }
    }

    private String chooseIconForGroup(String g) {
        if (isIntuitive) return "tv";
        String lower = g.toLowerCase(Locale.ROOT);
        if (lower.contains("depor") || lower.contains("sport") || lower.contains("futbol")) return "trophy";
        if (lower.contains("noticia") || lower.contains("news") || lower.contains("info")) return "news";
        if (lower.contains("cine") || lower.contains("movie") || lower.contains("film")) return "movie";
        if (lower.contains("serie") || lower.contains("drama")) return "series";
        if (lower.contains("infantil") || lower.contains("kid") || lower.contains("dibujo") || lower.contains("anim")) return "child";
        if (lower.contains("inter") || lower.contains("world") || lower.contains("global")) return "globe";
        return "tv";
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
        if (isIntuitive) {
            if (selected || focused) {
                row.setBackground(TvStyle.squircleSolid(context, 12, selected ? 0xFF007AFF : 0xFF1E2D4A));
                icon.setColor(0xFFFFFFFF);
                label.setTextColor(0xFFFFFFFF);
                label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                count.setTextColor(0xFFFFFFFF);
            } else {
                row.setBackground(null);
                icon.setColor(0xFF7E8B9E);
                label.setTextColor(0xFFCACBD4);
                label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                count.setTextColor(0xFF8F91A2);
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
        filteredChannels.clear();
        Set<String> favIds = repository.getFavoriteIds();
        List<String> recents = repository.getRecentIds();

        for (Channel c : fullChannels) {
            if ("favorites".equals(selectedCatId) && !favIds.contains(c.id)) continue;
            if ("recents".equals(selectedCatId) && !recents.contains(c.id)) continue;
            if (selectedCatId.startsWith("group:")) {
                String groupName = selectedCatId.substring(6);
                if (!groupName.equalsIgnoreCase(c.group)) continue;
            }
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase(Locale.ROOT);
                if (!c.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            }
            filteredChannels.add(c);
        }

        if (sortMode == 1) {
            Collections.sort(filteredChannels, (a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 2) {
            Collections.sort(filteredChannels, (a, b) -> b.name.compareToIgnoreCase(a.name));
        }

        catHeaderTitle.setText("Canales • " + selectedCatLabel);
        catHeaderBadge.setText(String.valueOf(filteredChannels.size()) + (AppStrings.isEnglish(context) ? " channels" : " canales"));
        channelAdapter.notifyDataSetChanged();

        if (!filteredChannels.isEmpty()) {
            selectForPreview(filteredChannels.get(0));
        } else {
            clearPreview();
        }
    }

    private void selectForPreview(Channel c) {
        currentSelectedChannel = c;
        previewTitle.setText(c.name);

        long nowTime = System.currentTimeMillis();
        Program now = epgRepository.now(c.tvgId.isEmpty() ? c.name : c.tvgId);
        Program next = epgRepository.next(c.tvgId.isEmpty() ? c.name : c.tvgId);

        if (now != null) {
            previewProgram.setText(now.title);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            previewSchedule.setText(sdf.format(now.startMillis) + " - " + sdf.format(now.endMillis));
            long total = Math.max(1, now.endMillis - now.startMillis);
            long progress = nowTime - now.startMillis;
            int pct = (int) Math.min(100, Math.max(0, (progress * 100) / total));
            previewProgress.setProgress(pct);
            long remainingMin = Math.max(1, (now.endMillis - nowTime) / 60000);
            previewRemaining.setText((AppStrings.isEnglish(context) ? "Remaining " : "Quedan ") + remainingMin + " min");
        } else {
            previewProgram.setText(c.group.isEmpty() ? (AppStrings.isEnglish(context) ? "Live broadcast" : "Emisión en directo") : c.group);
            previewSchedule.setText("20:00 - 21:00");
            previewProgress.setProgress(45);
            previewRemaining.setText(AppStrings.isEnglish(context) ? "Live" : "En directo");
        }

        if (next != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            nextTime.setText(sdf.format(next.startMillis));
            nextTitle.setText(next.title);
        } else {
            nextTime.setText("21:00");
            nextTitle.setText(AppStrings.isEnglish(context) ? "Program info" : "Programación habitual");
        }

        updateFavButtonState();

        if (!c.logoUrl.isEmpty()) {
            previewLogoText.setVisibility(GONE);
            LogoLoader.load(c.logoUrl, previewLogo);
        } else {
            previewLogo.setImageDrawable(null);
            previewLogoText.setVisibility(VISIBLE);
            String trimmed = c.name.trim();
            previewLogoText.setText(trimmed.length() > 2 ? trimmed.substring(0, 2).toUpperCase(Locale.ROOT) : trimmed.toUpperCase(Locale.ROOT));
        }
    }

    private void updateFavButtonState() {
        if (currentSelectedChannel != null) {
            boolean isFav = repository.isFavorite(currentSelectedChannel.id);
            btnFav.setText(isFav ? "★ " + (AppStrings.isEnglish(context) ? "Saved" : "Guardado") : "☆ " + (AppStrings.isEnglish(context) ? "Favorite" : "Favorito"));
        }
    }

    private void clearPreview() {
        currentSelectedChannel = null;
        previewTitle.setText("");
        previewProgram.setText(AppStrings.isEnglish(context) ? "No channels available" : "No hay canales disponibles");
        previewSchedule.setText("");
        previewRemaining.setText("");
        previewLogo.setImageDrawable(null);
        previewLogoText.setVisibility(GONE);
    }

    // ADAPTER FOR CHANNEL ROWS
    private final class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowHolder> {
        @Override public ChannelRowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ChannelRowHolder();
        }
        @Override public void onBindViewHolder(ChannelRowHolder holder, int position) {
            holder.bind(filteredChannels.get(position));
        }
        @Override public int getItemCount() {
            return filteredChannels.size();
        }
    }

    private final class ChannelRowHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final FrameLayout logoFrame;
        final ImageView logoView;
        final TextView logoTextView;
        final TextView nameView;
        final TextView programView;
        final TextView timeView;
        final ProgressBar rowProgress;
        Channel item;

        ChannelRowHolder() {
            super(new LinearLayout(context));
            row = (LinearLayout) itemView;
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            row.setMinimumHeight(dp(context, 54));

            // Emblem / Logo frame
            logoFrame = new FrameLayout(context);
            logoFrame.setBackground(TvStyle.squircleSolid(context, 8, 0xFF1E2130));
            logoFrame.setClipToOutline(true);

            logoView = new ImageView(context);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoFrame.addView(logoView, new FrameLayout.LayoutParams(-1, -1));

            logoTextView = new TextView(context);
            logoTextView.setTextSize(14);
            logoTextView.setTextColor(0xFFFFFFFF);
            logoTextView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            logoTextView.setGravity(Gravity.CENTER);
            logoFrame.addView(logoTextView, new FrameLayout.LayoutParams(-1, -1));

            row.addView(logoFrame, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 34)));

            // Text column (Name, Program, Progress)
            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(VERTICAL);
            textCol.setPadding(dp(context, 10), 0, dp(context, 8), 0);

            LinearLayout topNameRow = new LinearLayout(context);
            topNameRow.setOrientation(HORIZONTAL);
            topNameRow.setGravity(Gravity.CENTER_VERTICAL);

            nameView = new TextView(context);
            nameView.setTextSize(13);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            topNameRow.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));

            timeView = new TextView(context);
            timeView.setTextSize(10);
            timeView.setTextColor(0xFF8F91A2);
            topNameRow.addView(timeView);
            textCol.addView(topNameRow);

            programView = new TextView(context);
            programView.setTextSize(11);
            programView.setTextColor(0xFF8F91A2);
            programView.setSingleLine(true);
            programView.setEllipsize(TextUtils.TruncateAt.END);
            programView.setPadding(0, dp(context, 2), 0, 0);
            textCol.addView(programView);

            rowProgress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            rowProgress.setMax(100);
            rowProgress.setProgress(50);
            rowProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF38B2FF));
            rowProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF282C40));
            LinearLayout.LayoutParams rpp = new LinearLayout.LayoutParams(-1, dp(context, 2.5f));
            rpp.topMargin = dp(context, 4);
            textCol.addView(rowProgress, rpp);

            row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

            row.setOnClickListener(v -> {
                if (item != null && listener != null) {
                    listener.onPlay(item, filteredChannels, filteredChannels.indexOf(item));
                }
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && item != null) {
                    selectForPreview(item);
                }
                if (hasFocus) {
                    row.setBackground(TvStyle.squircleSolid(context, 12, 0xFFE2E2E6));
                    nameView.setTextColor(0xFF14151B);
                    programView.setTextColor(0xFF434554);
                    timeView.setTextColor(0xFF14151B);
                } else {
                    row.setBackground(null);
                    nameView.setTextColor(0xFFFFFFFF);
                    programView.setTextColor(0xFF8F91A2);
                    timeView.setTextColor(0xFF8F91A2);
                }
            });
        }

        void bind(Channel c) {
            this.item = c;
            nameView.setText(c.name);

            long nowTime = System.currentTimeMillis();
            Program now = epgRepository.now(c.tvgId.isEmpty() ? c.name : c.tvgId);
            if (now != null) {
                programView.setText(now.title);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                timeView.setText(sdf.format(now.endMillis));
                long total = Math.max(1, now.endMillis - now.startMillis);
                long prog = nowTime - now.startMillis;
                int pct = (int) Math.min(100, Math.max(0, (prog * 100) / total));
                rowProgress.setProgress(pct);
                rowProgress.setVisibility(VISIBLE);
            } else {
                programView.setText(c.group.isEmpty() ? (AppStrings.isEnglish(context) ? "Live TV" : "En directo") : c.group);
                timeView.setText("21:00");
                rowProgress.setProgress(40);
                rowProgress.setVisibility(VISIBLE);
            }

            if (c.logoUrl != null && !c.logoUrl.isEmpty()) {
                logoTextView.setVisibility(GONE);
                LogoLoader.load(c.logoUrl, logoView);
            } else {
                logoView.setImageDrawable(null);
                logoTextView.setVisibility(VISIBLE);
                String trimmed = c.name.trim();
                logoTextView.setText(trimmed.length() > 2 ? trimmed.substring(0, 2).toUpperCase(Locale.ROOT) : trimmed.toUpperCase(Locale.ROOT));
            }
        }
    }
}
