package gg.fotia.chat.condition;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinConditionsTest {

    @Test
    void supportsTextRegexAndListComparisons() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  name:
                    type: starts-with
                    value:
                      value1: "%name%"
                      value2: "Ti_"
                  group:
                    type: in-list
                    value:
                      papi: "%group%"
                      values: [vip, svip]
                  format:
                    type: regex
                    value:
                      papi: "%name%"
                      regex: "^[A-Za-z0-9_]{3,16}$"
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");

        assertTrue(engine.evaluate(conditions, context("Ti_Avanti", "vip")).passed());
        assertFalse(engine.evaluate(conditions, context("Player", "vip")).passed());
        assertFalse(engine.evaluate(conditions, context("Ti_Avanti", "default")).passed());
    }

    @Test
    void supportsNativePlayerConditions() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  permission: fotiachat.vip
                  world: [world, world_resource]
                  gamemode: [SURVIVAL, ADVENTURE]
                  level: 30
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");

        ConditionSubject accepted = new TestSubject(Set.of("fotiachat.vip"), "world", "SURVIVAL", 35);
        ConditionSubject rejected = new TestSubject(Set.of("fotiachat.vip"), "world", "SURVIVAL", 12);
        assertTrue(engine.evaluate(conditions, context(accepted)).passed());
        assertFalse(engine.evaluate(conditions, context(rejected)).passed());
    }

    @Test
    void supportsRangesWeatherTimePotionAndProbability() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                conditions:
                  ypos: "60~80"
                  weather: clear
                  time: day
                  potion-effect: "SPEED>=2"
                  random: 100
                """);

        ConditionEngine engine = ConditionEngine.createDefault();
        ConditionSet conditions = engine.parse(config.getConfigurationSection("conditions"), "conditions");
        ConditionSubject accepted = new TestSubject(Set.of(), "world", "SURVIVAL", 1) {
            @Override
            public double y() {
                return 70;
            }

            @Override
            public String weather() {
                return "CLEAR";
            }

            @Override
            public long time() {
                return 6000;
            }

            @Override
            public int potionLevel(String effect) {
                return effect.equalsIgnoreCase("SPEED") ? 2 : -1;
            }
        };
        assertTrue(engine.evaluate(conditions, context(accepted)).passed());

        YamlConfiguration impossible = new YamlConfiguration();
        impossible.loadFromString("conditions:\n  random: 0\n");
        ConditionSet never = engine.parse(impossible.getConfigurationSection("conditions"), "conditions");
        assertFalse(engine.evaluate(never, context(accepted)).passed());
    }

    private ConditionContext context(String name, String group) {
        return new ConditionContext(UUID.randomUUID(), null, input -> switch (input) {
            case "%name%" -> name;
            case "%group%" -> group;
            default -> input;
        });
    }

    private ConditionContext context(ConditionSubject subject) {
        return ConditionContext.ofSubject(UUID.randomUUID(), subject, input -> input);
    }

    private static class TestSubject implements ConditionSubject {

        private final Set<String> permissions;
        private final String world;
        private final String gamemode;
        private final int level;

        private TestSubject(Set<String> permissions, String world, String gamemode, int level) {
            this.permissions = permissions;
            this.world = world;
            this.gamemode = gamemode;
            this.level = level;
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        @Override
        public String world() {
            return world;
        }

        @Override
        public String gamemode() {
            return gamemode;
        }

        @Override
        public int level() {
            return level;
        }
    }
}
