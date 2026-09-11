package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/** Redis 会话切换与消息调度；主线程不等待连接、PING 或线程退出。 */
public class RedisHandler {
    private final FotiaChat plugin;
    private final CrossServerManager manager;
    private final Object lifecycleLock = new Object();
    private final Queue<RedisSession> retiredSessions = new ArrayDeque<>();
    private volatile RedisSession session;
    private volatile Consumer<CrossServerMessage> messageHandler;
    private Thread lifecycleWorker;

    public RedisHandler(FotiaChat plugin, CrossServerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void enable(String host, int port, String password, String channelName) {
        RedisSettings settings = RedisSettings.from(host, port, password, channelName, manager.getServerName(),
                plugin.getConfigManager().getConfig(), plugin.getConfigManager().isDebugMode());
        configure(settings);
        plugin.getLogger().info("Redis 跨服通信正在后台连接 (" + host + ":" + port + ")");
    }

    private void configure(RedisSettings settings) {
        synchronized (lifecycleLock) {
            RedisSession previous = session;
            if (previous != null) {
                previous.deactivate();
                // 未开始连接的中间配置只需取消队列，连续重载不会积累连接任务。
                if (previous.isStarted()) retiredSessions.add(previous);
            }
            session = settings == null ? null : new RedisSession(settings, plugin.getLogger(), this::handleMessage);
            if (lifecycleWorker == null) {
                lifecycleWorker = new Thread(this::reconcileSessions, "FotiaChat-Redis-Lifecycle");
                lifecycleWorker.setDaemon(true);
                lifecycleWorker.start();
            }
        }
    }

    private void reconcileSessions() {
        while (true) {
            RedisSession retired;
            synchronized (lifecycleLock) {
                retired = retiredSessions.poll();
                if (retired == null) {
                    RedisSession current = session;
                    if (current != null) current.start();
                    lifecycleWorker = null;
                    return;
                }
            }
            try {
                retired.close();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("关闭旧 Redis 会话失败: " + exception.getMessage());
            }
        }
    }

    private void handleMessage(RedisSession source, String payload) {
        if (source != session || !source.isRunning()) return;
        CrossServerMessage message = CrossServerMessage.deserialize(payload);
        if (message == null) {
            if (source.settings().debug()) plugin.getLogger().warning("丢弃无法解析的 Redis 跨服消息");
            return;
        }
        if (message.getServerName().equals(source.settings().serverName())) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Consumer<CrossServerMessage> handler = messageHandler;
                if (source == session && source.isRunning() && handler != null) handler.accept(message);
            });
        } catch (RuntimeException exception) {
            if (source.isRunning()) plugin.getLogger().warning("调度 Redis 跨服消息失败: " + exception.getMessage());
        }
    }

    public void sendMessage(CrossServerMessage message) {
        RedisSession current = session;
        if (current != null && message != null) current.publish(message.serialize());
    }

    public void disable() {
        configure(null);
    }

    public void setMessageHandler(Consumer<CrossServerMessage> handler) {
        messageHandler = handler;
    }

    /** 功能已启用；连接恢复期间消息会在有界队列中等待。 */
    public boolean isEnabled() {
        RedisSession current = session;
        return current != null && current.isRunning();
    }

    public boolean isConnected() {
        RedisSession current = session;
        return current != null && current.isConnected();
    }

    public int getPendingMessageCount() {
        RedisSession current = session;
        return current == null ? 0 : current.pendingCount();
    }
}
