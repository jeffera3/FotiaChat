package gg.fotia.chat.mention;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MentionMatcherTest {

    @Test
    void matchesAtMentionsAndBareOnlinePlayerNames() {
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                "你好 @Alice，Bob 也来一下",
                List.of("Alice", "Bob"),
                true
        );

        assertEquals(List.of(
                new MentionMatcher.Match(3, 9, "Alice"),
                new MentionMatcher.Match(10, 13, "Bob")
        ), matches);
    }

    @Test
    void matchesNamesCaseInsensitivelyAndUsesCanonicalName() {
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                "@alice ALICE",
                List.of("Alice"),
                true
        );

        assertEquals(List.of(
                new MentionMatcher.Match(0, 6, "Alice"),
                new MentionMatcher.Match(7, 12, "Alice")
        ), matches);
    }

    @Test
    void doesNotMatchPlayerNameInsideAnotherUsernameOrEmailLikeText() {
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                "Malice Alice_2 test@Alice",
                List.of("Alice"),
                true
        );

        assertEquals(List.of(), matches);
    }

    @Test
    void canRequireExplicitAtPrefix() {
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                "Alice @Alice",
                List.of("Alice"),
                false
        );

        assertEquals(List.of(
                new MentionMatcher.Match(6, 12, "Alice")
        ), matches);
    }

    @Test
    void prefersLongestOnlineNameAtTheSamePosition() {
        List<MentionMatcher.Match> matches = MentionMatcher.findMatches(
                "@Alex_2 Alex",
                List.of("Alex", "Alex_2"),
                true
        );

        assertEquals(List.of(
                new MentionMatcher.Match(0, 7, "Alex_2"),
                new MentionMatcher.Match(8, 12, "Alex")
        ), matches);
    }
}
