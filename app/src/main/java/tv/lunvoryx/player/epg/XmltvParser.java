package tv.lunvoryx.player.epg;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class XmltvParser {
    private static final long HOUR = 60L * 60 * 1000;
    private static final int MAX_PROGRAMS = 60000;

    private XmltvParser() {}

    public static List<Program> parse(InputStream input, long nowMillis) throws IOException {
        return parse(Xml.newPullParser(), input, nowMillis);
    }

    static List<Program> parse(XmlPullParser parser, InputStream input, long nowMillis) throws IOException {
        List<Program> result = new ArrayList<>();
        try {
            // Android's parser does not fetch external entities. Explicitly disable DTD processing.
            try { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false); }
            catch (XmlPullParserException ignored) {
                if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
                    throw new IOException("El dispositivo no permite leer XMLTV de forma segura.");
                }
            }
            parser.setInput(input, null);
            boolean rootSeen = false;
            boolean rootClosed = false;
            String channel = "";
            String title = "";
            String description = "";
            String field = null;
            StringBuilder text = new StringBuilder();
            long start = -1;
            long stop = -1;
            int programmeDepth = -1;
            boolean retain = false;
            int tokenCount = 0;
            for (int token = parser.getEventType(); token != XmlPullParser.END_DOCUMENT; token = parser.nextToken()) {
                if (++tokenCount > 4000000 || parser.getDepth() > 48) throw new IOException("La guía XMLTV es demasiado compleja.");
                if (Thread.currentThread().isInterrupted()) throw new IOException("Carga cancelada.");
                if (token == XmlPullParser.DOCDECL) {
                    String declaration = parser.getText();
                    if (declaration != null && declaration.indexOf('[') >= 0) throw new IOException("No se admiten entidades internas en XMLTV.");
                } else if (token == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if (rootClosed) throw new IOException("La guía XMLTV contiene datos fuera de su raíz.");
                    if (!rootSeen) {
                        if (!"tv".equals(name)) throw new IOException("El archivo no es una guía XMLTV.");
                        rootSeen = true;
                    }
                    if ("programme".equals(name) && parser.getDepth() == 2) {
                        programmeDepth = parser.getDepth();
                        channel = value(parser.getAttributeValue(null, "channel"), 512);
                        start = XmltvTime.parse(parser.getAttributeValue(null, "start"));
                        stop = XmltvTime.parse(parser.getAttributeValue(null, "stop"));
                        if (stop <= start) stop = start;
                        retain = !channel.isEmpty() && start >= 0 && start <= nowMillis + 72 * HOUR
                                && (stop > nowMillis - 6 * HOUR || start >= nowMillis - 6 * HOUR);
                        title = "";
                        description = "";
                    } else if (retain && parser.getDepth() == programmeDepth + 1
                            && (("title".equals(name) && title.isEmpty()) || ("desc".equals(name) && description.isEmpty()))) {
                        field = name;
                        text.setLength(0);
                    }
                } else if ((token == XmlPullParser.TEXT || token == XmlPullParser.CDSECT || token == XmlPullParser.ENTITY_REF) && field != null) {
                    String value = parser.getText();
                    if (value != null && text.length() < 4096) text.append(value, 0, Math.min(value.length(), 4096 - text.length()));
                } else if (token == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("tv".equals(name) && parser.getDepth() == 1) rootClosed = true;
                    if (field != null && field.equals(name) && parser.getDepth() == programmeDepth + 1) {
                        if ("title".equals(field)) title = text.toString().trim();
                        else description = text.toString().trim();
                        field = null;
                    }
                    if ("programme".equals(name) && parser.getDepth() == programmeDepth) {
                        if (retain && !title.isEmpty()) {
                            if (result.size() == MAX_PROGRAMS) throw new IOException("La guía supera los 60.000 programas para los próximos tres días.");
                            result.add(new Program(channel, title, description, start, stop));
                        }
                        retain = false;
                        field = null;
                        programmeDepth = -1;
                    }
                }
            }
            if (!rootSeen) throw new IOException("La guía XMLTV está vacía.");
            if (!rootClosed) throw new IOException("La descarga de la guía XMLTV está incompleta.");
            return result;
        } catch (XmlPullParserException e) {
            throw new IOException("No se puede leer la guía. Comprueba que sea un XMLTV válido.");
        }
    }

    private static String value(String text, int limit) {
        if (text == null) return "";
        return text.length() > limit ? text.substring(0, limit) : text;
    }
}
