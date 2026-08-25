package gg.fotia.chat.api;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * 组件级聊天管线结果。
 */
public record ChatPipelineResult(boolean accepted, Component component, String messageKey) {
    public ChatPipelineResult {
        component = Objects.requireNonNull(component, "component");
        messageKey = messageKey == null ? "" : messageKey;
        if (!accepted && messageKey.isBlank()) {
            throw new IllegalArgumentException("拒绝结果必须提供 messageKey");
        }
    }

    public static ChatPipelineResult accepted(Component component) {
        return new ChatPipelineResult(true, component, "");
    }

    public static ChatPipelineResult rejected(String messageKey) {
        return new ChatPipelineResult(false, Component.empty(), messageKey);
    }
}
