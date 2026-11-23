package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.epg.EpgRepository;
import tv.lunvoryx.player.epg.Program;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class LiveTvBrowseView extends LinearLayout {
    public interface Listener {
        void onPlay(Channel channel, List<Channel> list, int index);
        void onToggleFavorite(Channel channel);
    }

    private final Context context;
    private final PlaylistRepository repository;
    private final EpgRepository epgRepository;
    private final Listener listener;
    private final boolean isIntuitive;

    private final CatalogTask browseTask = new CatalogTask();
    private boolean categoriesLoaded;
    private List<Channel> filteredChannels = Collections.emptyList();

    private String selectedCatId = "all";
    private String selectedCatLabel = "Todas";
    private String searchQuery = "";
    private int sortMode = 0; // 0: Original, 1: A-Z, 2: Z-A

    // UI elements
    private CategoryList categoryList;
    private TextView catHeaderTitle;
    private TextView catHeaderBadge;
    private RecyclerView channelList;
    private ChannelRowAdapter channelAdapter;

    // Preview Panel
    private ImageView previewLogo;
    private android.view.TextureView previewVideo;
    private androidx.media3.exoplayer.ExoPlayer previewPlayer;
    private final Runnable startPreview = this::startVideoPreview;

    private void stopVideoPreview() {
        removeCallbacks(startPreview);
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        if (previewVideo != null) previewVideo.setAlpha(0f);
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void startVideoPreview() {
        Channel channel = currentSelectedChannel;
        if (previewVideo == null || channel == null || !isShown() || !hasWindowFocus()) return;
        stopVideoPreview();
        androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(channel.userAgent.isEmpty() ? "LunvoryxTV/1.0" : channel.userAgent);
        if (!channel.referer.isEmpty()) http.setDefaultRequestProperties(java.util.Collections.singletonMap("Referer", channel.referer));
        androidx.media3.exoplayer.ExoPlayer player = new androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        new androidx.media3.datasource.DefaultDataSource.Factory(context, http))).build();
        previewPlayer = player;
        player.setVolume(0f);
        if (channel.url.startsWith("asset:///demo/live/"))
            player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
        player.setVideoTextureView(previewVideo);
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                if (previewPlayer == player) previewVideo.setAlpha(1f);
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (previewPlayer == player) stopVideoPreview();
            }
        });
        androidx.media3.common.MediaItem.Builder media = new androidx.media3.common.MediaItem.Builder().setUri(channel.url);
        android.net.Uri uri = android.net.Uri.parse(channel.url);
        if ("m3u8".equalsIgnoreCase(uri.getQueryParameter("output")))
            media.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(media.build());
        player.prepare();
        player.play();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        stopVideoPreview();
        if (focused && isIntuitive) postDelayed(startPreview, 650);
    }
    private TextView previewLogoText;
    private TextView liveBadge;
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
        clearPreview();
        previewProgram.setText(AppStrings.isEnglish(context) ? "Loading channels…" : "Cargando canales…");
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
        colA.setBackground(isIntuitive ? IntuitivePanel.create(context) : TvStyle.squircleCard(context, 18));
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

        categoryList = new CategoryList(context, this::applyCategoryRowStyle, item -> {
            selectedCatId = item.id;
            selectedCatLabel = item.label;
            applyFilterAndSort();
        });
        colA.addView(categoryList, new LinearLayout.LayoutParams(-1, 0, 1));

        // Down scroll arrow hint
        LineIcon down = new LineIcon(context, "down");
        down.setColor(0xFF75788E);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14));
        dp.gravity = Gravity.CENTER_HORIZONTAL;
        dp.topMargin = dp(context, 4);
        colA.addView(down, dp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isIntuitive ? 0 : dp(context, 190), -1, isIntuitive ? .20f : 0);
        lp.rightMargin = dp(context, 10);
        body.addView(colA, lp);
    }

    private void buildChannelsColumn(LinearLayout body) {
        LinearLayout colB = new LinearLayout(context);
        colB.setOrientation(VERTICAL);
        colB.setBackground(isIntuitive ? IntuitivePanel.create(context) : TvStyle.squircleCard(context, 18));
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
        channelList.setHasFixedSize(true);
        channelList.setItemViewCacheSize(20);
        channelList.getRecycledViewPool().setMaxRecycledViews(0, 30);
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
        if (isIntuitive) lp.weight = .47f;
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
        colC.setBackground(isIntuitive ? IntuitivePanel.create(context) : TvStyle.squircleCard(context, 18));
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

        if (isIntuitive) {
            previewVideo = new android.view.TextureView(context);
            previewVideo.setAlpha(0f);
            screenFrame.addView(previewVideo, new FrameLayout.LayoutParams(-1, -1));
        }

        // Live badge: "● EN DIRECTO"
        liveBadge = new TextView(context);
        liveBadge.setText("● " + (AppStrings.isEnglish(context) ? "LIVE" : "EN DIRECTO"));
        liveBadge.setTextSize(10);
        liveBadge.setTextColor(0xFFFFFFFF);
        liveBadge.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        liveBadge.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        liveBadge.setBackground(TvStyle.squircleSolid(context, 10, 0xFFE50914));
        FrameLayout.LayoutParams lbp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lbp.setMargins(0, dp(context, 10), dp(context, 10), 0);
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
        previewTitle.setTextSize(16);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        names.addView(previewTitle);

        previewProgram = new TextView(context);
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
        previewSchedule.setTextSize(11);
        previewSchedule.setTextColor(0xFFCACBD4);
        timeRow.addView(previewSchedule, new LinearLayout.LayoutParams(0, -2, 1));

        previewRemaining = new TextView(context);
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
                stopVideoPreview();
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
        nextTime.setTextSize(11);
        nextTime.setTextColor(0xFF8F91A2);
        nextDetail.addView(nextTime);

        nextTitle = new TextView(context);
        nextTitle.setTextSize(11);
        nextTitle.setTextColor(0xFFF8F8FB);
        nextTitle.setPadding(dp(context, 10), 0, 0, 0);
        nextTitle.setSingleLine(true);
        nextTitle.setEllipsize(TextUtils.TruncateAt.END);
        nextDetail.addView(nextTitle, new LinearLayout.LayoutParams(0, -2, 1));
        nextCard.addView(nextDetail);

        colC.addView(nextCard);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isIntuitive ? 0 : dp(context, 252), -1, isIntuitive ? .33f : 0);
        body.addView(colC, lp);
    }

    private boolean focusFirstWhenReady;
    private View entryFocus;

    public void requestFocusFirstItem() {
        focusFirstWhenReady = true;
        entryFocus = getRootView().findFocus();
        if (categoriesLoaded) focusFirstAfterLayout();
    }

    private void focusFirstAfterLayout() {
        if (!focusFirstWhenReady) return;
        focusFirstWhenReady = false;
        View originalFocus = entryFocus;
        entryFocus = null;
        CategoryList.focusFirstAfterLayout(channelList, originalFocus);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyFilterAndSort();
    }

    @Override protected void onDetachedFromWindow() {
        stopVideoPreview();
        browseTask.cancel();
        super.onDetachedFromWindow();
    }

    private void showCategoryCounts(BrowseFilter.Counts counts) {
        List<CategoryList.Item> categories = new ArrayList<>(counts.groups.size() + 3);
        boolean english = AppStrings.isEnglish(context);
        categories.add(new CategoryList.Item("all", english ? "All" : "Todas", "all_grid", counts.total));
        categories.add(new CategoryList.Item("favorites", english ? "Favorites" : "Favoritos", "star", counts.favorites));
        categories.add(new CategoryList.Item("resume", english ? "Keep watching" : "Seguir viendo", "resume", counts.recent));
        for (Map.Entry<String, Integer> entry : counts.groups.entrySet()) {
            String group = entry.getKey();
            categories.add(new CategoryList.Item("group:" + group, group, chooseIconForGroup(group), entry.getValue()));
        }
        categoryList.show(categories, selectedCatId);
        categoriesLoaded = true;
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

    private void applyCategoryRowStyle(LinearLayout row, LineIcon icon, TextView label, TextView count, boolean selected, boolean focused) {
        if (isIntuitive) {
            if (selected || focused) {
                row.setBackground(selected ? IntuitivePanel.button(context, focused) : IntuitivePanel.create(context));
                icon.setColor(0xFFFFFFFF);
                label.setTextColor(0xFFFFFFFF);
                label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                count.setTextColor(0xFFFFFFFF);
            } else {
                row.setBackground(IntuitivePanel.create(context));
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
        String category = selectedCatId, query = searchQuery;
        int sort = sortMode;
        boolean initial = !categoriesLoaded;
        boolean english = AppStrings.isEnglish(context);
        catHeaderBadge.setText(english ? "Loading…" : "Cargando…");
        browseTask.submit(() -> BrowseFilter.CHANNELS.run(repository.getLiveChannels(), repository,
                category, query, sort, initial), result -> {
            if (initial) showCategoryCounts(result.counts);
            filteredChannels = result.items;
            catHeaderTitle.setText("Canales • " + selectedCatLabel);
            catHeaderBadge.setText(filteredChannels.size() + (english ? " channels" : " canales"));
            channelAdapter.notifyDataSetChanged();
            if (filteredChannels.isEmpty()) clearPreview();
            else selectForPreview(filteredChannels.get(0));
            if (initial) focusFirstAfterLayout();
        });
    }

    private void selectForPreview(Channel c) {
        if (isIntuitive && currentSelectedChannel != c) {
            stopVideoPreview();
            postDelayed(startPreview, 650);
        }
        currentSelectedChannel = c;
        liveBadge.setText(c.url.startsWith("asset:///demo/") ? "DEMO" : (AppStrings.isEnglish(context) ? "● LIVE" : "● EN DIRECTO"));
        liveBadge.setVisibility(VISIBLE);
        btnPlay.setVisibility(VISIBLE);
        btnFav.setVisibility(VISIBLE);
        previewTitle.setText(c.name);

        long nowTime = System.currentTimeMillis();
        Program now = epgRepository.now(c.tvgId.isEmpty() ? c.name : c.tvgId);
        Program next = epgRepository.next(c.tvgId.isEmpty() ? c.name : c.tvgId);

        previewProgress.setVisibility(now == null ? GONE : VISIBLE);
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
            previewSchedule.setText("");
            previewRemaining.setText(AppStrings.isEnglish(context) ? "Live" : "En directo");
        }

        if (next != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            nextTime.setText(sdf.format(next.startMillis));
            nextTitle.setText(next.title);
        } else {
            nextTime.setText("");
            nextTitle.setText(AppStrings.isEnglish(context) ? "Schedule unavailable" : "Sin información de programación");
        }

        updateFavButtonState();

        if (isIntuitive && c.url.startsWith("asset:///demo/live/") && c.name.equals("DAZN LaLiga") && c.logoUrl.isEmpty()) {
            LogoLoader.clear(previewLogo);
            previewLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            previewLogo.setPadding(0, 0, 0, 0);
            previewLogo.setImageBitmap(Artwork.get(context, R.drawable.intu_sport, 640));
            previewLogoText.setVisibility(GONE);
        } else if (!c.logoUrl.isEmpty()) {
            previewLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int inset = dp(context, 20);
            previewLogo.setPadding(inset, inset, inset, inset);
            previewLogoText.setVisibility(GONE);
            LogoLoader.load(c.logoUrl, previewLogo);
        } else {
            LogoLoader.clear(previewLogo);
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
        stopVideoPreview();
        currentSelectedChannel = null;
        liveBadge.setVisibility(GONE);
        previewProgress.setVisibility(GONE);
        btnPlay.setVisibility(GONE);
        btnFav.setVisibility(GONE);
        nextTime.setText("");
        nextTitle.setText("");
        previewTitle.setText("");
        previewProgram.setText(AppStrings.isEnglish(context) ? "No channels available" : "No hay canales disponibles");
        previewSchedule.setText("");
        previewRemaining.setText("");
        LogoLoader.clear(previewLogo);
        previewLogoText.setVisibility(GONE);
    }

    // ADAPTER FOR CHANNEL ROWS
    private final class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowHolder> {
        ChannelRowAdapter() {
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) {
            if (position >= 0 && position < filteredChannels.size()) {
                return filteredChannels.get(position).id.hashCode();
            }
            return position;
        }

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
            row.setMinimumHeight(dp(context, isIntuitive ? 42 : 54));
            if (isIntuitive) {
                row.setPadding(dp(context, 10), dp(context, 3), dp(context, 10), dp(context, 3));
                row.setBackground(IntuitivePanel.create(context));
                RecyclerView.LayoutParams bounds = new RecyclerView.LayoutParams(-1, -2);
                bounds.bottomMargin = dp(context, 3);
                row.setLayoutParams(bounds);
            }

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
                    stopVideoPreview();
                    listener.onPlay(item, filteredChannels, filteredChannels.indexOf(item));
                }
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && item != null) {
                    selectForPreview(item);
                }
                if (hasFocus) {
                    if (isIntuitive) {
                        row.setBackground(TvStyle.squircleSolid(context, 12, 0x33007AFF).setCustomStroke(0xFF007AFF, 2.0f));
                        nameView.setTextColor(0xFFFFFFFF);
                        programView.setTextColor(0xFFCBD5E1);
                        timeView.setTextColor(0xFFFFFFFF);
                    } else {
                        row.setBackground(TvStyle.squircleSolid(context, 12, 0xFFE2E2E6));
                        nameView.setTextColor(0xFF14151B);
                        programView.setTextColor(0xFF434554);
                        timeView.setTextColor(0xFF14151B);
                    }
                } else {
                    row.setBackground(isIntuitive ? IntuitivePanel.create(context) : null);
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
                timeView.setText("");
                rowProgress.setVisibility(GONE);
            }

            if (c.logoUrl != null && !c.logoUrl.isEmpty()) {
                logoTextView.setVisibility(GONE);
                LogoLoader.load(c.logoUrl, logoView);
            } else {
                LogoLoader.clear(logoView);
                int local = isIntuitive && c.url.startsWith("asset:///demo/live/") ? IntuitiveArtwork.channel(c.tvgId) : 0;
                if (local != 0) logoView.setImageBitmap(Artwork.get(context, local, 96));
                logoTextView.setVisibility(local == 0 ? VISIBLE : GONE);
                String trimmed = c.name.trim();
                logoTextView.setText(trimmed.length() > 2 ? trimmed.substring(0, 2).toUpperCase(Locale.ROOT) : trimmed.toUpperCase(Locale.ROOT));
            }
        }
    }
}
