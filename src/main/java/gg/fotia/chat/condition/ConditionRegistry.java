package gg.fotia.chat.condition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConditionRegistry {

    private final Map<String, ConditionFactory> factories = new LinkedHashMap<>();

    public void register(ConditionFactory factory, String... types) {
        Objects.requireNonNull(factory, "factory");
        for (String type : types) {
            String normalized = normalize(type);
            if (factories.putIfAbsent(normalized, factory) != null) {
                throw new IllegalArgumentException("Condition type is already registered: " + type);
            }
        }
    }

    public boolean unregister(String type) {
        return factories.remove(normalize(type)) != null;
    }

    public ConditionFactory factory(String type) {
        return factories.get(normalize(type));
    }

    public boolean contains(String type) {
        return factories.containsKey(normalize(type));
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
