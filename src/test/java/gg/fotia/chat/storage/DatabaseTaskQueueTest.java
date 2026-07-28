package gg.fotia.chat.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTaskQueueTest {

    @Test
    void executesTasksInSubmissionOrder() throws InterruptedException {
        DatabaseTaskQueue queue = new DatabaseTaskQueue("database-order-test");
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        try {
            assertTrue(queue.submit(() -> order.add("write")));
            assertTrue(queue.submit(() -> {
                order.add("refresh");
                done.countDown();
            }));

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("write", "refresh"), order);
        } finally {
            queue.close();
        }
    }

    @Test
    void flushWaitsForPreviouslyAcceptedTasks() {
        DatabaseTaskQueue queue = new DatabaseTaskQueue("database-flush-test");
        AtomicBoolean completed = new AtomicBoolean();
        try {
            assertTrue(queue.submit(() -> completed.set(true)));

            assertTrue(queue.flush(2, TimeUnit.SECONDS));
            assertTrue(completed.get());
        } finally {
            queue.close();
        }
    }

    @Test
    void stoppedQueueRejectsNewTasksButStillFlushesAcceptedTasks() {
        DatabaseTaskQueue queue = new DatabaseTaskQueue("database-stop-test");
        AtomicBoolean completed = new AtomicBoolean();
        try {
            assertTrue(queue.submit(() -> completed.set(true)));

            queue.stopAccepting();

            assertFalse(queue.submit(() -> {
            }));
            assertTrue(queue.flush(2, TimeUnit.SECONDS));
            assertTrue(completed.get());
        } finally {
            queue.close();
        }
    }
}
