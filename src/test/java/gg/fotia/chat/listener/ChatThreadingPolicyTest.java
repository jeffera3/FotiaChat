package gg.fotia.chat.listener;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatThreadingPolicyTest {

    @Test
    void fullChatPipelineRunsOnTheServerThread() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/gg/fotia/chat/listener/ChatListener.java"));

        assertTrue(source.contains("io.papermc.paper.event.player.ChatEvent"));
        assertTrue(source.contains("void onChat(ChatEvent event)"));
        assertFalse(source.contains("void onChat(AsyncChatEvent event)"));
    }
}
