package gg.fotia.chat.announcement;

import gg.fotia.chat.condition.ConditionContext;
import gg.fotia.chat.condition.ConditionEngine;
import gg.fotia.chat.condition.ConditionEvaluation;

import java.util.Objects;

public final class AnnouncementConditionEvaluator {

    private final ConditionEngine conditionEngine;

    public AnnouncementConditionEvaluator(ConditionEngine conditionEngine) {
        this.conditionEngine = Objects.requireNonNull(conditionEngine, "conditionEngine");
    }

    public ConditionEvaluation evaluate(Announcement announcement, ConditionContext context) {
        return conditionEngine.evaluate(announcement.getConditions(), context);
    }
}
