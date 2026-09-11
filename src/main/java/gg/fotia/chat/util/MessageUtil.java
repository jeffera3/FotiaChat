package gg.fotia.chat.util;

import gg.fotia.chat.FotiaChat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * 解析MiniMessage格式的消息
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(LegacyColorConverter.convertToMiniMessage(message));
    }

    /** 解析可信配置文本，并保留物品名称等组件占位符。 */
    public static Component parseConfigured(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(LegacyColorConverter.convertToMiniMessage(
                message == null ? "" : message), resolvers);
    }

    /**
     * 解析MiniMessage格式的消息，支持PlaceholderAPI
     */
    public static Component parse(String message, Player player) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // 处理PlaceholderAPI占位符
        if (isPlaceholderAPIEnabled() && player != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        return MINI_MESSAGE.deserialize(LegacyColorConverter.convertToMiniMessage(message));
    }

    /**
     * 解析旧版颜色代码(&)格式的消息
     */
    public static Component parseLegacy(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(message);
    }

    /**
     * 将Component转换为纯文本
     */
    public static String toPlainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * 将Component转换为MiniMessage格式字符串
     */
    public static String toMiniMessage(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * 检查PlaceholderAPI是否可用（结果缓存，插件启停时通过 refreshIntegrationCache 刷新）
     */
    public static boolean isPlaceholderAPIEnabled() {
        Boolean cached = placeholderApiEnabled;
        if (cached == null) {
            cached = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
            placeholderApiEnabled = cached;
        }
        return cached;
    }

    /**
     * 检查CraftEngine是否可用（结果缓存，插件启停时通过 refreshIntegrationCache 刷新）
     */
    public static boolean isCraftEngineEnabled() {
        Boolean cached = craftEngineEnabled;
        if (cached == null) {
            cached = Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
            craftEngineEnabled = cached;
        }
        return cached;
    }

    private static volatile Boolean placeholderApiEnabled;
    private static volatile Boolean craftEngineEnabled;

    /**
     * 使集成插件的启用状态缓存失效（在插件启用/禁用事件与 reload 时调用）
     */
    public static void refreshIntegrationCache() {
        placeholderApiEnabled = null;
        craftEngineEnabled = null;
    }

    /**
     * 获取带前缀的消息
     */
    public static Component withPrefix(Component message) {
        String prefix = FotiaChat.getInstance().getMessageManager().getRaw("prefix");
        return parse(prefix).append(message);
    }

    /**
     * 获取带前缀的消息
     */
    public static Component withPrefix(String message) {
        return withPrefix(parse(message));
    }
}
