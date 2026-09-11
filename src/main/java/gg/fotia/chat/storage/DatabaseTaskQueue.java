package gg.fotia.chat.storage;

import gg.fotia.chat.util.BoundedTaskQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class DatabaseTaskQueue implements AutoCloseable {
    private final BoundedTaskQueue queue;

    DatabaseTaskQueue(String threadName) {
        this(threadName, 4096, ignored -> {});
    }

    DatabaseTaskQueue(String threadName, int capacity, Consumer<String> warning) {
        queue = new BoundedTaskQueue(threadName, capacity, warning);
    }

    boolean submit(Runnable task) { return queue.submit(task); }
    void stopAccepting() { queue.stopAccepting(); }
    int pendingCount() { return queue.pendingCount(); }
    boolean flush(long timeout, TimeUnit unit) { return queue.flush(timeout, unit); }
    @Override public void close() { queue.close(); }
}
