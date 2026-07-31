package gg.fotia.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {

    @Test
    void softDependUsesUpstreamCustomNameplatesPluginName() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream);
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("- CustomNameplates"));
            assertFalse(descriptor.contains("- Custom-Nameplates"));
        }
    }

    @Test
    void mentionPermissionIsEnabledForPlayersByDefault() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream);
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("fotiachat.mention.use:"));
            assertTrue(descriptor.matches("(?s).*fotiachat\\.mention\\.use:.*?default: true.*"));
        }
    }

    @Test
    void defaultConfigContainsMentionDisplayHoverAndSoundSettings() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(config.contains("mention:"));
            assertTrue(config.contains("auto-detect-player-name: true"));
            assertTrue(config.contains("display:"));
            assertTrue(config.contains("hover:"));
            assertTrue(config.contains("sound:"));
        }
    }
}
