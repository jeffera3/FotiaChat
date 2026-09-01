package gg.fotia.chat.condition;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEngineTest {

    @Test
    void rootConditionsRequireEveryConditionToPass() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  world_name:
                    type: equals
                    value:
                      value1: "%world%"
                      value2: world
                  balance:
                    type: ">="
                    value:
                      value1: "%balance%"
                      value2: "1000"
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");

        assertTrue(engine.evaluate(conditions, context("world", "1250")).passed());
        assertFalse(engine.evaluate(conditions, context("world_nether", "1250")).passed());
        assertFalse(engine.evaluate(conditions, context("world", "999.99")).passed());
    }

    @Test
    void nestedOrAndInvertedConditionsShortCircuit() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  target:
                    type: "||"
                    value:
                      vip:
                        type: equals
                        value:
                          value1: "%group%"
                          value2: vip
                      high_level:
                        type: ">="
                        value:
                          value1: "%level%"
                          value2: "30"
                  not_blocked:
                    type: inverted
                    value:
                      blocked:
                        type: equals
                        value:
                          value1: "%status%"
                          value2: blocked
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");

        assertTrue(engine.evaluate(conditions, context(MapValues.of(
                "%group%", "vip", "%level%", "1", "%status%", "active"))).passed());
        assertTrue(engine.evaluate(conditions, context(MapValues.of(
                "%group%", "default", "%level%", "31", "%status%", "active"))).passed());
        assertFalse(engine.evaluate(conditions, context(MapValues.of(
                "%group%", "vip", "%level%", "31", "%status%", "blocked"))).passed());
    }

    @Test
    void refreshIntervalCachesEachPlayersResult() throws Exception {
        AtomicLong ticks = new AtomicLong();
        AtomicInteger evaluations = new AtomicInteger();
        ConditionEngine engine = ConditionEngine.createDefault(ticks::get);
        engine.registry().register(definition -> context -> {
            evaluations.incrementAndGet();
            return ConditionResult.pass("probe");
        }, "probe");

        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  cached_probe:
                    type: probe
                    refresh-interval: 20
                    value: true
                """);
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");
        ConditionContext context = context(MapValues.of());

        assertTrue(engine.evaluate(conditions, context).passed());
        assertTrue(engine.evaluate(conditions, context).passed());
        assertTrue(engine.evaluate(conditions, context(MapValues.of())).passed());
        assertTrue(evaluations.get() == 2);

        ticks.set(20);
        assertTrue(engine.evaluate(conditions, context).passed());
        assertTrue(evaluations.get() == 3);
    }

    @Test
    void unresolvedPlaceholderFailsClosed() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  missing:
                    type: equals
                    value:
                      value1: "%missing_placeholder%"
                      value2: "%missing_placeholder%"
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");

        assertFalse(engine.evaluate(conditions, context(MapValues.of())).passed());
    }

    @Test
    void emptyOrAndInvertedGroupsFailClosed() throws Exception {
        ConditionEngine engine = ConditionEngine.createDefault();
        YamlConfiguration emptyOr = new YamlConfiguration();
        emptyOr.loadFromString("conditions:\n  group:\n    type: '||'\n    value: {}\n");
        YamlConfiguration emptyInverted = new YamlConfiguration();
        emptyInverted.loadFromString("conditions:\n  group:\n    type: inverted\n    value: {}\n");

        assertFalse(engine.evaluate(engine.parse(emptyOr.getConfigurationSection("conditions"), "conditions"),
                context(MapValues.of())).passed());
        assertFalse(engine.evaluate(engine.parse(emptyInverted.getConfigurationSection("conditions"), "conditions"),
                context(MapValues.of())).passed());
    }

    @Test
    void registeredConditionExceptionFailsClosed() throws Exception {
        ConditionEngine engine = ConditionEngine.createDefault();
        engine.registry().register(definition -> context -> {
            throw new IllegalStateException("broken condition");
        }, "broken");
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("conditions:\n  broken: true\n");

        ConditionEvaluation evaluation = engine.evaluate(
                engine.parse(config.getConfigurationSection("conditions"), "conditions"),
                context(MapValues.of()));

        assertFalse(evaluation.passed());
        assertTrue(evaluation.traces().get(0).detail().contains("broken condition"));
    }

    private ConditionContext context(String world, String balance) {
        return new ConditionContext(UUID.randomUUID(), null, input -> switch (input) {
            case "%world%" -> world;
            case "%balance%" -> balance;
            default -> input;
        });
    }

    private ConditionContext context(MapValues values) {
        return new ConditionContext(UUID.randomUUID(), null, values::resolve);
    }

    private record MapValues(java.util.Map<String, String> values) {

        static MapValues of(String... entries) {
            java.util.Map<String, String> values = new java.util.HashMap<>();
            for (int index = 0; index < entries.length; index += 2) {
                values.put(entries[index], entries[index + 1]);
            }
            return new MapValues(values);
        }

        String resolve(String input) {
            return values.getOrDefault(input, input);
        }
    }
}
