package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseWriteRejectedException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** 将数据库过载反馈给命令发送者，避免后续成功提示。 */
public final class StorageAwareCommandExecutor implements CommandExecutor {
    private final FotiaChat plugin;
    private final CommandExecutor delegate;

    public StorageAwareCommandExecutor(FotiaChat plugin, CommandExecutor delegate) {
        this.plugin = plugin;
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return delegate.onCommand(sender, command, label, args);
        } catch (DatabaseWriteRejectedException exception) {
            plugin.getMessageManager().send(sender, "storage.busy");
            return true;
        }
    }
}
