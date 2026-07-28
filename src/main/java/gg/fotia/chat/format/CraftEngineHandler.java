package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;

import java.util.regex.Pattern;

/**
 * CraftEngine integration boundary for image tags and decorated chat components.
 */
public class CraftEngineHandler {

    private static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("<image:[^>]+>");

    public CraftEngineHandler(FotiaChat plugin) {
        // CraftEngine decorates Paper chat components before FotiaChat handles chat events.
    }

    public String processImageTags(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        if (!MessageUtil.isCraftEngineEnabled()) {
            return stripImageTags(message);
        }

        return message;
    }

    public static boolean containsImageTag(String message) {
        return message != null && !message.isEmpty() && message.contains("<image:");
    }

    public static String stripImageTags(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return IMAGE_TAG_PATTERN.matcher(message).replaceAll("");
    }

    public static Component stripImageTags(Component component) {
        if (component == null) {
            return Component.empty();
        }
        return component.replaceText(builder -> builder.match(IMAGE_TAG_PATTERN).replacement(""));
    }
}
