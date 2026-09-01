package gg.fotia.chat.condition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlayerConditions {

    private static final Pattern POTION_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_:.]+)\\s*(==|>=|<=|>|<)\\s*(-?\\d+)$");

    private PlayerConditions() {
    }

    static void register(ConditionRegistry registry) {
        registry.register(definition -> permission(definition, false), "permission");
        registry.register(definition -> permission(definition, true), "!permission");
        registry.register(definition -> valueList(definition, ConditionSubject::world, false), "world");
        registry.register(definition -> valueList(definition, ConditionSubject::world, true), "!world");
        registry.register(definition -> valueList(definition, ConditionSubject::gamemode, false), "gamemode");
        registry.register(definition -> valueList(definition, ConditionSubject::gamemode, true), "!gamemode");
        registry.register(definition -> valueList(definition, ConditionSubject::biome, false), "biome");
        registry.register(definition -> valueList(definition, ConditionSubject::biome, true), "!biome");
        registry.register(definition -> valueList(definition, ConditionSubject::environment, false), "environment");
        registry.register(definition -> valueList(definition, ConditionSubject::environment, true), "!environment");
        registry.register(PlayerConditions::level, "level");
        registry.register(PlayerConditions::yPosition, "ypos", "y-position");
        registry.register(definition -> valueList(definition, ConditionSubject::weather, false), "weather");
        registry.register(PlayerConditions::time, "time");
        registry.register(PlayerConditions::potionEffect, "potion-effect");
        registry.register(PlayerConditions::random, "random");
    }

    private static Condition permission(ConditionDefinition definition, boolean inverted)
            throws ConditionParseException {
        List<String> permissions = ConditionConfigValues.stringList(definition.value(), definition.path());
        return withSubject(subject -> permissions.stream().anyMatch(subject::hasPermission) != inverted,
                definition.type(), permissions.toString());
    }

    private static Condition valueList(ConditionDefinition definition,
                                       Function<ConditionSubject, String> getter,
                                       boolean inverted) throws ConditionParseException {
        Set<String> accepted = new HashSet<>();
        for (String value : ConditionConfigValues.stringList(definition.value(), definition.path())) {
            String normalized = value.toLowerCase(Locale.ROOT);
            accepted.add(normalized);
            if (value.indexOf(':') < 0) {
                accepted.add("minecraft:" + normalized);
            }
        }
        return withSubject(subject -> accepted.contains(getter.apply(subject).toLowerCase(Locale.ROOT)) != inverted,
                definition.type(), accepted.toString());
    }

    private static Condition level(ConditionDefinition definition) throws ConditionParseException {
        int required;
        try {
            required = Integer.parseInt(String.valueOf(definition.value()));
        } catch (NumberFormatException exception) {
            throw new ConditionParseException("Level must be an integer at " + definition.path(), exception);
        }
        return withSubject(subject -> subject.level() >= required, definition.type(), String.valueOf(required));
    }

    private static Condition yPosition(ConditionDefinition definition) throws ConditionParseException {
        List<NumberRange> ranges = numberRanges(definition.value(), definition.path(), false);
        return withSubject(subject -> ranges.stream().anyMatch(range -> range.contains(subject.y())),
                definition.type(), ranges.toString());
    }

    private static Condition time(ConditionDefinition definition) throws ConditionParseException {
        List<NumberRange> ranges = numberRanges(definition.value(), definition.path(), true);
        return withSubject(subject -> {
            long time = Math.floorMod(subject.time(), 24000L);
            return ranges.stream().anyMatch(range -> range.contains(time));
        }, definition.type(), ranges.toString());
    }

    private static Condition potionEffect(ConditionDefinition definition) throws ConditionParseException {
        String configured = ConditionConfigValues.singleValue(definition.value(), definition.path());
        Matcher matcher = POTION_PATTERN.matcher(configured.trim());
        if (!matcher.matches()) {
            throw new ConditionParseException("Invalid potion-effect at " + definition.path()
                    + "; expected EFFECT>=LEVEL");
        }
        String effect = matcher.group(1);
        String operator = matcher.group(2);
        int required = Integer.parseInt(matcher.group(3));
        return withSubject(subject -> compare(subject.potionLevel(effect), operator, required),
                definition.type(), configured);
    }

    private static Condition random(ConditionDefinition definition) throws ConditionParseException {
        double chance;
        try {
            chance = Double.parseDouble(
                    ConditionConfigValues.singleValue(definition.value(), definition.path()));
        } catch (NumberFormatException exception) {
            throw new ConditionParseException("Random chance must be a number at " + definition.path(), exception);
        }
        if (chance < 0 || chance > 100) {
            throw new ConditionParseException("Random chance must be between 0 and 100 at " + definition.path());
        }
        return context -> {
            boolean passed = chance >= 100
                    || chance > 0 && ThreadLocalRandom.current().nextDouble(100) < chance;
            String detail = "random " + chance + "%";
            return passed ? ConditionResult.pass(detail) : ConditionResult.fail(detail);
        };
    }

    private static Condition withSubject(Predicate<ConditionSubject> predicate, String type, String expected) {
        return context -> {
            ConditionSubject subject = context.subject();
            if (subject == null) {
                return ConditionResult.fail("No player context");
            }
            boolean passed = predicate.test(subject);
            String detail = type + " " + expected;
            return passed ? ConditionResult.pass(detail) : ConditionResult.fail(detail);
        };
    }

    private static boolean compare(int actual, String operator, int expected) {
        return switch (operator) {
            case "==" -> actual == expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            default -> false;
        };
    }

    private static List<NumberRange> numberRanges(Object raw, String path, boolean timeAliases)
            throws ConditionParseException {
        List<NumberRange> ranges = new ArrayList<>();
        for (String configured : ConditionConfigValues.stringList(raw, path)) {
            String value = configured.trim();
            if (timeAliases && value.equalsIgnoreCase("day")) {
                ranges.add(new NumberRange(0, 12300));
                continue;
            }
            if (timeAliases && value.equalsIgnoreCase("night")) {
                ranges.add(new NumberRange(12301, 23999));
                continue;
            }
            String[] split = value.split("~", 2);
            try {
                double first = Double.parseDouble(split[0].trim());
                double second = split.length == 1 ? first : Double.parseDouble(split[1].trim());
                ranges.add(new NumberRange(Math.min(first, second), Math.max(first, second)));
            } catch (NumberFormatException exception) {
                throw new ConditionParseException("Invalid numeric range at " + path + ": " + configured,
                        exception);
            }
        }
        return List.copyOf(ranges);
    }

    private record NumberRange(double minimum, double maximum) {

        boolean contains(double value) {
            return value >= minimum && value <= maximum;
        }
    }
}
