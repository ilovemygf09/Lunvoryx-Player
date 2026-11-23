package tv.lunvoryx.player.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;

/** A single-decoder TV player. No video or network resources survive onStop. */
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PlayerActivity extends Activity {
    private static final int INK = Color.rgb(11, 13, 24);
    private static final int SURFACE = Color.rgb(37, 38, 51);
    private static final int TEXT = Color.rgb(247, 248, 252);
    private static final int MUTED = Color.rgb(169, 173, 190);
    private static final int ACCENT = Color.rgb(239, 241, 248);
    private static final int BORDER = Color.rgb(65, 68, 85);
    private static final int TRANSPARENT = Color.TRANSPARENT;
    private static final long OVERLAY_TIMEOUT = 5500;
    private static final long WATCH_SAVE_INTERVAL = 15000;
    private static volatile Queue handoff;

    private static final class Queue {
        final String token = UUID.randomUUID().toString();
        final List<Channel> channels;
        Queue(List<Channel> channels) {
            this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        }
    }

    /** Copies only channel references; the entire catalog is never sent through Binder. */
    public static void start(Context context, List<Channel> channels, int selectedIndex) {
        if (channels == null || channels.isEmpty()) return;
        int position = Math.max(0, Math.min(selectedIndex, channels.size() - 1));
        Queue queue = new Queue(channels);
        handoff = queue;
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("queue", queue.token);
        intent.putExtra("index", position);
        intent.putExtras(channelBundle(queue.channels.get(position)));
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static Bundle channelBundle(Channel channel) {
        Bundle out = new Bundle();
        out.putString("id", channel.id);
        out.putString("name", channel.name);
        out.putString("url", channel.url);
        out.putString("group", channel.group);
        out.putString("logo", channel.logoUrl);
        out.putString("tvg", channel.tvgId);
        out.putString("agent", channel.userAgent);
        out.putString("referer", channel.referer);
        return out;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::hideControls;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            saveWatchProgress();
            handler.postDelayed(this, WATCH_SAVE_INTERVAL);
        }
    };
    private List<Channel> channels = Collections.emptyList();
    private String queueToken;
    private int selectedIndex;
    private long resumePosition = C.TIME_UNSET;
    private final DateFormat clockFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
    private long clockMinute = -1;
    private boolean restoringWatchPosition;
    private boolean resumePlaying = true;
    private boolean failed;
    private boolean dialogOpen;
    private AlertDialog activeDialog;
    private boolean consumeRevealKeyUp;
    private boolean scrubbing;
    private int resizeMode;
    private ExoPlayer player;
    private DefaultMediaSourceFactory sources;
    private String sourceUserAgent, sourceReferer;
    private Channel sessionChannel;
    private long sessionDuration = C.TIME_UNSET;
    private PlayerView video;
    private FrameLayout root;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout waitPanel;
    private LinearLayout errorPanel;
    private TextView title;
    private TextView subtitle;
    private TextView clock;
    private TextView favorite;
    private TextView pause;
    private TextView live;
    private TextView elapsed;
    private TextView duration;
    private TextView errorMessage;
    private TextView retry;
    private TextView previous;
    private TextView next;
    private SeekBar progress;
    private MediaSession mediaSession;
    private SharedPreferences preferences;
    private SharedPreferences watchProgress;
    private PlaylistRepository repository;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        preferences = getSharedPreferences("lunvoryx_player", MODE_PRIVATE);
        watchProgress = getSharedPreferences("watch_progress", MODE_PRIVATE);
        repository = new PlaylistRepository(getApplicationContext());
        resizeMode = preferences.getInt("resize", AspectRatioFrameLayout.RESIZE_MODE_FIT);
        Bundle state = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (state == null || state.getString("url", "").isEmpty()) {
            finish();
            return;
        }
        queueToken = state.getString("queue");
        Queue currentQueue = handoff;
        if (currentQueue != null && currentQueue.token.equals(queueToken)) {
            channels = currentQueue.channels;
            selectedIndex = Math.max(0, Math.min(state.getInt("index"), channels.size() - 1));
        } else {
            channels = Collections.singletonList(new Channel(state.getString("id", ""),
                    state.getString("name", "Canal"), state.getString("url", ""),
                    state.getString("group", ""), state.getString("logo", ""),
                    state.getString("tvg", ""), state.getString("agent", ""),
                    state.getString("referer", "")));
        }
        if (savedInstanceState != null) {
            resumePosition = state.getLong("position", C.TIME_UNSET);
            resumePlaying = state.getBoolean("playing", true);
        } else {
            resumePosition = readWatchPosition(current().id);
            restoringWatchPosition = resumePosition != C.TIME_UNSET;
        }
        buildUi();
        buildMediaSession();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        video = new PlayerView(this);
        video.setUseController(false);
        video.setFocusable(false);
        video.setResizeMode(resizeMode);
        video.setKeepContentOnPlayerReset(false);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(38), dp(27), dp(38), dp(45));
        topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE80B0D18, TRANSPARENT}));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        title = text("", 30, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = text("", 14, MUTED);
        heading.addView(title);
        heading.addView(subtitle);
        topBar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        favorite = button("Favorito", this::toggleFavorite);
        topBar.addView(favorite, buttonParams(128));
        clock = text("", 18, TEXT);
        clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topBar.addView(clock, new LinearLayout.LayoutParams(dp(82), dp(44)));
        root.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(38), dp(70), dp(38), dp(29));
        bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF50B0D18, TRANSPARENT}));
        LinearLayout timeline = new LinearLayout(this);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text("", 13, MUTED);
        timeline.addView(elapsed, new LinearLayout.LayoutParams(dp(68), -2));
        progress = new SeekBar(this);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        progress.setThumbTintList(ColorStateList.valueOf(ACCENT));
        progress.setContentDescription("Posición de reproducción. Izquierda y derecha para desplazarse.");
        progress.setFocusable(false);
        progress.setOnFocusChangeListener((v, focused) -> {
            progress.setBackground(focused ? rounded(SURFACE, 8) : null);
            if (focused) scheduleHide();
        });
        progress.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) seek(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 30000);
            return true;
        });
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && player != null && player.getDuration() > 0)
                    elapsed.setText(formatTime(value * player.getDuration() / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                scrubbing = true;
                handler.removeCallbacks(hideTask);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.isCurrentMediaItemSeekable() && player.getDuration() > 0)
                    player.seekTo(bar.getProgress() * player.getDuration() / 1000);
                scrubbing = false;
                scheduleHide();
            }
        });
        timeline.addView(progress, new LinearLayout.LayoutParams(0, dp(24), 1));
        duration = text("", 13, MUTED);
        duration.setGravity(Gravity.END);
        timeline.addView(duration, new LinearLayout.LayoutParams(dp(80), -2));
        bottomBar.addView(timeline, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        previous = button("Anterior", () -> changeChannel(-1));
        pause = button("Pausa", this::togglePause);
        next = button("Siguiente", () -> changeChannel(1));
        live = button("Al directo", this::goLive);
        controls.addView(previous, buttonParams(91));
        controls.addView(pause, buttonParams(96));
        controls.addView(next, buttonParams(96));
        controls.addView(live, buttonParams(96));
        controls.addView(button("Audio", () -> chooseTrack(C.TRACK_TYPE_AUDIO)), buttonParams(82));
        controls.addView(button("Subtítulos", () -> chooseTrack(C.TRACK_TYPE_TEXT)), buttonParams(110));
        controls.addView(button("Calidad", () -> chooseTrack(C.TRACK_TYPE_VIDEO)), buttonParams(91));
        controls.addView(button("Formato", this::chooseResize), buttonParams(98));
        bottomBar.addView(controls);
        TextView hint = text("OK  Controles     ↑ ↓  Canales     ← →  Avanzar / retroceder     Atrás  Volver", 12, MUTED);
        hint.setPadding(0, dp(13), 0, 0);
        bottomBar.addView(hint);
        root.addView(bottomBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        waitPanel = new LinearLayout(this);
        waitPanel.setOrientation(LinearLayout.VERTICAL);
        waitPanel.setGravity(Gravity.CENTER);
        waitPanel.setPadding(dp(26), dp(22), dp(26), dp(22));
        waitPanel.setBackground(rounded(0xDC0B0D18, 18));
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        waitPanel.addView(spinner, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView connecting = text("Conectando con el canal…", 16, TEXT);
        connecting.setPadding(0, dp(12), 0, 0);
        connecting.setGravity(Gravity.CENTER);
        waitPanel.addView(connecting, new LinearLayout.LayoutParams(-2, -2));
        int safePanelWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(76));
        root.addView(waitPanel, new FrameLayout.LayoutParams(Math.min(dp(300), safePanelWidth), -2, Gravity.CENTER));

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setPadding(dp(30), dp(28), dp(30), dp(26));
        errorPanel.setBackground(rounded(INK, 14));
        TextView errorTitle = text("No se puede reproducir este canal", 24, TEXT);
        errorTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        errorPanel.addView(errorTitle, new LinearLayout.LayoutParams(-1, -2));
        errorMessage = text("", 16, MUTED);
        errorMessage.setPadding(0, dp(12), 0, dp(22));
        errorPanel.addView(errorMessage, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout errorActions = new LinearLayout(this);
        retry = button("Reintentar", this::retryPlayback);
        errorActions.addView(retry, buttonParams(138));
        errorActions.addView(button("Volver a canales", this::finish), buttonParams(190));
        errorPanel.addView(errorActions, new LinearLayout.LayoutParams(-1, -2));
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(Math.min(dp(600), safePanelWidth), -2, Gravity.CENTER));
        setContentView(root);
        updateChannelUi();
        pause.requestFocus();
    }

    private void initializePlayer() {
        if (player != null || channels.isEmpty()) return;
        player = new ExoPlayer.Builder(this)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 45000, 1500, 3000)
                        .setPrioritizeTimeOverSizeThresholds(false)
                        .setTargetBufferBytes(32 * 1024 * 1024)
                        .build())
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage("es").build());
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                waitPanel.setVisibility(state == Player.STATE_BUFFERING && !failed ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    failed = false;
                    errorPanel.setVisibility(View.GONE);
                    if (restoringWatchPosition) {
                        restoringWatchPosition = false;
                        // A provider can reuse the same URL for a live signal later.
                        if (player != null && player.isCurrentMediaItemLive()) {
                            clearWatchProgress(current().id);
                            player.seekToDefaultPosition();
                        }
                    }
                    updateProgress();
                    scheduleHide();
                }
                if (state == Player.STATE_ENDED) {
                    clearWatchProgress(current().id);
                    showControls();
                }
                updatePause();
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                updatePause();
                if (playWhenReady) scheduleHide();
                else {
                    handler.removeCallbacks(hideTask);
                    showControls();
                }
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                updatePause();
                handler.removeCallbacks(checkpoint);
                if (playing && player != null && player.isCurrentMediaItemSeekable() && !player.isCurrentMediaItemLive())
                    handler.postDelayed(checkpoint, WATCH_SAVE_INTERVAL);
                if (playing) scheduleHide();
                else handler.removeCallbacks(hideTask);
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                failed = true;
                waitPanel.setVisibility(View.GONE);
                errorMessage.setText(friendlyError(error));
                errorPanel.setVisibility(View.VISIBLE);
                showControls();
                retry.requestFocus();
            }
            @Override public void onEvents(Player source, Player.Events events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAYER_ERROR,
                        Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    updateSession();
                }
            }
            @Override public void onTracksChanged(Tracks tracks) { updateProgress(); }
        });
        video.setPlayer(player);
        prepareChannel(resumePosition, resumePlaying);
    }

    private void prepareChannel(long position, boolean play) {
        if (player == null) return;
        Channel channel = current();
        failed = false;
        errorPanel.setVisibility(View.GONE);
        waitPanel.setVisibility(View.VISIBLE);
        MediaItem.Builder media = new MediaItem.Builder().setMediaId(channel.id).setUri(channel.url)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(channel.name).build());
        Uri uri = Uri.parse(channel.url);
        String path = uri.getPath();
        String pathLower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        String output = uri.getQueryParameter("output");
        if (pathLower.endsWith(".m3u8") || "m3u8".equalsIgnoreCase(output)) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (pathLower.endsWith(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().build());
        player.setRepeatMode(channel.url.startsWith("asset:///demo/live/") ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        player.setMediaSource(sourcesFor(channel).createMediaSource(media.build()), position);
        player.prepare();
        player.setPlayWhenReady(play);
        repository.recordPlayed(channel.id);
        updateChannelUi();
        showControls();
    }

    private DefaultMediaSourceFactory sourcesFor(Channel channel) {
        if (sources == null || !channel.userAgent.equals(sourceUserAgent) || !channel.referer.equals(sourceReferer)) {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(channel.userAgent.isEmpty() ? "LunvoryxTV/1.0" : channel.userAgent)
                    .setConnectTimeoutMs(15000).setReadTimeoutMs(15000)
                    .setAllowCrossProtocolRedirects(true);
            if (!channel.referer.isEmpty())
                http.setDefaultRequestProperties(Collections.singletonMap("Referer", channel.referer));
            sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http));
            sourceUserAgent = channel.userAgent;
            sourceReferer = channel.referer;
        }
        return sources;
    }

    private void changeChannel(int direction) {
        if (channels.size() < 2 || player == null) {
            showControls();
            return;
        }
        saveWatchProgress();
        selectedIndex = (selectedIndex + direction + channels.size()) % channels.size();
        resumePosition = readWatchPosition(current().id);
        restoringWatchPosition = resumePosition != C.TIME_UNSET;
        resumePlaying = true;
        getIntent().putExtras(channelBundle(current()));
        getIntent().putExtra("index", selectedIndex);
        prepareChannel(resumePosition, true);
    }

    private long readWatchPosition(String id) {
        long position = watchProgress.getLong(id + ".position", 0);
        long length = watchProgress.getLong(id + ".duration", 0);
        if (position >= 5000 && length > 10000 && position < length - 10000) return position;
        if (position != 0 || length != 0) clearWatchProgress(id);
        return C.TIME_UNSET;
    }

    /** Saves only actual, seekable VOD timelines; loading failures preserve the last valid checkpoint. */
    private void saveWatchProgress() {
        if (player == null || channels.isEmpty()) return;
        if (player.isCurrentMediaItemLive() || player.getPlaybackState() == Player.STATE_ENDED) {
            clearWatchProgress(current().id);
            return;
        }
        long length = player.getDuration();
        if (!player.isCurrentMediaItemSeekable() || length == C.TIME_UNSET || length <= 0 || failed) return;
        long position = player.getCurrentPosition();
        if (position < 5000 || position >= length - 10000) {
            clearWatchProgress(current().id);
            return;
        }
        watchProgress.edit().putLong(current().id + ".position", position)
                .putLong(current().id + ".duration", length).apply();
    }

    private void clearWatchProgress(String id) {
        if (!watchProgress.contains(id + ".position") && !watchProgress.contains(id + ".duration")) return;
        watchProgress.edit().remove(id + ".position").remove(id + ".duration").apply();
    }

    private Channel current() { return channels.get(selectedIndex); }

    private void updateChannelUi() {
        Channel channel = current();
        title.setText(channel.name);
        String group = channel.group == null || channel.group.isEmpty() ? "Mi lista" : channel.group;
        subtitle.setText(group + (channels.size() > 1 ? "    " + (selectedIndex + 1) + " / " + channels.size() : ""));
        favorite.setText(repository.isFavorite(channel.id) ? "★  Favorito" : "☆  Favorito");
        previous.setEnabled(channels.size() > 1);
        next.setEnabled(channels.size() > 1);
        previous.setAlpha(channels.size() > 1 ? 1f : .4f);
        next.setAlpha(channels.size() > 1 ? 1f : .4f);
        updatePause();
    }

    private void toggleFavorite() {
        repository.toggleFavorite(current().id);
        updateChannelUi();
        scheduleHide();
    }

    private void togglePause() {
        if (player == null) return;
        if (failed) { retryPlayback(); return; }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekToDefaultPosition();
            player.play();
        } else player.setPlayWhenReady(!player.getPlayWhenReady());
        updatePause();
        showControls();
    }

    private void startPlayback() {
        if (player == null) return;
        if (failed) {
            retryPlayback();
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekToDefaultPosition();
        player.play();
        showControls();
    }

    private void retryPlayback() {
        if (player == null) return;
        prepareChannel(player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition(), true);
        pause.requestFocus();
    }

    private void updatePause() {
        if (pause == null) return;
        pause.setText(player != null && player.getPlaybackState() == Player.STATE_ENDED ? "Repetir" :
                player != null && player.getPlayWhenReady() ? "Pausa" : "Reproducir");
    }

    private void goLive() {
        if (player != null && player.isCurrentMediaItemLive()) {
            player.seekToDefaultPosition();
            player.play();
        }
        scheduleHide();
    }

    private void seek(long deltaMs) {
        if (player == null) return;
        if (player.isCurrentMediaItemSeekable()) {
            long target = Math.max(0, player.getCurrentPosition() + deltaMs);
            long end = player.getDuration();
            if (end != C.TIME_UNSET) target = Math.min(target, end);
            player.seekTo(target);
            showControls();
        } else {
            showControls();
            Toast.makeText(this, "Este canal no permite desplazarse en el tiempo", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateProgress() {
        if (topBar == null || topBar.getVisibility() != View.VISIBLE) return;
        long now = System.currentTimeMillis();
        if (now / 60000 != clockMinute) {
            clockMinute = now / 60000;
            clock.setText(clockFormat.format(new Date(now)));
        }
        if (player == null) return;
        boolean isLive = player.isCurrentMediaItemLive();
        live.setVisibility(isLive ? View.VISIBLE : View.GONE);
        long total = player.getDuration();
        long position = player.getCurrentPosition();
        boolean timeline = total != C.TIME_UNSET && total > 0 && player.isCurrentMediaItemSeekable();
        if (!scrubbing) progress.setProgress(timeline ? (int) Math.min(1000, position * 1000 / total) : 0);
        progress.setEnabled(timeline);
        progress.setFocusable(timeline);
        progress.setAlpha(timeline ? 1f : .4f);
        if (!scrubbing) elapsed.setText(timeline ? formatTime(position) : "");
        duration.setText(isLive ? "En directo" : (timeline ? formatTime(total) : ""));
        if (player.getVideoFormat() != null) {
            Format format = player.getVideoFormat();
            String group = current().group == null ? "" : current().group;
            String quality = format.height > 0 ? format.height + "p" : "";
            subtitle.setText(group + (quality.isEmpty() ? "" : "    " + quality) +
                    (channels.size() > 1 ? "    " + (selectedIndex + 1) + " / " + channels.size() : ""));
        }
    }

    private void showControls() {
        if (topBar == null) return;
        boolean hidden = topBar.getVisibility() != View.VISIBLE;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if (hidden && !failed) pause.requestFocus();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
        updateProgress();
        scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideTask);
        if (!failed && !dialogOpen && !scrubbing && player != null && player.getPlayWhenReady() &&
                player.getPlaybackState() == Player.STATE_READY) handler.postDelayed(hideTask, OVERLAY_TIMEOUT);
    }

    private void hideControls() {
        if (failed || dialogOpen || topBar == null) return;
        handler.removeCallbacks(ticker);
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        root.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (consumeRevealKeyUp &&
                (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == KeyEvent.ACTION_UP) consumeRevealKeyUp = false;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_MEDIA_PAUSE || key == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (event.getRepeatCount() == 0 && player != null) {
                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY) startPlayback();
                    else if (key == KeyEvent.KEYCODE_MEDIA_PAUSE) { player.pause(); showControls(); }
                    else togglePause();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_MEDIA_NEXT) {
                if (event.getRepeatCount() == 0) changeChannel(1);
                return true;
            }
            if (key == KeyEvent.KEYCODE_CHANNEL_DOWN || key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                if (event.getRepeatCount() == 0) changeChannel(-1);
                return true;
            }
            if (key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { seek(30000); return true; }
            if (key == KeyEvent.KEYCODE_MEDIA_REWIND) { seek(-10000); return true; }
            if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_INFO) {
                if (event.getRepeatCount() == 0) showControls();
                return true;
            }
            if (topBar != null && topBar.getVisibility() != View.VISIBLE) {
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    consumeRevealKeyUp = true;
                    showControls();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP) { if (event.getRepeatCount() == 0) changeChannel(-1); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) { if (event.getRepeatCount() == 0) changeChannel(1); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { seek(30000); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { seek(-10000); return true; }
            }
            scheduleHide();
        }
        return super.dispatchKeyEvent(event);
    }

    // Android 13+ uses the registered OnBackInvokedCallback; this is the API 23–32 fallback.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (failed) { finish(); return; }
        if (topBar != null && topBar.getVisibility() == View.VISIBLE) hideControls();
        else finish();
    }

    private void chooseTrack(int type) {
        if (player == null) return;
        List<String> labels = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();
        boolean textType = type == C.TRACK_TYPE_TEXT;
        labels.add(textType ? "Desactivados" : "Automático");
        overrides.add(null);
        int checked = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                String label = trackLabel(format, type, labels.size());
                labels.add(label);
                overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                if (group.isTrackSelected(i) && (textType ||
                        player.getTrackSelectionParameters().overrides.containsKey(group.getMediaTrackGroup()))) checked = labels.size() - 1;
            }
        }
        if (labels.size() == 1) {
            Toast.makeText(this, textType ? "Este canal no ofrece subtítulos" : "No hay pistas disponibles todavía", Toast.LENGTH_SHORT).show();
            scheduleHide();
            return;
        }
        String heading = textType ? "Subtítulos" : (type == C.TRACK_TYPE_AUDIO ? "Pista de audio" : "Calidad de imagen");
        dialogOpen = true;
        handler.removeCallbacks(hideTask);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(heading)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    if (player != null) {
                        TrackSelectionParameters.Builder params = player.getTrackSelectionParameters().buildUpon()
                                .clearOverridesOfType(type).setTrackTypeDisabled(type, textType && which == 0);
                        if (which > 0) params.setOverrideForType(overrides.get(which));
                        player.setTrackSelectionParameters(params.build());
                    }
                    d.dismiss();
                }).setNegativeButton("Cerrar", null).create();
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> { activeDialog = null; dialogOpen = false; scheduleHide(); });
        dialog.show();
        styleDialog(dialog);
    }

    private String trackLabel(Format format, int type, int index) {
        if (type == C.TRACK_TYPE_VIDEO) {
            String height = format.height > 0 ? format.height + "p" : "Pista " + index;
            return height + (format.bitrate > 0 ? String.format(Locale.getDefault(), "  %.1f Mbps", format.bitrate / 1_000_000d) : "");
        }
        String language = format.language;
        String name = format.label;
        if (name == null || name.isEmpty()) {
            name = language == null || language.isEmpty() || "und".equals(language) ? "Pista " + index :
                    Locale.forLanguageTag(language).getDisplayLanguage(new Locale("es"));
        }
        if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) name += "  " + format.channelCount + " canales";
        return name;
    }

    private void chooseResize() {
        String[] labels = {"Ajustar (conservar proporciones)", "Llenar (recortar bordes)", "Estirar (ocupar pantalla)"};
        int[] values = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL};
        int selected = resizeMode == values[1] ? 1 : resizeMode == values[2] ? 2 : 0;
        dialogOpen = true;
        handler.removeCallbacks(hideTask);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Formato de imagen")
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    resizeMode = values[which];
                    video.setResizeMode(resizeMode);
                    preferences.edit().putInt("resize", resizeMode).apply();
                    d.dismiss();
                }).setNegativeButton("Cerrar", null).create();
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> { activeDialog = null; dialogOpen = false; scheduleHide(); });
        dialog.show();
        styleDialog(dialog);
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(rounded(SURFACE, 14));
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ACCENT);
    }

    private void buildMediaSession() {
        mediaSession = new MediaSession(this, "Lunvoryx Player");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { startPlayback(); }
            @Override public void onPause() { if (player != null) player.pause(); }
            @Override public void onSkipToNext() { changeChannel(1); }
            @Override public void onSkipToPrevious() { changeChannel(-1); }
            @Override public void onSeekTo(long pos) {
                if (player != null && player.isCurrentMediaItemSeekable()) player.seekTo(Math.max(0, pos));
            }
            @Override public void onStop() { finish(); }
        });
    }

    private void updateSession() {
        if (mediaSession == null || player == null) return;
        boolean keepAwake = !failed && player.getPlayWhenReady() &&
                (player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING);
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int state = failed ? PlaybackState.STATE_ERROR : player.getPlaybackState() == Player.STATE_BUFFERING ?
                PlaybackState.STATE_BUFFERING : player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP;
        if (channels.size() > 1) actions |= PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (player.isCurrentMediaItemSeekable()) actions |= PlaybackState.ACTION_SEEK_TO;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state, player.getCurrentPosition(), player.isPlaying() ? 1f : 0f).build());
        long length = player.getDuration();
        if (sessionChannel == current() && sessionDuration == length) return;
        sessionChannel = current();
        sessionDuration = length;
        android.media.MediaMetadata.Builder metadata = new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, current().name)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Lunvoryx Player");
        if (length != C.TIME_UNSET) metadata.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, length);
        mediaSession.setMetadata(metadata.build());
    }

    @Override protected void onStart() {
        super.onStart();
        initializePlayer();
        if (mediaSession != null) mediaSession.setActive(true);
    }

    @Override protected void onStop() {
        if (activeDialog != null) activeDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        consumeRevealKeyUp = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mediaSession != null) mediaSession.setActive(false);
        if (player != null) {
            saveWatchProgress();
            resumePosition = player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition();
            resumePlaying = player.getPlayWhenReady();
            video.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (channels.isEmpty()) return;
        out.putAll(channelBundle(current()));
        out.putString("queue", queueToken);
        out.putInt("index", selectedIndex);
        out.putLong("position", player == null ? resumePosition : player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition());
        out.putBoolean("playing", player == null ? resumePlaying : player.getPlayWhenReady());
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mediaSession != null) mediaSession.release();
        if (isFinishing() && handoff != null && handoff.token.equals(queueToken)) handoff = null;
        super.onDestroy();
    }

    private String friendlyError(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "No se ha podido conectar. Comprueba la conexión a Internet y vuelve a intentarlo.";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "El servidor ha rechazado la conexión. Comprueba que la lista sigue activa.";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return "Este dispositivo no puede decodificar el formato del canal. Prueba otra señal de la lista.";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                return "El formato de esta señal no es compatible o la dirección no contiene un vídeo.";
            default:
                return "La señal no está disponible en este momento. Puedes reintentar o elegir otro canal.";
        }
    }

    private TextView text(String content, float size, int color) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private TextView button(String label, Runnable action) {
        TextView view = text(label, 15, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setSingleLine(true);
        view.setPadding(dp(8), 0, dp(8), 0);
        StateListDrawable backgrounds = new StateListDrawable();
        backgrounds.addState(new int[]{android.R.attr.state_pressed}, rounded(ACCENT, 12));
        backgrounds.addState(new int[]{android.R.attr.state_focused}, rounded(ACCENT, 12));
        GradientDrawable idle = rounded(0xD0252633, 12);
        idle.setStroke(dp(1), BORDER);
        backgrounds.addState(new int[]{}, idle);
        view.setBackground(backgrounds);
        view.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_pressed}, new int[]{}
        }, new int[]{INK, INK, TEXT}));
        view.setOnClickListener(v -> { action.run(); scheduleHide(); });
        view.setOnFocusChangeListener((v, focused) -> { if (focused) scheduleHide(); });
        return view;
    }

    private LinearLayout.LayoutParams buttonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(44));
        params.setMarginEnd(dp(7));
        return params;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return seconds >= 3600 ? String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) :
                String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }
}
