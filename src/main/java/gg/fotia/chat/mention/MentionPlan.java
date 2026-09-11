package gg.fotia.chat.mention;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 一条消息的匹配结果，供不同语言的接收者复用。 */
public record MentionPlan(Component message, List<MentionMatcher.Match> matches, Set<UUID> mentionedPlayers) {
    public MentionPlan {
        matches = List.copyOf(matches);
        mentionedPlayers = Set.copyOf(mentionedPlayers);
    }
}
