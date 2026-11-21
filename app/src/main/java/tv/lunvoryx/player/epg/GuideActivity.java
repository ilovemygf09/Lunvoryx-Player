package tv.lunvoryx.player.epg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.player.PlayerActivity;

/** A remote-first now/next guide backed by the user's XMLTV source. */
public final class GuideActivity extends Activity {
    private static final int BG = Color.rgb(11, 13, 24);
    private static final int PANEL = Color.rgb(37, 38, 51);
    private static final int TEXT = Color.rgb(248, 248, 251);
    private static final int MUTED = Color.rgb(170, 170, 185);
    private static final int ACCENT = Color.rgb(235, 235, 241);
    private static final int INK = Color.rgb(21, 21, 28);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private EpgRepository guide;
    private PlaylistRepository playlists;
    private TextView status;
    private TextView clock;
    private TextView empty;
    private ListView list;
    private Button sourceButton;
    private Button refreshButton;
    private GuideAdapter adapter;
    private boolean busy;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(time.format(new Date()));
            adapter.notifyDataSetChanged();
            main.postDelayed(this, 60000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        guide = new EpgRepository(this);
        playlists = new PlaylistRepository(this);
        createLayout();
        setBusy(true, "Cargando programación…");
        worker.execute(() -> {
            String error = null;
            List<Channel> loaded = new ArrayList<>();
            try {
                playlists.load();
                loaded.addAll(playlists.getAllChannels());
                guide.load();
            } catch (Exception e) {
                error = "No se pudo cargar la guía guardada. Puedes actualizarla de nuevo.";
            }
            String finalError = error;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                channels.clear();
                channels.addAll(loaded);
                adapter.notifyDataSetChanged();
                setBusy(false, finalError == null ? summary() : finalError);
                if (!channels.isEmpty()) list.requestFocus();
                else sourceButton.requestFocus();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    @Override protected void onPause() {
        main.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(44), dp(28), dp(44), dp(24));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(55)));
        TextView title = label("Guía de televisión", 29, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        clock = label(time.format(new Date()), 20, MUTED);
        header.addView(clock, new LinearLayout.LayoutParams(dp(75), -1));
        Button back = button("Volver", v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(100), dp(43)));

        status = label("Cargando programación…", 14, MUTED);
        status.setMaxLines(2);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(52));
        actionsParams.bottomMargin = dp(14);
        root.addView(actions, actionsParams);
        sourceButton = button("Fuente XMLTV", v -> editSource());
        actions.addView(sourceButton, new LinearLayout.LayoutParams(dp(170), dp(44)));
        refreshButton = button("Actualizar", v -> {
            if (guide.getSource().isEmpty()) editSource();
            else refresh(guide.getSource());
        });
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(135), dp(44));
        refreshParams.leftMargin = dp(12);
        actions.addView(refreshButton, refreshParams);
        TextView hint = label("Selecciona un canal para verlo", 13, MUTED);
        hint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.addView(hint, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(dp(18), 0, dp(18), 0);
        addColumn(columns, "Canal", 0.27f);
        addColumn(columns, "Ahora", 0.39f);
        addColumn(columns, "Después", 0.34f);
        root.addView(columns, new LinearLayout.LayoutParams(-1, dp(29)));

        list = new ListView(this);
        list.setDividerHeight(dp(2));
        list.setDivider(new android.graphics.drawable.ColorDrawable(BG));
        list.setSelector(rowSelector());
        list.setDrawSelectorOnTop(false);
        list.setCacheColorHint(BG);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setItemsCanFocus(false);
        list.setPadding(0, dp(2), 0, dp(4));
        adapter = new GuideAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> PlayerActivity.start(this, new ArrayList<>(channels), position));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        empty = label("Tu guía empieza con una lista\nAñade canales desde Mis listas para ver su programación aquí.", 19, MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setMaxLines(3);
        root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(empty);
    }

    private void addColumn(LinearLayout row, String title, float weight) {
        row.addView(label(title, 12, MUTED), new LinearLayout.LayoutParams(0, -1, weight));
    }

    private void editSource() {
        if (busy) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        TextView explanation = label("Añade la URL XMLTV o XMLTV.gz de tu proveedor. Los canales se enlazan mediante su tvg-id.", 16, MUTED);
        explanation.setMaxLines(3);
        form.addView(explanation, new LinearLayout.LayoutParams(-1, dp(68)));
        EditText url = new EditText(this);
        url.setTextColor(TEXT);
        url.setHintTextColor(MUTED);
        url.setTextSize(16);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://proveedor.es/guia.xml.gz");
        url.setText(guide.getSource());
        url.setSelectAllOnFocus(true);
        form.addView(url, new LinearLayout.LayoutParams(-1, dp(52)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Fuente de programación")
                .setView(form)
                .setPositiveButton("Guardar y cargar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String address = url.getText().toString().trim();
                if (!(address.startsWith("https://") || address.startsWith("http://"))) {
                    url.setError("Introduce una URL http:// o https://");
                    url.requestFocus();
                    return;
                }
                dialog.dismiss();
                refresh(address);
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(dp(650), -2);
    }

    private void refresh(String address) {
        if (busy) return;
        setBusy(true, "Actualizando programación… Puedes seguir viendo tus canales.");
        worker.execute(() -> {
            String message;
            try {
                guide.refresh(address);
                message = summary();
            } catch (IOException e) {
                // Network exception details may contain a URL with subscription credentials.
                message = "No se pudo actualizar. Revisa la URL y la conexión; la guía anterior se conserva.";
            } catch (RuntimeException e) {
                message = "La fuente no contiene una guía XMLTV válida. La guía anterior se conserva.";
            }
            String finalMessage = message;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false, finalMessage);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private String summary() {
        if (guide.getSource().isEmpty()) return "Conecta la guía XMLTV de tu proveedor para consultar qué están emitiendo.";
        if (guide.getProgramCount() == 0) return "La fuente no contiene programas recientes. Comprueba la fecha de la guía o actualízala.";
        return "Programación guardada en este dispositivo. Actualizada "
                + new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(guide.getUpdatedAt())) + ".";
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        status.setText(message);
        sourceButton.setEnabled(!value);
        refreshButton.setEnabled(!value);
        sourceButton.setAlpha(value ? .5f : 1f);
        refreshButton.setAlpha(value ? .5f : 1f);
    }

    private final class GuideAdapter extends BaseAdapter {
        @Override public int getCount() { return channels.size(); }
        @Override public Channel getItem(int position) { return channels.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View reusable, ViewGroup parent) {
            LinearLayout row;
            TextView[] cells;
            if (reusable instanceof LinearLayout) {
                row = (LinearLayout) reusable;
                cells = (TextView[]) row.getTag();
            } else {
                row = new LinearLayout(GuideActivity.this);
                row.setPadding(dp(18), dp(12), dp(18), dp(12));
                row.setMinimumHeight(dp(82));
                row.setGravity(Gravity.CENTER_VERTICAL);
                cells = new TextView[]{label("", 17, TEXT), label("", 16, TEXT), label("", 15, MUTED)};
                float[] weights = {.27f, .39f, .34f};
                for (int i = 0; i < cells.length; i++) {
                    cells[i].setMaxLines(3);
                    cells[i].setEllipsize(TextUtils.TruncateAt.END);
                    cells[i].setPadding(0, 0, dp(18), 0);
                    cells[i].setFocusable(false);
                    row.addView(cells[i], new LinearLayout.LayoutParams(0, dp(68), weights[i]));
                }
                row.setTag(cells);
            }
            Channel channel = channels.get(position);
            Program current = guide.now(channel.tvgId);
            Program following = guide.next(channel.tvgId);
            cells[0].setText(channel.name);
            cells[1].setText(programText(current, channel.tvgId == null || channel.tvgId.isEmpty()
                    ? "Sin identificador de guía" : "Sin programación"));
            cells[2].setText(programText(following, "Sin programación"));
            row.setContentDescription(channel.name + ". Ahora: " + (current == null ? "sin programación" : current.title)
                    + ". Después: " + (following == null ? "sin programación" : following.title));
            return row;
        }
    }

    private String programText(Program program, String fallback) {
        if (program == null) return fallback;
        return time.format(new Date(program.startMillis)) + "  " + program.title;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_pressed}, new int[]{android.R.attr.state_focused}, new int[]{}
        }, new int[]{INK, INK, TEXT}));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(selector());
        button.setOnClickListener(listener);
        return button;
    }

    private StateListDrawable selector() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, surface(ACCENT, true));
        drawable.addState(new int[]{android.R.attr.state_focused}, surface(ACCENT, true));
        drawable.addState(new int[]{android.R.attr.state_selected}, surface(PANEL, true));
        drawable.addState(new int[]{}, surface(PANEL, false));
        return drawable;
    }

    private StateListDrawable rowSelector() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, surface(PANEL, true));
        drawable.addState(new int[]{android.R.attr.state_focused}, surface(PANEL, true));
        drawable.addState(new int[]{android.R.attr.state_selected}, surface(PANEL, true));
        drawable.addState(new int[]{}, surface(PANEL, false));
        return drawable;
    }

    private GradientDrawable surface(int color, boolean border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(7));
        if (border) drawable.setStroke(dp(2), ACCENT);
        return drawable;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
