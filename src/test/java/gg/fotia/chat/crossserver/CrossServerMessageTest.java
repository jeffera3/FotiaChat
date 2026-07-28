package gg.fotia.chat.crossserver;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrossServerMessageTest {

    @Test
    void roundTripPreservesDelimiterAndUnicodeContent() {
        UUID sender = UUID.randomUUID();
        CrossServerMessage original = new CrossServerMessage(
                CrossServerMessage.TYPE_CHAT,
                "survival||一服",
                sender,
                "玩家||A",
                "global||chat",
                "消息||<font:emoji:test>ꀣ</font>"
        );

        CrossServerMessage decoded = CrossServerMessage.deserialize(original.serialize());

        assertEquals(original.getType(), decoded.getType());
        assertEquals(original.getServerName(), decoded.getServerName());
        assertEquals(sender, decoded.getSenderUuid());
        assertEquals(original.getSenderName(), decoded.getSenderName());
        assertEquals(original.getChannelId(), decoded.getChannelId());
        assertEquals(original.getMessage(), decoded.getMessage());
    }

    @Test
    void legacyPayloadRemainsReadable() {
        String legacy = "CHAT||server1||||Server||global||legacy message||123";

        CrossServerMessage decoded = CrossServerMessage.deserialize(legacy);

        assertEquals(CrossServerMessage.TYPE_CHAT, decoded.getType());
        assertEquals("server1", decoded.getServerName());
        assertNull(decoded.getSenderUuid());
        assertEquals("legacy message", decoded.getMessage());
    }

    @Test
    void malformedPayloadReturnsNullInsteadOfThrowing() {
        assertNull(CrossServerMessage.deserialize(null));
        assertNull(CrossServerMessage.deserialize("FC2:not-base64"));
        assertNull(CrossServerMessage.deserialize("CHAT||server||bad-uuid||name||global||message||1"));
    }
}
