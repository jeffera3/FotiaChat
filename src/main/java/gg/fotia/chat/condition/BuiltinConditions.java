package gg.fotia.chat.condition;

final class BuiltinConditions {

    private BuiltinConditions() {
    }

    static void register(ConditionRegistry registry) {
        ComparisonConditions.register(registry);
        PlayerConditions.register(registry);
    }
}
