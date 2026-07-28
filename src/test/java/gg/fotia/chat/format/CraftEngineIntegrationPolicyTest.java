package gg.fotia.chat.format;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CraftEngineIntegrationPolicyTest {

    @Test
    void integrationDoesNotProbeVersionSpecificApisWithReflection() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/format/CraftEngineHandler.java"));

        assertFalse(source.contains("java.lang.reflect"));
        assertFalse(source.contains("Class.forName("));
        assertFalse(source.contains("BukkitAdaptors"));
    }
}
