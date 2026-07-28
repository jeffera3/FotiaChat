package gg.fotia.chat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 在保留 Adventure 样式树的前提下处理文本内容。
 */
public final class ComponentTextTransformer {

    private ComponentTextTransformer() {
    }

    public static Component slice(Component source, int startInclusive, int endExclusive) {
        if (source == null || endExclusive <= startInclusive) {
            return Component.empty();
        }
        int start = Math.max(0, startInclusive);
        int end = Math.max(start, endExclusive);
        return sliceNode(source, start, end, new Cursor());
    }

    public static Component replaceLiteral(Component source, String literal, ComponentLike replacement) {
        if (source == null || literal == null || literal.isEmpty()) {
            return source == null ? Component.empty() : source;
        }
        return source.replaceText(builder -> builder.matchLiteral(literal).replacement(replacement));
    }

    public static boolean containsFont(Component component) {
        if (component == null) {
            return false;
        }
        if (component.style().font() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsFont(child)) {
                return true;
            }
        }
        return false;
    }

    private static Component sliceNode(Component source, int start, int end, Cursor cursor) {
        Component result = source;
        if (source instanceof TextComponent textComponent) {
            String content = textComponent.content();
            int contentStart = cursor.position;
            int contentEnd = contentStart + content.length();
            int from = Math.max(start, contentStart) - contentStart;
            int to = Math.min(end, contentEnd) - contentStart;
            String retained = from < to ? content.substring(from, to) : "";
            result = textComponent.content(retained);
            cursor.position = contentEnd;
        }

        List<Component> children = new ArrayList<>(source.children().size());
        for (Component child : source.children()) {
            children.add(sliceNode(child, start, end, cursor));
        }
        return result.children(children);
    }

    private static final class Cursor {
        private int position;
    }
}
