package gg.fotia.chat.filter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Pattern;

/**
 * 过滤规则数据类
 */
public class FilterRule {

    /**
     * 替换方式
     */
    public enum ReplaceMode {
        /**
         * 只替换敏感词
         */
        REPLACE_WORD,

        /**
         * 替换整条消息
         */
        REPLACE_MESSAGE,

        /**
         * 阻止发送
         */
        BLOCK
    }

    private final String id;
    private final String pattern;
    private final FilterType type;
    private final ReplaceMode replaceMode;
    private final String replacement;
    private final boolean caseSensitive;
    private Pattern compiledPattern;
    private Pattern literalPattern;

    public FilterRule(String id, String pattern, FilterType type, ReplaceMode replaceMode,
                      String replacement, boolean caseSensitive) {
        this.id = id;
        this.pattern = pattern;
        this.type = type;
        this.replaceMode = replaceMode;
        this.replacement = replacement;
        this.caseSensitive = caseSensitive;
        compilePatterns();
    }

    private void compilePatterns() {
        if (type == FilterType.REGEX) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            this.compiledPattern = Pattern.compile(pattern, flags);
        } else if (type == FilterType.CONTAINS) {
            // 预编译字面量匹配，避免组件替换路径上每条消息重复 Pattern.compile
            this.literalPattern = Pattern.compile(Pattern.quote(pattern),
                    caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
    }

    public String getId() {
        return id;
    }

    public String getPattern() {
        return pattern;
    }

    public FilterType getType() {
        return type;
    }

    public ReplaceMode getReplaceMode() {
        return replaceMode;
    }

    public String getReplacement() {
        return replacement;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * 检查消息是否匹配此规则
     */
    public boolean matches(String message) {
        return switch (type) {
            case EXACT -> caseSensitive ? message.equals(pattern) : message.equalsIgnoreCase(pattern);
            case CONTAINS -> caseSensitive ? message.contains(pattern) : containsIgnoreCase(message, pattern);
            case REGEX -> compiledPattern.matcher(message).find();
        };
    }

    /**
     * 无分配的忽略大小写包含检查（热路径，避免每条消息 toLowerCase 拷贝）。
     */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        int max = haystack.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理消息
     * @return 处理后的消息，如果返回null表示消息被阻止
     */
    public String process(String message) {
        if (!matches(message)) {
            return message;
        }

        return switch (replaceMode) {
            case BLOCK -> null;
            case REPLACE_MESSAGE -> replacement;
            case REPLACE_WORD -> replaceWord(message);
        };
    }

    /**
     * 过滤组件文本，同时保留未命中部分的字体、悬浮和点击样式。
     */
    public Component process(Component message) {
        if (message == null) {
            return null;
        }
        return process(message, PlainTextComponentSerializer.plainText().serialize(message));
    }

    /**
     * 过滤组件文本；纯文本由调用方提供，避免每条规则重复序列化整棵组件树。
     */
    public Component process(Component message, String plainText) {
        if (message == null) {
            return null;
        }
        if (!matches(plainText)) {
            return message;
        }
        return switch (replaceMode) {
            case BLOCK -> null;
            case REPLACE_MESSAGE -> Component.text(replacement).style(message.style());
            case REPLACE_WORD -> replaceWord(message);
        };
    }

    private Component replaceWord(Component message) {
        if (type == FilterType.EXACT) {
            return Component.text(replacement).style(message.style());
        }

        Pattern replacementPattern = type == FilterType.REGEX ? compiledPattern : literalPattern;
        return message.replaceText(builder -> builder
                .match(replacementPattern)
                .replacement((match, matchedBuilder) -> {
                    String replacementText = replacement;
                    if (type == FilterType.REGEX) {
                        replacementText = compiledPattern.matcher(match.group()).replaceFirst(replacement);
                    }
                    return matchedBuilder.content(replacementText);
                }));
    }

    private String replaceWord(String message) {
        if (type == FilterType.REGEX) {
            return compiledPattern.matcher(message).replaceAll(replacement);
        }

        if (caseSensitive) {
            return message.replace(pattern, replacement);
        }

        // 不区分大小写的替换
        StringBuilder result = new StringBuilder();
        String lowerMessage = message.toLowerCase();
        String lowerPattern = pattern.toLowerCase();
        int lastEnd = 0;
        int index;

        while ((index = lowerMessage.indexOf(lowerPattern, lastEnd)) != -1) {
            result.append(message, lastEnd, index);
            result.append(replacement);
            lastEnd = index + pattern.length();
        }
        result.append(message.substring(lastEnd));

        return result.toString();
    }
}
