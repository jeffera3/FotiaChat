package gg.fotia.chat.localization;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LocaleResolver {

    public static final String FALLBACK_LOCALE = "zh_CN";

    private static final Set<String> SUPPORTED = Set.of(
            "zh_CN", "zh_TW", "ja_JP", "ko_KR", "en_US",
            "fr_FR", "de_DE", "it_IT", "pt_PT", "es_ES"
    );
    private static final Map<String, String> LANGUAGE_DEFAULTS = Map.of(
            "zh", "zh_CN",
            "ja", "ja_JP",
            "ko", "ko_KR",
            "en", "en_US",
            "fr", "fr_FR",
            "de", "de_DE",
            "it", "it_IT",
            "pt", "pt_PT",
            "es", "es_ES"
    );

    private final String defaultLocale;
    private final boolean useClientLocale;

    public LocaleResolver(String defaultLocale, boolean useClientLocale) {
        this.defaultLocale = canonicalSupported(defaultLocale);
        this.useClientLocale = useClientLocale;
    }

    public String resolve(String clientLocale) {
        if (!useClientLocale) {
            return defaultLocale;
        }

        String normalized = normalize(clientLocale);
        if (normalized.isEmpty()) {
            return defaultLocale;
        }
        if (normalized.equals("zh_HK") || normalized.equals("zh_MO") || normalized.equals("zh_HANT")) {
            return "zh_TW";
        }
        if (normalized.equals("zh_SG") || normalized.equals("zh_HANS")) {
            return "zh_CN";
        }
        if (SUPPORTED.contains(normalized)) {
            return normalized;
        }

        int separator = normalized.indexOf('_');
        String language = separator < 0 ? normalized : normalized.substring(0, separator);
        return LANGUAGE_DEFAULTS.getOrDefault(language, defaultLocale);
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public static Set<String> supportedLocales() {
        return SUPPORTED;
    }

    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return "";
        }
        String[] parts = locale.trim().replace('-', '_').split("_", 3);
        String language = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 1 || parts[1].isBlank()) {
            return language;
        }
        return language + "_" + parts[1].toUpperCase(Locale.ROOT);
    }

    private static String canonicalSupported(String locale) {
        String normalized = normalize(locale);
        return SUPPORTED.contains(normalized) ? normalized : FALLBACK_LOCALE;
    }
}
