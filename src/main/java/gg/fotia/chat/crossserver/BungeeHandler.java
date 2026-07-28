package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * BungeeCord消息处理器
 */
public class BungeeHandler implements PluginMessageListener, Listener {

    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL = "FotiaChat";
    // Bungee Forward 在代理端以 signed short 读取长度，最大安全值为 32767。
    private static final int MAX_FORWARD_BYTES = 32_767;
    private static final int MAX_PENDING_MESSAGES = 100;

    private final FotiaChat plugin;
    private final CrossServerManager manager;
    private final Deque<CrossServerMessage> pendingMessages = new ArrayDeque<>();
    private Consumer<CrossServerMessage> messageHandler;
    private volatile boolean enabled = false;
    private boolean warnedNoCarrier;

    public BungeeHandler(FotiaChat plugin, CrossServerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void enable() {
        if (enabled) return;

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
        plugin.getLogger().info("BungeeCord跨服通信已启用");
    }

    public void disable() {
        if (!enabled) return;

        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        HandlerList.unregisterAll(this);
        pendingMessages.clear();
        warnedNoCarrier = false;
        enabled = false;
        plugin.getLogger().info("BungeeCord跨服通信已禁用");
    }

    public void sendMessage(CrossServerMessage message) {
        if (!enabled || message == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> sendMessage(message));
            return;
        }

        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            enqueue(message);
            if (!warnedNoCarrier) {
                warnedNoCarrier = true;
                plugin.getLogger().warning("BungeeCord暂无在线玩家承载消息，已暂存队列并将在玩家上线后发送");
            }
            return;
        }

        flushPending(carrier);
        sendNow(carrier, message);
    }

    private void enqueue(CrossServerMessage message) {
        if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
            pendingMessages.removeFirst();
            plugin.getLogger().warning("BungeeCord待发队列已满，已丢弃最旧消息");
        }
        pendingMessages.addLast(message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || pendingMessages.isEmpty()) {
            return;
        }
        // 延迟一 tick，确保玩家的代理连接已可承载 plugin message。
        Bukkit.getScheduler().runTask(plugin, () -> flushPending(event.getPlayer()));
    }

    private void flushPending(Player carrier) {
        if (!enabled || carrier == null || !carrier.isOnline()) {
            return;
        }
        warnedNoCarrier = false;
        while (!pendingMessages.isEmpty()) {
            sendNow(carrier, pendingMessages.removeFirst());
        }
    }

    private void sendNow(Player carrier, CrossServerMessage message) {
        try {
            ByteArrayOutputStream dataBytes = new ByteArrayOutputStream();
            try (DataOutputStream dataOut = new DataOutputStream(dataBytes)) {
                dataOut.writeUTF(message.serialize());
            }
            byte[] data = dataBytes.toByteArray();
            if (data.length > MAX_FORWARD_BYTES) {
                plugin.getLogger().severe("BungeeCord消息过大，已拒绝发送: " + data.length
                        + " bytes（安全上限 " + MAX_FORWARD_BYTES + "）");
                return;
            }

            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            try (DataOutputStream msgOut = new DataOutputStream(msgBytes)) {
                msgOut.writeUTF("Forward");
                msgOut.writeUTF("ALL");
                msgOut.writeUTF(SUBCHANNEL);
                msgOut.writeShort(data.length);
                msgOut.write(data);
            }
            carrier.sendPluginMessage(plugin, CHANNEL, msgBytes.toByteArray());
        } catch (IOException exception) {
            plugin.getLogger().severe("发送BungeeCord消息失败: " + exception.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player,
                                        byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subchannel = in.readUTF();
            if (!SUBCHANNEL.equals(subchannel)) return;

            int len = in.readUnsignedShort();
            if (len > MAX_FORWARD_BYTES || len > in.available()) {
                throw new IOException("BungeeCord消息长度无效: " + len);
            }
            byte[] msgBytes = new byte[len];
            in.readFully(msgBytes);

            String data;
            try (DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgBytes))) {
                data = msgIn.readUTF();
            }

            CrossServerMessage crossMessage = CrossServerMessage.deserialize(data);
            if (crossMessage != null && messageHandler != null) {
                if (!crossMessage.getServerName().equals(manager.getServerName())) {
                    Bukkit.getScheduler().runTask(plugin, () -> messageHandler.accept(crossMessage));
                } else if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[Debug] 忽略同名服务器的跨服消息: " + manager.getServerName());
                }
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("处理BungeeCord消息失败: " + exception.getMessage());
        }
    }

    public void setMessageHandler(Consumer<CrossServerMessage> handler) {
        this.messageHandler = handler;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
