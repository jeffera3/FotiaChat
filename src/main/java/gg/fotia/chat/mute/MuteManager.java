package gg.fotia.chat.mute;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 禁言管理器。
 */
public class MuteManager {

    private final FotiaChat plugin;
    private final MuteCache cache = new MuteCache();
    private final DatabaseCacheCoordinator databaseCoordinator = new DatabaseCacheCoordinator();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private final AtomicLong lastSeenCacheVersion = new AtomicLong(-1L);
    private final Object fileSaveLock = new Object();
    private File mutesFile;
    private BukkitTask refreshTask;
    private boolean initialDatabaseLoadAttempted;

    public MuteManager(FotiaChat plugin) {
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
            dbManager.cleanExpiredMutes();
            startRefreshTask();
        } else {
            initialDatabaseLoadAttempted = false;
            loadFromFile();
        }
        plugin.getLogger().info("已加载 " + cache.size() + " 个禁言记录");
    }

    private void refreshFromDatabase() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null || !dbManager.isEnabled()) {
            return;
        }
        long refreshRevision = cache.revision();
        long remoteVersion = dbManager.loadCacheVersion(DatabaseManager.CACHE_MUTES);
        DatabaseManager.LoadResult<DatabaseManager.MuteRecord> result = dbManager.loadAllMutes();
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
                    long remoteVersion = dbManager.loadCacheVersion(DatabaseManager.CACHE_MUTES);
                    if (remoteVersion >= 0 && remoteVersion == lastSeenCacheVersion.get()) {
                        return;
                    }
                    DatabaseManager.LoadResult<DatabaseManager.MuteRecord> result = dbManager.loadAllMutes();
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

    private void applyDatabaseRefresh(DatabaseManager.LoadResult<DatabaseManager.MuteRecord> result,
                                      long refreshRevision) {
        if (!result.successful()) {
            return;
        }
        List<MuteData> records = result.records().stream()
                .map(this::toMuteData)
                .toList();
        cache.replaceIfRevision(records, System.currentTimeMillis(), refreshRevision);
    }

    private MuteData toMuteData(DatabaseManager.MuteRecord record) {
        return new MuteData(
                record.uuid(),
                record.username(),
                record.muteTime(),
                record.expireTime(),
                record.reason(),
                record.mutedBy()
        );
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

    private void loadFromFile() {
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!mutesFile.exists()) {
            try {
                mutesFile.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("无法创建禁言数据文件: " + exception.getMessage());
            }
        }
        FileConfiguration mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
        List<MuteData> records = new ArrayList<>();
        ConfigurationSection mutesSection = mutesConfig.getConfigurationSection("mutes");
        if (mutesSection != null) {
            for (String uuidText : mutesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidText);
                    ConfigurationSection section = mutesSection.getConfigurationSection(uuidText);
                    if (section != null) {
                        records.add(loadMuteData(uuid, section));
                    }
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("无效的UUID: " + uuidText);
                }
            }
        }
        cache.replace(records, System.currentTimeMillis());
    }

    private MuteData loadMuteData(UUID uuid, ConfigurationSection section) {
        return new MuteData(
                uuid,
                section.getString("name", "Unknown"),
                section.getLong("mute-time", 0),
                section.getLong("expire-time", 0),
                section.getString("reason", "无"),
                section.getString("muted-by", "Console")
        );
    }

    /**
     * 请求保存到文件：磁盘写入放到异步线程，避免命令在主线程做全量 YAML 重写。
     */
    public void save() {
        if (mutesFile == null) {
            return;
        }
        if (!savePending.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                savePending.set(false);
                writeMutesFile();
            });
        } catch (RuntimeException exception) {
            // 插件关闭期间调度器不可用，直接同步写
            savePending.set(false);
            writeMutesFile();
        }
    }

    private void writeMutesFile() {
        File file = mutesFile;
        if (file == null) {
            return;
        }
        synchronized (fileSaveLock) {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, MuteData> entry : cache.snapshot().entrySet()) {
                MuteData data = entry.getValue();
                if (data.isExpired()) {
                    continue;
                }
                String path = "mutes." + entry.getKey();
                config.set(path + ".name", data.getPlayerName());
                config.set(path + ".mute-time", data.getMuteTime());
                config.set(path + ".expire-time", data.getExpireTime());
                config.set(path + ".reason", data.getReason());
                config.set(path + ".muted-by", data.getMutedBy());
            }
            try {
                config.save(file);
            } catch (IOException exception) {
                plugin.getLogger().severe("无法保存禁言数据: " + exception.getMessage());
            }
        }
    }

    public void mute(Player player, long duration, String reason, String mutedBy) {
        mute(player.getUniqueId(), player.getName(), duration, reason, mutedBy);
    }

    public void mute(UUID uuid, String playerName, long duration, String reason, String mutedBy) {
        long muteTime = System.currentTimeMillis();
        long expireTime = duration > 0 ? muteTime + duration * 1000L : 0;
        MuteData data = new MuteData(uuid, playerName, muteTime, expireTime, reason, mutedBy);

        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            databaseCoordinator.mutate(
                    () -> cache.put(data),
                    () -> dbManager.saveMute(uuid, playerName, muteTime, expireTime, reason, mutedBy)
            );
        } else {
            cache.put(data);
            save();
        }
    }

    public boolean unmute(UUID uuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            MuteData removed = databaseCoordinator.mutate(
                    () -> cache.remove(uuid),
                    result -> {
                        if (result != null) {
                            dbManager.deleteMute(uuid);
                        }
                    }
            );
            return removed != null;
        }

        MuteData removed = cache.remove(uuid);
        if (removed != null) {
            save();
        }
        return removed != null;
    }

    public boolean isMuted(Player player) {
        String mutePermission = plugin.getConfigManager().getMutePermission();
        if (mutePermission != null && !mutePermission.isEmpty() && player.hasPermission(mutePermission)) {
            return true;
        }
        return isMuted(player.getUniqueId());
    }

    public boolean isMuted(UUID uuid) {
        // 快路径：绝大多数玩家不在禁言表，直接读不可变快照，不进入协调器锁
        MuteData data = cache.get(uuid);
        if (data == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (data.getExpireTime() <= 0 || now <= data.getExpireTime()) {
            return true;
        }

        // 已过期：走原有的移除与数据库清理路径
        DatabaseManager dbManager = plugin.getDatabaseManager();
        boolean removedExpired;
        if (dbManager != null && dbManager.isEnabled()) {
            removedExpired = databaseCoordinator.mutate(
                    () -> cache.removeExpired(uuid, now),
                    removed -> {
                        if (removed) {
                            dbManager.deleteMute(uuid);
                        }
                    }
            );
        } else {
            removedExpired = cache.removeExpired(uuid, now);
        }
        if (removedExpired) {
            return false;
        }
        return cache.get(uuid) != null;
    }

    public MuteData getMuteData(UUID uuid) {
        return isMuted(uuid) ? cache.get(uuid) : null;
    }

    public MuteData getMuteData(Player player) {
        return getMuteData(player.getUniqueId());
    }

    public Map<UUID, MuteData> getAllMutes() {
        return cache.snapshot();
    }

    public void stop() {
        stopRefreshTask();
        if (mutesFile != null) {
            writeMutesFile();
        }
    }
}
