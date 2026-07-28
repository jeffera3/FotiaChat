package gg.fotia.chat.filter;

import gg.fotia.chat.util.ComponentTextTransformer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterRuleComponentTest {

    @Test
    void wordReplacementPreservesEmojiFontSiblings() {
        FilterRule rule = new FilterRule(
                "bad-word",
                "bad",
                FilterType.CONTAINS,
                FilterRule.ReplaceMode.REPLACE_WORD,
                "***",
                false
        );
        Component source = Component.text("BAD ")
                .append(Component.text("ꀣ").font(Key.key("emoji", "blush")))
                .append(Component.text(" bad"));

        Component filtered = rule.process(source);

        assertEquals("*** ꀣ ***", PlainTextComponentSerializer.plainText().serialize(filtered));
        assertTrue(ComponentTextTransformer.containsFont(filtered));
    }
}
