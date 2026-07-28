package gg.fotia.chat.api;

import gg.fotia.chat.channel.Channel;
import org.bukkit.entity.Player;

/**
 * 在公共聊天正式分发前处理消息。回调始终在服务器主线程执行。
 */
@FunctionalInterface
public interface PublicChatInterceptor {

    /**
     * @return true 表示消息已由拦截器消费，不再由 FotiaChat 分发
     */
    boolean onPublicChat(Player sender, Channel channel, String plainMessage);
}
