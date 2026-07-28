package gg.fotia.chat.storage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseCacheCoordinatorTest {

    @Test
    void localMutationBeforeRefreshQueuesWriteBeforeRead() throws InterruptedException {
        DatabaseTaskQueue queue = new DatabaseTaskQueue("mutation-first-test");
        DatabaseCacheCoordinator coordinator = new DatabaseCacheCoordinator();
        AtomicReference<String> database = new AtomicReference<>("old");
        AtomicReference<String> cache = new AtomicReference<>("old");
        AtomicLong revision = new AtomicLong();
        CountDownLatch done = new CountDownLatch(1);
        try {
            coordinator.mutate(
                    () -> {
                        cache.set("new");
                        revision.incrementAndGet();
                    },
                    () -> queue.submit(() -> database.set("new"))
            );
            coordinator.refresh(revision::get, expectedRevision -> queue.submit(() -> {
                if (revision.get() == expectedRevision) {
                    cache.set(database.get());
                }
                done.countDown();
            }));

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals("new", cache.get());
        } finally {
            queue.close();
        }
    }

    @Test
    void localMutationDuringRefreshRejectsStaleRead() throws InterruptedException {
        DatabaseTaskQueue queue = new DatabaseTaskQueue("refresh-first-test");
        DatabaseCacheCoordinator coordinator = new DatabaseCacheCoordinator();
        AtomicReference<String> database = new AtomicReference<>("old");
        AtomicReference<String> cache = new AtomicReference<>("old");
        AtomicLong revision = new AtomicLong();
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        try {
            coordinator.refresh(revision::get, expectedRevision -> queue.submit(() -> {
                readStarted.countDown();
                try {
                    releaseRead.await();
                    String staleValue = database.get();
                    if (revision.get() == expectedRevision) {
                        cache.set(staleValue);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
            assertTrue(readStarted.await(2, TimeUnit.SECONDS));

            coordinator.mutate(
                    () -> {
                        cache.set("new");
                        revision.incrementAndGet();
                    },
                    () -> queue.submit(() -> database.set("new"))
            );
            releaseRead.countDown();

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals("new", cache.get());
        } finally {
            releaseRead.countDown();
            queue.close();
        }
    }
}
