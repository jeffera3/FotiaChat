package gg.fotia.chat.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTextTransformerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void sliceRemovesShortcutAndWhitespaceWithoutDroppingEmojiFont() {
        Component source = Component.text("#  hello ")
                .append(Component.text("ꀣ").font(Key.key("emoji", "blush")))
                .append(Component.text("  "));

        Component sliced = ComponentTextTransformer.slice(source, 3, PLAIN.serialize(source).length() - 2);

        assertEquals("hello ꀣ", PLAIN.serialize(sliced));
        assertTrue(ComponentTextTransformer.containsFont(sliced));
    }

    @Test
    void literalReplacementKeepsUnrelatedEmojiComponent() {
        Component source = Component.text("hello ")
                .append(Component.text("ꀣ").font(Key.key("emoji", "blush")))
                .append(Component.text(" [i]"));

        Component replaced = ComponentTextTransformer.replaceLiteral(source, "[i]", Component.text("ITEM"));

        assertEquals("hello ꀣ ITEM", PLAIN.serialize(replaced));
        assertTrue(ComponentTextTransformer.containsFont(replaced));
    }
}
