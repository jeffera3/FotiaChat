package gg.fotia.chat.crossserver;

import gg.fotia.chat.util.BoundedTaskQueue;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/** 一次 Redis 配置的连接与队列。连接、等待和关闭均在后台完成。 */
final class RedisSession {
    private final RedisSettings settings;
    private final Logger logger;
    private final BiConsumer<RedisSession, String> receiver;
    private final BoundedTaskQueue publisher;
    private final Object connectionSignal = new Object();
    private final Thread subscriber;
    private volatile JedisPool pool;
    private volatile Jedis subscriberConnection;
    private volatile boolean running = true;
    private volatile boolean connected;
    private boolean started;
    private long nextWarning;

    RedisSession(RedisSettings settings, Logger logger, BiConsumer<RedisSession, String> receiver) {
        this.settings = settings;
        this.logger = logger;
        this.receiver = receiver;
        publisher = new BoundedTaskQueue("FotiaChat-Redis-Publisher", settings.queueCapacity(), logger::warning);
        subscriber = new Thread(this::subscribeLoop, "FotiaChat-Redis-Subscriber");
        subscriber.setDaemon(true);
    }

    void start() {
        if (running && !started) {
            started = true;
            subscriber.start();
        }
    }

    boolean isStarted() { return started; }
    RedisSettings settings() { return settings; }
    boolean isRunning() { return running; }
    boolean isConnected() { return connected && running; }
    int pendingCount() { return publisher.pendingCount(); }

    boolean publish(String payload) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.messageMaxAgeMillis());
        return running && publisher.submit(() -> publishWhenConnected(payload, deadline));
    }

    private void publishWhenConnected(String payload, long deadline) {
        synchronized (connectionSignal) {
            while (running && !connected) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    warn("Redis 消息等待连接超时，已丢弃过期消息");
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(connectionSignal, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (!running) return;
        if (System.nanoTime() >= deadline) {
            warn("Redis 发布队列消息已过期，已丢弃");
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(settings.channel(), payload);
        } catch (RuntimeException exception) {
            // 发布结果可能不确定，不能直接重发，否则答题奖励等业务会收到重复消息。
            if (running) warn("Redis 消息发布失败: " + exception.getMessage());
        }
    }

    private void subscribeLoop() {
        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(4);
            config.setMaxIdle(2);
            config.setMaxWait(Duration.ofMillis(settings.timeoutMillis()));
            pool = settings.password().isBlank()
                    ? new JedisPool(config, settings.host(), settings.port(), settings.timeoutMillis())
                    : new JedisPool(config, settings.host(), settings.port(), settings.timeoutMillis(), settings.password());
            while (running) {
                try (Jedis jedis = pool.getResource()) {
                    subscriberConnection = jedis;
                    if (!running) break;
                    if (!"PONG".equalsIgnoreCase(jedis.ping())) {
                        throw new IllegalStateException("Redis PING 未返回 PONG");
                    }
                    if (!running) break;
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onSubscribe(String channel, int count) {
                            if (!running) {
                                unsubscribe();
                                return;
                            }
                            setConnected(true);
                            logger.info("Redis 跨服连接已就绪 (" + settings.host() + ":" + settings.port() + ")");
                        }

                        @Override
                        public void onMessage(String channel, String payload) {
                            if (running && settings.channel().equals(channel)) receiver.accept(RedisSession.this, payload);
                        }
                    }, settings.channel());
                } catch (RuntimeException exception) {
                    if (running) warn("Redis 连接中断，将自动重连: " + exception.getMessage());
                } finally {
                    setConnected(false);
                    subscriberConnection = null;
                }
                if (running) {
                    try {
                        Thread.sleep(settings.reconnectMillis());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (RuntimeException exception) {
            warn("Redis 连接初始化失败: " + exception.getMessage());
        } finally {
            setConnected(false);
        }
    }

    private void setConnected(boolean value) {
        synchronized (connectionSignal) {
            connected = running && value;
            connectionSignal.notifyAll();
        }
    }

    /** 主线程只停止接收任务和发出中断信号，不等待网络资源。 */
    void deactivate() {
        running = false;
        setConnected(false);
        subscriber.interrupt();
        int discarded = publisher.discardPending();
        if (discarded > 0) logger.warning("Redis 配置切换或关闭，已取消待发布消息: " + discarded);
    }

    /** 只能由生命周期后台线程调用。 */
    void close() {
        deactivate();
        Jedis connection = subscriberConnection;
        if (connection != null) {
            try {
                // 直接断开订阅 socket；不能提前把仍在订阅的 Jedis 归还连接池。
                connection.getConnection().disconnect();
            } catch (RuntimeException exception) {
                logger.fine("关闭 Redis 订阅连接: " + exception.getMessage());
            }
        }
        publisher.close();
        try {
            subscriber.join(settings.timeoutMillis() + 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        JedisPool currentPool = pool;
        if (currentPool != null) currentPool.close();
    }

    private synchronized void warn(String message) {
        long now = System.nanoTime();
        if (nextWarning == 0 || now - nextWarning >= 0) {
            nextWarning = now + TimeUnit.SECONDS.toNanos(30);
            logger.warning(message);
        }
    }
}
