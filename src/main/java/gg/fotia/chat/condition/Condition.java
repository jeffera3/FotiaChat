package gg.fotia.chat.condition;

@FunctionalInterface
public interface Condition {

    ConditionResult evaluate(ConditionContext context);
}
