package tv.lunvoryx.player.epg;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** XMLTV dates without an explicit timezone are UTC, never the device timezone. */
public final class XmltvTime {
    private static final Pattern VALUE = Pattern.compile("^(\\d{4}(?:\\d{2}){0,5})(?:\\s*([+-]\\d{4}|UTC|GMT|Z))?$");
    private static final String[] PATTERNS = {"yyyy", "yyyyMM", "yyyyMMdd", "yyyyMMddHH", "yyyyMMddHHmm", "yyyyMMddHHmmss"};

    private XmltvTime() {}

    public static long parse(String value) {
        if (value == null) return -1;
        Matcher match = VALUE.matcher(value.trim());
        if (!match.matches()) return -1;
        String digits = match.group(1);
        String offset = match.group(2);
        if (offset == null || "Z".equals(offset) || "UTC".equals(offset) || "GMT".equals(offset)) offset = "+0000";
        int hours = Integer.parseInt(offset.substring(1, 3));
        int minutes = Integer.parseInt(offset.substring(3, 5));
        if (hours > 23 || minutes > 59) return -1;
        SimpleDateFormat format = new SimpleDateFormat(PATTERNS[(digits.length() - 4) / 2] + " Z", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        String normalized = digits + " " + offset;
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(normalized, position);
        return date != null && position.getIndex() == normalized.length() ? date.getTime() : -1;
    }
}
