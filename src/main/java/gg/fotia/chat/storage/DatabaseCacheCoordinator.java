package gg.fotia.chat.storage;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class DatabaseCacheCoordinator {

    private final Object orderingLock = new Object();

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
