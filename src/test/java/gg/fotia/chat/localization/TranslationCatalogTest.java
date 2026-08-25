package gg.fotia.chat.localization;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationCatalogTest {

    @Test
    void resolvesCurrentLocaleBeforeFallbackCatalogs() {
        TranslationCatalog catalog = catalog();

        assertEquals("English", catalog.translate("en_US", "greeting"));
    }

    @Test
    void fallsBackToDefaultThenSimplifiedChineseThenKey() {
        TranslationCatalog catalog = catalog();

        assertEquals("Default only", catalog.translate("ja_JP", "default-only"));
        assertEquals("仅中文", catalog.translate("ja_JP", "chinese-only"));
        assertEquals("missing.key", catalog.translate("ja_JP", "missing.key"));
    }

    private TranslationCatalog catalog() {
        return new TranslationCatalog(Map.of(
                "zh_CN", Map.of("greeting", "中文", "chinese-only", "仅中文"),
                "en_US", Map.of("greeting", "English", "default-only", "Default only"),
                "ja_JP", Map.of()
        ), "en_US");
    }
}
