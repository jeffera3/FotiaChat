package gg.fotia.chat.condition;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

final class ComparisonConditions {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%]+%");

    private ComparisonConditions() {
    }

    static void register(ConditionRegistry registry) {
        registry.register(definition -> comparison(definition, Comparison.TEXT_EQUALS), "equals", "equal");
        registry.register(definition -> comparison(definition, Comparison.TEXT_NOT_EQUALS), "!equals", "!equal");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_EQUALS), "=", "==");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_NOT_EQUALS), "!=");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_GREATER), ">");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_AT_LEAST), ">=");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_LESS), "<");
        registry.register(definition -> comparison(definition, Comparison.NUMBER_AT_MOST), "<=");
        registry.register(definition -> textComparison(definition, String::contains, false),
                "contains", "contain");
        registry.register(definition -> textComparison(definition, String::contains, true),
                "!contains", "!contain");
        registry.register(definition -> textComparison(definition, String::startsWith, false),
                "starts-with", "startsWith", "start-with", "startWith");
        registry.register(definition -> textComparison(definition, String::startsWith, true),
                "!starts-with", "!startsWith", "!start-with", "!startWith");
        registry.register(definition -> textComparison(definition, String::endsWith, false),
                "ends-with", "endsWith", "end-with", "endWith");
        registry.register(definition -> textComparison(definition, String::endsWith, true),
                "!ends-with", "!endsWith", "!end-with", "!endWith");
        registry.register(definition -> regex(definition, false), "regex");
        registry.register(definition -> regex(definition, true), "!regex");
        registry.register(definition -> inList(definition, false), "in-list");
        registry.register(definition -> inList(definition, true), "!in-list");
    }

    private static Condition comparison(ConditionDefinition definition, Comparison comparison)
            throws ConditionParseException {
        ConfigurationSection value = ConditionConfigValues.requireSection(definition);
        String first = ConditionConfigValues.requiredString(value, "value1", definition.path());
        String second = ConditionConfigValues.requiredString(value, "value2", definition.path());
        return context -> {
            String resolvedFirst = context.resolve(first);
            String resolvedSecond = context.resolve(second);
            if (unresolved(first, resolvedFirst) || unresolved(second, resolvedSecond)) {
                return ConditionResult.fail("Unresolved placeholder: " + resolvedFirst + " / " + resolvedSecond);
            }
            try {
                boolean passed = comparison.test(resolvedFirst, resolvedSecond);
                return result(passed, resolvedFirst + " " + definition.type() + " " + resolvedSecond);
            } catch (NumberFormatException exception) {
                return ConditionResult.fail("Not a number: " + resolvedFirst + " / " + resolvedSecond);
            }
        };
    }

    private static Condition textComparison(ConditionDefinition definition,
                                            BiPredicate<String, String> predicate,
                                            boolean inverted) throws ConditionParseException {
        ConfigurationSection value = ConditionConfigValues.requireSection(definition);
        String first = ConditionConfigValues.requiredString(value, "value1", definition.path());
        String second = ConditionConfigValues.requiredString(value, "value2", definition.path());
        return context -> {
            String resolvedFirst = context.resolve(first);
            String resolvedSecond = context.resolve(second);
            if (unresolved(first, resolvedFirst) || unresolved(second, resolvedSecond)) {
                return ConditionResult.fail("Unresolved placeholder: " + resolvedFirst + " / " + resolvedSecond);
            }
            boolean passed = predicate.test(resolvedFirst, resolvedSecond) != inverted;
            return result(passed, resolvedFirst + " " + definition.type() + " " + resolvedSecond);
        };
    }

    private static Condition regex(ConditionDefinition definition, boolean inverted)
            throws ConditionParseException {
        ConfigurationSection value = ConditionConfigValues.requireSection(definition);
        String configured = ConditionConfigValues.requiredString(value, "papi", definition.path());
        String expression = ConditionConfigValues.requiredString(value, "regex", definition.path());
        Pattern pattern;
        try {
            pattern = Pattern.compile(expression);
        } catch (RuntimeException exception) {
            throw new ConditionParseException("Invalid regex at " + definition.path(), exception);
        }
        return context -> {
            String resolved = context.resolve(configured);
            if (unresolved(configured, resolved)) {
                return ConditionResult.fail("Unresolved placeholder: " + resolved);
            }
            return result(pattern.matcher(resolved).matches() != inverted, resolved);
        };
    }

    private static Condition inList(ConditionDefinition definition, boolean inverted)
            throws ConditionParseException {
        ConfigurationSection value = ConditionConfigValues.requireSection(definition);
        String configured = ConditionConfigValues.requiredString(value, "papi", definition.path());
        Set<String> accepted = new HashSet<>(
                ConditionConfigValues.stringList(value.get("values"), definition.path()));
        return context -> {
            String resolved = context.resolve(configured);
            if (unresolved(configured, resolved)) {
                return ConditionResult.fail("Unresolved placeholder: " + resolved);
            }
            return result(accepted.contains(resolved) != inverted, resolved);
        };
    }

    private static boolean unresolved(String configured, String resolved) {
        return configured.equals(resolved) && PLACEHOLDER_PATTERN.matcher(configured).find();
    }

    private static ConditionResult result(boolean passed, String detail) {
        return passed ? ConditionResult.pass(detail) : ConditionResult.fail(detail);
    }

    private enum Comparison {
        TEXT_EQUALS { @Override boolean test(String a, String b) { return a.equals(b); } },
        TEXT_NOT_EQUALS { @Override boolean test(String a, String b) { return !a.equals(b); } },
        NUMBER_EQUALS { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) == 0; } },
        NUMBER_NOT_EQUALS { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) != 0; } },
        NUMBER_GREATER { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) > 0; } },
        NUMBER_AT_LEAST { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) >= 0; } },
        NUMBER_LESS { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) < 0; } },
        NUMBER_AT_MOST { @Override boolean test(String a, String b) { return number(a).compareTo(number(b)) <= 0; } };

        abstract boolean test(String first, String second);

        static BigDecimal number(String value) {
            return new BigDecimal(value.trim());
        }
    }
}
