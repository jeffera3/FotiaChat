package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis 发布/订阅跨服消息处理器。
 */
public class RedisHandler {

    private static final long RECONNECT_DELAY_MILLIS = 5_000L;

    private final FotiaChat plugin;
    private final CrossServerManager manager;
    private volatile Consumer<CrossServerMessage> messageHandler;
    private volatile boolean enabled;
    private volatile boolean running;
    private volatile Jedis subscriberJedis;
    private volatile JedisPubSub subscription;

    private String channelName;
    private JedisPool jedisPool;
    private ExecutorService subscriberExecutor;
    private ExecutorService publisherExecutor;

    public RedisHandler(FotiaChat plugin, CrossServerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public synchronized void enable(String host, int port, String password, String channelName) {
        if (enabled) return;

        this.channelName = channelName == null || channelName.isBlank()
                ? "fotiachat:messages"
                : channelName.trim();

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setMaxWait(Duration.ofSeconds(2));

            String safePassword = password == null ? "" : password;
            if (safePassword.isBlank()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2_000);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2_000, safePassword);
            }

            // 启动时验证配置，避免“已启用”但永远连不上。
            try (Jedis jedis = jedisPool.getResource()) {
                if (!"PONG".equalsIgnoreCase(jedis.ping())) {
                    throw new IllegalStateException("Redis PING 未返回 PONG");
                }
            }

            subscriberExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FotiaChat-Redis-Subscriber");
                thread.setDaemon(true);
                return thread;
            });
            publisherExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FotiaChat-Redis-Publisher");
                thread.setDaemon(true);
                return thread;
            });

            running = true;
            enabled = true;
            subscriberExecutor.submit(this::subscribeLoop);
            plugin.getLogger().info("Redis跨服通信已启用 (" + host + ":" + port
                    + ", channel=" + this.channelName + ")");
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Redis连接失败: " + throwable.getMessage());
            closeResources();
        }
    }

    private void subscribeLoop() {
        while (running) {
            try (Jedis jedis = jedisPool.getResource()) {
                subscriberJedis = jedis;
                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String data) {
                        if (channelName.equals(channel)) {
                            handleMessage(data);
                        }
                    }
                };
                subscription = pubSub;
                jedis.subscribe(pubSub, channelName);
            } catch (Throwable throwable) {
                if (running) {
                    plugin.getLogger().warning("Redis订阅断开: " + throwable.getMessage()
                            + "，5秒后重连");
                    try {
                        Thread.sleep(RECONNECT_DELAY_MILLIS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                subscription = null;
                subscriberJedis = null;
            }
        }
    }

    private void handleMessage(String data) {
        CrossServerMessage message = CrossServerMessage.deserialize(data);
        Consumer<CrossServerMessage> handler = messageHandler;
        if (message == null || handler == null) {
            if (message == null && plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("[Debug] 丢弃无法解析的Redis跨服消息");
            }
            return;
        }
        if (message.getServerName().equals(manager.getServerName())) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> handler.accept(message));
        } catch (RuntimeException exception) {
            if (running) {
                plugin.getLogger().warning("调度Redis跨服消息失败: " + exception.getMessage());
            }
        }
    }

    public void sendMessage(CrossServerMessage message) {
        ExecutorService executor = publisherExecutor;
        if (!enabled || executor == null || message == null) return;

        String serialized = message.serialize();
        try {
            executor.submit(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.publish(channelName, serialized);
                } catch (Throwable throwable) {
                    if (running) {
                        plugin.getLogger().warning("发送Redis消息失败: " + throwable.getMessage());
                    }
                }
            });
        } catch (RuntimeException exception) {
            if (running) {
                plugin.getLogger().warning("Redis发布任务被拒绝: " + exception.getMessage());
            }
        }
    }

    public synchronized void disable() {
        boolean wasActive = enabled || running || jedisPool != null;
        closeResources();
        if (wasActive) {
            plugin.getLogger().info("Redis跨服通信已禁用");
        }
    }

    private void closeResources() {
        running = false;
        enabled = false;

        JedisPubSub currentSubscription = subscription;
        if (currentSubscription != null) {
            try {
                currentSubscription.unsubscribe();
            } catch (RuntimeException ignored) {
            }
        }
        Jedis currentSubscriber = subscriberJedis;
        if (currentSubscriber != null) {
            try {
                currentSubscriber.close();
            } catch (RuntimeException ignored) {
            }
        }

        shutdownExecutor(publisherExecutor);
        shutdownExecutor(subscriberExecutor);
        publisherExecutor = null;
        subscriberExecutor = null;

        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void setMessageHandler(Consumer<CrossServerMessage> handler) {
        this.messageHandler = handler;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
