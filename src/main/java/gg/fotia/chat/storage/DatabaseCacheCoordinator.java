package gg.fotia.chat.storage;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class DatabaseCacheCoordinator {

    private final Object orderingLock = new Object();

    public void persistThenMutate(Runnable enqueueWrite, Runnable cacheMutation) {
        synchronized (orderingLock) {
            enqueueWrite.run();
            cacheMutation.run();
        }
    }

    public <T> T ordered(Supplier<T> action) {
        synchronized (orderingLock) {
            return action.get();
        }
    }

    public void mutate(Runnable cacheMutation, Runnable enqueueWrite) {
        synchronized (orderingLock) {
            cacheMutation.run();
            enqueueWrite.run();
        }
    }

    public <T> T mutate(Supplier<T> cacheMutation, Consumer<T> enqueueWrite) {
        synchronized (orderingLock) {
            T result = cacheMutation.get();
            enqueueWrite.accept(result);
            return result;
        }
    }

    public void refresh(LongSupplier revisionSupplier, LongConsumer enqueueRead) {
        synchronized (orderingLock) {
            enqueueRead.accept(revisionSupplier.getAsLong());
        }
    }
}
