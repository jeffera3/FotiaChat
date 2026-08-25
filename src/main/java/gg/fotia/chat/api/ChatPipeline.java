package gg.fotia.chat.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 供外部路由复用的组件级聊天安全管线。
 */
@FunctionalInterface
public interface ChatPipeline {

    ChatPipelineResult process(Player sender, Component input, ChatPipelineContext context);
}
