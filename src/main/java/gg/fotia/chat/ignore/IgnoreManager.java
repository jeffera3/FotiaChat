package gg.fotia.chat.ignore;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseCacheCoordinator;
import gg.fotia.chat.storage.DatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家屏蔽管理器。
 */
public class IgnoreManager {

    private final FotiaChat plugin;
    private final IgnoreCache cache = new IgnoreCache();
    private final DatabaseCacheCoordinator databaseCoordinator = new DatabaseCacheCoordinator();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private final AtomicLong lastSeenCacheVersion = new AtomicLong(-1L);
    private final Object fileSaveLock = new Object();
    private File ignoresFile;
    private BukkitTask refreshTask;
    private boolean initialDatabaseLoadAttempted;

    public IgnoreManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        stopRefreshTask();
        lastSeenCacheVersion.set(-1L);
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            if (!initialDatabaseLoadAttempted) {
                initialDatabaseLoadAttempted = true;
                refreshFromDatabase();
            } else {
                refreshFromDatabaseAsync();
            }
            startRefreshTask();
            plugin.getLogger().info("屏蔽数据使用数据库缓存，聊天路径不会查询数据库");
        } else {
            initialDatabaseLoadAttempted = false;
            loadFromFile();
            plugin.getLogger().info("已加载 " + cache.ownerCount() + " 个玩家的屏蔽列表");
        }
    }

    private void loadFromFile() {
        ignoresFile = new File(plugin.getDataFolder(), "ignores.yml");
        if (!ignoresFile.exists()) {
            try {
                ignoresFile.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("无法创建屏蔽数据文件: " + exception.getMessage());
            }
        }

        FileConfiguration ignoresConfig = YamlConfiguration.loadConfiguration(ignoresFile);
        List<IgnoreCache.Entry> entries = new ArrayList<>();
        ConfigurationSection ignoresSection = ignoresConfig.getConfigurationSection("ignores");
        if (ignoresSection != null) {
            for (String ownerText : ignoresSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(ownerText);
                    for (String targetText : ignoresSection.getStringList(ownerText)) {
                        try {
                            entries.add(new IgnoreCache.Entry(owner, UUID.fromString(targetText), ""));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("无效的UUID: " + ownerText);
                }
            }
        }
        cache.replace(entries);
    }

    private void refreshFromDatabase() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null || !dbManager.isEnabled()) {
            return;
        }
        long refreshRevision = cache.revision();
        long remoteVersion = dbManager.loadCacheVersion(DatabaseManager.CACHE_IGNORES);
        DatabaseManager.LoadResult<DatabaseManager.IgnoreRecord> result = dbManager.loadAllIgnores();
        if (!result.successful()) {
            return;
        }
        applyDatabaseRefresh(result, refreshRevision);
        if (remoteVersion >= 0) {
            lastSeenCacheVersion.set(remoteVersion);
        }
    }

    private void refreshFromDatabaseAsync() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null || !dbManager.isEnabled() || !refreshPending.compareAndSet(false, true)) {
            return;
        }
        databaseCoordinator.refresh(cache::revision, refreshRevision -> {
            boolean submitted = dbManager.submitTask(() -> {
                try {
                    // 先比对版本号，数据没有变更时跳过全表拉取
                    long remoteVersion = dbManager.loadCacheVersion(DatabaseManager.CACHE_IGNORES);
                    if (remoteVersion >= 0 && remoteVersion == lastSeenCacheVersion.get()) {
                        return;
                    }
                    DatabaseManager.LoadResult<DatabaseManager.IgnoreRecord> result = dbManager.loadAllIgnores();
                    applyDatabaseRefresh(result, refreshRevision);
                    if (result.successful() && remoteVersion >= 0) {
                        lastSeenCacheVersion.set(remoteVersion);
                    }
                } finally {
                    refreshPending.set(false);
                }
            });
            if (!submitted) {
                refreshPending.set(false);
            }
        });
    }

    private void applyDatabaseRefresh(DatabaseManager.LoadResult<DatabaseManager.IgnoreRecord> result,
                                      long refreshRevision) {
        if (!result.successful()) {
            return;
        }
        List<IgnoreCache.Entry> entries = result.records().stream()
                .map(record -> new IgnoreCache.Entry(record.playerUuid(), record.ignoredUuid(), record.ignoredName()))
                .toList();
        cache.replaceIfRevision(entries, refreshRevision);
    }

    private void startRefreshTask() {
        long seconds = Math.max(1L, plugin.getConfigManager().getConfig()
                .getLong("storage.mysql.cache-refresh-seconds", 5L));
        refreshTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::refreshFromDatabaseAsync,
                seconds * 20L,
                seconds * 20L
        );
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * 请求保存到文件：磁盘写入放到异步线程，避免命令在主线程做全量 YAML 重写。
     */
    public void save() {
        if (ignoresFile == null) {
            return;
        }
        if (!savePending.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                savePending.set(false);
                writeIgnoresFile();
            });
        } catch (RuntimeException exception) {
            // 插件关闭期间调度器不可用，直接同步写
            savePending.set(false);
            writeIgnoresFile();
        }
    }

    private void writeIgnoresFile() {
        File file = ignoresFile;
        if (file == null) {
            return;
        }
        synchronized (fileSaveLock) {
            YamlConfiguration config = new YamlConfiguration();
            Map<UUID, List<String>> grouped = new LinkedHashMap<>();
            for (IgnoreCache.Entry entry : cache.entries()) {
                grouped.computeIfAbsent(entry.owner(), ignored -> new ArrayList<>()).add(entry.target().toString());
            }
            grouped.forEach((owner, ignored) -> config.set("ignores." + owner, ignored));
            try {
                config.save(file);
            } catch (IOException exception) {
                plugin.getLogger().severe("无法保存屏蔽数据: " + exception.getMessage());
            }
        }
    }

    public void addIgnore(UUID playerUuid, UUID ignoredUuid, String ignoredName) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            databaseCoordinator.persistThenMutate(
                    () -> dbManager.addIgnore(playerUuid, ignoredUuid, ignoredName),
                    () -> cache.add(playerUuid, ignoredUuid, ignoredName)
            );
        } else {
            cache.add(playerUuid, ignoredUuid, ignoredName);
            save();
        }
    }

    public void removeIgnore(UUID playerUuid, UUID ignoredUuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            databaseCoordinator.persistThenMutate(
                    () -> dbManager.removeIgnore(playerUuid, ignoredUuid),
                    () -> cache.remove(playerUuid, ignoredUuid)
            );
        } else {
            cache.remove(playerUuid, ignoredUuid);
            save();
        }
    }

    public boolean isIgnoring(UUID playerUuid, UUID targetUuid) {
        return cache.contains(playerUuid, targetUuid);
    }

    public List<String> getIgnoreList(UUID playerUuid) {
        List<IgnoreCache.Entry> entries = cache.entries(playerUuid);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (IgnoreCache.Entry entry : entries) {
            if (!entry.name().isBlank()) {
                names.add(entry.name());
                continue;
            }
            Player player = plugin.getServer().getPlayer(entry.target());
            names.add(player == null ? entry.target().toString().substring(0, 8) + "..." : player.getName());
        }
        return names;
    }

    public boolean toggleIgnore(UUID playerUuid, UUID ignoredUuid, String ignoredName) {
        if (isIgnoring(playerUuid, ignoredUuid)) {
            removeIgnore(playerUuid, ignoredUuid);
            return false;
        }
        addIgnore(playerUuid, ignoredUuid, ignoredName);
        return true;
    }

    public void stop() {
        stopRefreshTask();
        if (ignoresFile != null) {
            writeIgnoresFile();
        }
    }
}
