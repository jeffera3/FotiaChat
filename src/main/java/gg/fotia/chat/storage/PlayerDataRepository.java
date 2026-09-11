package gg.fotia.chat.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/** 玩家偏好读写；登录仅刷新身份信息，不用旧快照覆盖已保存的设置。 */
final class PlayerDataRepository {
    private final DatabaseManager database;
    private final Logger logger;

    PlayerDataRepository(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    PlayerDataLoadResult initialize(UUID uuid, String username, String defaultChannel) {
        String sql = """
                INSERT INTO fotiachat_players (uuid, username, channel_id, color_id)
                VALUES (?, ?, ?, NULL)
                ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = CURRENT_TIMESTAMP
                """;
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setString(3, defaultChannel);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT channel_id, color_id FROM fotiachat_players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new PlayerDataLoadResult(true, new DatabaseManager.PlayerData(
                                uuid, result.getString("channel_id"), result.getString("color_id")));
                    }
                }
            }
        } catch (SQLException exception) {
            logger.warning("加载玩家设置失败，保留数据库原有设置: " + uuid + ": " + exception.getMessage());
        }
        return PlayerDataLoadResult.failure();
    }

    /**
     * 保存玩家数据
     */
    public void savePlayerData(UUID uuid, String username, String channelId, String colorId) {
        if (!database.isEnabled()) return;

        String sql = """
            INSERT INTO fotiachat_players (uuid, username, channel_id, color_id)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                channel_id = VALUES(channel_id),
                color_id = VALUES(color_id)
            """;

        database.enqueueWrite(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, channelId);
                stmt.setString(4, colorId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.warning("保存玩家数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 加载玩家数据
     */
    public DatabaseManager.PlayerData loadPlayerData(UUID uuid) {
        return load(uuid).data();
    }

    PlayerDataLoadResult load(UUID uuid) {
        if (!database.isEnabled()) return PlayerDataLoadResult.failure();

        String sql = "SELECT channel_id, color_id FROM fotiachat_players WHERE uuid = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerDataLoadResult(true, new DatabaseManager.PlayerData(
                            uuid, rs.getString("channel_id"), rs.getString("color_id")));
                }
            }
        } catch (SQLException e) {
            logger.warning("加载玩家数据失败: " + e.getMessage());
            return PlayerDataLoadResult.failure();
        }

        return new PlayerDataLoadResult(true, null);
    }

    /**
     * 更新玩家频道（upsert，玩家行缺失时自动补建，避免更新被静默丢弃）
     */
    public void updatePlayerChannel(UUID uuid, String username, String channelId) {
        if (!database.isEnabled()) return;

        String sql = """
            INSERT INTO fotiachat_players (uuid, username, channel_id)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                channel_id = VALUES(channel_id)
            """;

        database.enqueueWrite(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, channelId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.warning("更新玩家频道失败: " + e.getMessage());
            }
        });
    }

    /**
     * 更新玩家颜色（upsert，玩家行缺失时自动补建）
     */
    public void updatePlayerColor(UUID uuid, String username, String colorId) {
        if (!database.isEnabled()) return;

        String sql = """
            INSERT INTO fotiachat_players (uuid, username, color_id)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                color_id = VALUES(color_id)
            """;

        database.enqueueWrite(() -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, colorId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.warning("更新玩家颜色失败: " + e.getMessage());
            }
        });
    }

}
