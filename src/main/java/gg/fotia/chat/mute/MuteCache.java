package gg.fotia.chat.mute;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 禁言记录的读优化快照缓存。
 */
final class MuteCache {

    private volatile Map<UUID, MuteData> state = Map.of();
    private volatile long revision;

    synchronized void replace(Collection<MuteData> records, long now) {
        state = buildState(records, now);
        revision++;
    }

    synchronized boolean replaceIfRevision(Collection<MuteData> records, long now, long expectedRevision) {
        if (revision != expectedRevision) {
            return false;
        }
        state = buildState(records, now);
        revision++;
        return true;
    }

    synchronized long revision() {
        return revision;
    }

    private Map<UUID, MuteData> buildState(Collection<MuteData> records, long now) {
        Map<UUID, MuteData> next = new LinkedHashMap<>();
        for (MuteData record : records) {
            if (!isExpired(record, now)) {
                next.put(record.getPlayerUuid(), record);
            }
        }
        return Map.copyOf(next);
    }

    synchronized void put(MuteData data) {
        Map<UUID, MuteData> next = new LinkedHashMap<>(state);
        next.put(data.getPlayerUuid(), data);
        state = Map.copyOf(next);
        revision++;
    }

    synchronized MuteData remove(UUID uuid) {
        MuteData existing = state.get(uuid);
        if (existing == null) {
            return null;
        }
        Map<UUID, MuteData> next = new LinkedHashMap<>(state);
        next.remove(uuid);
        state = Map.copyOf(next);
        revision++;
        return existing;
    }

    synchronized boolean removeExpired(UUID uuid, long now) {
        MuteData existing = state.get(uuid);
        if (existing == null || !isExpired(existing, now)) {
            return false;
        }
        remove(uuid);
        return true;
    }

    MuteData get(UUID uuid) {
        return state.get(uuid);
    }

    Map<UUID, MuteData> snapshot() {
        return state;
    }

    int size() {
        return state.size();
    }

    private boolean isExpired(MuteData data, long now) {
        return data.getExpireTime() > 0 && now > data.getExpireTime();
    }
}
