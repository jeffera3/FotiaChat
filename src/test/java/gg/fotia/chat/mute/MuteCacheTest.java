package gg.fotia.chat.mute;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuteCacheTest {

    @Test
    void replaceDropsExpiredRecordsAndKeepsPermanentRecords() {
        long now = 10_000L;
        MuteData expired = mute(now - 1);
        MuteData permanent = mute(0);
        MuteCache cache = new MuteCache();

        cache.replace(List.of(expired, permanent), now);

        assertNull(cache.get(expired.getPlayerUuid()));
        assertSame(permanent, cache.get(permanent.getPlayerUuid()));
    }

    @Test
    void removeExpiredReportsWhetherRecordWasRemoved() {
        long now = 10_000L;
        MuteData expired = mute(now - 1);
        MuteData active = mute(now + 1);
        MuteCache cache = new MuteCache();
        cache.put(expired);
        cache.put(active);

        assertTrue(cache.removeExpired(expired.getPlayerUuid(), now));
        assertFalse(cache.removeExpired(active.getPlayerUuid(), now));
        assertSame(active, cache.get(active.getPlayerUuid()));
    }

    @Test
    void staleDatabaseRefreshCannotOverwriteLocalUpdate() {
        long now = 10_000L;
        MuteData local = mute(now + 1_000L);
        MuteData stale = mute(now + 2_000L);
        MuteCache cache = new MuteCache();
        long refreshRevision = cache.revision();

        cache.put(local);

        assertFalse(cache.replaceIfRevision(List.of(stale), now, refreshRevision));
        assertSame(local, cache.get(local.getPlayerUuid()));
        assertNull(cache.get(stale.getPlayerUuid()));
    }

    @Test
    void databaseRefreshReplacesCacheWhenRevisionIsUnchanged() {
        long now = 10_000L;
        MuteData fresh = mute(now + 1_000L);
        MuteCache cache = new MuteCache();
        long refreshRevision = cache.revision();

        assertTrue(cache.replaceIfRevision(List.of(fresh), now, refreshRevision));
        assertSame(fresh, cache.get(fresh.getPlayerUuid()));
    }

    private MuteData mute(long expireTime) {
        UUID uuid = UUID.randomUUID();
        return new MuteData(uuid, "Player", 1L, expireTime, "reason", "Console");
    }
}
