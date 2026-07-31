package gg.fotia.chat.mention;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MentionMatcher {

    private MentionMatcher() {
    }

    public static List<Match> findMatches(String message,
                                          Collection<String> onlinePlayerNames,
                                          boolean detectBarePlayerNames) {
        if (message == null || message.isEmpty() || onlinePlayerNames == null || onlinePlayerNames.isEmpty()) {
            return List.of();
        }

        Map<String, String> canonicalNames = new LinkedHashMap<>();
        onlinePlayerNames.stream()
                .filter(name -> name != null && !name.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .forEach(name -> canonicalNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        if (canonicalNames.isEmpty()) {
            return List.of();
        }

        String nameAlternation = canonicalNames.values().stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        String explicitMention = "(?<![A-Za-z0-9_@])@(" + nameAlternation + ")(?![A-Za-z0-9_])";
        String expression = detectBarePlayerNames
                ? explicitMention + "|(?<![A-Za-z0-9_@])(" + nameAlternation + ")(?![A-Za-z0-9_])"
                : explicitMention;

        Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(message);
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) {
            String matchedName = matcher.group(1);
            if (matchedName == null && matcher.groupCount() >= 2) {
                matchedName = matcher.group(2);
            }
            String canonicalName = canonicalNames.get(matchedName.toLowerCase(Locale.ROOT));
            matches.add(new Match(matcher.start(), matcher.end(), canonicalName));
        }
        return List.copyOf(matches);
    }

    public record Match(int start, int end, String playerName) {
    }
}
