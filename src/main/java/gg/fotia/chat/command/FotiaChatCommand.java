package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FotiaChat主命令
 * /fotiachat reload - 重载配置
 * /fotiachat help - 帮助信息
 * /fotiachat version - 版本信息
 */
public class FotiaChatCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final List<String> subCommands = Arrays.asList("reload", "help", "version");

    public FotiaChatCommand(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "help" -> showHelp(sender);
            case "version" -> showVersion(sender);
            case "viewsnapshot" -> handleViewSnapshot(sender, args);
            default -> {
                plugin.getMessageManager().send(sender, "general.invalid-args",
                        Map.of("usage", getRaw(sender, "command.usage.fotiachat")));
            }
        }

        return true;
    }

    /**
     * 处理重载命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("fotiachat.admin.reload")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }

        try {
            plugin.reload();
            plugin.getMessageManager().send(sender, "general.reload-success");
        } catch (Exception e) {
            plugin.getMessageManager().send(sender, "general.reload-failed",
                    Map.of("error", String.valueOf(e.getMessage())));
            plugin.getLogger().severe("重载配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        List<String> helpKeys = List.of(
                "command.fotiachat.help.header",
                "command.fotiachat.help.reload",
                "command.fotiachat.help.help",
                "command.fotiachat.help.version",
                "command.fotiachat.help.channel",
                "command.fotiachat.help.chatcolor",
                "command.fotiachat.help.msg",
                "command.fotiachat.help.reply",
                "command.fotiachat.help.mute",
                "command.fotiachat.help.unmute",
                "command.fotiachat.help.footer"
        );

        for (String key : helpKeys) {
            plugin.getMessageManager().send(sender, key);
        }
    }

    /**
     * 显示版本信息
     */
    private void showVersion(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String author = String.join(", ", plugin.getDescription().getAuthors());

        plugin.getMessageManager().send(sender, "command.fotiachat.version.header");
        plugin.getMessageManager().send(sender, "command.fotiachat.version.version",
                Map.of("version", version));
        plugin.getMessageManager().send(sender, "command.fotiachat.version.authors",
                Map.of("authors", author));
        plugin.getMessageManager().send(sender, "command.fotiachat.version.api",
                Map.of("api", String.valueOf(plugin.getDescription().getAPIVersion())));
        if (plugin.getCrossServerManager().isEnabled()) {
            plugin.getMessageManager().send(sender, "command.fotiachat.version.crossserver-enabled",
                    Map.of("type", plugin.getCrossServerManager().getType()));
        } else {
            plugin.getMessageManager().send(sender, "command.fotiachat.version.crossserver-disabled");
        }
        plugin.getMessageManager().send(sender, "command.fotiachat.version.footer");
    }

    private String getRaw(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            return plugin.getMessageManager().getRaw(player, key);
        }
        return plugin.getMessageManager().getRaw(key);
    }

    /**
     * 处理查看快照命令
     */
    private void handleViewSnapshot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            return;
        }

        try {
            UUID snapshotId = UUID.fromString(args[1]);
            plugin.getItemDisplayManager().openSnapshotGui(player, snapshotId);
        } catch (IllegalArgumentException e) {
            // 无效的UUID，忽略
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
