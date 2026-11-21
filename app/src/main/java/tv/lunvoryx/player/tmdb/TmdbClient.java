package tv.lunvoryx.player.tmdb;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TmdbClient {
    private static final String PREFS = "display";
    private static final String KEY_API_KEY = "tmdb_api_key";
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    public static class Details {
        public String title;
        public String overview;
        public String posterUrl;
        public String backdropUrl;
        public String releaseDate;
        public double rating;
        public String trailerUrl;
        public List<String> cast = new ArrayList<>();
    }

    public static String getApiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_KEY, "");
    }

    public static void setApiKey(Context context, String apiKey) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public static boolean hasApiKey(Context context) {
        String key = getApiKey(context);
        return key != null && !key.trim().isEmpty();
    }

    public static Details searchAndGetDetails(Context context, String title, boolean isMovie, String langCode) {
        String apiKey = getApiKey(context);
        if (apiKey == null || apiKey.trim().isEmpty() || title == null || title.trim().isEmpty()) {
            return null;
        }

        try {
            String cleanTitle = cleanSearchTitle(title);
            String encoded = URLEncoder.encode(cleanTitle, "UTF-8");
            String endpoint = isMovie ? "movie" : "tv";
            String lang = (langCode != null && langCode.equals("en")) ? "en-US" : "es-ES";

            String searchUrl = BASE_URL + "/search/" + endpoint + "?api_key=" + apiKey + "&query=" + encoded + "&language=" + lang;
            String searchJson = httpGet(searchUrl);
            if (searchJson == null) return null;

            JSONObject searchObj = new JSONObject(searchJson);
            JSONArray results = searchObj.optJSONArray("results");
            if (results == null || results.length() == 0) return null;

            JSONObject first = results.getJSONObject(0);
            int id = first.optInt("id", -1);
            if (id <= 0) return null;

            Details details = new Details();
            details.title = isMovie ? first.optString("title", title) : first.optString("name", title);
            details.overview = first.optString("overview", "");
            String posterPath = first.optString("poster_path", null);
            if (posterPath != null && !posterPath.equals("null") && !posterPath.isEmpty()) {
                details.posterUrl = IMAGE_BASE + posterPath;
            }
            String backdropPath = first.optString("backdrop_path", null);
            if (backdropPath != null && !backdropPath.equals("null") && !backdropPath.isEmpty()) {
                details.backdropUrl = "https://image.tmdb.org/t/p/w1280" + backdropPath;
            }
            details.releaseDate = isMovie ? first.optString("release_date", "") : first.optString("first_air_date", "");
            details.rating = first.optDouble("vote_average", 0.0);

            // Fetch credits and videos with append_to_response
            String detailsUrl = BASE_URL + "/" + endpoint + "/" + id + "?api_key=" + apiKey + "&language=" + lang + "&append_to_response=credits,videos";
            String fullJson = httpGet(detailsUrl);
            if (fullJson != null) {
                JSONObject fullObj = new JSONObject(fullJson);
                if (details.overview == null || details.overview.isEmpty()) {
                    details.overview = fullObj.optString("overview", "");
                }
                JSONObject credits = fullObj.optJSONObject("credits");
                if (credits != null) {
                    JSONArray castArray = credits.optJSONArray("cast");
                    if (castArray != null) {
                        for (int i = 0; i < Math.min(castArray.length(), 6); i++) {
                            JSONObject actor = castArray.getJSONObject(i);
                            String name = actor.optString("name", "");
                            String charName = actor.optString("character", "");
                            if (!name.isEmpty()) {
                                details.cast.add(charName.isEmpty() ? name : name + " (" + charName + ")");
                            }
                        }
                    }
                }
                JSONObject videos = fullObj.optJSONObject("videos");
                if (videos != null) {
                    JSONArray vidArray = videos.optJSONArray("results");
                    if (vidArray != null) {
                        for (int i = 0; i < vidArray.length(); i++) {
                            JSONObject vid = vidArray.getJSONObject(i);
                            String site = vid.optString("site", "");
                            String type = vid.optString("type", "");
                            String key = vid.optString("key", "");
                            if ("YouTube".equalsIgnoreCase(site) && ("Trailer".equalsIgnoreCase(type) || "Teaser".equalsIgnoreCase(type)) && !key.isEmpty()) {
                                details.trailerUrl = "https://www.youtube.com/watch?v=" + key;
                                break;
                            }
                        }
                    }
                }
            }

            return details;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String cleanSearchTitle(String raw) {
        if (raw == null) return "";
        String s = raw;
        // Strip common tags e.g. [1080p], (2023), HD, 4K
        s = s.replaceAll("(?i)\\[.*?\\]", " ");
        s = s.replaceAll("(?i)\\b(1080p|720p|4k|uhd|fhd|hevc|x264|x265|latino|castellano|sub|dual)\\b", " ");
        // Strip year in parentheses at end: (2022) -> keep or clean
        s = s.replaceAll("[\\(\\)]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "LunvoryxPlayer/1.0");
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
