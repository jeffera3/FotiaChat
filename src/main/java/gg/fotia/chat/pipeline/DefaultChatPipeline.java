package gg.fotia.chat.pipeline;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.api.ChatPipeline;
import gg.fotia.chat.api.ChatPipelineContext;
import gg.fotia.chat.api.ChatPipelineResult;
import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.format.CraftEngineHandler;
import gg.fotia.chat.util.ComponentTextTransformer;
import gg.fotia.chat.util.LegacyColorConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * 与公聊一致地执行禁言、图片权限、过滤、颜色、emoji 与物品组件处理。
 */
public final class DefaultChatPipeline implements ChatPipeline {

    private static final String IMAGE_PERMISSION = "fotiachat.image";
    private static final String INLINE_COLOR_PERMISSION = "fotiachat.color.format";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final FotiaChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public DefaultChatPipeline(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public ChatPipelineResult process(Player sender, Component input, ChatPipelineContext context) {
        if (plugin.getMuteManager().isMuted(sender)) {
            return ChatPipelineResult.rejected("chat.muted");
        }
        Component working = input == null ? Component.empty() : input;
        if (!sender.hasPermission(IMAGE_PERMISSION)
                && CraftEngineHandler.containsImageTag(PLAIN.serialize(working))) {
            working = CraftEngineHandler.stripImageTags(working);
        }

        boolean rich = ComponentTextTransformer.containsFont(working);
        String plain = PLAIN.serialize(working);
        if (!sender.hasPermission("fotiachat.filter.bypass")) {
            if (rich) {
                Component filtered = plugin.getFilterManager().filter(working);
                if (filtered == null) {
                    return ChatPipelineResult.rejected("filter.blocked");
                }
                working = filtered;
            } else {
                String filtered = plugin.getFilterManager().filter(plain);
                if (filtered == null) {
                    return ChatPipelineResult.rejected("filter.blocked");
                }
                plain = filtered;
            }
        }

        ChatColor color = plugin.getColorManager().getPlayerColor(sender);
        Component prepared;
        if (rich) {
            prepared = plugin.getChatFormatter().prepareMessage(sender, working, color);
        } else {
            boolean allowInlineColors = plugin.getConfigManager().isAllowColorCodes()
                    && sender.hasPermission(INLINE_COLOR_PERMISSION);
            String safe = allowInlineColors
                    ? LegacyColorConverter.convertToMiniMessage(plain)
                    : plain.replace("\\", "\\\\").replace("<", "\\<");
            prepared = plugin.getChatFormatter().prepareMessage(sender, safe, color);
        }
        if (PLAIN.serialize(prepared).isBlank()) {
            return ChatPipelineResult.rejected("chat.empty-message");
        }
        return ChatPipelineResult.accepted(prepared);
    }
}
