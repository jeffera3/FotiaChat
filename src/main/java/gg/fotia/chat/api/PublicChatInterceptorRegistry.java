package gg.fotia.chat.api;

import gg.fotia.chat.channel.Channel;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 公共聊天拦截器注册表。
 */
public final class PublicChatInterceptorRegistry {

    private final CopyOnWriteArrayList<PublicChatInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private final Consumer<Throwable> failureHandler;

    public PublicChatInterceptorRegistry(Consumer<Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public void register(PublicChatInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.addIfAbsent(interceptor);
        }
    }

    public void unregister(PublicChatInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    public boolean intercept(Player sender, Channel channel, String plainMessage) {
        for (PublicChatInterceptor interceptor : interceptors) {
            try {
                if (interceptor.onPublicChat(sender, channel, plainMessage)) {
                    return true;
                }
            } catch (Throwable throwable) {
                // 拦截器来自外部 Addon，Error（如 NoClassDefFoundError）也不能拖垮全服聊天
                failureHandler.accept(throwable);
            }
        }
        return false;
    }

    public void clear() {
        interceptors.clear();
    }
}
