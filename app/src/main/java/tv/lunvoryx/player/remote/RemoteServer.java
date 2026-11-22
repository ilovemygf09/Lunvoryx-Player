package tv.lunvoryx.player.remote;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteServer {
    public interface Listener {
        void onPlaylistReceived(String name, String url, String content);
        default void onNavigateBack() {}
    }

    public static final long MAX_PAYLOAD_BYTES = 250L * 1024 * 1024; // 250 MB max payload guard
    private static final long INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000; // 15 min auto-shutdown

    private final Listener listener;
    private final File cacheDir;
    private final String sessionToken;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running;
    private volatile long lastActivityTime;
    private int port = -1;
    private String hostAddress;

    public RemoteServer(Listener listener) {
        this(listener, null);
    }

    public RemoteServer(Listener listener, File cacheDir) {
        this.listener = listener;
        this.cacheDir = cacheDir;
        this.sessionToken = generateSecureToken();
        this.lastActivityTime = System.currentTimeMillis();
    }

    private static String generateSecureToken() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[16]; // 128 bits of entropy
        sr.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public synchronized boolean start() {
        if (running) return true;
        try {
            hostAddress = findLocalIpAddress();
            if (hostAddress == null) {
                hostAddress = "127.0.0.1";
            }

            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(8080));
                port = 8080;
            } catch (IOException e) {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(0));
                port = serverSocket.getLocalPort();
            }

            threadPool = Executors.newCachedThreadPool();
            running = true;
            lastActivityTime = System.currentTimeMillis();

            Thread serverThread = new Thread(this::listenLoop, "Lunvoryx-RemoteServer");
            serverThread.setDaemon(true);
            serverThread.start();
            return true;
        } catch (IOException e) {
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
    }

    public int getPort() {
        return port;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public String getUrl() {
        return "http://" + hostAddress + ":" + port + "/?token=" + sessionToken;
    }

    private void listenLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                // Check inactivity timeout
                if (System.currentTimeMillis() - lastActivityTime > INACTIVITY_TIMEOUT_MS) {
                    stop();
                    break;
                }

                serverSocket.setSoTimeout(5000);
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (java.net.SocketTimeoutException timeout) {
                    continue;
                }

                lastActivityTime = System.currentTimeMillis();
                if (threadPool != null && !threadPool.isShutdown()) {
                    threadPool.execute(() -> handleClient(client));
                } else {
                    try { client.close(); } catch (IOException ignored) {}
                }
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int b;
            int matches = 0;
            while ((b = in.read()) != -1) {
                headerBytes.write(b);
                if (b == '\r' && (matches == 0 || matches == 2)) {
                    matches++;
                } else if (b == '\n' && (matches == 1 || matches == 3)) {
                    matches++;
                    if (matches == 4) break;
                } else {
                    matches = (b == '\r') ? 1 : 0;
                }
            }

            String headersStr = headerBytes.toString("ISO-8859-1");
            String[] lines = headersStr.split("\r\n");
            if (lines.length == 0 || lines[0].isEmpty()) return;

            String[] parts = lines[0].split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            long contentLength = 0;
            String contentType = "";
            for (String h : lines) {
                if (h.regionMatches(true, 0, "content-length:", 0, 15)) {
                    try {
                        contentLength = Long.parseLong(h.substring(15).trim());
                    } catch (NumberFormatException ignored) {}
                } else if (h.regionMatches(true, 0, "content-type:", 0, 13)) {
                    contentType = h.substring(13).trim();
                }
            }

            if ("OPTIONS".equalsIgnoreCase(method)) {
                String cors = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type, X-Session-Token\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(cors.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            if ("GET".equalsIgnoreCase(method) && ("/".equals(path) || path.startsWith("/?"))) {
                byte[] html = getPageHtml(hostAddress, port, sessionToken).getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + html.length + "\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(html);
                out.flush();
            } else if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/status")) {
                byte[] resp = "{\"status\":\"ok\",\"app\":\"Lunvoryx Player\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + resp.length + "\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(resp);
                out.flush();
            } else if (path.startsWith("/api/navigate")) {
                if (!isAuthorized(path, lines, null)) {
                    sendUnauthorized(out);
                    return;
                }

                byte[] resp = "{\"status\":\"ok\",\"action\":\"navigate_back\"}".getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + resp.length + "\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(resp);
                out.flush();

                if (listener != null) {
                    listener.onNavigateBack();
                }
            } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/playlist")) {
                // Size validation guard to prevent memory exhaustion
                if (contentLength > MAX_PAYLOAD_BYTES) {
                    byte[] err = "{\"error\":\"El tamaño excede el límite máximo seguro permitido (250 MB).\"}".getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 413 Payload Too Large\r\n" +
                            "Content-Type: application/json; charset=UTF-8\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: " + err.length + "\r\n" +
                            "Connection: close\r\n\r\n";
                    out.write(headers.getBytes(StandardCharsets.UTF_8));
                    out.write(err);
                    out.flush();
                    return;
                }

                byte[] bodyBytes = new byte[(int) contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = in.read(bodyBytes, totalRead, (int) contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }

                String body = new String(bodyBytes, StandardCharsets.UTF_8);

                // Authentication token verification
                if (!isAuthorized(path, lines, body)) {
                    sendUnauthorized(out);
                    return;
                }

                String name = "Mi lista";
                String url = null;
                String content = null;

                try {
                    JSONObject json = new JSONObject(body);
                    name = json.optString("name", "Mi lista");
                    if (name.trim().isEmpty()) name = "Mi lista";

                    if (json.has("server") && json.has("username") && json.has("password")) {
                        String srv = json.optString("server", "").trim();
                        String usr = json.optString("username", "").trim();
                        String pwd = json.optString("password", "").trim();

                        if (!srv.isEmpty() && !usr.isEmpty() && !pwd.isEmpty()) {
                            String base = srv.replaceAll("/+$", "");
                            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                                base = "http://" + base;
                            }
                            if (isValidHttpUrl(base)) {
                                url = base + "/get.php?username=" + URLEncoder.encode(usr, "UTF-8")
                                        + "&password=" + URLEncoder.encode(pwd, "UTF-8")
                                        + "&type=m3u_plus&output=ts";
                            }
                        }
                    }

                    if (url == null && json.has("url")) {
                        String u = json.optString("url", null);
                        if (isValidHttpUrl(u)) url = u;
                    }
                    if (json.has("content")) content = json.optString("content", null);
                } catch (Exception notJson) {
                    if (body.contains("=") && !body.startsWith("#EXTM3U")) {
                        String[] pairs = body.split("&");
                        String srv = null, usr = null, pwd = null;
                        for (String pair : pairs) {
                            String[] kv = pair.split("=", 2);
                            if (kv.length == 2) {
                                String k = java.net.URLDecoder.decode(kv[0], "UTF-8");
                                String v = java.net.URLDecoder.decode(kv[1], "UTF-8");
                                if ("name".equalsIgnoreCase(k) && !v.trim().isEmpty()) name = v.trim();
                                if ("url".equalsIgnoreCase(k) && !v.trim().isEmpty() && isValidHttpUrl(v.trim())) url = v.trim();
                                if ("content".equalsIgnoreCase(k) && !v.trim().isEmpty()) content = v;
                                if ("server".equalsIgnoreCase(k) && !v.trim().isEmpty()) srv = v.trim();
                                if ("username".equalsIgnoreCase(k) && !v.trim().isEmpty()) usr = v.trim();
                                if ("password".equalsIgnoreCase(k) && !v.trim().isEmpty()) pwd = v.trim();
                            }
                        }
                        if (url == null && srv != null && usr != null && pwd != null) {
                            String base = srv.replaceAll("/+$", "");
                            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                                base = "http://" + base;
                            }
                            if (isValidHttpUrl(base)) {
                                url = base + "/get.php?username=" + URLEncoder.encode(usr, "UTF-8")
                                        + "&password=" + URLEncoder.encode(pwd, "UTF-8")
                                        + "&type=m3u_plus&output=ts";
                            }
                        }
                    } else if (isValidHttpUrl(body.trim())) {
                        url = body.trim();
                    } else if (body.startsWith("#EXTM3U")) {
                        content = body;
                    }
                }

                if (url == null && (content == null || content.trim().isEmpty())) {
                    byte[] err = "{\"error\":\"Datos inválidos: No se encontró URL o contenido M3U válido.\"}".getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 400 Bad Request\r\n" +
                            "Content-Type: application/json; charset=UTF-8\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: " + err.length + "\r\n" +
                            "Connection: close\r\n\r\n";
                    out.write(headers.getBytes(StandardCharsets.UTF_8));
                    out.write(err);
                    out.flush();
                    return;
                }

                byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + resp.length + "\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(resp);
                out.flush();

                if (listener != null) {
                    listener.onPlaylistReceived(name, url, content);
                }
            } else {
                String notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                out.write(notFound.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isAuthorized(String path, String[] headers, String body) {
        // 1. Check token in URL query string (?token=...)
        String urlToken = extractQueryParam(path, "token");
        if (sessionToken.equalsIgnoreCase(urlToken)) return true;

        // 2. Check X-Session-Token header
        if (headers != null) {
            for (String h : headers) {
                if (h.regionMatches(true, 0, "x-session-token:", 0, 16)) {
                    String val = h.substring(16).trim();
                    if (sessionToken.equalsIgnoreCase(val)) return true;
                }
            }
        }

        // 3. Check JSON body token
        if (body != null && body.contains("\"token\"")) {
            try {
                JSONObject json = new JSONObject(body);
                if (sessionToken.equalsIgnoreCase(json.optString("token", ""))) return true;
            } catch (Exception ignored) {}
        }

        return false;
    }

    private static void sendUnauthorized(OutputStream out) throws IOException {
        byte[] err = "{\"error\":\"Acceso no autorizado: Token de emparejamiento inválido o ausente.\"}".getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 401 Unauthorized\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + err.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(err);
        out.flush();
    }

    public static boolean isValidHttpUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.ROOT);
        return (u.startsWith("http://") || u.startsWith("https://")) && u.length() > 8;
    }

    private static String extractQueryParam(String path, String param) {
        if (path == null) return null;
        int q = path.indexOf('?');
        if (q < 0 || q == path.length() - 1) return null;
        String query = path.substring(q + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && param.equalsIgnoreCase(kv[0])) {
                try {
                    return java.net.URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception ignored) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static String findLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                String name = intf.getName().toLowerCase(Locale.ROOT);
                if (name.contains("wlan") || name.contains("eth") || name.contains("en")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getPageHtml(String host, int port, String token) {
        String hostPort = (host != null ? host : "192.168.1.50") + ":" + port;
        return "<!DOCTYPE html>\n" +
                "<html lang=\"es\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">\n" +
                "  <title>Lunvoryx Player - Añadir lista desde el móvil</title>\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }\n" +
                "    body {\n" +
                "      background-color: #080911;\n" +
                "      background-image:\n" +
                "        radial-gradient(circle at 85% 10%, rgba(138, 58, 245, 0.22) 0%, transparent 50%),\n" +
                "        radial-gradient(circle at 15% 20%, rgba(37, 99, 235, 0.2) 0%, transparent 45%),\n" +
                "        radial-gradient(circle at 10% 85%, rgba(147, 51, 234, 0.18) 0%, transparent 50%),\n" +
                "        radial-gradient(circle at 90% 90%, rgba(59, 130, 246, 0.18) 0%, transparent 50%);\n" +
                "      background-attachment: fixed;\n" +
                "      color: #F8FAFC;\n" +
                "      min-height: 100vh;\n" +
                "      display: flex;\n" +
                "      justify-content: center;\n" +
                "      align-items: flex-start;\n" +
                "      padding: 24px 16px 40px 16px;\n" +
                "    }\n" +
                "    .container {\n" +
                "      width: 100%;\n" +
                "      max-width: 420px;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "      gap: 16px;\n" +
                "    }\n" +
                "    /* Lunvoryx Brand Header */\n" +
                "    .header-brand {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "      margin-bottom: 2px;\n" +
                "    }\n" +
                "    .brand-logo-wrap {\n" +
                "      width: 52px;\n" +
                "      height: 52px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      margin-bottom: 4px;\n" +
                "    }\n" +
                "    .brand-title {\n" +
                "      font-size: 19px;\n" +
                "      font-weight: 800;\n" +
                "      letter-spacing: 2px;\n" +
                "      color: #FFFFFF;\n" +
                "      line-height: 1.1;\n" +
                "    }\n" +
                "    .brand-subtitle {\n" +
                "      font-size: 10.5px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 4px;\n" +
                "      color: #38BDF8;\n" +
                "      margin-top: 3px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 6px;\n" +
                "    }\n" +
                "    /* Page Title & Subtitle */\n" +
                "    .title-row {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 10px;\n" +
                "      margin-top: 6px;\n" +
                "    }\n" +
                "    .title-icon {\n" +
                "      color: #FFFFFF;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .main-heading {\n" +
                "      font-size: 21px;\n" +
                "      font-weight: 700;\n" +
                "      color: #FFFFFF;\n" +
                "      letter-spacing: -0.2px;\n" +
                "    }\n" +
                "    .page-sub {\n" +
                "      font-size: 13px;\n" +
                "      color: #94A3B8;\n" +
                "      text-align: center;\n" +
                "      line-height: 1.4;\n" +
                "      margin-top: -8px;\n" +
                "    }\n" +
                "    /* Badges & Address */\n" +
                "    .status-badge {\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 7px;\n" +
                "      background: rgba(16, 185, 129, 0.12);\n" +
                "      border: 1px solid rgba(16, 185, 129, 0.4);\n" +
                "      color: #34D399;\n" +
                "      font-size: 12.5px;\n" +
                "      font-weight: 600;\n" +
                "      padding: 6px 14px;\n" +
                "      border-radius: 9999px;\n" +
                "    }\n" +
                "    .status-dot {\n" +
                "      width: 7px;\n" +
                "      height: 7px;\n" +
                "      background: #10B981;\n" +
                "      border-radius: 50%;\n" +
                "      box-shadow: 0 0 8px #10B981;\n" +
                "    }\n" +
                "    .address-pill {\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 10px;\n" +
                "      background: rgba(255, 255, 255, 0.05);\n" +
                "      border: 1px solid rgba(255, 255, 255, 0.15);\n" +
                "      color: #E2E8F0;\n" +
                "      font-size: 14px;\n" +
                "      font-weight: 500;\n" +
                "      padding: 8px 18px;\n" +
                "      border-radius: 9999px;\n" +
                "      cursor: pointer;\n" +
                "      transition: background 0.2s;\n" +
                "    }\n" +
                "    .address-pill:active { background: rgba(255, 255, 255, 0.12); }\n" +
                "    .copy-icon { color: #94A3B8; display: flex; align-items: center; }\n" +
                "    /* Main Glassmorphic Card */\n" +
                "    .glass-card {\n" +
                "      width: 100%;\n" +
                "      background: linear-gradient(170deg, rgba(16, 22, 44, 0.92) 0%, rgba(9, 12, 26, 0.96) 100%);\n" +
                "      backdrop-filter: blur(25px);\n" +
                "      -webkit-backdrop-filter: blur(25px);\n" +
                "      border: 1.5px solid rgba(59, 130, 246, 0.85);\n" +
                "      border-radius: 26px;\n" +
                "      box-shadow: 0 0 26px rgba(59, 130, 246, 0.32), inset 0 0 16px rgba(59, 130, 246, 0.08);\n" +
                "      padding: 18px 16px 20px 16px;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "    }\n" +
                "    /* Tabs */\n" +
                "    .tabs-bar {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 6px;\n" +
                "      margin-bottom: 20px;\n" +
                "      overflow-x: auto;\n" +
                "      scrollbar-width: none;\n" +
                "    }\n" +
                "    .tabs-bar::-webkit-scrollbar { display: none; }\n" +
                "    .tab-item {\n" +
                "      flex: 1;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 7px;\n" +
                "      padding: 9px 12px;\n" +
                "      border-radius: 9999px;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 600;\n" +
                "      color: #94A3B8;\n" +
                "      background: transparent;\n" +
                "      border: 1px solid transparent;\n" +
                "      cursor: pointer;\n" +
                "      white-space: nowrap;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .tab-item.active {\n" +
                "      background: #FFFFFF;\n" +
                "      color: #0A0D1A;\n" +
                "      font-weight: 700;\n" +
                "      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.35);\n" +
                "    }\n" +
                "    /* Section header inside card */\n" +
                "    .section-header {\n" +
                "      display: flex;\n" +
                "      align-items: flex-start;\n" +
                "      gap: 12px;\n" +
                "      margin-bottom: 16px;\n" +
                "      padding-left: 2px;\n" +
                "    }\n" +
                "    .section-icon {\n" +
                "      color: #38BDF8;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      margin-top: 2px;\n" +
                "    }\n" +
                "    .section-title {\n" +
                "      font-size: 17px;\n" +
                "      font-weight: 700;\n" +
                "      color: #FFFFFF;\n" +
                "    }\n" +
                "    .section-desc {\n" +
                "      font-size: 12.5px;\n" +
                "      color: #94A3B8;\n" +
                "      margin-top: 2px;\n" +
                "      line-height: 1.35;\n" +
                "    }\n" +
                "    /* Input containers */\n" +
                "    .input-box {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      background: rgba(14, 18, 34, 0.9);\n" +
                "      border: 1px solid rgba(255, 255, 255, 0.12);\n" +
                "      border-radius: 14px;\n" +
                "      padding: 0 14px;\n" +
                "      height: 48px;\n" +
                "      margin-bottom: 11px;\n" +
                "      transition: border-color 0.2s, box-shadow 0.2s;\n" +
                "    }\n" +
                "    .input-box:focus-within {\n" +
                "      border-color: rgba(59, 130, 246, 0.9);\n" +
                "      box-shadow: 0 0 12px rgba(59, 130, 246, 0.28);\n" +
                "    }\n" +
                "    .input-leading-icon {\n" +
                "      color: #64748B;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      margin-right: 12px;\n" +
                "      flex-shrink: 0;\n" +
                "    }\n" +
                "    .input-field {\n" +
                "      flex: 1;\n" +
                "      background: transparent;\n" +
                "      border: none;\n" +
                "      outline: none;\n" +
                "      color: #FFFFFF;\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "    .input-field::placeholder { color: #64748B; font-size: 13.5px; }\n" +
                "    .input-action-icon {\n" +
                "      color: #64748B;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      cursor: pointer;\n" +
                "      padding: 4px;\n" +
                "    }\n" +
                "    /* Checkbox */\n" +
                "    .checkbox-row {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 10px;\n" +
                "      margin: 4px 0 18px 4px;\n" +
                "      cursor: pointer;\n" +
                "      user-select: none;\n" +
                "    }\n" +
                "    .custom-checkbox {\n" +
                "      width: 18px;\n" +
                "      height: 18px;\n" +
                "      border: 1.5px solid rgba(255, 255, 255, 0.3);\n" +
                "      border-radius: 5px;\n" +
                "      background: rgba(255, 255, 255, 0.04);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      transition: all 0.15s;\n" +
                "    }\n" +
                "    .custom-checkbox.checked {\n" +
                "      background: #3B82F6;\n" +
                "      border-color: #3B82F6;\n" +
                "    }\n" +
                "    .custom-checkbox.checked svg { display: block; }\n" +
                "    .custom-checkbox svg { display: none; stroke: #FFFFFF; stroke-width: 3; }\n" +
                "    .checkbox-label {\n" +
                "      font-size: 13px;\n" +
                "      color: #CBD5E1;\n" +
                "    }\n" +
                "    /* File picker drop area */\n" +
                "    .file-dropzone {\n" +
                "      border: 1.5px dashed rgba(59, 130, 246, 0.5);\n" +
                "      background: rgba(14, 18, 34, 0.6);\n" +
                "      border-radius: 16px;\n" +
                "      padding: 24px 16px;\n" +
                "      text-align: center;\n" +
                "      cursor: pointer;\n" +
                "      margin-bottom: 16px;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .file-dropzone:hover { border-color: #3B82F6; background: rgba(59, 130, 246, 0.08); }\n" +
                "    .file-drop-icon { color: #38BDF8; margin-bottom: 8px; }\n" +
                "    .file-drop-title { font-size: 14px; font-weight: 600; color: #FFFFFF; }\n" +
                "    .file-drop-sub { font-size: 12px; color: #94A3B8; margin-top: 4px; }\n" +
                "    /* Buttons */\n" +
                "    .btn-primary {\n" +
                "      width: 100%;\n" +
                "      height: 48px;\n" +
                "      background: #FFFFFF;\n" +
                "      color: #0A0D1A;\n" +
                "      border: none;\n" +
                "      border-radius: 9999px;\n" +
                "      font-size: 15.5px;\n" +
                "      font-weight: 700;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 8px;\n" +
                "      cursor: pointer;\n" +
                "      box-shadow: 0 4px 18px rgba(255, 255, 255, 0.16);\n" +
                "      transition: transform 0.15s, opacity 0.15s;\n" +
                "    }\n" +
                "    .btn-primary:active { transform: scale(0.98); opacity: 0.9; }\n" +
                "    .btn-primary:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }\n" +
                "    .switch-method-btn {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 7px;\n" +
                "      margin-top: 14px;\n" +
                "      color: #94A3B8;\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 500;\n" +
                "      background: transparent;\n" +
                "      border: none;\n" +
                "      cursor: pointer;\n" +
                "      transition: color 0.2s;\n" +
                "      width: 100%;\n" +
                "    }\n" +
                "    .switch-method-btn:active { color: #E2E8F0; }\n" +
                "    /* Bottom Wifi Card */\n" +
                "    .wifi-card {\n" +
                "      width: 100%;\n" +
                "      background: linear-gradient(135deg, rgba(26, 18, 50, 0.88) 0%, rgba(14, 11, 30, 0.94) 100%);\n" +
                "      backdrop-filter: blur(20px);\n" +
                "      -webkit-backdrop-filter: blur(20px);\n" +
                "      border: 1.5px solid rgba(168, 85, 247, 0.65);\n" +
                "      border-radius: 22px;\n" +
                "      box-shadow: 0 0 20px rgba(168, 85, 247, 0.22);\n" +
                "      padding: 18px 16px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .wifi-left-icon {\n" +
                "      color: #FFFFFF;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      padding-right: 14px;\n" +
                "      border-right: 1px solid rgba(255, 255, 255, 0.12);\n" +
                "    }\n" +
                "    .wifi-right-text {\n" +
                "      padding-left: 14px;\n" +
                "      flex: 1;\n" +
                "    }\n" +
                "    .wifi-title {\n" +
                "      font-size: 15px;\n" +
                "      font-weight: 700;\n" +
                "      color: #FFFFFF;\n" +
                "    }\n" +
                "    .wifi-sub {\n" +
                "      font-size: 12px;\n" +
                "      color: #CBD5E1;\n" +
                "      margin-top: 3px;\n" +
                "      line-height: 1.35;\n" +
                "    }\n" +
                "    .wifi-info-line {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 6px;\n" +
                "      margin-top: 7px;\n" +
                "      font-size: 11px;\n" +
                "      font-style: italic;\n" +
                "      color: #94A3B8;\n" +
                "    }\n" +
                "    /* Bottom Return Button */\n" +
                "    .btn-return {\n" +
                "      width: 100%;\n" +
                "      height: 48px;\n" +
                "      background: rgba(255, 255, 255, 0.05);\n" +
                "      border: 1.5px solid rgba(59, 130, 246, 0.55);\n" +
                "      border-radius: 9999px;\n" +
                "      color: #FFFFFF;\n" +
                "      font-size: 14.5px;\n" +
                "      font-weight: 600;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 10px;\n" +
                "      cursor: pointer;\n" +
                "      box-shadow: 0 0 16px rgba(59, 130, 246, 0.14);\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .btn-return:active { background: rgba(59, 130, 246, 0.15); transform: scale(0.98); }\n" +
                "    /* Success Screen */\n" +
                "    .success-panel {\n" +
                "      display: none;\n" +
                "      text-align: center;\n" +
                "      padding: 24px 8px;\n" +
                "    }\n" +
                "    .success-icon-wrap {\n" +
                "      width: 64px;\n" +
                "      height: 64px;\n" +
                "      background: rgba(16, 185, 129, 0.15);\n" +
                "      border: 2px solid #10B981;\n" +
                "      border-radius: 50%;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      color: #10B981;\n" +
                "      margin-bottom: 16px;\n" +
                "      box-shadow: 0 0 20px rgba(16, 185, 129, 0.35);\n" +
                "    }\n" +
                "    .success-title { font-size: 20px; font-weight: 700; color: #FFFFFF; margin-bottom: 8px; }\n" +
                "    .success-desc { font-size: 13.5px; color: #94A3B8; line-height: 1.45; margin-bottom: 22px; }\n" +
                "    /* Toast Feedback */\n" +
                "    .toast {\n" +
                "      position: fixed;\n" +
                "      bottom: 24px;\n" +
                "      background: #1E293B;\n" +
                "      border: 1px solid #3B82F6;\n" +
                "      color: #FFFFFF;\n" +
                "      padding: 10px 20px;\n" +
                "      border-radius: 9999px;\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 600;\n" +
                "      box-shadow: 0 8px 24px rgba(0,0,0,0.5);\n" +
                "      opacity: 0;\n" +
                "      transform: translateY(20px);\n" +
                "      transition: all 0.25s ease;\n" +
                "      pointer-events: none;\n" +
                "      z-index: 999;\n" +
                "    }\n" +
                "    .toast.show { opacity: 1; transform: translateY(0); }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"container\">\n" +
                "    <!-- Header with Lunvoryx Logo -->\n" +
                "    <div class=\"header-brand\">\n" +
                "      <div class=\"brand-logo-wrap\">\n" +
                "        <svg width=\"48\" height=\"48\" viewBox=\"0 0 100 100\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                "          <defs>\n" +
                "            <linearGradient id=\"lunvGrad\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\n" +
                "              <stop offset=\"0%\" stop-color=\"#38BDF8\"/>\n" +
                "              <stop offset=\"100%\" stop-color=\"#2563EB\"/>\n" +
                "            </linearGradient>\n" +
                "            <filter id=\"lunvGlow\" x=\"-20%\" y=\"-20%\" width=\"140%\" height=\"140%\">\n" +
                "              <feGaussianBlur stdDeviation=\"3\" result=\"blur\"/>\n" +
                "              <feComposite in=\"SourceGraphic\" in2=\"blur\" operator=\"over\"/>\n" +
                "            </filter>\n" +
                "          </defs>\n" +
                "          <path d=\"M50 14 L80 30 V68 L50 86 L20 68 V30 Z\" stroke=\"url(#lunvGrad)\" stroke-width=\"4.5\" fill=\"rgba(37,99,235,0.12)\" filter=\"url(#lunvGlow)\"/>\n" +
                "          <path d=\"M64 26 C72 32 76 40 76 48\" stroke=\"#38BDF8\" stroke-width=\"3.5\" stroke-linecap=\"round\"/>\n" +
                "          <path d=\"M70 18 C81 27 86 38 86 50\" stroke=\"#60A5FA\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-dasharray=\"2 2\"/>\n" +
                "          <path d=\"M42 36 L66 50 L42 64 Z\" fill=\"url(#lunvGrad)\"/>\n" +
                "        </svg>\n" +
                "      </div>\n" +
                "      <div class=\"brand-title\">LUNVORYX</div>\n" +
                "      <div class=\"brand-subtitle\"><span>—</span> PLAYER <span>—</span></div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Title & Description -->\n" +
                "    <div class=\"title-row\">\n" +
                "      <div class=\"title-icon\">\n" +
                "        <svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"5\" y=\"2\" width=\"14\" height=\"20\" rx=\"3\" ry=\"3\"></rect><line x1=\"12\" y1=\"18\" x2=\"12.01\" y2=\"18\"></line></svg>\n" +
                "      </div>\n" +
                "      <h1 class=\"main-heading\">Añadir lista desde el móvil</h1>\n" +
                "    </div>\n" +
                "    <p class=\"page-sub\">Esta página envía los datos al reproductor en tu red local.</p>\n" +
                "\n" +
                "    <!-- Status Badge -->\n" +
                "    <div class=\"status-badge\">\n" +
                "      <div class=\"status-dot\"></div>\n" +
                "      <span>Reproductor detectado</span>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Address Pill -->\n" +
                "    <div class=\"address-pill\" onclick=\"copyAddress()\">\n" +
                "      <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"7\" width=\"20\" height=\"13\" rx=\"2\"></rect><polyline points=\"17 2 12 7 7 2\"></polyline></svg>\n" +
                "      <span id=\"ipText\">" + hostPort + "</span>\n" +
                "      <div class=\"copy-icon\">\n" +
                "        <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\" ry=\"2\"></rect><path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"></path></svg>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Center Glassmorphic Card -->\n" +
                "    <div class=\"glass-card\">\n" +
                "      <!-- Top Tabs -->\n" +
                "      <div class=\"tabs-bar\">\n" +
                "        <div id=\"tab-xtream\" class=\"tab-item active\" onclick=\"selectTab('xtream')\">\n" +
                "          <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"2\" width=\"20\" height=\"8\" rx=\"2\"></rect><rect x=\"2\" y=\"14\" width=\"20\" height=\"8\" rx=\"2\"></rect><line x1=\"6\" y1=\"6\" x2=\"6.01\" y2=\"6\"></line><line x1=\"6\" y1=\"18\" x2=\"6.01\" y2=\"18\"></line></svg>\n" +
                "          <span>Xtream Codes</span>\n" +
                "        </div>\n" +
                "        <div id=\"tab-url\" class=\"tab-item\" onclick=\"selectTab('url')\">\n" +
                "          <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path><path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\"></path></svg>\n" +
                "          <span>M3U URL</span>\n" +
                "        </div>\n" +
                "        <div id=\"tab-file\" class=\"tab-item\" onclick=\"selectTab('file')\">\n" +
                "          <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"></path><polyline points=\"14 2 14 8 20 8\"></polyline></svg>\n" +
                "          <span>Archivo M3U</span>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Tab 1: Xtream Codes Form -->\n" +
                "      <div id=\"view-xtream\">\n" +
                "        <div class=\"section-header\">\n" +
                "          <div class=\"section-icon\">\n" +
                "            <svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"2\" width=\"20\" height=\"8\" rx=\"2\"></rect><rect x=\"2\" y=\"14\" width=\"20\" height=\"8\" rx=\"2\"></rect><line x1=\"6\" y1=\"6\" x2=\"6.01\" y2=\"6\"></line><line x1=\"6\" y1=\"18\" x2=\"6.01\" y2=\"18\"></line></svg>\n" +
                "          </div>\n" +
                "          <div>\n" +
                "            <div class=\"section-title\">Xtream Codes</div>\n" +
                "            <div class=\"section-desc\">Añade tu lista con tus credenciales de proveedor.</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z\"></path><line x1=\"7\" y1=\"7\" x2=\"7.01\" y2=\"7\"></line></svg>\n" +
                "          </div>\n" +
                "          <input id=\"xtream-name\" class=\"input-field\" type=\"text\" placeholder=\"Nombre de la lista\" maxlength=\"60\">\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"3\" width=\"20\" height=\"6\" rx=\"2\"></rect><rect x=\"2\" y=\"15\" width=\"20\" height=\"6\" rx=\"2\"></rect><line x1=\"6\" y1=\"6\" x2=\"6.01\" y2=\"6\"></line><line x1=\"6\" y1=\"18\" x2=\"6.01\" y2=\"18\"></line></svg>\n" +
                "          </div>\n" +
                "          <input id=\"xtream-server\" class=\"input-field\" type=\"text\" placeholder=\"URL del servidor (http://, https://)\" autocomplete=\"off\">\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"></path><circle cx=\"12\" cy=\"7\" r=\"4\"></circle></svg>\n" +
                "          </div>\n" +
                "          <input id=\"xtream-user\" class=\"input-field\" type=\"text\" placeholder=\"Usuario\" autocomplete=\"off\">\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"3\" y=\"11\" width=\"18\" height=\"11\" rx=\"2\" ry=\"2\"></rect><path d=\"M7 11V7a5 5 0 0 1 10 0v4\"></path></svg>\n" +
                "          </div>\n" +
                "          <input id=\"xtream-pass\" class=\"input-field\" type=\"password\" placeholder=\"Contraseña\" autocomplete=\"off\">\n" +
                "          <div class=\"input-action-icon\" onclick=\"togglePasswordVisibility()\">\n" +
                "            <svg id=\"eye-icon\" width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class=\"checkbox-row\" onclick=\"togglePasswordCheckbox()\">\n" +
                "          <div id=\"pass-checkbox\" class=\"custom-checkbox\">\n" +
                "            <svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"none\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>\n" +
                "          </div>\n" +
                "          <span class=\"checkbox-label\">Mostrar contraseña</span>\n" +
                "        </div>\n" +
                "        <button id=\"btn-submit-xtream\" class=\"btn-primary\" onclick=\"submitXtream()\">\n" +
                "          <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg>\n" +
                "          <span>Añadir lista</span>\n" +
                "        </button>\n" +
                "        <button class=\"switch-method-btn\" onclick=\"cycleNextTab()\">\n" +
                "          <svg width=\"15\" height=\"15\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"17 1 21 5 17 9\"></polyline><path d=\"M3 5h18\"></path><polyline points=\"7 23 3 19 7 15\"></polyline><path d=\"M21 19H3\"></path></svg>\n" +
                "          <span>Usar otro método</span>\n" +
                "        </button>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Tab 2: M3U URL Form -->\n" +
                "      <div id=\"view-url\" style=\"display: none;\">\n" +
                "        <div class=\"section-header\">\n" +
                "          <div class=\"section-icon\">\n" +
                "            <svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path><path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\"></path></svg>\n" +
                "          </div>\n" +
                "          <div>\n" +
                "            <div class=\"section-title\">Enlace M3U</div>\n" +
                "            <div class=\"section-desc\">Añade una lista en formato M3U desde una URL.</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z\"></path><line x1=\"7\" y1=\"7\" x2=\"7.01\" y2=\"7\"></line></svg>\n" +
                "          </div>\n" +
                "          <input id=\"m3u-name\" class=\"input-field\" type=\"text\" placeholder=\"Nombre de la lista\" maxlength=\"60\">\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path><path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\"></path></svg>\n" +
                "          </div>\n" +
                "          <input id=\"m3u-url\" class=\"input-field\" type=\"url\" placeholder=\"URL de la lista M3U (http://, https://)\">\n" +
                "        </div>\n" +
                "        <button id=\"btn-submit-url\" class=\"btn-primary\" style=\"margin-top: 14px;\" onclick=\"submitUrl()\">\n" +
                "          <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg>\n" +
                "          <span>Añadir lista</span>\n" +
                "        </button>\n" +
                "        <button class=\"switch-method-btn\" onclick=\"cycleNextTab()\">\n" +
                "          <svg width=\"15\" height=\"15\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"17 1 21 5 17 9\"></polyline><path d=\"M3 5h18\"></path><polyline points=\"7 23 3 19 7 15\"></polyline><path d=\"M21 19H3\"></path></svg>\n" +
                "          <span>Usar otro método</span>\n" +
                "        </button>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Tab 3: File Form -->\n" +
                "      <div id=\"view-file\" style=\"display: none;\">\n" +
                "        <div class=\"section-header\">\n" +
                "          <div class=\"section-icon\">\n" +
                "            <svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"></path><polyline points=\"14 2 14 8 20 8\"></polyline></svg>\n" +
                "          </div>\n" +
                "          <div>\n" +
                "            <div class=\"section-title\">Archivo M3U</div>\n" +
                "            <div class=\"section-desc\">Importa una lista M3U desde un archivo local (.m3u, .m3u8).</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class=\"input-box\">\n" +
                "          <div class=\"input-leading-icon\">\n" +
                "            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z\"></path><line x1=\"7\" y1=\"7\" x2=\"7.01\" y2=\"7\"></line></svg>\n" +
                "          </div>\n" +
                "          <input id=\"file-name\" class=\"input-field\" type=\"text\" placeholder=\"Nombre de la lista\" maxlength=\"60\">\n" +
                "        </div>\n" +
                "        <input id=\"file-input\" type=\"file\" accept=\".m3u,.m3u8,text/plain\" style=\"display: none;\" onchange=\"handleFileSelected(this)\">\n" +
                "        <div class=\"file-dropzone\" onclick=\"document.getElementById('file-input').click()\">\n" +
                "          <div class=\"file-drop-icon\">\n" +
                "            <svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"><path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"></path><polyline points=\"17 8 12 3 7 8\"></polyline><line x1=\"12\" y1=\"3\" x2=\"12\" y2=\"15\"></line></svg>\n" +
                "          </div>\n" +
                "          <div id=\"file-selected-title\" class=\"file-drop-title\">Seleccionar archivo M3U</div>\n" +
                "          <div id=\"file-selected-sub\" class=\"file-drop-sub\">Toca aquí para elegir tu archivo .m3u o .m3u8</div>\n" +
                "        </div>\n" +
                "        <button id=\"btn-submit-file\" class=\"btn-primary\" onclick=\"submitFile()\">\n" +
                "          <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg>\n" +
                "          <span>Añadir lista</span>\n" +
                "        </button>\n" +
                "        <button class=\"switch-method-btn\" onclick=\"cycleNextTab()\">\n" +
                "          <svg width=\"15\" height=\"15\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"17 1 21 5 17 9\"></polyline><path d=\"M3 5h18\"></path><polyline points=\"7 23 3 19 7 15\"></polyline><path d=\"M21 19H3\"></path></svg>\n" +
                "          <span>Usar otro método</span>\n" +
                "        </button>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Success Message View -->\n" +
                "      <div id=\"view-success\" class=\"success-panel\">\n" +
                "        <div class=\"success-icon-wrap\">\n" +
                "          <svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>\n" +
                "        </div>\n" +
                "        <div class=\"success-title\">¡Lista enviada con éxito!</div>\n" +
                "        <div class=\"success-desc\">Tu televisor Lunvoryx Player está cargando los canales ahora mismo.</div>\n" +
                "        <button class=\"btn-primary\" onclick=\"resetForms()\">Añadir otra lista</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Bottom Wifi Card -->\n" +
                "    <div class=\"wifi-card\">\n" +
                "      <div class=\"wifi-left-icon\">\n" +
                "        <svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"><path d=\"M5 12.55a11 11 0 0 1 14.08 0\"></path><path d=\"M1.42 9a16 16 0 0 1 21.16 0\"></path><path d=\"M8.53 16.11a6 6 0 0 1 6.95 0\"></path><line x1=\"12\" y1=\"20\" x2=\"12.01\" y2=\"20\"></line></svg>\n" +
                "      </div>\n" +
                "      <div class=\"wifi-right-text\">\n" +
                "        <div class=\"wifi-title\">Misma red Wi-Fi</div>\n" +
                "        <div class=\"wifi-sub\">Tu móvil y tu TV deben estar conectados a la misma red Wi-Fi.</div>\n" +
                "        <div class=\"wifi-info-line\">\n" +
                "          <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"12\" y1=\"16\" x2=\"12\" y2=\"12\"></line><line x1=\"12\" y1=\"8\" x2=\"12.01\" y2=\"8\"></line></svg>\n" +
                "          <span>Tus datos se envían directamente al reproductor en tu red local.</span>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Return to Playlists Button -->\n" +
                "    <button class=\"btn-return\" onclick=\"returnToPlaylists()\">\n" +
                "      <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"7\" width=\"20\" height=\"13\" rx=\"2\"></rect><polyline points=\"17 2 12 7 7 2\"></polyline></svg>\n" +
                "      <span>Volver a Mis listas</span>\n" +
                "    </button>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\" class=\"toast\">¡Texto copiado!</div>\n" +
                "\n" +
                "  <script>\n" +
                "    const SESSION_TOKEN = \"" + token + "\";\n" +
                "    const urlParams = new URLSearchParams(window.location.search);\n" +
                "    const authToken = urlParams.get('token') || SESSION_TOKEN || '';\n" +
                "    let currentTab = 'xtream';\n" +
                "    const tabs = ['xtream', 'url', 'file'];\n" +
                "    let selectedFileContent = null;\n" +
                "\n" +
                "    function selectTab(tab) {\n" +
                "      currentTab = tab;\n" +
                "      tabs.forEach(t => {\n" +
                "        const tabEl = document.getElementById('tab-' + t);\n" +
                "        const viewEl = document.getElementById('view-' + t);\n" +
                "        if (t === tab) {\n" +
                "          tabEl.classList.add('active');\n" +
                "          viewEl.style.display = 'block';\n" +
                "        } else {\n" +
                "          tabEl.classList.remove('active');\n" +
                "          viewEl.style.display = 'none';\n" +
                "        }\n" +
                "      });\n" +
                "      document.getElementById('view-success').style.display = 'none';\n" +
                "    }\n" +
                "\n" +
                "    function cycleNextTab() {\n" +
                "      const idx = tabs.indexOf(currentTab);\n" +
                "      const next = tabs[(idx + 1) % tabs.length];\n" +
                "      selectTab(next);\n" +
                "    }\n" +
                "\n" +
                "    let showPassword = false;\n" +
                "    function togglePasswordVisibility() {\n" +
                "      showPassword = !showPassword;\n" +
                "      const passInput = document.getElementById('xtream-pass');\n" +
                "      const chk = document.getElementById('pass-checkbox');\n" +
                "      const icon = document.getElementById('eye-icon');\n" +
                "      passInput.type = showPassword ? 'text' : 'password';\n" +
                "      if (showPassword) {\n" +
                "        chk.classList.add('checked');\n" +
                "        icon.innerHTML = '<path d=\"M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24\"></path><line x1=\"1\" y1=\"1\" x2=\"23\" y2=\"23\"></line>';\n" +
                "      } else {\n" +
                "        chk.classList.remove('checked');\n" +
                "        icon.innerHTML = '<path d=\"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8z\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle>';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function togglePasswordCheckbox() {\n" +
                "      togglePasswordVisibility();\n" +
                "    }\n" +
                "\n" +
                "    function copyAddress() {\n" +
                "      const text = 'http://' + (document.getElementById('ipText').innerText || window.location.host) + '/?token=' + encodeURIComponent(authToken);\n" +
                "      if (navigator.clipboard) {\n" +
                "        navigator.clipboard.writeText(text);\n" +
                "      }\n" +
                "      showToast('¡Dirección copiada!');\n" +
                "    }\n" +
                "\n" +
                "    function showToast(msg) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      t.classList.add('show');\n" +
                "      setTimeout(() => t.classList.remove('show'), 2500);\n" +
                "    }\n" +
                "\n" +
                "    async function submitXtream() {\n" +
                "      const name = document.getElementById('xtream-name').value.trim() || 'Xtream IPTV';\n" +
                "      const server = document.getElementById('xtream-server').value.trim();\n" +
                "      const username = document.getElementById('xtream-user').value.trim();\n" +
                "      const password = document.getElementById('xtream-pass').value.trim();\n" +
                "\n" +
                "      if (!server || !username || !password) {\n" +
                "        showToast('Completa el servidor, usuario y contraseña');\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      const btn = document.getElementById('btn-submit-xtream');\n" +
                "      btn.disabled = true;\n" +
                "      btn.innerHTML = 'Enviando al reproductor...';\n" +
                "\n" +
                "      try {\n" +
                "        const res = await fetch('/api/playlist?token=' + encodeURIComponent(authToken), {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json', 'X-Session-Token': authToken },\n" +
                "          body: JSON.stringify({ type: 'xtream', name, server, username, password, token: authToken })\n" +
                "        });\n" +
                "        if (res.ok) {\n" +
                "          showSuccess();\n" +
                "        } else {\n" +
                "          const errJson = await res.json().catch(() => ({}));\n" +
                "          showToast(errJson.error || 'Error al enviar la lista al televisor');\n" +
                "        }\n" +
                "      } catch (err) {\n" +
                "        showToast('Error de conexión con la TV');\n" +
                "      } finally {\n" +
                "        btn.disabled = false;\n" +
                "        btn.innerHTML = '<svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg><span>Añadir lista</span>';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    async function submitUrl() {\n" +
                "      const name = document.getElementById('m3u-name').value.trim() || 'Mi lista M3U';\n" +
                "      const url = document.getElementById('m3u-url').value.trim();\n" +
                "      if (!url || (!url.startsWith('http://') && !url.startsWith('https://'))) {\n" +
                "        showToast('Introduce una URL válida (http:// o https://)');\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      const btn = document.getElementById('btn-submit-url');\n" +
                "      btn.disabled = true;\n" +
                "      btn.innerHTML = 'Enviando...';\n" +
                "\n" +
                "      try {\n" +
                "        const res = await fetch('/api/playlist?token=' + encodeURIComponent(authToken), {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json', 'X-Session-Token': authToken },\n" +
                "          body: JSON.stringify({ type: 'm3u_url', name, url, token: authToken })\n" +
                "        });\n" +
                "        if (res.ok) {\n" +
                "          showSuccess();\n" +
                "        } else {\n" +
                "          const errJson = await res.json().catch(() => ({}));\n" +
                "          showToast(errJson.error || 'Error al procesar en el televisor');\n" +
                "        }\n" +
                "      } catch (e) {\n" +
                "        showToast('Error de conexión con la TV');\n" +
                "      } finally {\n" +
                "        btn.disabled = false;\n" +
                "        btn.innerHTML = '<svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg><span>Añadir lista</span>';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function handleFileSelected(input) {\n" +
                "      const file = input.files[0];\n" +
                "      if (!file) return;\n" +
                "      if (file.size > 250 * 1024 * 1024) {\n" +
                "        showToast('El archivo no debe superar 250 MB');\n" +
                "        return;\n" +
                "      }\n" +
                "      document.getElementById('file-selected-title').innerText = file.name;\n" +
                "      document.getElementById('file-selected-sub').innerText = (file.size / 1024).toFixed(1) + ' KB - Listo para enviar';\n" +
                "      const reader = new FileReader();\n" +
                "      reader.onload = function(e) {\n" +
                "        selectedFileContent = e.target.result;\n" +
                "      };\n" +
                "      reader.readAsText(file);\n" +
                "    }\n" +
                "\n" +
                "    async function submitFile() {\n" +
                "      const name = document.getElementById('file-name').value.trim() || 'Lista Local';\n" +
                "      if (!selectedFileContent) {\n" +
                "        showToast('Selecciona un archivo M3U primero');\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      const btn = document.getElementById('btn-submit-file');\n" +
                "      btn.disabled = true;\n" +
                "      btn.innerHTML = 'Enviando archivo...';\n" +
                "\n" +
                "      try {\n" +
                "        const res = await fetch('/api/playlist?token=' + encodeURIComponent(authToken), {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json', 'X-Session-Token': authToken },\n" +
                "          body: JSON.stringify({ type: 'm3u_file', name, content: selectedFileContent, token: authToken })\n" +
                "        });\n" +
                "        if (res.ok) {\n" +
                "          showSuccess();\n" +
                "        } else {\n" +
                "          const errJson = await res.json().catch(() => ({}));\n" +
                "          showToast(errJson.error || 'Error al enviar archivo al televisor');\n" +
                "        }\n" +
                "      } catch (e) {\n" +
                "        showToast('Error de conexión con la TV');\n" +
                "      } finally {\n" +
                "        btn.disabled = false;\n" +
                "        btn.innerHTML = '<svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"></line><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"></line></svg><span>Añadir lista</span>';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function showSuccess() {\n" +
                "      tabs.forEach(t => document.getElementById('view-' + t).style.display = 'none');\n" +
                "      document.getElementById('view-success').style.display = 'block';\n" +
                "    }\n" +
                "\n" +
                "    function resetForms() {\n" +
                "      selectTab('xtream');\n" +
                "    }\n" +
                "\n" +
                "    async function returnToPlaylists() {\n" +
                "      showToast('Volviendo a Mis listas en la TV...');\n" +
                "      try {\n" +
                "        await fetch('/api/navigate?view=playlists&token=' + encodeURIComponent(authToken), {\n" +
                "          headers: { 'X-Session-Token': authToken }\n" +
                "        });\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
