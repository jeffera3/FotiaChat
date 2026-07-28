package gg.fotia.chat.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicChatInterceptorRegistryTest {

    @Test
    void stopsAtFirstInterceptorThatConsumesMessage() {
        PublicChatInterceptorRegistry registry = new PublicChatInterceptorRegistry(exception -> {});
        AtomicInteger calls = new AtomicInteger();
        registry.register((sender, channel, message) -> {
            calls.incrementAndGet();
            return true;
        });
        registry.register((sender, channel, message) -> {
            calls.incrementAndGet();
            return false;
        });

        assertTrue(registry.intercept(null, null, "answer"));
        assertEquals(1, calls.get());
    }

    @Test
    void unregisterRemovesInterceptor() {
        PublicChatInterceptorRegistry registry = new PublicChatInterceptorRegistry(exception -> {});
        PublicChatInterceptor interceptor = (sender, channel, message) -> true;
        registry.register(interceptor);
        registry.unregister(interceptor);

        assertFalse(registry.intercept(null, null, "message"));
    }

    @Test
    void faultyInterceptorDoesNotBreakRemainingInterceptors() {
        AtomicInteger failures = new AtomicInteger();
        PublicChatInterceptorRegistry registry = new PublicChatInterceptorRegistry(exception -> failures.incrementAndGet());
        registry.register((sender, channel, message) -> {
            throw new IllegalStateException("boom");
        });
        registry.register((sender, channel, message) -> true);

        assertTrue(registry.intercept(null, null, "message"));
        assertEquals(1, failures.get());
    }
}
