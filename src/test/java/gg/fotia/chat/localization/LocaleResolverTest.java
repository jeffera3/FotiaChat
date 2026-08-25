package gg.fotia.chat.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleResolverTest {

    @Test
    void mapsMinecraftRegionalLocalesToSupportedCatalogs() {
        LocaleResolver resolver = new LocaleResolver("zh_CN", true);

        assertEquals("zh_CN", resolver.resolve("zh_sg"));
        assertEquals("zh_TW", resolver.resolve("zh_hk"));
        assertEquals("zh_TW", resolver.resolve("zh_hant"));
        assertEquals("zh_CN", resolver.resolve("zh_hans"));
        assertEquals("ja_JP", resolver.resolve("ja_jp"));
        assertEquals("ko_KR", resolver.resolve("ko_kr"));
        assertEquals("en_US", resolver.resolve("en_gb"));
        assertEquals("fr_FR", resolver.resolve("fr_ca"));
        assertEquals("de_DE", resolver.resolve("de_at"));
        assertEquals("it_IT", resolver.resolve("it_ch"));
        assertEquals("pt_PT", resolver.resolve("pt_br"));
        assertEquals("es_ES", resolver.resolve("es_mx"));
    }

    @Test
    void usesConfiguredDefaultWhenClientLocaleIsDisabledOrUnsupported() {
        assertEquals("en_US", new LocaleResolver("en_US", false).resolve("ja_jp"));
        assertEquals("fr_FR", new LocaleResolver("fr_FR", true).resolve("ru_ru"));
    }

    @Test
    void invalidDefaultFallsBackToSimplifiedChinese() {
        assertEquals("zh_CN", new LocaleResolver("ru_RU", true).defaultLocale());
    }
}
