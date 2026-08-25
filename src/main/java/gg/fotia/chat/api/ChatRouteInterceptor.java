package gg.fotia.chat.api;

import gg.fotia.chat.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 在公共频道投递前消费已经过 FotiaChat 安全管线处理的组件消息。
 */
@FunctionalInterface
public interface ChatRouteInterceptor {

    /**
     * @return true 表示消息已由外部路由完整消费，FotiaChat 不再公开投递
     */
    boolean onPublicChat(Player sender, Channel channel, String plainMessage, Component component);
}
