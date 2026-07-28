package gg.fotia.chat.color;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 聊天颜色数据类
 */
public class ChatColor {

    private final String id;
    private final String name;
    private final ColorType type;
    private final String format;
    private final String permission;

    public ChatColor(String id, String name, ColorType type, String format, String permission) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.format = format;
        this.permission = permission;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ColorType getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getPermission() {
        return permission;
    }

    /**
     * 应用颜色到消息
     */
    public String apply(String message) {
        return switch (type) {
            case SINGLE -> format + message;
            case GRADIENT -> "<gradient:" + format + ">" + message + "</gradient>";
            case RAINBOW -> "<rainbow>" + message + "</rainbow>";
        };
    }

    /**
     * 应用颜色到已经解析好的消息组件。
     * 通过 MiniMessage 组件占位符注入，不再对组件树做 serialize→deserialize 往返，
     * 避免复杂 hover（如物品展示）在往返中失真。
     */
    public Component apply(Component message, MiniMessage miniMessage) {
        Component safeMessage = message == null ? Component.empty() : message;
        MiniMessage parser = miniMessage == null ? MiniMessage.miniMessage() : miniMessage;
        String wrapper = switch (type) {
            case SINGLE -> format + "<msg>";
            case GRADIENT -> "<gradient:" + format + "><msg></gradient>";
            case RAINBOW -> "<rainbow><msg></rainbow>";
        };
        return parser.deserialize(wrapper,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("msg", safeMessage));
    }

    /**
     * 检查玩家是否有权限使用此颜色
     */
    public boolean hasPermission(org.bukkit.entity.Player player) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return player.hasPermission(permission);
    }
}
