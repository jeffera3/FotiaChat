package gg.fotia.chat.itemdisplay;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SnapshotStoreTest {

    @Test
    void evictsOldestSnapshotWhenGlobalLimitIsReached() {
        SnapshotStore store = new SnapshotStore(2, 2);
        ItemSnapshot first = snapshot(UUID.randomUUID(), 100L);
        ItemSnapshot second = snapshot(UUID.randomUUID(), 100L);
        ItemSnapshot third = snapshot(UUID.randomUUID(), 100L);

        store.put(first, 0L);
        store.put(second, 0L);
        store.put(third, 0L);

        assertNull(store.get(first.id(), 0L));
        assertSame(second, store.get(second.id(), 0L));
        assertSame(third, store.get(third.id(), 0L));
        assertEquals(2, store.size());
    }

    @Test
    void evictsOldestSnapshotForSamePlayerAtPerPlayerLimit() {
        UUID owner = UUID.randomUUID();
        SnapshotStore store = new SnapshotStore(10, 1);
        ItemSnapshot first = snapshot(owner, 100L);
        ItemSnapshot second = snapshot(owner, 100L);

        store.put(first, 0L);
        store.put(second, 0L);

        assertNull(store.get(first.id(), 0L));
        assertSame(second, store.get(second.id(), 0L));
    }

    @Test
    void expiredSnapshotIsRemovedOnRead() {
        SnapshotStore store = new SnapshotStore(10, 10);
        ItemSnapshot expired = snapshot(UUID.randomUUID(), 5L);
        store.put(expired, 0L);

        assertNull(store.get(expired.id(), 6L));
        assertEquals(0, store.size());
    }

    private ItemSnapshot snapshot(UUID owner, long expiresAt) {
        return new ItemSnapshot(
                UUID.randomUUID(),
                owner,
                "Player",
                ItemSnapshot.Type.INVENTORY,
                new ItemStack[0],
                expiresAt
        );
    }
}
