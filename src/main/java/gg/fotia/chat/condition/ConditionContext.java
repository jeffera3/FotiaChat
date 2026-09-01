package gg.fotia.chat.condition;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ConditionContext {

    private final UUID subjectId;
    private final Player player;
    private final ConditionSubject subject;
    private final Function<String, String> resolver;
    private final Map<String, String> resolvedValues = new HashMap<>();

    public ConditionContext(UUID subjectId, @Nullable Player player, Function<String, String> resolver) {
        this(subjectId, player, player == null ? null : new BukkitConditionSubject(player), resolver);
    }

    private ConditionContext(UUID subjectId, @Nullable Player player, @Nullable ConditionSubject subject,
                             Function<String, String> resolver) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.player = player;
        this.subject = subject;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static ConditionContext ofSubject(UUID subjectId, ConditionSubject subject,
                                             Function<String, String> resolver) {
        return new ConditionContext(subjectId, null, Objects.requireNonNull(subject, "subject"), resolver);
    }

    public UUID subjectId() {
        return subjectId;
    }

    @Nullable
    public Player player() {
        return player;
    }

    @Nullable
    public ConditionSubject subject() {
        return subject;
    }

    public String resolve(String input) {
        String safeInput = input == null ? "" : input;
        return resolvedValues.computeIfAbsent(safeInput, key -> {
            String resolved = resolver.apply(key);
            return resolved == null ? "" : resolved;
        });
    }
}
