package gg.fotia.chat.crossserver;

import org.bukkit.configuration.ConfigurationSection;

/** 在主线程读取配置，后台连接仅使用不可变快照。 */
record RedisSettings(String host, int port, String password, String channel, String serverName,
                     int timeoutMillis, long reconnectMillis, int queueCapacity, long messageMaxAgeMillis,
                     boolean debug) {
    static RedisSettings from(String host, int port, String password, String channel, String serverName,
                              ConfigurationSection config, boolean debug) {
        return new RedisSettings(host, port, password == null ? "" : password,
                channel == null || channel.isBlank() ? "fotiachat:messages" : channel.trim(), serverName,
                Math.max(100, config.getInt("cross-server.redis.timeout-millis", 2000)),
                Math.max(100L, config.getLong("cross-server.redis.reconnect-delay-millis", 5000)),
                Math.max(1, config.getInt("cross-server.redis.publish-queue-capacity", 1024)),
                Math.max(1L, config.getLong("cross-server.redis.message-max-age-seconds", 60)) * 1000L,
                debug);
    }
}
