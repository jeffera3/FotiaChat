package gg.fotia.chat.mention;

import gg.fotia.chat.util.ComponentTextTransformer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionComponentDecoratorTest {

    @Test
    void replacesMentionRangesWithoutDroppingAdjacentFontComponents() {
        Component source = Component.text("ꀣ")
                .font(Key.key("fotia", "emoji"))
                .append(Component.text(" hello @Alice and Alice"));
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                PlainTextComponentSerializer.plainText().serialize(source),
                List.of("Alice"),
                true
        );

        MentionComponentDecorator.Result result = MentionComponentDecorator.decorate(
                source,
                matches,
                name -> Component.text("@" + name, NamedTextColor.YELLOW)
        );

        assertEquals("ꀣ hello @Alice and @Alice",
                PlainTextComponentSerializer.plainText().serialize(result.component()));
        assertEquals(Set.of("Alice"), result.mentionedPlayerNames());
        assertTrue(ComponentTextTransformer.containsFont(result.component()));
    }
}
