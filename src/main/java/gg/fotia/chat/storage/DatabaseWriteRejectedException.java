package gg.fotia.chat.storage;

/** 队列未接收写入，调用方不得更新缓存或提示保存成功。 */
public final class DatabaseWriteRejectedException extends RuntimeException {
    public DatabaseWriteRejectedException() {
        super("Database write queue is full or closed");
    }
}
