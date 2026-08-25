package gg.fotia.chat.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageUtilLegacyColorTest {

    @Test
    void parseConvertsLegacyAndHexColorsBeforeMiniMessage() {
        String serialized = MiniMessage.miniMessage().serialize(
                MessageUtil.parse("&cRed &#12AB34Hex §lBold"));

        assertTrue(serialized.contains("<red>"));
        assertTrue(serialized.contains("<#12ab34>"));
        assertTrue(serialized.contains("<bold>"));
    }
}
