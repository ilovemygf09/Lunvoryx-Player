package tv.lunvoryx.player.epg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable per-channel index; building it runs off the UI thread. */
public final class EpgIndex {
    private final Map<String, List<Program>> channels;

    public EpgIndex(List<Program> programs) {
        Map<String, List<Program>> result = new HashMap<>();
        for (Program program : programs) {
            List<Program> items = result.get(program.channelId);
            if (items == null) {
                items = new ArrayList<>();
                result.put(program.channelId, items);
            }
            items.add(program);
        }
        for (Map.Entry<String, List<Program>> entry : result.entrySet()) {
            List<Program> items = entry.getValue();
            Collections.sort(items, (left, right) -> Long.compare(left.startMillis, right.startMillis));
            List<Program> normalized = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                Program p = items.get(i);
                // XMLTV permits an omitted stop; a following start supplies an exact boundary.
                if (p.endMillis <= p.startMillis && i + 1 < items.size() && items.get(i + 1).startMillis > p.startMillis) {
                    p = new Program(p.channelId, p.title, p.description, p.startMillis, items.get(i + 1).startMillis);
                }
                normalized.add(p);
            }
            entry.setValue(Collections.unmodifiableList(normalized));
        }
        channels = Collections.unmodifiableMap(result);
    }

    public Program now(String channelId, long time) {
        List<Program> items = channels.get(channelId);
        if (items == null) return null;
        int end = upperBound(items, time);
        for (int i = end - 1; i >= 0; i--) {
            Program p = items.get(i);
            if (p.isOnAir(time)) return p;
        }
        return null;
    }

    public Program next(String channelId, long time) {
        List<Program> items = channels.get(channelId);
        if (items == null) return null;
        int position = upperBound(items, time);
        return position < items.size() ? items.get(position) : null;
    }

    private static int upperBound(List<Program> items, long time) {
        int low = 0;
        int high = items.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (items.get(middle).startMillis <= time) low = middle + 1;
            else high = middle;
        }
        return low;
    }
}
