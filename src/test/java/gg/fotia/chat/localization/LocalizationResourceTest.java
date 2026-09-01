package gg.fotia.chat.localization;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourceTest {

    private static final Pattern TOKENS = Pattern.compile("\\{[a-zA-Z0-9_-]+}|%[a-zA-Z0-9_:-]+%");

    @Test
    void everySupportedLocaleHasTheSameTranslationKeysAndNoBom() throws Exception {
        Path directory = Path.of("src/main/resources/locales");
        YamlConfiguration reference = YamlConfiguration.loadConfiguration(directory.resolve("zh_CN.yml").toFile());
        Set<String> expectedKeys = stringKeys(directory.resolve("zh_CN.yml").toFile());

        for (String locale : LocaleResolver.supportedLocales()) {
            Path path = directory.resolve(locale + ".yml");
            assertTrue(Files.isRegularFile(path), () -> "Missing locale file: " + locale);
            byte[] bytes = Files.readAllBytes(path);
            assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xEF
                    && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF,
                    () -> "UTF-8 BOM is not allowed: " + locale);
            assertEquals(expectedKeys, stringKeys(path.toFile()), () -> "Translation keys differ: " + locale);
            YamlConfiguration translated = YamlConfiguration.loadConfiguration(path.toFile());
            for (String key : expectedKeys) {
                assertEquals(tokens(reference.getString(key, "")), tokens(translated.getString(key, "")),
                        () -> "Formatting tokens differ in " + locale + ": " + key);
            }
        }
    }

    @Test
    void everyConfiguredLanguageKeyExistsInSimplifiedChineseCatalog() {
        YamlConfiguration catalog = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/locales/zh_CN.yml").toFile());
        List<Path> configs = List.of(
                Path.of("src/main/resources/channels.yml"),
                Path.of("src/main/resources/colors.yml"),
                Path.of("src/main/resources/announcements.yml"),
                Path.of("src/main/resources/menus/color-menu.yml"),
                Path.of("src/main/resources/menus/item-display.yml")
        );

        for (Path path : configs) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
            for (String pathKey : config.getKeys(true)) {
                String leaf = pathKey.substring(pathKey.lastIndexOf('.') + 1).toLowerCase();
                if (leaf.endsWith("-key") && config.isString(pathKey)) {
                    assertTrue(catalog.isString(config.getString(pathKey)),
                            () -> "Missing translation for " + path + ": " + config.getString(pathKey));
                } else if (leaf.endsWith("-keys") && config.isList(pathKey)) {
                    for (String translationKey : config.getStringList(pathKey)) {
                        assertTrue(catalog.isString(translationKey),
                                () -> "Missing translation for " + path + ": " + translationKey);
                    }
                }
            }
        }
    }

    @Test
    void bundledKeysFillMissingEntriesInExistingLocaleFiles() throws Exception {
        YamlConfiguration existing = new YamlConfiguration();
        existing.loadFromString("announcement:\n  sent: old value\n");
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new StringReader(
                Files.readString(Path.of("src/main/resources/locales/zh_CN.yml"), StandardCharsets.UTF_8)));
        java.util.Map<String, String> entries = LocalizationService.mergeCatalog(existing, bundled);

        assertEquals("<!i><red>控制台执行时必须指定在线玩家。",
                entries.get("announcement.test-player-required"));
    }

    private Set<String> stringKeys(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Set<String> keys = new TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> tokens(String value) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        Matcher matcher = TOKENS.matcher(value);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        tokens.sort(String::compareTo);
        return tokens;
    }
}
