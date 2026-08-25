package gg.fotia.chat.localization;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TranslationCatalog {

    private final Map<String, Map<String, String>> translations;
    private final String defaultLocale;

    public TranslationCatalog(Map<String, Map<String, String>> translations, String defaultLocale) {
        Map<String, Map<String, String>> catalogs = new LinkedHashMap<>();
        translations.forEach((locale, entries) ->
                catalogs.put(LocaleResolver.normalize(locale), Map.copyOf(entries)));
        this.translations = Map.copyOf(catalogs);
        this.defaultLocale = new LocaleResolver(defaultLocale, false).defaultLocale();
    }

    public String translate(String locale, String key) {
        String localized = lookup(LocaleResolver.normalize(locale), key);
        if (localized != null) {
            return localized;
        }
        localized = lookup(defaultLocale, key);
        if (localized != null) {
            return localized;
        }
        localized = lookup(LocaleResolver.FALLBACK_LOCALE, key);
        return localized == null ? key : localized;
    }

    public boolean contains(String locale, String key) {
        return lookup(LocaleResolver.normalize(locale), key) != null;
    }

    private String lookup(String locale, String key) {
        Map<String, String> catalog = translations.get(locale);
        return catalog == null ? null : catalog.get(key);
    }
}
