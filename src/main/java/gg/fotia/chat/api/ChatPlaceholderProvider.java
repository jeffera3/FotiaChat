package gg.fotia.chat.api;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 为聊天格式提供额外占位符的扩展点。
 *
 * 返回的键可写为 nameplate 或 %nameplate%，格式化器会统一处理。
 */
@FunctionalInterface
public interface ChatPlaceholderProvider {

    Map<String, String> provide(Player player);
}
