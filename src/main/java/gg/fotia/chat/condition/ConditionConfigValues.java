package gg.fotia.chat.condition;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

final class ConditionConfigValues {

    private ConditionConfigValues() {
    }

    static ConfigurationSection requireSection(ConditionDefinition definition)
            throws ConditionParseException {
        if (definition.value() instanceof ConfigurationSection section) {
            return section;
        }
        throw new ConditionParseException("Condition value must be a section at " + definition.path());
    }

    static String requiredString(ConfigurationSection section, String key, String path)
            throws ConditionParseException {
        String value = section.getString(key);
        if (value == null) {
            throw new ConditionParseException("Missing " + key + " at " + path);
        }
        return value;
    }

    static List<String> stringList(Object raw, String path) throws ConditionParseException {
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>(list.size());
            for (Object value : list) {
                values.add(String.valueOf(value));
            }
            return List.copyOf(values);
        }
        if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return List.of(String.valueOf(raw));
        }
        throw new ConditionParseException("Condition value must be a scalar or list at " + path);
    }

    static String singleValue(Object raw, String path) throws ConditionParseException {
        List<String> values = stringList(raw, path);
        if (values.size() != 1) {
            throw new ConditionParseException("Condition requires one value at " + path);
        }
        return values.get(0);
    }
}
