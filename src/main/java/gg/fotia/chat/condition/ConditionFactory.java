package gg.fotia.chat.condition;

@FunctionalInterface
public interface ConditionFactory {

    Condition create(ConditionDefinition definition) throws ConditionParseException;
}
