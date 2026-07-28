package gg.fotia.chat.announcement;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 公告管理器
 */
public class AnnouncementManager {

    private final FotiaChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration announcementConfig;
    private final Map<String, Announcement> announcements = new LinkedHashMap<>();
    private final Map<String, BukkitTask> tasks = new HashMap<>();
    private final Map<String, Integer> messageIndex = new HashMap<>();

    private boolean enabled;
    private boolean random;
    private final Random randomSource = new Random();

    public AnnouncementManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // 停止所有现有任务
        stopAllTasks();
        announcements.clear();
        messageIndex.clear();

        saveDefaultConfig();

        File announcementFile = new File(plugin.getDataFolder(), "announcements.yml");
        announcementConfig = YamlConfiguration.loadConfiguration(announcementFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("announcements.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            announcementConfig.setDefaults(defaultConfig);
        }

        // 读取全局设置
        this.enabled = announcementConfig.getBoolean("enabled", true);
        this.random = announcementConfig.getBoolean("random", false);

        // 加载公告
        ConfigurationSection announcementsSection = announcementConfig.getConfigurationSection("announcements");
        if (announcementsSection != null) {
            for (String id : announcementsSection.getKeys(false)) {
                ConfigurationSection section = announcementsSection.getConfigurationSection(id);
                if (section != null) {
                    loadAnnouncement(id, section);
                }
            }
        }

        // 启动公告任务
        if (enabled) {
            startAllTasks();
        }

        plugin.getLogger().info("已加载 " + announcements.size() + " 条公告");
    }

    private void loadAnnouncement(String id, ConfigurationSection section) {
        boolean announcementEnabled = section.getBoolean("enabled", true);
        String permission = section.getString("permission", "");
        int interval = section.getInt("interval", 300);
        List<String> messages = section.getStringList("messages");
        String sound = section.getString("sound", "");
        float soundVolume = (float) section.getDouble("sound-volume", 1.0);
        float soundPitch = (float) section.getDouble("sound-pitch", 1.0);

        if (sound != null && !sound.isBlank()) {
            try {
                Sound.valueOf(sound.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("公告 " + id + " 的音效不存在: " + sound);
                sound = "";
            }
        }

        // 加载Hover配置
        boolean hoverEnabled = false;
        List<String> hoverText = new ArrayList<>();
        ConfigurationSection hoverSection = section.getConfigurationSection("hover");
        if (hoverSection != null) {
            hoverEnabled = hoverSection.getBoolean("enabled", false);
            hoverText = hoverSection.getStringList("text");
        }

        // 加载Click配置
        boolean clickEnabled = false;
        ClickEvent.Action clickAction = ClickEvent.Action.SUGGEST_COMMAND;
        String clickValue = "";
        ConfigurationSection clickSection = section.getConfigurationSection("click");
        if (clickSection != null) {
            clickEnabled = clickSection.getBoolean("enabled", false);
            String actionStr = clickSection.getString("action", "SUGGEST_COMMAND");
            clickAction = parseClickAction(actionStr);
            clickValue = clickSection.getString("value", "");
        }

        Announcement announcement = new Announcement(id, permission, interval, messages,
                announcementEnabled, sound, soundVolume, soundPitch,
                hoverEnabled, hoverText, clickEnabled, clickAction, clickValue);
        announcements.put(id, announcement);
    }

    /**
     * 解析点击动作类型
     */
    private ClickEvent.Action parseClickAction(String action) {
        return switch (action.toUpperCase()) {
            case "RUN_COMMAND" -> ClickEvent.Action.RUN_COMMAND;
            case "OPEN_URL" -> ClickEvent.Action.OPEN_URL;
            case "COPY_TO_CLIPBOARD" -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> ClickEvent.Action.SUGGEST_COMMAND;
        };
    }

    private void saveDefaultConfig() {
        File announcementFile = new File(plugin.getDataFolder(), "announcements.yml");
        if (!announcementFile.exists()) {
            plugin.saveResource("announcements.yml", false);
        }
    }

    /**
     * 启动所有公告任务
     */
    public void startAllTasks() {
        for (Announcement announcement : announcements.values()) {
            if (announcement.isEnabled()) {
                startTask(announcement);
            }
        }
    }

    /**
     * 停止所有公告任务
     */
    public void stopAllTasks() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    /**
     * 启动单个公告任务
     */
    private void startTask(Announcement announcement) {
        if (tasks.containsKey(announcement.getId())) {
            return;
        }

        long intervalTicks = announcement.getInterval() * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            broadcastNext(announcement);
        }, intervalTicks, intervalTicks);

        tasks.put(announcement.getId(), task);
    }

    /**
     * 自动任务按 random/顺序轮播选择单条公告文本。
     */
    private void broadcastNext(Announcement announcement) {
        if (announcement.getMessages().isEmpty()) {
            return;
        }
        broadcastLines(announcement, List.of(getNextMessage(announcement)));
    }

    /**
     * 手动发送保留“发送该公告全部消息行”的原有语义。
     */
    public void broadcast(Announcement announcement) {
        if (announcement.getMessages().isEmpty()) {
            return;
        }
        broadcastLines(announcement, announcement.getMessages());
    }

    private void broadcastLines(Announcement announcement, List<String> lines) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        boolean playerSpecific = containsPlayerPlaceholder(lines)
                || containsPlayerPlaceholder(announcement.getHoverText())
                || containsPlayerPlaceholder(announcement.getClickValue());
        List<Component> sharedComponents = playerSpecific ? List.of() : buildSharedComponents(announcement, lines);

        Sound sound = null;
        if (announcement.hasSound()) {
            try {
                sound = Sound.valueOf(announcement.getSound().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }

        for (Player player : players) {
            if (announcement.hasPermission() && !player.hasPermission(announcement.getPermission())) {
                continue;
            }
            if (playerSpecific) {
                for (String line : lines) {
                    player.sendMessage(buildAnnouncementComponent(announcement, line, player));
                }
            } else {
                for (Component component : sharedComponents) {
                    player.sendMessage(component);
                }
            }
            if (sound != null) {
                player.playSound(player.getLocation(), sound,
                        announcement.getSoundVolume(), announcement.getSoundPitch());
            }
        }
    }

    private boolean containsPlayerPlaceholder(Collection<String> values) {
        for (String value : values) {
            if (containsPlayerPlaceholder(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPlayerPlaceholder(String value) {
        return value != null && value.contains("%");
    }

    private List<Component> buildSharedComponents(Announcement announcement, List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            Component component = miniMessage.deserialize(line);
            if (announcement.isHoverEnabled() && !announcement.getHoverText().isEmpty()) {
                Component hover = Component.empty();
                for (int index = 0; index < announcement.getHoverText().size(); index++) {
                    if (index > 0) {
                        hover = hover.append(Component.newline());
                    }
                    hover = hover.append(miniMessage.deserialize(announcement.getHoverText().get(index)));
                }
                component = component.hoverEvent(HoverEvent.showText(hover));
            }
            if (announcement.isClickEnabled() && !announcement.getClickValue().isEmpty()) {
                component = component.clickEvent(ClickEvent.clickEvent(
                        announcement.getClickAction(), announcement.getClickValue()));
            }
            components.add(component);
        }
        return List.copyOf(components);
    }

    /**
     * 构建带hover和click的公告组件
     */
    private Component buildAnnouncementComponent(Announcement announcement, String message, Player player) {
        // 解析基础消息
        Component component = MessageUtil.parse(message, player);

        // 如果没有hover和click，直接返回
        if (!announcement.isHoverEnabled() && !announcement.isClickEnabled()) {
            return component;
        }

        // 添加Hover事件
        if (announcement.isHoverEnabled() && !announcement.getHoverText().isEmpty()) {
            List<Component> hoverLines = new ArrayList<>();
            for (String line : announcement.getHoverText()) {
                // 替换占位符
                String processedLine = plugin.getChatFormatter().getPlaceholderHandler()
                        .setPlaceholders(player, line);
                hoverLines.add(miniMessage.deserialize(processedLine));
            }

            // 合并多行
            Component hoverComponent = Component.empty();
            for (int i = 0; i < hoverLines.size(); i++) {
                hoverComponent = hoverComponent.append(hoverLines.get(i));
                if (i < hoverLines.size() - 1) {
                    hoverComponent = hoverComponent.append(Component.newline());
                }
            }

            component = component.hoverEvent(HoverEvent.showText(hoverComponent));
        }

        // 添加Click事件
        if (announcement.isClickEnabled() && !announcement.getClickValue().isEmpty()) {
            String clickValue = plugin.getChatFormatter().getPlaceholderHandler()
                    .setPlaceholders(player, announcement.getClickValue());

            ClickEvent clickEvent = ClickEvent.clickEvent(announcement.getClickAction(), clickValue);
            component = component.clickEvent(clickEvent);
        }

        return component;
    }

    /**
     * 获取下一条消息
     */
    private String getNextMessage(Announcement announcement) {
        List<String> messages = announcement.getMessages();
        if (messages.size() == 1) {
            return messages.get(0);
        }

        if (random) {
            return messages.get(randomSource.nextInt(messages.size()));
        }

        // 顺序播放
        int index = messageIndex.getOrDefault(announcement.getId(), 0);
        String message = messages.get(index);
        messageIndex.put(announcement.getId(), (index + 1) % messages.size());
        return message;
    }

    /**
     * 手动发送公告
     */
    public boolean sendAnnouncement(String id) {
        Announcement announcement = announcements.get(id);
        if (announcement == null) {
            return false;
        }
        broadcast(announcement);
        return true;
    }

    /**
     * 发送自定义消息给所有玩家
     */
    public void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(MessageUtil.parse(message, player));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            startAllTasks();
        } else {
            stopAllTasks();
        }
    }

    public Map<String, Announcement> getAnnouncements() {
        return announcements;
    }

    public Announcement getAnnouncement(String id) {
        return announcements.get(id);
    }

    public List<String> getAnnouncementIds() {
        return new ArrayList<>(announcements.keySet());
    }
}
