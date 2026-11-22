package tv.lunvoryx.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.Playlist;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.epg.GuideActivity;
import tv.lunvoryx.player.player.PlayerActivity;
import tv.lunvoryx.player.ui.AppStrings;
import tv.lunvoryx.player.ui.HomeBackdrop;
import tv.lunvoryx.player.ui.LineIcon;
import tv.lunvoryx.player.ui.LiveTvBrowseView;
import tv.lunvoryx.player.ui.LogoLoader;
import tv.lunvoryx.player.ui.MediaBrowseView;
import tv.lunvoryx.player.ui.ModernSwitch;
import tv.lunvoryx.player.ui.SearchViewLayout;
import tv.lunvoryx.player.ui.AddPlaylistViewLayout;
import tv.lunvoryx.player.ui.TvStyle;
import tv.lunvoryx.player.ui.SquircleGlassDrawable;
import tv.lunvoryx.player.ui.HomeScreen;
import tv.lunvoryx.player.ui.IntuitiveHomeScreen;
import tv.lunvoryx.player.ui.IntuitiveTopBar;
import tv.lunvoryx.player.ui.MediaDetailView;
import tv.lunvoryx.player.data.MediaKind;
import tv.lunvoryx.player.remote.QrCodeGenerator;
import tv.lunvoryx.player.remote.RemoteServer;
import tv.lunvoryx.player.tmdb.TmdbClient;
import tv.lunvoryx.player.updater.UpdateManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static tv.lunvoryx.player.ui.TvStyle.*;

public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PlaylistRepository repository;
    private SharedPreferences settings;
    private LinearLayout content, nav;
    private HomeBackdrop backdrop;
    private TextView status, subtitle;
    private RecyclerView grid;
    private final ArrayList<Channel> shown = new ArrayList<>();
    private ChannelAdapter adapter;
    private String section = "Inicio", group = "Todos", query = "", lastFocusedId = "";
    private boolean busy = false, loaded = false;
    private int queryVersion = 0;
    private static final int PICK_PLAYLIST = 41;
    private RemoteServer remoteServer;
    private AddPlaylistViewLayout addPlaylistLayout;
    private boolean startupUpdateChecked = false;
    private boolean isIntuitive = false;
    private IntuitiveTopBar intuitiveTopBar;
    private MediaDetailView currentDetailView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fullscreen(this);
        repository = new PlaylistRepository(this);
        settings = getSharedPreferences("display", MODE_PRIVATE);
        if (state != null) {
            section = state.getString("section", "Inicio");
            group = state.getString("group", "Todos");
            query = state.getString("query", "");
            lastFocusedId = state.getString("focus", "");
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        buildShell();
    }

    @Override protected void onResume() {
        super.onResume();
        fullscreen(this);
        if (!busy) {
            work(AppStrings.isEnglish(this) ? "Loading library…" : "Cargando biblioteca…", () -> {
                repository.load();
            }, () -> {
                loaded = true;
                render();
                if (!startupUpdateChecked) {
                    startupUpdateChecked = true;
                    checkStartupUpdate();
                }
            });
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("section", section);
        out.putString("group", group);
        out.putString("query", query);
        out.putString("focus", lastFocusedId);
    }

    @Override protected void onDestroy() {
        if (remoteServer != null) remoteServer.stop();
        if (addPlaylistLayout != null) addPlaylistLayout.stopServer();
        main.removeCallbacksAndMessages(null);
        io.shutdown();
        super.onDestroy();
    }

    private void buildShell() {
        isIntuitive = settings.getBoolean("intuitive_mode", false);
        FrameLayout scene = new FrameLayout(this);
        backdrop = new HomeBackdrop(this);
        backdrop.setIntuitive(isIntuitive);
        scene.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        if (isIntuitive) {
            nav = null;
            LinearLayout root = column(this);
            scene.addView(root, new FrameLayout.LayoutParams(-1, -1));

            intuitiveTopBar = new IntuitiveTopBar(this, new IntuitiveTopBar.Listener() {
                @Override public void onSectionSelected(String selectedSection) {
                    if ("TV en vivo".equalsIgnoreCase(selectedSection)) {
                        MainActivity.this.navigate("TV En Vivo");
                    } else {
                        MainActivity.this.navigate(selectedSection);
                    }
                }
                @Override public void onSearchClicked() {
                    MainActivity.this.navigate("Buscar");
                }
                @Override public void onBackClicked() {
                    handleBack();
                }
            });
            root.addView(intuitiveTopBar, new LinearLayout.LayoutParams(-1, dp(this, 58)));

            content = column(this);
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            setContentView(scene);
            render();
            return;
        }

        intuitiveTopBar = null;
        LinearLayout root = row(this);
        root.setGravity(Gravity.TOP);
        scene.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // Anti-slop Frosted Glass Sidebar with Continuous Squircle Right Corners
        nav = column(this);
        nav.setPadding(dp(this, 6), dp(this, 16), dp(this, 6), dp(this, 14));
        SquircleGlassDrawable sidebarGlass = TvStyle.squircleSurface(this, 22)
                .setCorners(false, true, true, false);
        nav.setBackground(sidebarGlass);

        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(this, 172), -1);
        np.setMargins(0, 0, 0, 0);
        root.addView(nav, np);

        // Header logo: clean transparent vector/PNG integration without box or cards
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.brand_header);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(this, 114), dp(this, 76));
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        bp.topMargin = dp(this, 4);
        bp.bottomMargin = dp(this, 20);
        nav.addView(brand, bp);

        String[] navKeys = {"Inicio", "TV En Vivo", "Películas", "Series", "Buscar", "Ajustes"};
        String[] icons = {"home", "tv", "movie", "series", "search", "settings"};
        View lastNav = null;
        for (int i = 0; i < navKeys.length; i++) {
            lastNav = addNav(navKeys[i], icons[i]);
        }

        nav.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        View listNav = addNav("Mis listas", "list");
        if (lastNav != null) {
            listNav.setNextFocusUpId(lastNav.getId());
            lastNav.setNextFocusDownId(listNav.getId());
        }

        content = column(this);
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(scene);
        render();
    }

    private View addNav(String key, String icon) {
        String label = AppStrings.get(this, "nav_" + keyToId(key));
        LinearLayout b = row(this);
        b.setId(View.generateViewId());
        b.setTag(key);
        b.setPadding(dp(this, 14), 0, dp(this, 12), 0);
        b.setFocusable(true);
        b.setClickable(true);

        LineIcon symbol = new LineIcon(this, icon);
        b.addView(symbol, new LinearLayout.LayoutParams(dp(this, 20), dp(this, 20)));

        TextView title = text(this, label, 14, 0xFFCACBD4);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = dp(this, 13);
        b.addView(title, tp);

        b.setContentDescription(label);
        b.setOnClickListener(v -> navigate(key));

        // Use custom drawable that matches reference image: active/focused is a soft light-grey pill (#D0D1D6) with dark bold text (#14151B)
        updateNavPillState(b, symbol, title, section.equals(key), false);

        b.setOnFocusChangeListener((v, focused) -> {
            updateNavPillState(b, symbol, title, section.equals(key), focused);
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(this, 42));
        p.bottomMargin = dp(this, 4);
        nav.addView(b, p);
        return b;
    }

    private String keyToId(String key) {
        switch (key) {
            case "Inicio": return "home";
            case "TV En Vivo": return "live";
            case "Películas": return "movies";
            case "Series": return "series";
            case "Buscar": return "search";
            case "Ajustes": return "settings";
            case "Mis listas": return "playlists";
            default: return "home";
        }
    }

    private void updateNavPillState(LinearLayout b, LineIcon symbol, TextView title, boolean isSelected, boolean isFocused) {
        if (isSelected && isFocused) {
            // Active and Focused: Solid white squircle pill with Apple lift and subtle pearl rim
            SquircleGlassDrawable pill = TvStyle.squircleSolid(this, 14, 0xFFFFFFFF)
                    .setCustomStroke(0x22000000, 1.0f);
            b.setBackground(pill);
            symbol.setColor(0xFF11131A);
            title.setTextColor(0xFF11131A);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            b.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
        } else if (isSelected) {
            // Selected active pill: Clean Apple white pill
            SquircleGlassDrawable pill = TvStyle.squircleSolid(this, 14, 0xFFFFFFFF)
                    .setCustomStroke(0x18000000, 0.8f);
            b.setBackground(pill);
            symbol.setColor(0xFF11131A);
            title.setTextColor(0xFF11131A);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            b.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        } else if (isFocused) {
            // Focused non-selected: Frosted glass squircle with Apple TV focus glow
            SquircleGlassDrawable pill = TvStyle.squircleCard(this, 14).setFocused(true);
            b.setBackground(pill);
            symbol.setColor(0xFFFFFFFF);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
        } else {
            b.setBackground(null);
            symbol.setColor(0xFFB5B6C2);
            title.setTextColor(0xFFCACBD4);
            title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            b.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void navigate(String key) {
        if (currentDetailView != null) {
            currentDetailView = null;
            if (intuitiveTopBar != null) {
                intuitiveTopBar.setShowBackButton(false);
            }
        }
        if (key.equals("Guía")) {
            startActivity(new Intent(this, GuideActivity.class));
            return;
        }
        section = key;
        group = "Todos";
        if (!key.equals("Buscar")) {
            query = "";
        }
        lastFocusedId = "";
        render();
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        queryVersion++;
        content.removeAllViews();
        grid = null;
        backdrop.setHome(section.equals("Inicio"));
        content.setPadding(dp(this, 20), section.equals("Inicio") ? 0 : dp(this, 26), dp(this, 24), section.equals("Inicio") ? 0 : dp(this, 16));

        // Refresh navigation pills visual selection
        if (nav != null) {
            for (int i = 0; i < nav.getChildCount(); i++) {
                View v = nav.getChildAt(i);
                if (v instanceof LinearLayout && v.getTag() != null) {
                    LinearLayout b = (LinearLayout) v;
                    boolean isSelected = section.equals(v.getTag());
                    boolean isFocused = v.isFocused();
                    if (b.getChildCount() >= 2 && b.getChildAt(0) instanceof LineIcon && b.getChildAt(1) instanceof TextView) {
                        updateNavPillState(b, (LineIcon) b.getChildAt(0), (TextView) b.getChildAt(1), isSelected, isFocused);
                        ((TextView) b.getChildAt(1)).setText(AppStrings.get(this, "nav_" + keyToId((String) v.getTag())));
                    }
                }
            }
        }

        if (intuitiveTopBar != null) {
            intuitiveTopBar.setSection(section.equals("TV En Vivo") ? "TV en vivo" : section);
            intuitiveTopBar.setShowBackButton(currentDetailView != null);
        }

        if (loaded && section.equals("Inicio")) {
            if (isIntuitive) {
                content.setPadding(0, 0, 0, 0);
                content.addView(new IntuitiveHomeScreen(this, repository, new IntuitiveHomeScreen.Listener() {
                    @Override public void onNavigate(String target) {
                        if ("TV en vivo".equalsIgnoreCase(target)) MainActivity.this.navigate("TV En Vivo");
                        else MainActivity.this.navigate(target);
                    }
                    @Override public void onReloadPlaylist() {
                        work(AppStrings.isEnglish(MainActivity.this) ? "Reloading playlist…" : "Recargando lista…", () -> {
                            repository.load();
                        }, () -> {
                            Toast.makeText(MainActivity.this, AppStrings.isEnglish(MainActivity.this) ? "Playlist reloaded successfully" : "Lista recargada correctamente", Toast.LENGTH_SHORT).show();
                            render();
                        });
                    }
                    @Override public void onOpenSettings() {
                        MainActivity.this.navigate("Ajustes");
                    }
                }), new LinearLayout.LayoutParams(-1, -1));
                return;
            }

            content.addView(new HomeScreen(this, repository, new HomeScreen.Actions() {
                public void navigate(String value) {
                    if ("TV en vivo".equalsIgnoreCase(value)) MainActivity.this.navigate("TV En Vivo");
                    else MainActivity.this.navigate(value);
                }
                public void addList() { showAddOptions(); }
                public void demo() {
                    work(AppStrings.isEnglish(MainActivity.this) ? "Adding demo list…" : "Añadiendo lista de prueba…",
                            () -> repository.addDemoPlaylist(),
                            () -> { section = "Inicio"; render(); });
                }
                public void play(List<Channel> queue, int index) {
                    Channel c = queue.get(index);
                    lastFocusedId = c.id;
                    work(AppStrings.isEnglish(MainActivity.this) ? "Opening content…" : "Abriendo contenido…",
                            () -> repository.recordPlayed(c.id),
                            () -> PlayerActivity.start(MainActivity.this, queue, index));
                }
            }), new LinearLayout.LayoutParams(-1, -1));

            if (getCurrentFocus() == null && nav != null) {
                for (int i = 0; i < nav.getChildCount(); i++) {
                    if ("Inicio".equals(nav.getChildAt(i).getTag())) nav.getChildAt(i).requestFocus();
                }
            }
            return;
        }

        LinearLayout header = row(this);
        String headerTitle = AppStrings.get(this, "nav_" + keyToId(section));
        TextView title = text(this, headerTitle, 29, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm a");
        clock.setTextSize(16);
        clock.setTextColor(MUTED);
        header.addView(clock);
        content.addView(header);

        subtitle = text(this, "", 13, MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(this, 7);
        sp.bottomMargin = dp(this, 20);
        content.addView(subtitle, sp);

        if (!loaded) {
            subtitle.setText(AppStrings.isEnglish(this) ? "Preparing your library…" : "Preparando tu biblioteca…");
            return;
        }

        if (section.equals("Buscar")) {
            renderSearchSection();
            return;
        }
        if (section.equals("TV En Vivo")) {
            renderLiveTvBrowseSection();
            return;
        }
        if (section.equals("Películas") || section.equals("Series")) {
            renderMediaBrowseSection(section.equals("Películas"));
            return;
        }
        if (section.equals("Mis listas")) {
            renderPlaylists();
            return;
        }
        if (section.equals("Ajustes")) {
            renderSettings();
            return;
        }
        if (repository.getAllChannels().isEmpty()) {
            renderWelcome();
            return;
        }
        renderChannels();
    }

    private void renderLiveTvBrowseSection() {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        LiveTvBrowseView browseView = new LiveTvBrowseView(this, repository, new LiveTvBrowseView.Listener() {
            @Override public void onPlay(Channel channel, List<Channel> list, int index) {
                lastFocusedId = channel.id;
                work(AppStrings.isEnglish(MainActivity.this) ? "Opening channel…" : "Abriendo canal…",
                        () -> repository.recordPlayed(channel.id),
                        () -> PlayerActivity.start(MainActivity.this, new ArrayList<>(list), Math.max(0, index)));
            }
            @Override public void onToggleFavorite(Channel channel) {
                repository.toggleFavorite(channel.id);
            }
        }, isIntuitive);
        content.addView(browseView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderMediaBrowseSection(boolean isMovies) {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        MediaBrowseView browseView = new MediaBrowseView(this, repository, isMovies, new MediaBrowseView.Listener() {
            @Override public void onPlay(Channel channel, List<Channel> list, int index) {
                if (isIntuitive) {
                    showMediaDetails(channel, isMovies, list, index);
                } else {
                    lastFocusedId = channel.id;
                    work(AppStrings.isEnglish(MainActivity.this) ? "Opening…" : "Abriendo…",
                            () -> repository.recordPlayed(channel.id),
                            () -> PlayerActivity.start(MainActivity.this, new ArrayList<>(list), Math.max(0, index)));
                }
            }
            @Override public void onOpenDetails(Channel channel, List<Channel> list, int index) {
                if (isIntuitive) {
                    showMediaDetails(channel, isMovies, list, index);
                } else {
                    onPlay(channel, list, index);
                }
            }
            @Override public void onToggleFavorite(Channel channel) {
                repository.toggleFavorite(channel.id);
            }
        }, isIntuitive);
        content.addView(browseView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showMediaDetails(Channel channel, boolean isMovies, List<Channel> list, int index) {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        if (intuitiveTopBar != null) {
            intuitiveTopBar.setShowBackButton(true);
        }
        currentDetailView = new MediaDetailView(this, channel, isMovies, repository, new MediaDetailView.Listener() {
            @Override public void onPlay(Channel c) {
                lastFocusedId = c.id;
                work(AppStrings.isEnglish(MainActivity.this) ? "Opening…" : "Abriendo…",
                        () -> repository.recordPlayed(c.id),
                        () -> PlayerActivity.start(MainActivity.this, new ArrayList<>(list), Math.max(0, index)));
            }
            @Override public void onToggleFavorite(Channel c) {
                repository.toggleFavorite(c.id);
            }
            @Override public void onBack() {
                closeDetailView();
            }
        });
        content.addView(currentDetailView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void closeDetailView() {
        if (currentDetailView != null) {
            currentDetailView = null;
            if (intuitiveTopBar != null) {
                intuitiveTopBar.setShowBackButton(false);
            }
            render();
        }
    }

    private void renderSearchSection() {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        SearchViewLayout searchView = new SearchViewLayout(this, repository, new SearchViewLayout.Listener() {
            @Override public void onPlay(Channel channel, List<Channel> list, int index) {
                lastFocusedId = channel.id;
                work(AppStrings.isEnglish(MainActivity.this) ? "Opening…" : "Abriendo…",
                        () -> repository.recordPlayed(channel.id),
                        () -> PlayerActivity.start(MainActivity.this, new ArrayList<>(list), Math.max(0, index)));
            }
            @Override public void onToggleFavorite(Channel channel) {
                repository.toggleFavorite(channel.id);
            }
            @Override public void onShowDetails(Channel channel) {
                // Info / details
            }
        });

        if (nav != null) {
            for (int i = 0; i < nav.getChildCount(); i++) {
                View v = nav.getChildAt(i);
                if ("Buscar".equals(v.getTag()) && searchView.getSearchField() != null) {
                    v.setNextFocusRightId(searchView.getSearchField().getId());
                }
            }
        }

        content.addView(searchView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderWelcome() {
        subtitle.setText(AppStrings.isEnglish(this) ? "A place for all your channels." : "Un lugar para todos tus canales.");
        LinearLayout intro = row(this);
        intro.setPadding(dp(this, 28), dp(this, 26), dp(this, 28), dp(this, 26));
        intro.setBackground(shape(this, PANEL, 16));

        LinearLayout copy = column(this);
        intro.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView heading = text(this, AppStrings.isEnglish(this) ? "Your next story\nstarts here." : "Tu próxima historia\nempieza aquí.", 34, TEXT);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLineSpacing(dp(this, 3), 1);
        copy.addView(heading);

        TextView body = text(this, AppStrings.isEnglish(this) ? "Add your IPTV playlist and enjoy\nyour channels with your remote." : "Añade tu lista IPTV y disfruta\nde tus canales con el mando.", 16, MUTED);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(this, 18);
        bp.bottomMargin = dp(this, 23);
        copy.addView(body, bp);

        TextView add = button(this, AppStrings.get(this, "add_list"), this::showAddOptions);
        copy.addView(add, new LinearLayout.LayoutParams(-2, -2));

        ImageView symbol = new ImageView(this);
        symbol.setImageResource(R.drawable.app_logo);
        symbol.setAlpha(.9f);
        intro.addView(symbol, new LinearLayout.LayoutParams(dp(this, 145), dp(this, 145)));
        content.addView(intro, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout lower = row(this);
        TextView note = text(this, AppStrings.isEnglish(this) ? "Want to try first?" : "¿Quieres probar primero?", 13, MUTED);
        lower.addView(note, new LinearLayout.LayoutParams(0, -2, 1));

        TextView demo = button(this, AppStrings.get(this, "demo_list"), () -> work(AppStrings.isEnglish(this) ? "Adding demo list…" : "Añadiendo lista de prueba…", () -> repository.addDemoPlaylist(), () -> { section = "Películas"; render(); }));
        lower.addView(demo);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(this, 18);
        content.addView(lower, lp);
        add.requestFocus();
    }

    private void renderChannels() {
        LinearLayout filters = row(this);
        TextView groups = button(this, AppStrings.get(this, "filter_group") + group + "  ▾", this::chooseGroup);
        filters.addView(groups, new LinearLayout.LayoutParams(0, dp(this, 43), 1));

        TextView search = button(this, query.isEmpty() ? AppStrings.get(this, "search_action") : AppStrings.get(this, "search_action") + ": " + query, this::searchDialog);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(this, 160), dp(this, 43));
        p.leftMargin = dp(this, 8);
        filters.addView(search, p);

        TextView favorite = button(this, AppStrings.get(this, "favorites"), () -> navigate(section.equals("Favoritos") ? "Todos" : "Favoritos"));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, dp(this, 43));
        fp.leftMargin = dp(this, 8);
        filters.addView(favorite, fp);

        TextView guide = button(this, AppStrings.get(this, "guide"), () -> navigate("Guía"));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, dp(this, 43));
        gp.leftMargin = dp(this, 8);
        filters.addView(guide, gp);
        content.addView(filters);

        grid = new RecyclerView(this);
        grid.setId(View.generateViewId());
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(this, 14), 0, dp(this, 8));
        grid.setHasFixedSize(true);
        adapter = new ChannelAdapter();
        grid.setAdapter(adapter);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = text(this, AppStrings.isEnglish(this) ? "OK  Play / Info          Hold OK  Favorite          Menu  Options" : "OK  Ver canal          Mantener OK  Favorito          Menú  Opciones", 11, MUTED);
        content.addView(hint);
        filterChannels(true);
    }

    private void filterChannels(boolean restoreFocus) {
        final int version = ++queryVersion;
        final String requestedSection = section, requestedGroup = group, requestedQuery = query;
        final List<Channel> all = repository.getAllChannels();
        final Set<String> favorites = repository.getFavoriteIds();
        final List<String> recents = repository.getRecentIds();
        final boolean alphabetical = settings.getBoolean("alphabetical", false);

        io.execute(() -> {
            ArrayList<Channel> result = new ArrayList<>();
            String normalized = requestedQuery.toLowerCase(Locale.ROOT);
            for (Channel c : all) {
                MediaKind kind = MediaKind.classify(c);
                if (requestedSection.equals("TV En Vivo") && kind != MediaKind.LIVE) continue;
                if (requestedSection.equals("Películas") && kind != MediaKind.MOVIE) continue;
                if (requestedSection.equals("Series") && kind != MediaKind.SERIES) continue;
                if (requestedSection.equals("Favoritos") && !favorites.contains(c.id)) continue;
                if (requestedSection.equals("Recientes") && !recents.contains(c.id)) continue;
                if (!requestedGroup.equals("Todos") && !c.group.equals(requestedGroup)) continue;
                if (!normalized.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(normalized) && !c.group.toLowerCase(Locale.ROOT).contains(normalized)) continue;
                result.add(c);
            }
            if (requestedSection.equals("Recientes")) Collections.sort(result, (a, b) -> Integer.compare(recents.indexOf(a.id), recents.indexOf(b.id)));
            else if (alphabetical) Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));

            main.post(() -> {
                if (isDestroyed() || version != queryVersion || grid == null) return;
                shown.clear();
                shown.addAll(result);
                adapter.notifyDataSetChanged();

                if (result.isEmpty()) {
                    if (requestedSection.equals("Buscar")) {
                        subtitle.setText(query.isEmpty() ? AppStrings.get(MainActivity.this, "search_prompt") : AppStrings.get(MainActivity.this, "empty_search"));
                    } else if (requestedSection.equals("Favoritos")) {
                        subtitle.setText(AppStrings.isEnglish(MainActivity.this) ? "Hold OK on a channel to save it here." : "Mantén OK sobre un canal para guardarlo aquí.");
                    } else if (requestedSection.equals("Recientes")) {
                        subtitle.setText(AppStrings.isEnglish(MainActivity.this) ? "Watched channels will appear here." : "Los contenidos que veas aparecerán aquí.");
                    } else {
                        subtitle.setText(AppStrings.isEnglish(MainActivity.this) ? "No content in this category." : "Tu lista no tiene contenido en esta categoría o con estos filtros.");
                    }
                } else {
                    subtitle.setText(result.size() + (AppStrings.isEnglish(MainActivity.this) ? " items in your library" : " contenidos en tu biblioteca") + (query.isEmpty() ? "" : " — «" + query + "»"));
                }

                if (restoreFocus && !shown.isEmpty()) {
                    int target = 0;
                    for (int i = 0; i < shown.size(); i++) {
                        if (shown.get(i).id.equals(lastFocusedId)) {
                            target = i;
                            break;
                        }
                    }
                    restoreGridFocus(target, version);
                }
            });
        });
    }

    private void restoreGridFocus(int position, int version) {
        final RecyclerView targetGrid = grid;
        targetGrid.scrollToPosition(position);
        targetGrid.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            int passes;
            @Override public void onGlobalLayout() {
                RecyclerView.ViewHolder holder = targetGrid.findViewHolderForAdapterPosition(position);
                boolean valid = version == queryVersion && grid == targetGrid && !isDestroyed();
                if (!valid || holder != null || ++passes >= 3) {
                    if (targetGrid.getViewTreeObserver().isAlive()) targetGrid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (valid && holder != null) holder.itemView.requestFocus();
                }
            }
        });
        targetGrid.requestLayout();
    }

    private void chooseGroup() {
        TreeSet<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Channel c : repository.getAllChannels()) groups.add(c.group);
        ArrayList<String> values = new ArrayList<>();
        values.add("Todos");
        values.addAll(groups);
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.isEnglish(this) ? "Channel Groups" : "Grupos de canales")
                .setSingleChoiceItems(values.toArray(new String[0]), values.indexOf(group), (d, i) -> {
                    group = values.get(i);
                    d.dismiss();
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Close" : "Cerrar", null)
                .show();
    }

    private void searchDialog() {
        EditText field = input(AppStrings.isEnglish(this) ? "Name or group" : "Nombre o grupo", query, false);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(AppStrings.isEnglish(this) ? "Search" : "Buscar canal")
                .setView(field)
                .setPositiveButton(AppStrings.get(this, "search_action"), (d, w) -> {
                    query = field.getText().toString().trim();
                    render();
                })
                .setNeutralButton(AppStrings.isEnglish(this) ? "Clear" : "Limpiar", (d, w) -> {
                    query = "";
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .create();
        field.setOnEditorActionListener((v, action, event) -> {
            query = field.getText().toString().trim();
            dialog.dismiss();
            render();
            return true;
        });
        dialog.show();
        field.requestFocus();
    }

    private void play(Channel c) {
        lastFocusedId = c.id;
        final ArrayList<Channel> queue = new ArrayList<>(shown);
        final int index = queue.indexOf(c);
        work(AppStrings.isEnglish(this) ? "Opening channel…" : "Abriendo canal…",
                () -> repository.recordPlayed(c.id),
                () -> PlayerActivity.start(this, queue, Math.max(0, index)));
    }

    private void toggleFavorite(Channel c) {
        work(AppStrings.isEnglish(this) ? "Saving favorite…" : "Guardando favorito…", () -> repository.toggleFavorite(c.id), () -> {
            Toast.makeText(this, repository.isFavorite(c.id) ?
                    (AppStrings.isEnglish(this) ? "Added to favorites" : "Añadido a favoritos") :
                    (AppStrings.isEnglish(this) ? "Removed from favorites" : "Eliminado de favoritos"), Toast.LENGTH_SHORT).show();
            filterChannels(true);
        });
    }

    private void channelOptions(Channel c) {
        boolean hasTmdb = TmdbClient.hasApiKey(this);
        List<String> options = new ArrayList<>();
        options.add(AppStrings.isEnglish(this) ? "Play" : "Reproducir");
        options.add(repository.isFavorite(c.id) ? (AppStrings.isEnglish(this) ? "Remove from favorites" : "Quitar de favoritos") : (AppStrings.isEnglish(this) ? "Add to favorites" : "Añadir a favoritos"));
        if (hasTmdb) {
            options.add(AppStrings.isEnglish(this) ? "View Info & Trailer (TMDB)" : "Ver ficha y tráiler (TMDB)");
        }

        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(options.toArray(new String[0]), (d, i) -> {
                    if (i == 0) play(c);
                    else if (i == 1) toggleFavorite(c);
                    else if (i == 2) showTmdbDetailsDialog(c);
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Close" : "Cerrar", null)
                .show();
    }

    private void showTmdbDetailsDialog(Channel c) {
        work(AppStrings.isEnglish(this) ? "Fetching TMDB details…" : "Obteniendo datos de TMDB…", () -> {
            boolean isMovie = MediaKind.classify(c) == MediaKind.MOVIE;
            TmdbClient.Details details = TmdbClient.searchAndGetDetails(this, c.name, isMovie, AppStrings.getLang(this));
            main.post(() -> {
                if (details == null) {
                    Toast.makeText(this, AppStrings.isEnglish(this) ? "No TMDB information found for this title." : "No se encontró información en TMDB para este título.", Toast.LENGTH_SHORT).show();
                    return;
                }
                displayTmdbModal(details, c);
            });
        }, () -> {});
    }

    private void displayTmdbModal(TmdbClient.Details d, Channel c) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = column(this);
        layout.setPadding(dp(this, 20), dp(this, 12), dp(this, 20), dp(this, 12));

        if (d.posterUrl != null) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LogoLoader.load(d.posterUrl, poster);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(this, 120), dp(this, 180));
            pp.gravity = Gravity.CENTER_HORIZONTAL;
            pp.bottomMargin = dp(this, 12);
            layout.addView(poster, pp);
        }

        TextView title = text(this, d.title + (d.releaseDate != null && !d.releaseDate.isEmpty() ? " (" + d.releaseDate + ")" : ""), 18, TEXT);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        layout.addView(title);

        if (d.rating > 0) {
            TextView rating = text(this, "★ " + String.format(Locale.ROOT, "%.1f", d.rating) + " / 10", 14, 0xFFFFD700);
            rating.setPadding(0, dp(this, 4), 0, dp(this, 8));
            layout.addView(rating);
        }

        if (d.overview != null && !d.overview.isEmpty()) {
            TextView ov = text(this, d.overview, 13, MUTED);
            ov.setLineSpacing(dp(this, 3), 1);
            layout.addView(ov);
        }

        if (!d.cast.isEmpty()) {
            TextView castLabel = text(this, "\n" + (AppStrings.isEnglish(this) ? "Cast:" : "Reparto:"), 13, TEXT);
            castLabel.setTypeface(Typeface.DEFAULT_BOLD);
            layout.addView(castLabel);
            for (String actor : d.cast) {
                TextView act = text(this, "• " + actor, 12, MUTED);
                layout.addView(act);
            }
        }

        scroll.addView(layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(AppStrings.isEnglish(this) ? "Media Info" : "Ficha de contenido")
                .setView(scroll)
                .setPositiveButton(AppStrings.isEnglish(this) ? "Play" : "Reproducir", (dlg, which) -> play(c));

        if (d.trailerUrl != null) {
            builder.setNeutralButton(AppStrings.isEnglish(this) ? "Trailer" : "Ver Tráiler", (dlg, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(d.trailerUrl));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "No browser found to open trailer", Toast.LENGTH_SHORT).show();
                }
            });
        }

        builder.setNegativeButton(AppStrings.isEnglish(this) ? "Close" : "Cerrar", null).show();
    }

    private void renderPlaylists() {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);

        if (addPlaylistLayout != null) {
            addPlaylistLayout.stopServer();
        }

        addPlaylistLayout = new AddPlaylistViewLayout(this, repository, new AddPlaylistViewLayout.Listener() {
            @Override public void onImportUrl(String name, String url) {
                work(AppStrings.isEnglish(MainActivity.this) ? "Importing list…" : "Importando lista…",
                        () -> repository.importUrl(name, url),
                        () -> {
                            section = "TV En Vivo";
                            group = "Todos";
                            render();
                            Toast.makeText(MainActivity.this, (AppStrings.isEnglish(MainActivity.this) ? "Playlist «" : "Lista «") + name + (AppStrings.isEnglish(MainActivity.this) ? "» imported!" : "» importada con éxito!"), Toast.LENGTH_LONG).show();
                        });
            }

            @Override public void onImportContent(String name, String content) {
                work(AppStrings.isEnglish(MainActivity.this) ? "Importing list…" : "Importando lista…",
                        () -> repository.importContent(name, content),
                        () -> {
                            section = "TV En Vivo";
                            group = "Todos";
                            render();
                            Toast.makeText(MainActivity.this, (AppStrings.isEnglish(MainActivity.this) ? "Playlist «" : "Lista «") + name + (AppStrings.isEnglish(MainActivity.this) ? "» imported!" : "» importada con éxito!"), Toast.LENGTH_LONG).show();
                        });
            }

            @Override public void onSelectFile() {
                chooseFile();
            }

            @Override public void onManagePlaylists() {
                showManagePlaylistsDialog();
            }
        });

        if (nav != null) {
            for (int i = 0; i < nav.getChildCount(); i++) {
                View v = nav.getChildAt(i);
                if ("Mis listas".equals(v.getTag()) && addPlaylistLayout.getFirstFocusable() != null) {
                    v.setNextFocusRightId(addPlaylistLayout.getFirstFocusable().getId());
                }
            }
        }

        content.addView(addPlaylistLayout, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showManagePlaylistsDialog() {
        List<Playlist> lists = repository.getPlaylists();
        if (lists.isEmpty()) {
            Toast.makeText(this, AppStrings.isEnglish(this) ? "No saved playlists" : "No hay listas guardadas", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[lists.size() + 1];
        for (int i = 0; i < lists.size(); i++) {
            Playlist p = lists.get(i);
            names[i] = p.name + " (" + p.channels.size() + (AppStrings.isEnglish(this) ? " channels)" : " canales)");
        }
        names[lists.size()] = AppStrings.get(this, "demo_list");

        new AlertDialog.Builder(this)
                .setTitle(AppStrings.isEnglish(this) ? "Saved Playlists" : "Mis listas guardadas")
                .setItems(names, (dialog, which) -> {
                    if (which == lists.size()) {
                        work(AppStrings.isEnglish(this) ? "Adding demo list…" : "Añadiendo lista de prueba…",
                                () -> repository.addDemoPlaylist(),
                                () -> {
                                    section = "TV En Vivo";
                                    render();
                                });
                    } else {
                        playlistOptions(lists.get(which));
                    }
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Close" : "Cerrar", null)
                .show();
    }

    private void playlistOptions(Playlist p) {
        if (!operationAvailable()) return;
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(new String[]{AppStrings.isEnglish(this) ? "Refresh channels" : "Actualizar canales", AppStrings.isEnglish(this) ? "Delete playlist" : "Eliminar lista"}, (d, i) -> {
                    if (i == 0) work(AppStrings.isEnglish(this) ? "Updating list…" : "Actualizando lista…", () -> repository.refresh(p.id), this::render);
                    else new AlertDialog.Builder(this)
                            .setTitle(AppStrings.isEnglish(this) ? "Delete «" + p.name + "»?" : "¿Eliminar «" + p.name + "»?")
                            .setMessage(AppStrings.isEnglish(this) ? "It will be deleted from device." : "Se eliminará del dispositivo. Puedes volver a importarla.")
                            .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                            .setPositiveButton(AppStrings.isEnglish(this) ? "Delete" : "Eliminar", (dialog, which) -> work(AppStrings.isEnglish(this) ? "Deleting list…" : "Eliminando lista…", () -> repository.removePlaylist(p.id), this::render))
                            .show();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Close" : "Cerrar", null)
                .show();
    }

    private void renderSettings() {
        subtitle.setText(AppStrings.get(this, "subtitle_settings"));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout settingsList = column(this);
        settingsList.setPadding(0, 0, 0, dp(this, 32));
        scroller.addView(settingsList);
        content.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        // Group 1: General
        TextView genHeader = text(this, AppStrings.get(this, "section_general"), 15, TEXT);
        genHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        settingsList.addView(genHeader);

        LinearLayout genCard = column(this);
        genCard.setBackground(TvStyle.squircleCard(this, 18));
        genCard.setPadding(0, dp(this, 4), 0, dp(this, 4));

        LinearLayout.LayoutParams gcp = new LinearLayout.LayoutParams(-1, -2);
        gcp.topMargin = dp(this, 10);
        gcp.bottomMargin = dp(this, 22);
        settingsList.addView(genCard, gcp);

        // Row 0: Modo Intuitivo (Switch)
        View row0 = addSettingSwitchRow(genCard, "brand", AppStrings.get(this, "setting_intuitive_mode"), AppStrings.get(this, "setting_intuitive_sub"), "intuitive_mode", false, true);

        // Row 1: Logotipos de canales (Switch)
        View row1 = addSettingSwitchRow(genCard, "tv", AppStrings.get(this, "setting_logos"), null, "logos", true, true);

        // Row 2: Orden alfabético (Switch)
        addSettingSwitchRow(genCard, "sort", AppStrings.get(this, "setting_alpha"), null, "alphabetical", false, true);

        // Row 3: Configurar guía de programación (Chevron + Subtitle)
        addSettingActionRow(genCard, "calendar", AppStrings.get(this, "setting_guide"), AppStrings.get(this, "setting_guide_sub"), null, () -> startActivity(new Intent(this, GuideActivity.class)), true);

        // Row 4: Comprobar actualizaciones (Chevron)
        addSettingActionRow(genCard, "refresh", AppStrings.get(this, "setting_updates"), null, null, this::checkUpdates, true);

        // Row 5: Idioma (Value + Chevron)
        String langVal = AppStrings.isEnglish(this) ? "English" : "Español";
        addSettingActionRow(genCard, "globe", AppStrings.get(this, "setting_lang"), null, langVal, this::chooseLanguage, false);

        // Group 2: Reproducción
        TextView repHeader = text(this, AppStrings.get(this, "section_playback"), 15, TEXT);
        repHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        settingsList.addView(repHeader);

        LinearLayout repCard = column(this);
        repCard.setBackground(TvStyle.squircleCard(this, 18));
        repCard.setPadding(0, dp(this, 4), 0, dp(this, 4));

        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(-1, -2);
        rcp.topMargin = dp(this, 10);
        rcp.bottomMargin = dp(this, 22);
        settingsList.addView(repCard, rcp);

        // Row 1: Reproductor de vídeo (Value: Automático >)
        String playerVal = settings.getString("video_player_type", AppStrings.get(this, "val_auto"));
        addSettingActionRow(repCard, "player", AppStrings.get(this, "setting_video_player"), null, playerVal, this::choosePlayerType, true);

        // Row 2: Subtítulos (Value: Preferencias >)
        addSettingActionRow(repCard, "subtitles", AppStrings.get(this, "setting_subtitles"), null, AppStrings.get(this, "val_prefs"), this::chooseSubtitles, true);

        // Row 3: Buffer de reproducción (Value: Normal >)
        String bufVal = settings.getString("playback_buffer_type", AppStrings.get(this, "val_normal"));
        addSettingActionRow(repCard, "buffer", AppStrings.get(this, "setting_buffer"), null, bufVal, this::chooseBufferType, false);

        // Group 3: Más opciones (TMDB, Actualizaciones por Wi-Fi)
        TextView moreHeader = text(this, AppStrings.isEnglish(this) ? "Advanced & Features" : "Avanzado y Conectividad", 15, TEXT);
        moreHeader.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        settingsList.addView(moreHeader);

        LinearLayout moreCard = column(this);
        moreCard.setBackground(TvStyle.squircleCard(this, 18));
        moreCard.setPadding(0, dp(this, 4), 0, dp(this, 4));

        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(-1, -2);
        mcp.topMargin = dp(this, 10);
        settingsList.addView(moreCard, mcp);

        // TMDB
        String tmdbVal = TmdbClient.hasApiKey(this) ? AppStrings.get(this, "configured") : AppStrings.get(this, "not_configured");
        addSettingActionRow(moreCard, "key", AppStrings.get(this, "setting_tmdb"), AppStrings.get(this, "setting_tmdb_sub"), tmdbVal, this::configureTmdbKey, true);

        // GitHub Repository
        String ghRepo = settings.getString("github_repo", "David/Lunvoryx-Player");
        addSettingActionRow(moreCard, "brand", AppStrings.isEnglish(this) ? "GitHub Repository" : "Repositorio de GitHub", AppStrings.isEnglish(this) ? "Releases & updates source" : "Origen de actualizaciones y releases", ghRepo, this::configureGitHubRepo, false);

        // Set D-pad focus from sidebar to first setting row
        if (nav != null) {
            for (int i = 0; i < nav.getChildCount(); i++) {
                View v = nav.getChildAt(i);
                if ("Ajustes".equals(v.getTag())) v.setNextFocusRightId(row0.getId());
            }
        } else {
            row0.post(row0::requestFocus);
        }
    }

    private View addSettingSwitchRow(LinearLayout card, String iconKind, String titleText, String subtitleText, String prefKey, boolean defaultVal, boolean addDivider) {
        LinearLayout row = row(this);
        row.setId(View.generateViewId());
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(this, 18), dp(this, 12), dp(this, 18), dp(this, 12));
        row.setMinimumHeight(dp(this, 52));

        LineIcon icon = new LineIcon(this, iconKind);
        icon.setColor(0xFFCACBD4);
        row.addView(icon, new LinearLayout.LayoutParams(dp(this, 22), dp(this, 22)));

        LinearLayout textCol = column(this);
        textCol.setPadding(dp(this, 16), 0, 0, 0);
        TextView title = text(this, titleText, 14, 0xFFF2F3F8);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textCol.addView(title);
        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView sub = text(this, subtitleText, 11, 0xFF8F91A2);
            sub.setPadding(0, dp(this, 2), 0, 0);
            textCol.addView(sub);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        ModernSwitch ms = new ModernSwitch(this);
        boolean currentVal = settings.getBoolean(prefKey, defaultVal);
        ms.setChecked(currentVal, false);
        row.addView(ms, new LinearLayout.LayoutParams(dp(this, 40), dp(this, 24)));

        row.setOnClickListener(v -> {
            boolean next = !ms.isChecked();
            ms.setChecked(next, true);
            settings.edit().putBoolean(prefKey, next).apply();
            if ("intuitive_mode".equals(prefKey)) {
                buildShell();
                return;
            }
            if ("logos".equals(prefKey) || "alphabetical".equals(prefKey)) {
                // Background update without resetting full view scroll
            }
        });

        row.setOnFocusChangeListener((v, focused) -> {
            applyRowFocus(row, icon, title, focused);
        });

        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        if (addDivider) {
            View div = new View(this);
            div.setBackgroundColor(0x1FFFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(this, 1));
            lp.setMargins(dp(this, 56), 0, dp(this, 18), 0);
            card.addView(div, lp);
        }

        return row;
    }

    private View addSettingActionRow(LinearLayout card, String iconKind, String titleText, String subtitleText, String rightValue, Runnable action, boolean addDivider) {
        LinearLayout row = row(this);
        row.setId(View.generateViewId());
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(this, 18), dp(this, 12), dp(this, 18), dp(this, 12));
        row.setMinimumHeight(dp(this, 52));

        LineIcon icon = new LineIcon(this, iconKind);
        icon.setColor(0xFFCACBD4);
        row.addView(icon, new LinearLayout.LayoutParams(dp(this, 22), dp(this, 22)));

        LinearLayout textCol = column(this);
        textCol.setPadding(dp(this, 16), 0, 0, 0);
        TextView title = text(this, titleText, 14, 0xFFF2F3F8);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textCol.addView(title);
        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView sub = text(this, subtitleText, 11, 0xFF8F91A2);
            sub.setPadding(0, dp(this, 2), 0, 0);
            textCol.addView(sub);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        if (rightValue != null && !rightValue.isEmpty()) {
            TextView valView = text(this, rightValue, 13, 0xFFB4B6C5);
            valView.setPadding(0, 0, dp(this, 6), 0);
            row.addView(valView);
        }

        LineIcon chevron = new LineIcon(this, "chevron");
        chevron.setColor(0xFF8F91A2);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(this, 16), dp(this, 16)));

        row.setOnClickListener(v -> action.run());

        row.setOnFocusChangeListener((v, focused) -> {
            applyRowFocus(row, icon, title, focused);
            chevron.setColor(focused ? 0xFF14151B : 0xFF8F91A2);
        });

        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        if (addDivider) {
            View div = new View(this);
            div.setBackgroundColor(0x1FFFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(this, 1));
            lp.setMargins(dp(this, 56), 0, dp(this, 18), 0);
            card.addView(div, lp);
        }

        return row;
    }

    private void applyRowFocus(LinearLayout row, LineIcon icon, TextView title, boolean focused) {
        if (focused) {
            SquircleGlassDrawable focusPill = TvStyle.squircleSolid(this, 12, 0xFFE2E2E6);
            row.setBackground(focusPill);
            icon.setColor(0xFF14151B);
            title.setTextColor(0xFF14151B);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        } else {
            row.setBackground(null);
            icon.setColor(0xFFCACBD4);
            title.setTextColor(0xFFF2F3F8);
            title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    private void choosePlayerType() {
        String[] types = {AppStrings.get(this, "val_auto"), "Media3 ExoPlayer", "ExoPlayer (HLS/Dash)", "VLC / Externo"};
        int selected = settings.getInt("video_player_index", 0);
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.get(this, "setting_video_player"))
                .setSingleChoiceItems(types, selected, (d, which) -> {
                    settings.edit().putInt("video_player_index", which)
                            .putString("video_player_type", types[which]).apply();
                    d.dismiss();
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .show();
    }

    private void chooseSubtitles() {
        String[] opts = {
                AppStrings.isEnglish(this) ? "Enabled (Default)" : "Activados (Por defecto)",
                AppStrings.isEnglish(this) ? "Disabled" : "Desactivados",
                AppStrings.isEnglish(this) ? "Always show Spanish" : "Forzar subtítulos en español",
                AppStrings.isEnglish(this) ? "Always show English" : "Forzar subtítulos en inglés"
        };
        int selected = settings.getInt("subs_mode", 0);
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.get(this, "setting_subtitles"))
                .setSingleChoiceItems(opts, selected, (d, which) -> {
                    settings.edit().putInt("subs_mode", which).apply();
                    d.dismiss();
                    Toast.makeText(this, AppStrings.isEnglish(this) ? "Subtitle preferences saved" : "Preferencias de subtítulos guardadas", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .show();
    }

    private void chooseBufferType() {
        String[] opts = {
                AppStrings.get(this, "val_normal") + " (15s)",
                AppStrings.get(this, "val_large") + " (30s)",
                AppStrings.get(this, "val_small") + " (5s)"
        };
        int selected = settings.getInt("buffer_index", 0);
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.get(this, "setting_buffer"))
                .setSingleChoiceItems(opts, selected, (d, which) -> {
                    String clean = which == 1 ? AppStrings.get(this, "val_large") : (which == 2 ? AppStrings.get(this, "val_small") : AppStrings.get(this, "val_normal"));
                    settings.edit().putInt("buffer_index", which)
                            .putString("playback_buffer_type", clean).apply();
                    d.dismiss();
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .show();
    }

    private void chooseLanguage() {
        String[] langs = {"Español", "English"};
        int selected = AppStrings.isEnglish(this) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.get(this, "setting_lang"))
                .setSingleChoiceItems(langs, selected, (d, which) -> {
                    AppStrings.setLang(this, which == 1 ? "en" : "es");
                    d.dismiss();
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .show();
    }

    private void configureTmdbKey() {
        String currentKey = TmdbClient.getApiKey(this);
        EditText input = new EditText(this);
        input.setText(currentKey);
        input.setHint("Ej. 3a1b2c3d4e5f...");
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setPadding(dp(this, 16), dp(this, 12), dp(this, 16), dp(this, 12));

        LinearLayout container = column(this);
        container.setPadding(dp(this, 24), dp(this, 8), dp(this, 24), 0);
        TextView desc = text(this,
                AppStrings.isEnglish(this) ?
                        "Enter your free API Key from themoviedb.org to enable posters, synopsis, cast, and trailers for movies and series." :
                        "Introduce tu clave gratuita de themoviedb.org para enriquecer películas y series con portadas, sinopsis, reparto y enlaces a trailers.",
                12, MUTED);
        desc.setPadding(0, 0, 0, dp(this, 12));
        container.addView(desc);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("TMDB API Key")
                .setView(container)
                .setPositiveButton(AppStrings.isEnglish(this) ? "Save" : "Guardar", (d, w) -> {
                    TmdbClient.setApiKey(this, input.getText().toString().trim());
                    Toast.makeText(this, AppStrings.isEnglish(this) ? "TMDB API Key updated" : "Clave TMDB guardada", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNeutralButton(AppStrings.isEnglish(this) ? "Delete key" : "Borrar clave", (d, w) -> {
                    TmdbClient.setApiKey(this, "");
                    Toast.makeText(this, AppStrings.isEnglish(this) ? "TMDB API Key removed" : "Clave eliminada", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null)
                .show();
    }

    private void checkStartupUpdate() {
        if (UpdateManager.isSuppressedFor24h(this)) return;
        io.execute(() -> {
            String checkUrl = settings.getString(UpdateManager.PREF_UPDATE_URL, UpdateManager.DEFAULT_UPDATE_URL);
            UpdateManager.UpdateInfo info = UpdateManager.checkForUpdate(MainActivity.this, checkUrl);
            if (info != null && info.hasUpdate && !isFinishing() && !isDestroyed()) {
                main.post(() -> showUpdateDialog(info));
            }
        });
    }

    private void checkUpdates() {
        work(AppStrings.isEnglish(this) ? "Checking updates on GitHub…" : "Comprobando actualizaciones en GitHub…", () -> {
            String checkUrl = settings.getString(UpdateManager.PREF_UPDATE_URL, UpdateManager.DEFAULT_UPDATE_URL);
            UpdateManager.UpdateInfo info = UpdateManager.checkForUpdate(this, checkUrl);

            main.post(() -> {
                if (info.hasUpdate) {
                    showUpdateDialog(info);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(AppStrings.isEnglish(this) ? "Lunvoryx Player is up to date" : "Lunvoryx Player está actualizado")
                            .setMessage(AppStrings.isEnglish(this) ?
                                    "You have the latest version installed (" + info.currentVersion + ").\nUpdates are fetched directly from GitHub Releases." :
                                    "Tienes instalada la última versión (" + info.currentVersion + ").\nEl reproductor comprueba las versiones directamente desde las Releases de GitHub.")
                            .setPositiveButton(AppStrings.isEnglish(this) ? "Understood" : "Entendido", null)
                            .show();
                }
            });
        }, () -> {});
    }

    private void showUpdateDialog(UpdateManager.UpdateInfo info) {
        String title = AppStrings.isEnglish(this) ? "Update available!" : "¡Actualización disponible!";
        String msg = (AppStrings.isEnglish(this) ?
                "A new version of Lunvoryx Player (v" + info.latestVersion + ") is available. Would you like to update now?" :
                "Hay una nueva versión de Lunvoryx Player (v" + info.latestVersion + ") disponible. ¿Deseas actualizar ahora?")
                + (info.releaseNotes != null && !info.releaseNotes.isEmpty() ? "\n\n" + info.releaseNotes : "");

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(AppStrings.isEnglish(this) ? "Update" : "Actualizar", (dialog, which) -> {
                    startDownloadAndInstall(info.downloadUrl);
                })
                .setNeutralButton(AppStrings.isEnglish(this) ? "Don't remind for 24h" : "No recordar en 24h", (dialog, which) -> {
                    UpdateManager.suppressFor24h(MainActivity.this);
                })
                .setNegativeButton(AppStrings.isEnglish(this) ? "Don't update" : "No actualizar", (dialog, which) -> {
                    dialog.dismiss();
                })
                .create();
        d.show();
    }

    private void configureGitHubRepo() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(AppStrings.isEnglish(this) ? "GitHub Repository" : "Repositorio de GitHub");
        b.setMessage(AppStrings.isEnglish(this) ?
                "Enter your GitHub owner/repo (e.g. David/Lunvoryx-Player):" :
                "Introduce tu usuario/repositorio de GitHub (ej. David/Lunvoryx-Player):");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setBackground(shape(this, PANEL, 8));
        input.setPadding(dp(this, 16), dp(this, 12), dp(this, 16), dp(this, 12));
        String current = settings.getString("github_repo", "David/Lunvoryx-Player");
        input.setText(current);

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(this, 24), dp(this, 10), dp(this, 24), dp(this, 10));
        wrapper.addView(input);
        b.setView(wrapper);

        b.setPositiveButton(AppStrings.isEnglish(this) ? "Save" : "Guardar", (d, w) -> {
            String val = input.getText().toString().trim().replaceAll("^https?://github\\.com/", "").replaceAll("/+$", "");
            if (!val.isEmpty()) {
                if (!UpdateManager.isValidRepoString(val)) {
                    Toast.makeText(this, AppStrings.isEnglish(this) ? "Invalid format. Expected: user/repository" : "Formato inválido. Debe ser: usuario/repositorio", Toast.LENGTH_SHORT).show();
                    return;
                }
                settings.edit()
                        .putString("github_repo", val)
                        .putString(UpdateManager.PREF_UPDATE_URL, "https://api.github.com/repos/" + val + "/releases/latest")
                        .apply();
                render();
                Toast.makeText(this, AppStrings.isEnglish(this) ? "GitHub repository saved" : "Repositorio de GitHub guardado", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton(AppStrings.isEnglish(this) ? "Cancel" : "Cancelar", null);
        b.show();
    }

    private void startDownloadAndInstall(String apkUrl) {
        work(AppStrings.isEnglish(this) ? "Downloading APK update…" : "Descargando actualización APK…", () -> {
            UpdateManager.downloadAndInstall(this, apkUrl, new UpdateManager.DownloadCallback() {
                @Override public void onProgress(int percent) {
                    main.post(() -> {
                        if (status != null) status.setText((AppStrings.isEnglish(MainActivity.this) ? "Downloading " : "Descargando ") + percent + "%");
                    });
                }
                @Override public void onSuccess(File apkFile) {
                    main.post(() -> {
                        if (status != null) status.setText(AppStrings.isEnglish(MainActivity.this) ? "Local library" : "Biblioteca local");
                        UpdateManager.launchApkInstall(MainActivity.this, apkFile);
                    });
                }
                @Override public void onError(String message) {
                    main.post(() -> {
                        if (status != null) status.setText(AppStrings.isEnglish(MainActivity.this) ? "Local library" : "Biblioteca local");
                        showError((AppStrings.isEnglish(MainActivity.this) ? "Download failed: " : "Error en descarga: ") + message);
                    });
                }
            });
        }, () -> {});
    }

    private TextView addSetting(String label, boolean enabled, String key) {
        String state = enabled ? AppStrings.get(this, "enabled") : AppStrings.get(this, "disabled");
        TextView b = button(this, label + "     " + state, () -> {
            settings.edit().putBoolean(key, !enabled).apply();
            render();
        });
        b.setId(View.generateViewId());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(this, 10);
        content.addView(b, p);
        return b;
    }

    private void showAddOptions() {
        section = "Mis listas";
        render();
    }

    private EditText input(String hint, String value, boolean uri) {
        EditText e = new EditText(this);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setPadding(dp(this, 14), dp(this, 12), dp(this, 14), dp(this, 12));
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (uri ? android.text.InputType.TYPE_TEXT_VARIATION_URI : android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        e.setSelectAllOnFocus(true);
        return e;
    }

    private void chooseFile() {
        if (!operationAvailable()) return;
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, PICK_PLAYLIST);
        } catch (android.content.ActivityNotFoundException e) {
            showError("Este televisor no tiene un selector de archivos. Añade la lista desde una URL.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_PLAYLIST && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
            work(AppStrings.isEnglish(this) ? "Reading file…" : "Leyendo archivo…", () -> repository.importFile("Lista importada", uri), () -> {
                section = "Todos";
                render();
            });
        }
    }

    private interface Job { void run() throws Exception; }

    private boolean operationAvailable() {
        if (!busy) return true;
        Toast.makeText(this, AppStrings.isEnglish(this) ? "Operation in progress. Please wait." : "Hay una operación en curso. Espera a que termine y vuelve a intentarlo.", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void work(String message, Job job, Runnable done) {
        if (!operationAvailable()) return;
        busy = true;
        if (status != null) status.setText(message);
        io.execute(() -> {
            try {
                job.run();
                main.post(() -> {
                    busy = false;
                    if (isDestroyed()) return;
                    if (status != null) status.setText(AppStrings.isEnglish(this) ? "Local library" : "Biblioteca local");
                    done.run();
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    if (isDestroyed()) return;
                    if (status != null) status.setText(AppStrings.isEnglish(this) ? "Local library" : "Biblioteca local");
                    showError(safeMessage(e));
                });
            }
        });
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        if (m == null || m.contains("://")) return AppStrings.isEnglish(this) ? "Could not complete operation. Check network connection and playlist address." : "No se ha podido completar la operación. Comprueba la conexión y la dirección de la lista.";
        return m;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(AppStrings.isEnglish(this) ? "Could not load" : "No se ha podido cargar")
                .setMessage(message)
                .setPositiveButton(AppStrings.isEnglish(this) ? "Understood" : "Entendido", null)
                .show();
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_MENU && !shown.isEmpty()) {
            for (Channel c : shown) {
                if (c.id.equals(lastFocusedId)) {
                    channelOptions(c);
                    return true;
                }
            }
        }
        return super.onKeyDown(key, event);
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (currentDetailView != null) {
            closeDetailView();
            return;
        }
        if (!query.isEmpty() || !group.equals("Todos")) {
            query = "";
            group = "Todos";
            render();
        } else if (!section.equals("Inicio")) {
            navigate("Inicio");
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(AppStrings.isEnglish(this) ? "Exit Lunvoryx Player?" : "¿Salir de Lunvoryx Player?")
                    .setNegativeButton(AppStrings.isEnglish(this) ? "Keep watching" : "Seguir viendo", null)
                    .setPositiveButton(AppStrings.isEnglish(this) ? "Exit" : "Salir", (d, w) -> finish())
                    .show();
        }
    }

    private final class ChannelAdapter extends RecyclerView.Adapter<ChannelHolder> {
        @Override public ChannelHolder onCreateViewHolder(ViewGroup parent, int type) { return new ChannelHolder(); }
        @Override public int getItemCount() { return shown.size(); }
        @Override public void onBindViewHolder(ChannelHolder h, int position) { h.bind(shown.get(position), position); }
    }

    private final class ChannelHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final TextView initials, number, name, category, star;
        final ImageView logo;
        Channel channel;

        ChannelHolder() {
            super(column(MainActivity.this));
            card = (LinearLayout) itemView;
            card.setPadding(dp(MainActivity.this, 16), dp(MainActivity.this, 14), dp(MainActivity.this, 16), dp(MainActivity.this, 14));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(MainActivity.this, 142));
            params.setMargins(dp(MainActivity.this, 2), dp(MainActivity.this, 2), dp(MainActivity.this, 10), dp(MainActivity.this, 10));
            card.setLayoutParams(params);
            card.setBackground(focusBackground(MainActivity.this, PANEL));
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setClickable(true);

            LinearLayout top = row(MainActivity.this);
            FrameLayout emblem = new FrameLayout(MainActivity.this);
            initials = text(MainActivity.this, "", 25, TEXT);
            initials.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            initials.setGravity(Gravity.CENTER);
            emblem.addView(initials, new FrameLayout.LayoutParams(-1, -1));

            logo = new ImageView(MainActivity.this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setBackgroundColor(PANEL);
            logo.setVisibility(View.GONE);
            emblem.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            top.addView(emblem, new LinearLayout.LayoutParams(dp(MainActivity.this, 54), dp(MainActivity.this, 48)));

            top.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, 1, 1));
            number = text(MainActivity.this, "", 11, MUTED);
            top.addView(number);

            star = text(MainActivity.this, "★", 16, ACCENT);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.leftMargin = dp(MainActivity.this, 6);
            top.addView(star, sp);
            card.addView(top);

            card.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(1, 0, 1));
            name = text(MainActivity.this, "", 16, TEXT);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name);

            category = text(MainActivity.this, "", 11, MUTED);
            category.setSingleLine(true);
            category.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(MainActivity.this, 6);
            card.addView(category, cp);

            card.setOnClickListener(v -> {
                if (channel != null) {
                    if (TmdbClient.hasApiKey(MainActivity.this) && (MediaKind.classify(channel) == MediaKind.MOVIE || MediaKind.classify(channel) == MediaKind.SERIES)) {
                        channelOptions(channel);
                    } else {
                        play(channel);
                    }
                }
            });

            card.setOnLongClickListener(v -> {
                if (channel != null) toggleFavorite(channel);
                return true;
            });

            card.setOnFocusChangeListener((v, focused) -> {
                if (focused && channel != null) lastFocusedId = channel.id;
                name.setTextColor(focused ? INK : TEXT);
                initials.setTextColor(focused ? INK : TEXT);
                category.setTextColor(focused ? 0xFF465538 : MUTED);
                number.setTextColor(focused ? 0xFF465538 : MUTED);
                star.setTextColor(focused ? INK : ACCENT);
            });
        }

        void bind(Channel c, int position) {
            channel = c;
            name.setText(c.name);
            category.setText(c.group);
            number.setText(String.format(Locale.ROOT, "%02d", position + 1));
            star.setVisibility(repository.isFavorite(c.id) ? View.VISIBLE : View.GONE);

            String trimmed = c.name.trim();
            String[] parts = trimmed.split("\\s+");
            String letters = trimmed.isEmpty() ? "TV" : trimmed.substring(0, 1);
            if (parts.length > 1 && !parts[1].isEmpty()) letters += parts[1].substring(0, 1);
            else if (trimmed.length() > 1) letters = trimmed.substring(0, 2);
            initials.setText(letters.toUpperCase(Locale.ROOT));

            logo.setBackgroundColor(Color.TRANSPARENT);
            logo.setVisibility(settings.getBoolean("logos", true) && !c.logoUrl.isEmpty() ? View.VISIBLE : View.GONE);
            if (logo.getVisibility() == View.VISIBLE) LogoLoader.load(c.logoUrl, logo);
            else {
                logo.setTag(null);
                logo.setImageDrawable(null);
            }
            card.setContentDescription(c.name + ", " + c.group + (repository.isFavorite(c.id) ? ", favorito" : ""));
        }
    }
}
