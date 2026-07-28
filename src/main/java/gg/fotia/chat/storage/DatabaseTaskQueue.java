package gg.fotia.chat.storage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

final class DatabaseTaskQueue implements AutoCloseable {

    private final ExecutorService executor;
    private final Object lifecycleLock = new Object();
    private boolean accepting = true;

    DatabaseTaskQueue(String threadName) {
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    boolean submit(Runnable task) {
        synchronized (lifecycleLock) {
            if (!accepting || executor.isShutdown()) {
                return false;
            }
            try {
                executor.execute(task);
                return true;
            } catch (RejectedExecutionException ignored) {
                return false;
            }
        }
    }

    void stopAccepting() {
        synchronized (lifecycleLock) {
            accepting = false;
        }
    }

    boolean flush(long timeout, TimeUnit unit) {
        CountDownLatch drained = new CountDownLatch(1);
        synchronized (lifecycleLock) {
            if (executor.isShutdown()) {
                return false;
            }
            try {
                executor.execute(drained::countDown);
            } catch (RejectedExecutionException ignored) {
                return false;
            }
        }
        try {
            return drained.await(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        stopAccepting();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
