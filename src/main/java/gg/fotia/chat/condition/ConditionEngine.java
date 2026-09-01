package gg.fotia.chat.condition;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class ConditionEngine {

    private final ConditionRegistry registry;
    private final ConditionParser parser;
    private final ConditionCache cache = new ConditionCache();
    private final LongSupplier tickSource;

    private ConditionEngine(ConditionRegistry registry, LongSupplier tickSource) {
        this.registry = registry;
        this.parser = new ConditionParser(registry);
        this.tickSource = tickSource;
    }

    public static ConditionEngine createDefault() {
        return createDefault(() -> System.nanoTime() / 50_000_000L);
    }

    static ConditionEngine createDefault(LongSupplier tickSource) {
        ConditionRegistry registry = new ConditionRegistry();
        BuiltinConditions.register(registry);
        return new ConditionEngine(registry, tickSource);
    }

    public ConditionRegistry registry() {
        return registry;
    }

    public ConditionSet parse(ConfigurationSection section, String basePath) throws ConditionParseException {
        return parser.parse(section, basePath);
    }

    public ConditionEvaluation evaluate(ConditionSet conditions, ConditionContext context) {
        List<ConditionTrace> traces = new ArrayList<>();
        long currentTick = tickSource.getAsLong();
        for (ConditionNode condition : conditions.conditions()) {
            if (!condition.evaluate(context, cache, currentTick, traces)) {
                return new ConditionEvaluation(false, traces);
            }
        }
        return new ConditionEvaluation(true, traces);
    }

    public void clearCache(UUID subjectId) {
        cache.clear(subjectId);
    }

    public void clearCache() {
        cache.clear();
    }
}
