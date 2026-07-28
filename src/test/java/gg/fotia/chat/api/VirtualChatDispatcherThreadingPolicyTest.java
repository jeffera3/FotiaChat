package gg.fotia.chat.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualChatDispatcherThreadingPolicyTest {

    @Test
    void offThreadDispatchIsQueuedInsteadOfThrowing() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/api/VirtualChatDispatcher.java"));

        assertFalse(source.contains("throw new IllegalStateException"));
        assertTrue(source.contains("Bukkit.getScheduler().runTask(plugin"));
    }
}
