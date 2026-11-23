package tv.lunvoryx.player.ui;

import tv.lunvoryx.player.R;

final class IntuitiveArtwork {
    private IntuitiveArtwork() {}

    static int channel(String id) {
        switch (id) {
            case "dazn.es": return R.drawable.intu_channel_0;
            case "la1.es": return R.drawable.intu_channel_1;
            case "antena3.es": return R.drawable.intu_channel_2;
            case "mcine.es": return R.drawable.intu_channel_3;
            case "eurosport.es": return R.drawable.intu_channel_4;
            case "natgeo.es": return R.drawable.intu_channel_5;
            case "lasexta.es": return R.drawable.intu_channel_6;
            case "cuatro.es": return R.drawable.intu_channel_7;
            default: return 0;
        }
    }

    static int poster(String title) {
        switch (title) {
            case "La mangosta": return R.drawable.intu_peliculas_0;
            case "El vestido": return R.drawable.intu_peliculas_1;
            case "Colisión": return R.drawable.intu_peliculas_2;
            case "Kraken": return R.drawable.intu_peliculas_3;
            case "Recreación de un asesinato": return R.drawable.intu_peliculas_4;
            case "Insidious: Fuera del límite": return R.drawable.intu_peliculas_5;
            case "La última chispa de esperanza": return R.drawable.intu_peliculas_6;
            case "¿Por qué me casé otra vez?": return R.drawable.intu_peliculas_7;
            case "En la ruta del crimen": return R.drawable.intu_peliculas_8;
            case "Sintel": return R.drawable.intu_peliculas_9;
            case "Luces en la catedral": return R.drawable.intu_peliculas_10;
            case "Cómo entrenar a tu dragón 2": return R.drawable.intu_peliculas_11;
            case "Stranger Things": return R.drawable.intu_series_0;
            case "Miércoles": return R.drawable.intu_series_1;
            case "La casa de papel": return R.drawable.intu_series_2;
            case "Dark": return R.drawable.intu_series_3;
            case "El juego del calamar": return R.drawable.intu_series_4;
            case "The Crown": return R.drawable.intu_series_5;
            case "Black Mirror": return R.drawable.intu_series_6;
            case "The Witcher": return R.drawable.intu_series_7;
            case "One Piece": return R.drawable.intu_series_8;
            case "Lupin": return R.drawable.intu_series_9;
            case "Élite": return R.drawable.intu_series_10;
            case "You": return R.drawable.intu_series_11;
            default: return 0;
        }
    }
}
