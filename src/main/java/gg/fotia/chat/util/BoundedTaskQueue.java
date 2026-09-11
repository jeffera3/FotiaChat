package gg.fotia.chat.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 有界、按顺序执行的后台队列；拒绝任务时绝不在调用线程执行 I/O。 */
public final class BoundedTaskQueue implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Object lock = new Object();
    private final Consumer<String> warning;
    private final int capacity;
    private boolean accepting = true;
    private long submitted;
    private long completed;
    private long rejected;
    private long nextWarning;

    public BoundedTaskQueue(String name, int capacity, Consumer<String> warning) {
        this.capacity = Math.max(1, capacity);
        this.warning = warning;
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(this.capacity), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean submit(Runnable task) {
        synchronized (lock) {
            if (!accepting) return false;
            try {
                executor.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        synchronized (lock) {
                            completed++;
                            lock.notifyAll();
                        }
                    }
                });
                submitted++;
                return true;
            } catch (RejectedExecutionException exception) {
                rejected++;
                long now = System.nanoTime();
                if (nextWarning == 0 || now - nextWarning >= 0) {
                    nextWarning = now + TimeUnit.SECONDS.toNanos(30);
                    warning.accept("后台队列已满，待处理=" + executor.getQueue().size()
                            + "/" + capacity + "，累计拒绝=" + rejected);
                }
                return false;
            }
        }
    }

    public int pendingCount() { return executor.getQueue().size(); }

    public void stopAccepting() {
        synchronized (lock) { accepting = false; }
    }

    /** 等待调用前已接收的任务，不向可能已满的队列插入额外任务。 */
    public boolean flush(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (lock) {
            long target = submitted;
            while (completed < target) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public int discardPending() {
        stopAccepting();
        return executor.shutdownNow().size();
    }

    @Override
    public void close() {
        stopAccepting();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
