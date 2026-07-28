package gg.fotia.chat.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseShutdownPolicyTest {

    @Test
    void managerFlushesQueuedWritesBeforeClosingThePool() throws IOException {
        String queueSource = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/storage/DatabaseTaskQueue.java"));
        String managerSource = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/storage/DatabaseManager.java"));

        assertTrue(queueSource.contains("boolean flush("));
        assertTrue(managerSource.contains("taskQueue.flush("));
    }
}
