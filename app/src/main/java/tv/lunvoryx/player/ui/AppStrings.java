package tv.lunvoryx.player.ui;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppStrings {
    private static final String PREF = "display";
    private static final String KEY_LANG = "language"; // "es" or "en"

    public static String getLang(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LANG, "es");
    }

    public static void setLang(Context c, String lang) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LANG, lang).apply();
    }

    public static boolean isEnglish(Context c) {
        return "en".equalsIgnoreCase(getLang(c));
    }

    public static String get(Context c, String key) {
        boolean en = isEnglish(c);
        switch (key) {
            case "nav_home": return en ? "Home" : "Inicio";
            case "nav_live": return en ? "Live TV" : "TV En Vivo";
            case "nav_movies": return en ? "Movies" : "Películas";
            case "nav_series": return en ? "Series" : "Series";
            case "nav_search": return en ? "Search" : "Buscar";
            case "nav_settings": return en ? "Settings" : "Ajustes";
            case "nav_playlists": return en ? "My Lists" : "Mis listas";

            case "local_library": return en ? "Local library" : "Biblioteca local";
            case "subtitle_search": return en ? "Search across all your channels and content" : "Busca en todos tus canales y contenidos";
            case "subtitle_settings": return en ? "Personalize your experience" : "Personaliza tu experiencia";
            case "subtitle_playlists": return en ? "Your sources, saved on this device." : "Tus fuentes, guardadas en este dispositivo.";
            case "section_general": return en ? "General" : "General";
            case "section_playback": return en ? "Playback" : "Reproducción";
            case "setting_logos": return en ? "Channel logos" : "Logotipos de canales";
            case "setting_alpha": return en ? "Alphabetical order" : "Orden alfabético";
            case "setting_lang": return en ? "Language" : "Idioma";
            case "setting_guide": return en ? "Configure program guide" : "Configurar guía de programación";
            case "setting_guide_sub": return en ? "Manage EPG guide" : "Gestiona la guía EPG";
            case "setting_updates": return en ? "Check for updates" : "Comprobar actualizaciones";
            case "setting_video_player": return en ? "Video player" : "Reproductor de vídeo";
            case "setting_subtitles": return en ? "Subtitles" : "Subtítulos";
            case "setting_buffer": return en ? "Playback buffer" : "Buffer de reproducción";
            case "setting_tmdb": return en ? "TMDB API Key" : "API Key de TMDB";
            case "setting_tmdb_sub": return en ? "Trailers, posters, synopsis and cast" : "Tráilers, portadas, sinopsis y reparto";
            case "setting_send_update": return en ? "Send update via Wi-Fi" : "Mandar actualización por Wi-Fi";
            case "setting_send_update_sub": return en ? "Transfer and install APK from phone" : "Transferir e instalar APK desde el móvil";
            case "val_auto": return en ? "Automatic" : "Automático";
            case "val_prefs": return en ? "Preferences" : "Preferencias";
            case "val_normal": return en ? "Normal" : "Normal";
            case "val_large": return en ? "Large (Stable)" : "Grande (Estable)";
            case "val_small": return en ? "Small (Fast)" : "Pequeño (Rápido)";
            case "enabled": return en ? "Enabled" : "Activado";
            case "disabled": return en ? "Disabled" : "Desactivado";
            case "not_configured": return en ? "Not configured" : "Sin configurar";
            case "configured": return en ? "Configured" : "Configurada";

            case "search_hint": return en ? "Enter channel or movie title" : "Escribe el nombre del canal o película";
            case "search_action": return en ? "Search" : "Buscar";
            case "all": return en ? "All" : "Todos";
            case "filter_group": return en ? "Group: " : "Grupo: ";
            case "favorites": return en ? "Favorites" : "Favoritos";
            case "guide": return en ? "Guide" : "Guía";
            case "add_list": return en ? "+ Add list" : "+ Añadir lista";
            case "demo_list": return en ? "Demo list" : "Lista de prueba";
            case "empty_search": return en ? "No channels match your search query." : "No se han encontrado canales con ese término.";
            case "search_prompt": return en ? "Type above to search across your library." : "Usa el botón de búsqueda arriba para buscar en tu biblioteca.";

            default: return key;
        }
    }
}
