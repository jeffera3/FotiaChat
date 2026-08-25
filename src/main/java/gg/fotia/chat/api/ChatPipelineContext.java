package gg.fotia.chat.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 外部聊天路由的稳定上下文。
 */
public record ChatPipelineContext(String routeId, UUID targetId) {
    public ChatPipelineContext {
        routeId = Objects.requireNonNull(routeId, "routeId");
        if (routeId.isBlank()) {
            throw new IllegalArgumentException("routeId 不能为空");
        }
    }
}
