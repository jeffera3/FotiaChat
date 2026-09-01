package gg.fotia.chat.condition;

import java.util.List;

final class ConditionNode {

    private final int id;
    private final String path;
    private final String type;
    private final int refreshInterval;
    private final Condition condition;
    private final CompositeMode compositeMode;
    private final List<ConditionNode> children;

    private ConditionNode(int id, String path, String type, int refreshInterval,
                          Condition condition, CompositeMode compositeMode, List<ConditionNode> children) {
        this.id = id;
        this.path = path;
        this.type = type;
        this.refreshInterval = Math.max(0, refreshInterval);
        this.condition = condition;
        this.compositeMode = compositeMode;
        this.children = List.copyOf(children);
    }

    static ConditionNode leaf(int id, String path, String type, int refreshInterval, Condition condition) {
        return new ConditionNode(id, path, type, refreshInterval, condition, null, List.of());
    }

    static ConditionNode composite(int id, String path, String type, int refreshInterval,
                                   CompositeMode mode, List<ConditionNode> children) {
        return new ConditionNode(id, path, type, refreshInterval, null, mode, children);
    }

    boolean evaluate(ConditionContext context, ConditionCache cache, long currentTick,
                     List<ConditionTrace> traces) {
        if (refreshInterval > 0) {
            ConditionCache.CachedResult cached = cache.get(context.subjectId(), id, currentTick);
            if (cached != null) {
                ConditionResult result = cached.result();
                traces.add(new ConditionTrace(path, type, result.passed(), result.detail(), true));
                return result.passed();
            }
        }

        ConditionResult result;
        try {
            result = compositeMode == null
                    ? condition.evaluate(context)
                    : evaluateComposite(context, cache, currentTick, traces);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            result = ConditionResult.fail("Condition error: " + message);
        }
        if (refreshInterval > 0) {
            cache.put(context.subjectId(), id, result, currentTick + refreshInterval);
        }
        traces.add(new ConditionTrace(path, type, result.passed(), result.detail(), false));
        return result.passed();
    }

    private ConditionResult evaluateComposite(ConditionContext context, ConditionCache cache,
                                               long currentTick, List<ConditionTrace> traces) {
        boolean passed = switch (compositeMode) {
            case AND -> evaluateAnd(context, cache, currentTick, traces);
            case OR -> evaluateOr(context, cache, currentTick, traces);
            case INVERTED -> !children.isEmpty() && !evaluateAnd(context, cache, currentTick, traces);
        };
        String detail = compositeMode.name() + " (" + children.size() + ")";
        return passed ? ConditionResult.pass(detail) : ConditionResult.fail(detail);
    }

    private boolean evaluateAnd(ConditionContext context, ConditionCache cache,
                                long currentTick, List<ConditionTrace> traces) {
        if (children.isEmpty()) {
            return true;
        }
        for (ConditionNode child : children) {
            if (!child.evaluate(context, cache, currentTick, traces)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateOr(ConditionContext context, ConditionCache cache,
                               long currentTick, List<ConditionTrace> traces) {
        for (ConditionNode child : children) {
            if (child.evaluate(context, cache, currentTick, traces)) {
                return true;
            }
        }
        return false;
    }

    enum CompositeMode {
        AND,
        OR,
        INVERTED
    }
}
