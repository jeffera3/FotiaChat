package gg.fotia.chat.util;

import net.kyori.adventure.text.event.ClickEvent;

/**
 * 兼容不同 Adventure 版本的 ClickEvent.Action 访问器。
 * 避免在类初始化时直接引用可能不存在的枚举常量（会触发 NoSuchFieldError）。
 */
public final class ClickActionCompat {

    private ClickActionCompat() {
    }

    /**
     * 返回一个安全的默认 ClickEvent.Action：优先返回 SUGGEST_COMMAND（若存在），其次尝试 RUN_COMMAND/OPEN_URL，最终退回到 values()[0]。
     */
    public static ClickEvent.Action defaultAction() {
        ClickEvent.Action[] vals = ClickEvent.Action.values();
        if (vals == null || vals.length == 0) return null;
        for (ClickEvent.Action a : vals) {
            if ("SUGGEST_COMMAND".equals(a.name())) return a;
        }
        for (ClickEvent.Action a : vals) {
            if ("RUN_COMMAND".equals(a.name())) return a;
        }
        for (ClickEvent.Action a : vals) {
            if ("OPEN_URL".equals(a.name())) return a;
        }
        return vals[0];
    }

    /**
     * 根据名称安全解析枚举，解析失败时返回 defaultAction()
     */
    public static ClickEvent.Action safeValueOf(String name) {
        if (name == null || name.isBlank()) return defaultAction();
        try {
            return ClickEvent.Action.valueOf(name.toUpperCase());
        } catch (Throwable ignored) {
            return defaultAction();
        }
    }
}
