package gg.fotia.chat.ignore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 屏蔽关系的读优化快照缓存。
 */
final class IgnoreCache {

    private volatile Map<UUID, Map<UUID, String>> state = Map.of();
    private volatile long revision;

    synchronized void replace(Collection<Entry> entries) {
        state = buildState(entries);
        revision++;
    }

    synchronized boolean replaceIfRevision(Collection<Entry> entries, long expectedRevision) {
        if (revision != expectedRevision) {
            return false;
        }
        state = buildState(entries);
        revision++;
        return true;
    }

    synchronized long revision() {
        return revision;
    }

    private Map<UUID, Map<UUID, String>> buildState(Collection<Entry> entries) {
        Map<UUID, LinkedHashMap<UUID, String>> mutable = new LinkedHashMap<>();
        for (Entry entry : entries) {
            mutable.computeIfAbsent(entry.owner(), ignored -> new LinkedHashMap<>())
                    .put(entry.target(), safeName(entry.name()));
        }
        return freeze(mutable);
    }

    synchronized void add(UUID owner, UUID target, String name) {
        Map<UUID, LinkedHashMap<UUID, String>> mutable = copyState();
        mutable.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(target, safeName(name));
        state = freeze(mutable);
        revision++;
    }

    synchronized void remove(UUID owner, UUID target) {
        Map<UUID, LinkedHashMap<UUID, String>> mutable = copyState();
        Map<UUID, String> ignored = mutable.get(owner);
        if (ignored == null) {
            revision++;
            return;
        }
        ignored.remove(target);
        if (ignored.isEmpty()) {
            mutable.remove(owner);
        }
        state = freeze(mutable);
        revision++;
    }

    boolean contains(UUID owner, UUID target) {
        Map<UUID, String> ignored = state.get(owner);
        return ignored != null && ignored.containsKey(target);
    }

    List<String> names(UUID owner) {
        Map<UUID, String> ignored = state.get(owner);
        return ignored == null ? List.of() : List.copyOf(ignored.values());
    }

    List<Entry> entries(UUID owner) {
        Map<UUID, String> ignored = state.get(owner);
        if (ignored == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        ignored.forEach((target, name) -> entries.add(new Entry(owner, target, name)));
        return List.copyOf(entries);
    }

    List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        state.forEach((owner, ignored) ->
                ignored.forEach((target, name) -> entries.add(new Entry(owner, target, name))));
        return List.copyOf(entries);
    }

    int ownerCount() {
        return state.size();
    }

    private Map<UUID, LinkedHashMap<UUID, String>> copyState() {
        Map<UUID, LinkedHashMap<UUID, String>> copy = new LinkedHashMap<>();
        state.forEach((owner, ignored) -> copy.put(owner, new LinkedHashMap<>(ignored)));
        return copy;
    }

    private Map<UUID, Map<UUID, String>> freeze(Map<UUID, LinkedHashMap<UUID, String>> source) {
        Map<UUID, Map<UUID, String>> frozen = new LinkedHashMap<>();
        source.forEach((owner, ignored) -> frozen.put(owner, Map.copyOf(ignored)));
        return Map.copyOf(frozen);
    }

    private String safeName(String name) {
        return name == null ? "" : name;
    }

    record Entry(UUID owner, UUID target, String name) {
    }
}
