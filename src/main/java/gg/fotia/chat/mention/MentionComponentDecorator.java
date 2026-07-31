package gg.fotia.chat.mention;

import gg.fotia.chat.util.ComponentTextTransformer;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class MentionComponentDecorator {

    private MentionComponentDecorator() {
    }

    public static Result decorate(Component source,
                                  List<MentionMatcher.Match> matches,
                                  Function<String, Component> replacementFactory) {
        Component safeSource = source == null ? Component.empty() : source;
        if (matches == null || matches.isEmpty()) {
            return new Result(safeSource, Set.of());
        }

        Component result = Component.empty();
        Set<String> mentionedNames = new LinkedHashSet<>();
        int cursor = 0;
        for (MentionMatcher.Match match : matches) {
            if (match.start() < cursor || match.end() < match.start()) {
                continue;
            }
            result = result.append(ComponentTextTransformer.slice(safeSource, cursor, match.start()));
            result = result.append(replacementFactory.apply(match.playerName()));
            mentionedNames.add(match.playerName());
            cursor = match.end();
        }
        result = result.append(ComponentTextTransformer.slice(safeSource, cursor, Integer.MAX_VALUE));
        return new Result(result, Set.copyOf(mentionedNames));
    }

    public record Result(Component component, Set<String> mentionedPlayerNames) {
    }
}
