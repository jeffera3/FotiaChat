package gg.fotia.chat.condition;

public record ConditionResult(boolean passed, String detail) {

    public ConditionResult {
        detail = detail == null ? "" : detail;
    }

    public static ConditionResult pass(String detail) {
        return new ConditionResult(true, detail);
    }

    public static ConditionResult fail(String detail) {
        return new ConditionResult(false, detail);
    }
}
