package tv.lunvoryx.player.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import tv.lunvoryx.player.data.Playlist;
import tv.lunvoryx.player.data.PlaylistRepository;
import tv.lunvoryx.player.remote.QrCodeGenerator;
import tv.lunvoryx.player.remote.RemoteServer;
import java.net.URLEncoder;
import java.util.List;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class AddPlaylistViewLayout extends ScrollView {
    public interface Listener {
        void onImportUrl(String name, String url);
        void onImportContent(String name, String content);
        void onSelectFile();
        void onManagePlaylists();
    }

    private final Context context;
    private final PlaylistRepository repository;
    private final Listener listener;
    private RemoteServer remoteServer;

    private EditText xtreamName;
    private EditText xtreamServer;
    private EditText xtreamUser;
    private EditText xtreamPass;
    private EditText m3uUrlInput;
    private TextView serverUrlText;
    private ImageView qrImageView;
    private boolean passVisible = false;

    public AddPlaylistViewLayout(Context context, PlaylistRepository repository, Listener listener) {
        super(context);
        this.context = context;
        this.repository = repository;
        this.listener = listener;

        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);

        buildUi();
        startLocalServer();
    }

    public View getFirstFocusable() {
        return xtreamName != null ? xtreamName : this;
    }

    public void stopServer() {
        if (remoteServer != null) {
            remoteServer.stop();
            remoteServer = null;
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 12));
        addView(root);

        // 1. Header (Icon + Title + Subtitle + Saved playlists button if any)
        buildHeader(root);

        // 2. Top area: Row with Left (Xtream Codes) and Right (Enlace M3U & Archivo M3U)
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(topRow, new LinearLayout.LayoutParams(-1, dp(context, 350)));

        buildXtreamCard(topRow);

        View gap = new View(context);
        topRow.addView(gap, new LinearLayout.LayoutParams(dp(context, 14), -1));

        buildM3uCardsColumn(topRow);

        // Gap between rows
        View rowGap = new View(context);
        root.addView(rowGap, new LinearLayout.LayoutParams(-1, dp(context, 12)));

        // 3. Bottom area: Full-width "Desde tu móvil" banner card
        buildMobileCard(root);
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(context, 12));

        LineIcon icon = new LineIcon(context, "playlist_add");
        icon.setColor(0xFFFFFFFF);
        headerRow.addView(icon, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));

        LinearLayout headText = new LinearLayout(context);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(context, 12), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(AppStrings.isEnglish(context) ? "Add Playlist" : "Añadir lista");
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        headText.addView(title);

        TextView sub = new TextView(context);
        sub.setText(AppStrings.isEnglish(context) ? "Choose how you want to import your content" : "Elige cómo quieres importar tu contenido");
        sub.setTextSize(11);
        sub.setTextColor(0xFF8F91A2);
        headText.addView(sub);

        headerRow.addView(headText, new LinearLayout.LayoutParams(0, -2, 1));

        // If user already has playlists, show button to view/manage them
        List<Playlist> existing = repository.getPlaylists();
        if (!existing.isEmpty()) {
            TextView manageBtn = new TextView(context);
            manageBtn.setText((AppStrings.isEnglish(context) ? "Saved lists (" : "Mis listas (") + existing.size() + ")  ›");
            manageBtn.setTextSize(12);
            manageBtn.setTextColor(0xFFFFFFFF);
            manageBtn.setTypeface(Typeface.DEFAULT_BOLD);
            manageBtn.setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7));
            manageBtn.setBackground(TvStyle.squircleCard(context, 12));
            manageBtn.setFocusable(true);
            manageBtn.setClickable(true);
            manageBtn.setOnFocusChangeListener((v, hasFocus) -> {
                manageBtn.setTextColor(hasFocus ? 0xFF0B0D18 : 0xFFFFFFFF);
            });
            manageBtn.setOnClickListener(v -> {
                if (listener != null) listener.onManagePlaylists();
            });
            headerRow.addView(manageBtn);
        }

        root.addView(headerRow);
    }

    private void buildXtreamCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        card.setBackground(TvStyle.squircleAccent(context, 18));

        // Header: Server icon + Title + Subtitle
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon sIcon = new LineIcon(context, "server");
        sIcon.setColor(0xFFFFFFFF);
        head.addView(sIcon, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));

        LinearLayout tCol = new LinearLayout(context);
        tCol.setOrientation(LinearLayout.VERTICAL);
        tCol.setPadding(dp(context, 12), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText("Xtream Codes");
        title.setTextSize(17);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tCol.addView(title);

        TextView sub = new TextView(context);
        sub.setText(AppStrings.isEnglish(context) ? "Add playlist with provider credentials" : "Añade tu lista con tus credenciales de proveedor");
        sub.setTextSize(11);
        sub.setTextColor(0xFF8F91A2);
        tCol.addView(sub);
        head.addView(tCol);
        card.addView(head);

        // Spacer
        View sp1 = new View(context);
        card.addView(sp1, new LinearLayout.LayoutParams(-1, dp(context, 12)));

        // 4 Input fields
        xtreamName = createInputField(card, "tag", AppStrings.isEnglish(context) ? "Playlist name" : "Nombre de la lista", false, false);
        xtreamServer = createInputField(card, "server", AppStrings.isEnglish(context) ? "Server URL (http://, https://)" : "URL del servidor (http://, https://)", false, false);
        xtreamUser = createInputField(card, "user", AppStrings.isEnglish(context) ? "Username" : "Usuario", false, false);
        xtreamPass = createInputField(card, "lock", AppStrings.isEnglish(context) ? "Password" : "Contraseña", true, true);

        // Spacer before button
        View sp2 = new View(context);
        card.addView(sp2, new LinearLayout.LayoutParams(-1, 0, 1));

        // "Conectar" button
        LinearLayout btnConnect = new LinearLayout(context);
        btnConnect.setOrientation(LinearLayout.HORIZONTAL);
        btnConnect.setGravity(Gravity.CENTER);
        btnConnect.setFocusable(true);
        btnConnect.setClickable(true);

        SquircleGlassDrawable btnBg = TvStyle.squircleSolid(context, 12, 0xFFFFFFFF);
        btnConnect.setBackground(btnBg);

        LineIcon linkIcon = new LineIcon(context, "link");
        linkIcon.setColor(0xFF0B0D18);
        btnConnect.addView(linkIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView btnLabel = new TextView(context);
        btnLabel.setText(AppStrings.isEnglish(context) ? "Connect" : "Conectar");
        btnLabel.setTextSize(13.5f);
        btnLabel.setTextColor(0xFF0B0D18);
        btnLabel.setTypeface(Typeface.DEFAULT_BOLD);
        btnLabel.setPadding(dp(context, 8), 0, 0, 0);
        btnConnect.addView(btnLabel);

        btnConnect.setOnFocusChangeListener((v, hasFocus) -> {
            btnBg.setSolidColor(hasFocus ? 0xFF0A84FF : 0xFFFFFFFF);
            btnBg.setCustomStroke(hasFocus ? 0x99FFFFFF : 0, hasFocus ? 1.0f : 0);
            linkIcon.setColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            btnLabel.setTextColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
        });

        btnConnect.setOnClickListener(v -> submitXtream());
        card.addView(btnConnect, new LinearLayout.LayoutParams(-1, dp(context, 38)));

        parent.addView(card, new LinearLayout.LayoutParams(0, -1, 1.05f));
    }

    private void buildM3uCardsColumn(LinearLayout parent) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        // Card 1: Enlace M3U
        LinearLayout card1 = new LinearLayout(context);
        card1.setOrientation(LinearLayout.VERTICAL);
        card1.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        card1.setBackground(TvStyle.squircleCard(context, 18));

        LinearLayout h1 = new LinearLayout(context);
        h1.setOrientation(LinearLayout.HORIZONTAL);
        h1.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon lIcon = new LineIcon(context, "link");
        lIcon.setColor(0xFFFFFFFF);
        h1.addView(lIcon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        LinearLayout t1 = new LinearLayout(context);
        t1.setOrientation(LinearLayout.VERTICAL);
        t1.setPadding(dp(context, 12), 0, 0, 0);

        TextView title1 = new TextView(context);
        title1.setText("Enlace M3U");
        title1.setTextSize(16.5f);
        title1.setTextColor(0xFFFFFFFF);
        title1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t1.addView(title1);

        TextView sub1 = new TextView(context);
        sub1.setText(AppStrings.isEnglish(context) ? "Add a playlist in M3U format from a URL" : "Añade una lista en formato M3U desde una URL");
        sub1.setTextSize(11);
        sub1.setTextColor(0xFF8F91A2);
        t1.addView(sub1);
        h1.addView(t1);
        card1.addView(h1);

        // Input field for M3U URL
        View gap1 = new View(context);
        card1.addView(gap1, new LinearLayout.LayoutParams(-1, dp(context, 8)));
        m3uUrlInput = createInputField(card1, "link", AppStrings.isEnglish(context) ? "M3U playlist URL (http://, https://)" : "URL de la lista M3U (http://, https://)", false, false);

        // Button "+ Añadir lista"
        LinearLayout btnAddM3u = new LinearLayout(context);
        btnAddM3u.setOrientation(LinearLayout.HORIZONTAL);
        btnAddM3u.setGravity(Gravity.CENTER);
        btnAddM3u.setFocusable(true);
        btnAddM3u.setClickable(true);

        SquircleGlassDrawable bBg1 = TvStyle.squircleSolid(context, 12, 0xFFFFFFFF);
        btnAddM3u.setBackground(bBg1);

        LineIcon plusIcon = new LineIcon(context, "plus");
        plusIcon.setColor(0xFF0B0D18);
        btnAddM3u.addView(plusIcon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        TextView bText1 = new TextView(context);
        bText1.setText(AppStrings.isEnglish(context) ? "Add playlist" : "Añadir lista");
        bText1.setTextSize(13);
        bText1.setTextColor(0xFF0B0D18);
        bText1.setTypeface(Typeface.DEFAULT_BOLD);
        bText1.setPadding(dp(context, 6), 0, 0, 0);
        btnAddM3u.addView(bText1);

        btnAddM3u.setOnFocusChangeListener((v, hasFocus) -> {
            bBg1.setSolidColor(hasFocus ? 0xFF0A84FF : 0xFFFFFFFF);
            bBg1.setCustomStroke(hasFocus ? 0x99FFFFFF : 0, hasFocus ? 1.0f : 0);
            plusIcon.setColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            bText1.setTextColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
        });
        btnAddM3u.setOnClickListener(v -> submitM3uUrl());

        card1.addView(btnAddM3u, new LinearLayout.LayoutParams(-1, dp(context, 36)));
        col.addView(card1, new LinearLayout.LayoutParams(-1, 0, 1));

        // Gap between the two cards
        View cGap = new View(context);
        col.addView(cGap, new LinearLayout.LayoutParams(-1, dp(context, 10)));

        // Card 2: Archivo M3U
        LinearLayout card2 = new LinearLayout(context);
        card2.setOrientation(LinearLayout.VERTICAL);
        card2.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        card2.setBackground(TvStyle.squircleCard(context, 18));

        LinearLayout h2 = new LinearLayout(context);
        h2.setOrientation(LinearLayout.HORIZONTAL);
        h2.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon fIcon = new LineIcon(context, "file");
        fIcon.setColor(0xFFFFFFFF);
        h2.addView(fIcon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        LinearLayout t2 = new LinearLayout(context);
        t2.setOrientation(LinearLayout.VERTICAL);
        t2.setPadding(dp(context, 12), 0, 0, 0);

        TextView title2 = new TextView(context);
        title2.setText("Archivo M3U");
        title2.setTextSize(16.5f);
        title2.setTextColor(0xFFFFFFFF);
        title2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t2.addView(title2);

        TextView sub2 = new TextView(context);
        sub2.setText(AppStrings.isEnglish(context) ? "Import M3U playlist from a local file (USB, disk, etc.)" : "Importa una lista M3U desde un archivo local (USB, disco, etc.)");
        sub2.setTextSize(11);
        sub2.setTextColor(0xFF8F91A2);
        t2.addView(sub2);
        h2.addView(t2);
        card2.addView(h2);

        View gap2 = new View(context);
        card2.addView(gap2, new LinearLayout.LayoutParams(-1, 0, 1));

        // Button "Seleccionar archivo"
        LinearLayout btnSelectFile = new LinearLayout(context);
        btnSelectFile.setOrientation(LinearLayout.HORIZONTAL);
        btnSelectFile.setGravity(Gravity.CENTER);
        btnSelectFile.setFocusable(true);
        btnSelectFile.setClickable(true);

        SquircleGlassDrawable bBg2 = TvStyle.squircleSolid(context, 12, 0xFFFFFFFF);
        btnSelectFile.setBackground(bBg2);

        LineIcon folderIcon = new LineIcon(context, "folder");
        folderIcon.setColor(0xFF0B0D18);
        btnSelectFile.addView(folderIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        TextView bText2 = new TextView(context);
        bText2.setText(AppStrings.isEnglish(context) ? "Select file" : "Seleccionar archivo");
        bText2.setTextSize(13);
        bText2.setTextColor(0xFF0B0D18);
        bText2.setTypeface(Typeface.DEFAULT_BOLD);
        bText2.setPadding(dp(context, 6), 0, 0, 0);
        btnSelectFile.addView(bText2);

        btnSelectFile.setOnFocusChangeListener((v, hasFocus) -> {
            bBg2.setSolidColor(hasFocus ? 0xFF0A84FF : 0xFFFFFFFF);
            bBg2.setCustomStroke(hasFocus ? 0x99FFFFFF : 0, hasFocus ? 1.0f : 0);
            folderIcon.setColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            bText2.setTextColor(hasFocus ? 0xFFFFFFFF : 0xFF0B0D18);
            v.animate().scaleX(hasFocus ? 1.02f : 1.0f).scaleY(hasFocus ? 1.02f : 1.0f).setDuration(120).start();
        });
        btnSelectFile.setOnClickListener(v -> {
            if (listener != null) listener.onSelectFile();
        });

        card2.addView(btnSelectFile, new LinearLayout.LayoutParams(-1, dp(context, 36)));
        col.addView(card2, new LinearLayout.LayoutParams(-1, 0, 1));

        parent.addView(col, new LinearLayout.LayoutParams(0, -1, 0.95f));
    }

    private void buildMobileCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));

        card.setBackground(TvStyle.squircleAccent(context, 18));

        // Section 1: Left Info (Phone icon + Title + Description)
        LinearLayout leftCol = new LinearLayout(context);
        leftCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout lHead = new LinearLayout(context);
        lHead.setOrientation(LinearLayout.HORIZONTAL);
        lHead.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon phoneIcon = new LineIcon(context, "phone");
        phoneIcon.setColor(0xFFFFFFFF);
        lHead.addView(phoneIcon, new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));

        TextView title = new TextView(context);
        title.setText(AppStrings.isEnglish(context) ? "From your mobile" : "Desde tu móvil");
        title.setTextSize(18);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(dp(context, 10), 0, 0, 0);
        lHead.addView(title);
        leftCol.addView(lHead);

        TextView desc = new TextView(context);
        desc.setText(AppStrings.isEnglish(context) ?
                "Connect your phone and TV to the same Wi-Fi. Scan the QR code or open the URL in your phone browser to enter your M3U URL or Xtream credentials comfortably." :
                "Conecta tu móvil y tu TV a la misma red Wi-Fi. Escanea el QR o abre la dirección en el navegador del móvil para introducir cómodamente tu URL M3U o credenciales Xtream.");
        desc.setTextSize(11);
        desc.setTextColor(0xFF8F91A2);
        desc.setLineSpacing(dp(context, 2), 1);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = dp(context, 8);
        leftCol.addView(desc, dp);

        card.addView(leftCol, new LinearLayout.LayoutParams(0, -2, 1.15f));

        // Section 2: Center QR Code
        FrameLayout qrFrame = new FrameLayout(context);
        qrFrame.setBackground(TvStyle.squircleSolid(context, 12, 0xFFFFFFFF));
        int qrPad = dp(context, 6);
        qrFrame.setPadding(qrPad, qrPad, qrPad, qrPad);

        qrImageView = new ImageView(context);
        qrFrame.addView(qrImageView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(context, 110), dp(context, 110));
        qp.leftMargin = dp(context, 16);
        qp.rightMargin = dp(context, 16);
        card.addView(qrFrame, qp);

        // Section 3: Right details (Wi-Fi + Active status pill, URL pill, compatibility notes)
        LinearLayout rightCol = new LinearLayout(context);
        rightCol.setOrientation(LinearLayout.VERTICAL);

        // Top row: Wi-Fi icon + Status pill
        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon wifiIcon = new LineIcon(context, "wifi");
        wifiIcon.setColor(0xFFFFFFFF);
        statusRow.addView(wifiIcon, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));

        TextView activePill = new TextView(context);
        activePill.setText(AppStrings.isEnglish(context) ? "● Local server active" : "●  Servidor local activo");
        activePill.setTextSize(11);
        activePill.setTextColor(0xFF00E676);
        activePill.setTypeface(Typeface.DEFAULT_BOLD);
        activePill.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));

        activePill.setBackground(TvStyle.squircleSolid(context, 12, 0x2200E676).setCustomStroke(0x5500E676, 1f));

        LinearLayout.LayoutParams apParams = new LinearLayout.LayoutParams(-2, -2);
        apParams.leftMargin = dp(context, 10);
        statusRow.addView(activePill, apParams);
        rightCol.addView(statusRow);

        // Middle row: URL display pill
        LinearLayout urlPill = new LinearLayout(context);
        urlPill.setOrientation(LinearLayout.HORIZONTAL);
        urlPill.setGravity(Gravity.CENTER_VERTICAL);
        urlPill.setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7));

        urlPill.setBackground(TvStyle.squircleInput(context, 12));

        LineIcon uIcon = new LineIcon(context, "link");
        uIcon.setColor(0xFF8F91A2);
        urlPill.addView(uIcon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        serverUrlText = new TextView(context);
        serverUrlText.setText("http://127.0.0.1:8080");
        serverUrlText.setTextSize(12.5f);
        serverUrlText.setTextColor(0xFFD4DCF0);
        serverUrlText.setSingleLine(true);
        serverUrlText.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        urlPill.addView(serverUrlText, new LinearLayout.LayoutParams(0, -2, 1));

        LineIcon copyIcon = new LineIcon(context, "copy");
        copyIcon.setColor(0xFF8F91A2);
        urlPill.addView(copyIcon, new LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)));

        LinearLayout.LayoutParams upLayout = new LinearLayout.LayoutParams(-1, -2);
        upLayout.topMargin = dp(context, 8);
        upLayout.bottomMargin = dp(context, 8);
        rightCol.addView(urlPill, upLayout);

        // Bottom row: Feature tags
        LinearLayout tagsRow = new LinearLayout(context);
        tagsRow.setOrientation(LinearLayout.HORIZONTAL);
        tagsRow.setGravity(Gravity.CENTER_VERTICAL);

        LineIcon pTag = new LineIcon(context, "phone");
        pTag.setColor(0xFF8F91A2);
        tagsRow.addView(pTag, new LinearLayout.LayoutParams(dp(context, 13), dp(context, 13)));

        TextView t1 = new TextView(context);
        t1.setText(AppStrings.isEnglish(context) ? "Compatible with M3U and Xtream Codes" : "Compatible con M3U y Xtream Codes");
        t1.setTextSize(10.5f);
        t1.setTextColor(0xFF8F91A2);
        t1.setPadding(dp(context, 5), 0, dp(context, 14), 0);
        tagsRow.addView(t1);

        LineIcon bTag = new LineIcon(context, "bolt");
        bTag.setColor(0xFF8F91A2);
        tagsRow.addView(bTag, new LinearLayout.LayoutParams(dp(context, 13), dp(context, 13)));

        TextView t2 = new TextView(context);
        t2.setText(AppStrings.isEnglish(context) ? "Fast & no external cloud services" : "Rápido y sin servicios externos");
        t2.setTextSize(10.5f);
        t2.setTextColor(0xFF8F91A2);
        t2.setPadding(dp(context, 5), 0, 0, 0);
        tagsRow.addView(t2);

        rightCol.addView(tagsRow);

        card.addView(rightCol, new LinearLayout.LayoutParams(0, -2, 1.25f));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText createInputField(LinearLayout parent, String iconKind, String hintText, boolean isPassword, boolean addEyeToggle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), 0, dp(context, 12), 0);

        SquircleGlassDrawable bg = TvStyle.squircleInput(context, 10);
        row.setBackground(bg);

        LineIcon icon = new LineIcon(context, iconKind);
        icon.setColor(0xFF7C8098);
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

        EditText et = new EditText(context);
        et.setHint(hintText);
        et.setHintTextColor(0xFF5F6478);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(13);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        if (isPassword) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        et.setOnFocusChangeListener((v, hasFocus) -> {
            bg.setFocused(hasFocus);
            icon.setColor(hasFocus ? 0xFF00E5FF : 0xFF7C8098);
        });

        row.addView(et, new LinearLayout.LayoutParams(0, -2, 1));

        if (addEyeToggle) {
            LineIcon eyeIcon = new LineIcon(context, "eye");
            eyeIcon.setColor(0xFF7C8098);
            eyeIcon.setFocusable(true);
            eyeIcon.setClickable(true);
            eyeIcon.setOnClickListener(v -> {
                passVisible = !passVisible;
                if (passVisible) {
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    eyeIcon.setColor(0xFF3574E8);
                } else {
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    eyeIcon.setColor(0xFF7C8098);
                }
                et.setSelection(et.getText().length());
            });
            row.addView(eyeIcon, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));
        }

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(context, 38));
        rp.bottomMargin = dp(context, 8);
        parent.addView(row, rp);
        return et;
    }

    private void submitXtream() {
        String name = xtreamName.getText().toString().trim();
        String server = xtreamServer.getText().toString().trim();
        String user = xtreamUser.getText().toString().trim();
        String pass = xtreamPass.getText().toString().trim();

        if (TextUtils.isEmpty(server) || TextUtils.isEmpty(user) || TextUtils.isEmpty(pass)) {
            Toast.makeText(context, AppStrings.isEnglish(context) ? "Please fill in server URL, username and password" : "Completa la URL del servidor, usuario y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(name)) {
            name = "Xtream IPTV";
        }

        String base = server.replaceAll("/+$", "");
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }

        try {
            String m3uUrl = base + "/get.php?username=" + URLEncoder.encode(user, "UTF-8")
                    + "&password=" + URLEncoder.encode(pass, "UTF-8")
                    + "&type=m3u_plus&output=ts";

            if (listener != null) {
                listener.onImportUrl(name, m3uUrl);
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void submitM3uUrl() {
        String url = m3uUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            Toast.makeText(context, AppStrings.isEnglish(context) ? "Please enter a valid M3U URL (http:// or https://)" : "Introduce una URL M3U válida (http:// o https://)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (listener != null) {
            listener.onImportUrl("Mi lista M3U", url);
        }
    }

    private void startLocalServer() {
        if (remoteServer != null) {
            remoteServer.stop();
        }

        remoteServer = new RemoteServer(new RemoteServer.Listener() {
            @Override public void onPlaylistReceived(String name, String url, String content) {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        if (listener != null) {
                            if (url != null && !url.isEmpty()) listener.onImportUrl(name, url);
                            else listener.onImportContent(name, content);
                        }
                    });
                }
            }
            @Override public void onNavigateBack() {
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        if (listener != null) {
                            listener.onManagePlaylists();
                        }
                    });
                }
            }
        });

        boolean started = remoteServer.start();
        String url = remoteServer.getUrl();
        if (started && url != null) {
            serverUrlText.setText(url);
            Bitmap qr = QrCodeGenerator.generate(url, dp(context, 130));
            if (qr != null && qrImageView != null) {
                qrImageView.setImageBitmap(qr);
            }
        } else {
            serverUrlText.setText("http://127.0.0.1:8080");
        }
    }
}
