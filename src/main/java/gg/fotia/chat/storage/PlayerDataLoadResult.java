package gg.fotia.chat.storage;

/** 数据不存在与读取失败必须分开，避免将异常当成新玩家。 */
public record PlayerDataLoadResult(boolean successful, DatabaseManager.PlayerData data) {
    public static PlayerDataLoadResult failure() {
        return new PlayerDataLoadResult(false, null);
    }
}
