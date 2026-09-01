package gg.fotia.chat.condition;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class ConditionParser {

    private final ConditionRegistry registry;
    private final AtomicInteger conditionIds = new AtomicInteger();

    ConditionParser(ConditionRegistry registry) {
        this.registry = registry;
    }

    ConditionSet parse(ConfigurationSection section, String basePath) throws ConditionParseException {
        if (section == null) {
            return new ConditionSet(List.of());
        }

        List<ConditionNode> conditions = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            conditions.add(parseNode(section, key, basePath + "." + key));
        }
        return new ConditionSet(conditions);
    }

    private ConditionNode parseNode(ConfigurationSection parent, String key, String path)
            throws ConditionParseException {
        String type = key;
        Object value = parent.get(key);
        int refreshInterval = 0;

        if (!registry.contains(type) && compositeMode(type) == null) {
            ConfigurationSection rich = parent.getConfigurationSection(key);
            if (rich == null) {
                throw new ConditionParseException("Unknown condition type at " + path + ": " + type);
            }
            type = rich.getString("type", "");
            value = rich.get("value");
            refreshInterval = rich.getInt("refresh-interval", 0);
        }

        ConditionNode.CompositeMode compositeMode = compositeMode(type);
        if (compositeMode != null) {
            if (!(value instanceof ConfigurationSection childrenSection)) {
                throw new ConditionParseException("Composite condition requires a section at " + path);
            }
            List<ConditionNode> children = new ArrayList<>();
            for (String childKey : childrenSection.getKeys(false)) {
                children.add(parseNode(childrenSection, childKey, path + ".value." + childKey));
            }
            return ConditionNode.composite(conditionIds.incrementAndGet(), path, type,
                    refreshInterval, compositeMode, children);
        }

        ConditionFactory factory = registry.factory(type);
        if (factory == null) {
            throw new ConditionParseException("Unknown condition type at " + path + ": " + type);
        }
        Condition condition = factory.create(new ConditionDefinition(type, value, path, refreshInterval));
        return ConditionNode.leaf(conditionIds.incrementAndGet(), path, type, refreshInterval, condition);
    }

    private ConditionNode.CompositeMode compositeMode(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "&&", "and" -> ConditionNode.CompositeMode.AND;
            case "||", "or" -> ConditionNode.CompositeMode.OR;
            case "inverted", "not" -> ConditionNode.CompositeMode.INVERTED;
            default -> null;
        };
    }
}
