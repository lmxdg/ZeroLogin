package dev.zerologin.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 语言标签解析规则的单元测试。
 *
 * <p>只覆盖不依赖 Bukkit 的静态方法，因此无需服务端即可运行。
 */
class MessagesResolveTest {

    @Test
    void supportsExactlySevenLanguages() {
        assertEquals(List.of("en_us", "zh_cn", "zh_tw", "ja_jp", "ko_kr", "ru_ru", "es_es"),
                Messages.SUPPORTED_LANGUAGES);
    }

    @Test
    void acceptsSupportedTagsVerbatim() {
        for (String lang : Messages.SUPPORTED_LANGUAGES) {
            assertEquals(lang, Messages.resolve(lang, "en_us"));
            assertTrue(Messages.isSupported(lang));
        }
    }

    @Test
    void normalizesCaseAndSeparator() {
        assertEquals("zh_cn", Messages.resolve("zh-CN", "en_us"));
        assertEquals("zh_cn", Messages.resolve("ZH_CN", "en_us"));
        assertEquals("en_us", Messages.resolve("EN_us", "zh_cn"));
        assertEquals("ja_jp", Messages.resolve("ja-JP", "en_us"));
    }

    @Test
    void mapsLanguageWithoutRegion() {
        assertEquals("en_us", Messages.resolve("en", "zh_cn"));
        assertEquals("ja_jp", Messages.resolve("ja", "zh_cn"));
        assertEquals("ko_kr", Messages.resolve("ko", "zh_cn"));
        assertEquals("ru_ru", Messages.resolve("ru", "zh_cn"));
        assertEquals("es_es", Messages.resolve("es", "zh_cn"));
    }

    @Test
    void mapsRegionalVariants() {
        assertEquals("es_es", Messages.resolve("es_mx", "en_us"));
        assertEquals("es_es", Messages.resolve("es_419", "en_us"));
        assertEquals("zh_tw", Messages.resolve("zh_tw", "en_us"));
        assertEquals("zh_tw", Messages.resolve("zh_hk", "en_us"));
        assertEquals("zh_tw", Messages.resolve("zh_mo", "en_us"));
        assertEquals("zh_tw", Messages.resolve("zh_hant", "en_us"));
        assertEquals("zh_cn", Messages.resolve("zh_sg", "en_us"));
    }

    @Test
    void unknownLanguageFallsBack() {
        assertEquals("en_us", Messages.resolve("de_de", "en_us"));
        assertEquals("zh_cn", Messages.resolve("fr_fr", "zh_cn"));
        assertFalse(Messages.isSupported("de_de"));
        assertFalse(Messages.isSupported("fr_fr"));
    }

    @Test
    void blankAndAutoReturnFallback() {
        assertEquals("en_us", Messages.resolve(null, "en_us"));
        assertEquals("en_us", Messages.resolve("  ", "en_us"));
        assertEquals("en_us", Messages.resolve(Messages.AUTO, "en_us"));
    }

    @Test
    void normalizeDefaultsToSimplifiedChinese() {
        assertEquals("zh_cn", Messages.normalize(null));
        assertEquals("zh_cn", Messages.normalize("de_de"));
        assertEquals("ja_jp", Messages.normalize("ja_jp"));
    }
}
