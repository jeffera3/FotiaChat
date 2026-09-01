package gg.fotia.chat.condition;

import java.util.List;

public final class ConditionSet {

    private static final ConditionSet EMPTY = new ConditionSet(List.of());

    private final List<ConditionNode> conditions;

    ConditionSet(List<ConditionNode> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    public static ConditionSet empty() {
        return EMPTY;
    }

    List<ConditionNode> conditions() {
        return conditions;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }
}
