package gg.fotia.chat.condition;

import java.util.List;

public record ConditionEvaluation(boolean passed, List<ConditionTrace> traces) {

    public ConditionEvaluation {
        traces = List.copyOf(traces);
    }
}
