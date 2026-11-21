package tv.lunvoryx.player.updater;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Pattern;

public final class UpdateManager {
    public static final String PREF_UPDATE_URL = "update_check_url";
    public static final String DEFAULT_UPDATE_URL = "https://api.github.com/repos/david/lunvoryx-player/releases/latest";
    private static final String PREFS_NAME = "updates";
    private static final String KEY_SUPPRESSED_UNTIL = "suppressed_until";
    public static final long MAX_APK_SIZE_BYTES = 150 * 1024 * 1024; // 150 MB safety threshold
    private static final Pattern SAFE_REPO_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$");

    public static boolean isSuppressedFor24h(Context context) {
        long until = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_SUPPRESSED_UNTIL, 0);
        return System.currentTimeMillis() < until;
    }

    public static void suppressFor24h(Context context) {
        long until = System.currentTimeMillis() + (24L * 60 * 60 * 1000);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_SUPPRESSED_UNTIL, until).apply();
    }

    public static boolean isValidRepoString(String repo) {
        return repo != null && SAFE_REPO_PATTERN.matcher(repo.trim()).matches();
    }

    public static boolean isSafeHttpsUrl(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        return trimmed.startsWith("https://") && trimmed.length() > 8;
    }

    public static class UpdateInfo {
        public final boolean hasUpdate;
        public final String latestVersion;
        public final String currentVersion;
        public final String downloadUrl;
        public final String releaseNotes;
        public final String expectedSha256;

        public UpdateInfo(boolean hasUpdate, String latestVersion, String currentVersion, String downloadUrl, String releaseNotes) {
            this(hasUpdate, latestVersion, currentVersion, downloadUrl, releaseNotes, null);
        }

        public UpdateInfo(boolean hasUpdate, String latestVersion, String currentVersion, String downloadUrl, String releaseNotes, String expectedSha256) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.currentVersion = currentVersion;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
            this.expectedSha256 = expectedSha256;
        }
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File apkFile);
        void onError(String message);
    }

    public static UpdateInfo checkForUpdate(Context context, String checkUrl) {
        String currentVersion = "1.0.0";
        try {
            currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        if (checkUrl == null || checkUrl.trim().isEmpty()) {
            return new UpdateInfo(false, currentVersion, currentVersion, null, null);
        }

        String safeUrl = checkUrl.trim();
        if (!isSafeHttpsUrl(safeUrl)) {
            return new UpdateInfo(false, currentVersion, currentVersion, null, "Por seguridad, las actualizaciones solo se admiten mediante conexiones cifradas HTTPS.");
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(safeUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "LunvoryxPlayer/1.0");

            if (conn.getResponseCode() != 200) {
                if (safeUrl.contains("api.github.com/repos/")) {
                    String fallback = safeUrl.replace("https://api.github.com/repos/", "https://raw.githubusercontent.com/")
                            .replace("/releases/latest", "/main/version.json");
                    return checkForUpdateDirect(fallback, currentVersion);
                }
                return new UpdateInfo(false, currentVersion, currentVersion, null, "HTTP " + conn.getResponseCode());
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }

            JSONObject json = new JSONObject(sb.toString());
            String latestTag = json.optString("tag_name", json.optString("version", currentVersion));
            String cleanLatest = latestTag.replaceAll("(?i)^v", "").trim();
            String notes = json.optString("body", json.optString("notes", ""));
            String sha256 = json.optString("sha256", null);

            String apkUrl = null;
            if (json.has("apk_url")) {
                apkUrl = json.getString("apk_url");
            } else if (json.has("assets")) {
                JSONArray assets = json.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }

            if (apkUrl != null && !isSafeHttpsUrl(apkUrl)) {
                return new UpdateInfo(false, cleanLatest, currentVersion, null, "La URL del paquete no es segura (debe ser HTTPS).");
            }

            boolean isNewer = compareVersions(cleanLatest, currentVersion) > 0;
            return new UpdateInfo(isNewer && apkUrl != null, cleanLatest, currentVersion, apkUrl, notes, sha256);
        } catch (Exception e) {
            return new UpdateInfo(false, currentVersion, currentVersion, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static UpdateInfo checkForUpdateDirect(String checkUrl, String currentVersion) {
        if (!isSafeHttpsUrl(checkUrl)) {
            return new UpdateInfo(false, currentVersion, currentVersion, null, "URL de comprobación no segura.");
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(checkUrl.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "LunvoryxPlayer/1.0");

            if (conn.getResponseCode() != 200) {
                return new UpdateInfo(false, currentVersion, currentVersion, null, "HTTP " + conn.getResponseCode());
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }

            JSONObject json = new JSONObject(sb.toString());
            String latestTag = json.optString("tag_name", json.optString("version", currentVersion));
            String cleanLatest = latestTag.replaceAll("(?i)^v", "").trim();
            String notes = json.optString("body", json.optString("notes", ""));
            String sha256 = json.optString("sha256", null);

            String apkUrl = json.optString("apk_url", null);
            if (apkUrl == null && json.has("assets")) {
                JSONArray assets = json.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }

            if (apkUrl != null && !isSafeHttpsUrl(apkUrl)) {
                return new UpdateInfo(false, cleanLatest, currentVersion, null, "La URL del APK debe ser HTTPS.");
            }

            boolean isNewer = compareVersions(cleanLatest, currentVersion) > 0;
            return new UpdateInfo(isNewer && apkUrl != null, cleanLatest, currentVersion, apkUrl, notes, sha256);
        } catch (Exception e) {
            return new UpdateInfo(false, currentVersion, currentVersion, null, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void downloadAndInstall(Context context, String apkUrl, DownloadCallback callback) {
        downloadAndInstall(context, apkUrl, null, callback);
    }

    public static void downloadAndInstall(Context context, String apkUrl, String expectedSha256, DownloadCallback callback) {
        if (!isSafeHttpsUrl(apkUrl)) {
            if (callback != null) callback.onError("Descarga abortada: La URL debe usar HTTPS seguro.");
            return;
        }

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        File apkFile = null;
        try {
            URL url = new URL(apkUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("User-Agent", "LunvoryxPlayer/1.0");
            conn.connect();

            int length = conn.getContentLength();
            if (length > MAX_APK_SIZE_BYTES) {
                throw new SecurityException("El archivo APK supera el tamaño máximo de seguridad (" + (MAX_APK_SIZE_BYTES / (1024*1024)) + " MB).");
            }

            in = conn.getInputStream();

            File cacheDir = new File(context.getCacheDir(), "updates");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            apkFile = new File(cacheDir, "update.apk");
            if (apkFile.exists()) apkFile.delete();

            out = new FileOutputStream(apkFile);
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            int lastProgress = 0;

            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                total += read;
                if (total > MAX_APK_SIZE_BYTES) {
                    throw new SecurityException("Descarga abortada: El archivo excedió el límite seguro de tamaño.");
                }
                if (length > 0) {
                    int p = (int) (total * 100 / length);
                    if (p != lastProgress) {
                        lastProgress = p;
                        if (callback != null) callback.onProgress(p);
                    }
                }
            }
            out.flush();

            // Perform comprehensive security verification before reporting success
            verifyDownloadedApk(context, apkFile, expectedSha256);

            if (callback != null) callback.onSuccess(apkFile);
        } catch (Exception e) {
            if (apkFile != null && apkFile.exists()) apkFile.delete();
            if (callback != null) callback.onError(e.getMessage());
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    public static void verifyDownloadedApk(Context context, File apkFile, String expectedSha256) throws SecurityException {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) {
            throw new SecurityException("El archivo APK descargado no existe o está vacío.");
        }

        // 1. Verify SHA-256 hash if provided
        if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
            String actualHash = computeSha256(apkFile);
            if (actualHash == null || !actualHash.equalsIgnoreCase(expectedSha256.trim())) {
                throw new SecurityException("La verificación de integridad SHA-256 falló. El archivo descargado puede estar corrupto o modificado.");
            }
        }

        // 2. Validate package structure
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo downloadedInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
        if (downloadedInfo == null) {
            throw new SecurityException("El archivo descargado no es un paquete APK de Android válido.");
        }

        // 3. Verify exact Package Name match
        String expectedPkg = context.getPackageName();
        if (!expectedPkg.equals(downloadedInfo.packageName)) {
            throw new SecurityException("El paquete del APK descargado (" + downloadedInfo.packageName + ") no coincide con la aplicación legítima (" + expectedPkg + ").");
        }

        // 4. Anti-downgrade validation
        try {
            PackageInfo installedInfo = pm.getPackageInfo(expectedPkg, flags);
            long installedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                    installedInfo.getLongVersionCode() : installedInfo.versionCode;
            long downloadedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                    downloadedInfo.getLongVersionCode() : downloadedInfo.versionCode;

            if (downloadedCode < installedCode) {
                throw new SecurityException("Protección anti-downgrade: La versión del APK descargado (" + downloadedCode + ") es inferior a la versión instalada (" + installedCode + ").");
            }

            // 5. Cryptographic signature and certificate verification
            if (!verifySignaturesMatch(installedInfo, downloadedInfo)) {
                throw new SecurityException("Protección criptográfica: El certificado de firma del APK descargado no coincide con el de la aplicación instalada. Actualización abortada para proteger tu televisor.");
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("No se ha podido comprobar la identidad de la aplicación instalada.");
        }
    }

    public static boolean verifySignaturesMatch(PackageInfo installed, PackageInfo downloaded) {
        if (installed == null || downloaded == null) return false;

        byte[][] installedCerts = getCertificateBytes(installed);
        byte[][] downloadedCerts = getCertificateBytes(downloaded);

        if (installedCerts == null || downloadedCerts == null || installedCerts.length == 0 || downloadedCerts.length == 0) {
            return false;
        }

        for (byte[] iCert : installedCerts) {
            boolean matched = false;
            for (byte[] dCert : downloadedCerts) {
                if (Arrays.equals(iCert, dCert)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static byte[][] getCertificateBytes(PackageInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.SigningInfo signingInfo = info.signingInfo;
                if (signingInfo == null) return null;
                android.content.pm.Signature[] sigs = signingInfo.hasMultipleSigners() ?
                        signingInfo.getApkContentsSigners() : signingInfo.getSigningCertificateHistory();
                if (sigs == null) return null;
                byte[][] bytes = new byte[sigs.length][];
                for (int i = 0; i < sigs.length; i++) {
                    bytes[i] = sigs[i].toByteArray();
                }
                return bytes;
            } else {
                android.content.pm.Signature[] sigs = info.signatures;
                if (sigs == null) return null;
                byte[][] bytes = new byte[sigs.length][];
                for (int i = 0; i < sigs.length; i++) {
                    bytes[i] = sigs[i].toByteArray();
                }
                return bytes;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static String computeSha256(File file) {
        if (file == null || !file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void launchApkInstall(Context context, File apkFile) {
        try {
            // Check unknown sources installation permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    Intent manageIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.getPackageName()));
                    manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(manageIntent);
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
            } else {
                uri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "0";
        if (v2 == null) v2 = "0";
        v1 = v1.replaceAll("(?i)^v", "").trim();
        v2 = v2.replaceAll("(?i)^v", "").trim();

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int max = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < max; i++) {
            int n1 = i < parts1.length ? parseNumber(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseNumber(parts2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseNumber(String s) {
        try {
            String digits = s.replaceAll("\\D+", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
