package gg.fotia.chat.api;

import gg.fotia.chat.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 隔离第三方组件级聊天路由异常。
 */
public final class ChatRouteInterceptorRegistry {

    private final CopyOnWriteArrayList<ChatRouteInterceptor> interceptors = new CopyOnWriteArrayList<>();
    private final Consumer<Throwable> failureHandler;

    public ChatRouteInterceptorRegistry(Consumer<Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public void register(ChatRouteInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.addIfAbsent(interceptor);
        }
    }

    public void unregister(ChatRouteInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    public boolean intercept(Player sender, Channel channel, String plainMessage, Component component) {
        for (ChatRouteInterceptor interceptor : interceptors) {
            try {
                if (interceptor.onPublicChat(sender, channel, plainMessage, component)) {
                    return true;
                }
            } catch (Throwable throwable) {
                failureHandler.accept(throwable);
            }
        }
        return false;
    }

    public void clear() {
        interceptors.clear();
    }
}
