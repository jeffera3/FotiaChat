package gg.fotia.chat.itemdisplay;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 有界物品快照存储，按创建顺序淘汰最旧记录。
 */
final class SnapshotStore {

    private final LinkedHashMap<UUID, ItemSnapshot> snapshots = new LinkedHashMap<>();
    private int maxTotal;
    private int maxPerPlayer;

    SnapshotStore(int maxTotal, int maxPerPlayer) {
        configure(maxTotal, maxPerPlayer, System.currentTimeMillis());
    }

    synchronized void configure(int maxTotal, int maxPerPlayer, long now) {
        this.maxTotal = Math.max(1, maxTotal);
        this.maxPerPlayer = Math.max(1, maxPerPlayer);
        cleanup(now);
        trimToLimits();
    }

    synchronized void put(ItemSnapshot snapshot, long now) {
        cleanup(now);
        snapshots.remove(snapshot.id());
        while (countForPlayer(snapshot.playerId()) >= maxPerPlayer) {
            removeOldestForPlayer(snapshot.playerId());
        }
        while (snapshots.size() >= maxTotal) {
            removeOldest();
        }
        snapshots.put(snapshot.id(), snapshot);
    }

    synchronized ItemSnapshot get(UUID id, long now) {
        ItemSnapshot snapshot = snapshots.get(id);
        if (snapshot != null && snapshot.expireTime() < now) {
            snapshots.remove(id);
            return null;
        }
        return snapshot;
    }

    synchronized void cleanup(long now) {
        snapshots.entrySet().removeIf(entry -> entry.getValue().expireTime() < now);
    }

    synchronized int size() {
        return snapshots.size();
    }

    synchronized void clear() {
        snapshots.clear();
    }

    private void trimToLimits() {
        Map<UUID, Integer> playerCounts = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, ItemSnapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            ItemSnapshot snapshot = iterator.next().getValue();
            int count = playerCounts.merge(snapshot.playerId(), 1, Integer::sum);
            if (count > maxPerPlayer) {
                iterator.remove();
            }
        }
        while (snapshots.size() > maxTotal) {
            removeOldest();
        }
    }

    private int countForPlayer(UUID playerId) {
        int count = 0;
        for (ItemSnapshot snapshot : snapshots.values()) {
            if (snapshot.playerId().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    private void removeOldestForPlayer(UUID playerId) {
        Iterator<Map.Entry<UUID, ItemSnapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().playerId().equals(playerId)) {
                iterator.remove();
                return;
            }
        }
    }

    private void removeOldest() {
        Iterator<UUID> iterator = snapshots.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
