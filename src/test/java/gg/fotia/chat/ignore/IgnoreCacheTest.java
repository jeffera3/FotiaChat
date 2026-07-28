package gg.fotia.chat.ignore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoreCacheTest {

    @Test
    void replaceAndLocalUpdatesAreImmediatelyVisible() {
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        IgnoreCache cache = new IgnoreCache();

        cache.replace(List.of(new IgnoreCache.Entry(owner, first, "First")));
        assertTrue(cache.contains(owner, first));
        assertEquals(List.of("First"), cache.names(owner));

        cache.add(owner, second, "Second");
        cache.remove(owner, first);

        assertFalse(cache.contains(owner, first));
        assertTrue(cache.contains(owner, second));
        assertEquals(List.of("Second"), cache.names(owner));
    }

    @Test
    void staleDatabaseRefreshCannotOverwriteLocalUpdate() {
        UUID owner = UUID.randomUUID();
        UUID localTarget = UUID.randomUUID();
        UUID staleTarget = UUID.randomUUID();
        IgnoreCache cache = new IgnoreCache();
        long refreshRevision = cache.revision();

        cache.add(owner, localTarget, "Local");

        assertFalse(cache.replaceIfRevision(
                List.of(new IgnoreCache.Entry(owner, staleTarget, "Stale")),
                refreshRevision
        ));
        assertTrue(cache.contains(owner, localTarget));
        assertFalse(cache.contains(owner, staleTarget));
    }

    @Test
    void databaseRefreshReplacesCacheWhenRevisionIsUnchanged() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        IgnoreCache cache = new IgnoreCache();
        long refreshRevision = cache.revision();

        assertTrue(cache.replaceIfRevision(
                List.of(new IgnoreCache.Entry(owner, target, "Fresh")),
                refreshRevision
        ));
        assertTrue(cache.contains(owner, target));
    }
}
