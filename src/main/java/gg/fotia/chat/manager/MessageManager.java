package gg.fotia.chat.manager;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.localization.LocalizationService;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class MessageManager {

    private final FotiaChat plugin;
    private final LocalizationService localization;

    public MessageManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.localization = new LocalizationService(plugin);
    }

    public void loadMessages() {
        ConfigManager config = plugin.getConfigManager();
        localization.load(
                config.getLanguage(),
                config.isUseClientLocale(),
                config.isWarnMissingTranslation()
        );
    }

    public String getRaw(String key) {
        return localization.translateDefault(key);
    }

    public String getRaw(Player player, String key) {
        return localization.translate(player, key);
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        return replacePlaceholders(getRaw(key), placeholders);
    }

    public String getRaw(Player player, String key, Map<String, String> placeholders) {
        return replacePlaceholders(getRaw(player, key), placeholders);
    }

    public Component get(String key) {
        return MessageUtil.parse(getRaw(key));
    }

    public Component get(String key, Map<String, String> placeholders) {
        return MessageUtil.parse(getRaw(key, placeholders));
    }

    public Component get(String key, Player player) {
        return MessageUtil.parse(getRaw(player, key), player);
    }

    public Component get(String key, Player player, Map<String, String> placeholders) {
        return MessageUtil.parse(getRaw(player, key, placeholders), player);
    }

    public void send(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            player.sendMessage(get(key, player));
        } else {
            sender.sendMessage(get(key));
        }
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            player.sendMessage(get(key, player, placeholders));
        } else {
            sender.sendMessage(get(key, placeholders));
        }
    }

    public String getLocale(Player player) {
        return localization.locale(player);
    }

    public LocalizationService getLocalization() {
        return localization;
    }

    public String resolveConfigured(Player player, String text) {
        if (text == null || !text.startsWith("lang:")) {
            return text == null ? "" : text;
        }
        return getRaw(player, text.substring("lang:".length()));
    }

    public String resolveConfigured(String text) {
        if (text == null || !text.startsWith("lang:")) {
            return text == null ? "" : text;
        }
        return getRaw(text.substring("lang:".length()));
    }

    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
