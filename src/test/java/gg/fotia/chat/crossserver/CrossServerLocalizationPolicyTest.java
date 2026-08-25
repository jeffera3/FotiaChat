package gg.fotia.chat.crossserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossServerLocalizationPolicyTest {

    @Test
    void remotePlayerMessageIsInjectedAsComponentAfterTrustedPapiParsing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/crossserver/CrossServerManager.java"));

        assertTrue(source.contains("CROSS_SERVER_MESSAGE_MARKER"));
        assertTrue(source.contains("replaceText"));
        assertFalse(source.contains("\"message\", message.getMessage()"));
    }
}
