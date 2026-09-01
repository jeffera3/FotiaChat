package gg.fotia.chat.condition;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ConditionCache {

    private final Map<UUID, Map<Integer, CachedResult>> results = new ConcurrentHashMap<>();

    CachedResult get(UUID subjectId, int conditionId, long currentTick) {
        Map<Integer, CachedResult> playerResults = results.get(subjectId);
        if (playerResults == null) {
            return null;
        }
        CachedResult cached = playerResults.get(conditionId);
        return cached != null && currentTick < cached.expiresAtTick() ? cached : null;
    }

    void put(UUID subjectId, int conditionId, ConditionResult result, long expiresAtTick) {
        results.computeIfAbsent(subjectId, ignored -> new ConcurrentHashMap<>())
                .put(conditionId, new CachedResult(result, expiresAtTick));
    }

    void clear(UUID subjectId) {
        results.remove(subjectId);
    }

    void clear() {
        results.clear();
    }

    record CachedResult(ConditionResult result, long expiresAtTick) {
    }
}
