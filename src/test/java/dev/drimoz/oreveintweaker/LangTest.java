package dev.drimoz.oreveintweaker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangTest {
    private static final Pattern HOLE = Pattern.compile("\\{(\\d+)}");

    private static Set<String> holes(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = HOLE.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyLanguageFileLoads() {
        for (String code : Lang.CODES) {
            assertFalse(Lang.table(code).isEmpty(), code + ".properties is missing or empty");
        }
    }

    @Test
    void everyLanguageHasExactlyTheEnglishKeys() {
        Set<String> english = Lang.table("en").stringPropertyNames();
        for (String code : Lang.CODES) {
            Set<String> keys = Lang.table(code).stringPropertyNames();
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(english);
            assertTrue(missing.isEmpty(), code + " lacks " + missing);
            assertTrue(extra.isEmpty(), code + " has keys English does not: " + extra);
        }
    }

    /** A swapped or lost {n} shows up in the game as a number in the wrong place, or none. */
    @Test
    void everyTextKeepsTheEnglishPlaceholders() {
        Properties en = Lang.table("en");
        for (String code : Lang.CODES) {
            Properties p = Lang.table(code);
            for (String key : en.stringPropertyNames()) {
                assertEquals(holes(en.getProperty(key)), holes(p.getProperty(key)), code + " / " + key);
            }
        }
    }

    @Test
    void noTextIsEmptyBrokenOrLeftInEnglish() {
        Properties en = Lang.table("en");
        for (String code : Lang.CODES) {
            Properties p = Lang.table(code);
            for (String key : p.stringPropertyNames()) {
                String v = p.getProperty(key);
                assertFalse(v.isBlank(), code + " / " + key + " is empty");
                assertFalse(v.contains("�"), code + " / " + key + " has a broken character (not UTF-8?)");
                assertFalse(v.contains("\\"), code + " / " + key + " has a backslash");
            }
            if (code.equals("en")) continue;
            // Some texts are the same in every language (a lone "mods"); a copy of English is a file nobody translated.
            long same = p.stringPropertyNames().stream().filter(k -> p.getProperty(k).equals(en.getProperty(k))).count();
            assertTrue(same < 12, code + " keeps " + same + " English texts");
        }
    }

    @Test
    void aMinecraftLanguageCodeFindsItsFile() {
        assertEquals("fr", Lang.code("fr_fr"));
        assertEquals("fr", Lang.code("fr_ca"));
        assertEquals("pt", Lang.code("pt_br"));
        assertEquals("zh", Lang.code("zh_tw"));
        assertEquals("ru", Lang.code("RU_RU"));
        assertEquals("en", Lang.code("ja_jp")); // no file: English
        assertEquals("en", Lang.code(null));    // the console
        assertEquals("en", Lang.code(""));
        assertEquals("en", Lang.code("f"));
    }

    @Test
    void holesAreFilledByTheirNumber() {
        assertEquals("b a b", Lang.fill("{1} {0} {1}", "a", "b"));
        assertEquals("100% $1 \\ ok", Lang.fill("{0} ok", "100% $1 \\")); // a value is never read as a pattern
        assertEquals("{1}", Lang.fill("{1}", "only"));                    // no argument: left as written, no crash
        assertEquals("{0}", Lang.fill("{0}", "{0}"));                     // a value that looks like a hole stays as it is
    }

    @Test
    void anUnknownLanguageOrKeyFallsBack() {
        assertEquals("Previous page", Lang.tr("ja_jp", "tip_prev"));
        assertEquals("no_such_key", Lang.tr("fr_fr", "no_such_key"));
    }

    @Test
    void textsComeOutInTheAskedLanguage() {
        assertEquals("Page précédente", Lang.tr("fr_fr", "tip_prev"));
        assertEquals("Les 4 ensemble : 8,862 blocs, 12/chunk, 3.1% des minerais",
                Lang.tr("fr_fr", "group_total", 4, "8,862", "12", "3.1%"));
        assertEquals("All 4 together: 8,862 blocks, 12/chunk, 3.1% of ores",
                Lang.tr(null, "group_total", 4, "8,862", "12", "3.1%"));
    }

    @Test
    void thePageTextsAreTheKeysWithThePagePrefix() {
        for (String code : Lang.CODES) {
            long expected = Lang.table(code).stringPropertyNames().stream().filter(k -> k.startsWith("page.")).count();
            assertEquals(expected, Lang.page(code).size(), code);
            assertTrue(Lang.page(code).keySet().stream().noneMatch(k -> k.startsWith("page.")), code);
        }
        assertTrue(Lang.page("en").containsKey("grouped"));
        assertTrue(Lang.page("zh").containsKey("make_group"));
    }

    /** The keys the page's script asks for: one renamed in the page and not here would show as its own name. */
    @Test
    void everyKeyThePageAsksForExists() throws Exception {
        String html;
        try (var in = LangTest.class.getResourceAsStream("/census.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> asked = new TreeSet<>();
        Matcher m = Pattern.compile("\\bt\\(\\s*\"(\\w+)\"|data-t=\"(\\w+)\"|\"(sum_one|lead_share|lead)\"|\\? \"(sum_one)\" : \"(sum)\"").matcher(html);
        while (m.find()) for (int i = 1; i <= m.groupCount(); i++) if (m.group(i) != null) asked.add(m.group(i));
        asked.addAll(Set.of("sum", "sum_one", "lead", "lead_share")); // chosen in code, so not found by the scan
        assertTrue(asked.size() > 20, "the scan of census.html found only " + asked);
        for (String code : Lang.CODES) {
            Set<String> have = Lang.page(code).keySet();
            for (String key : asked) assertTrue(have.contains(key), code + " has no page." + key);
        }
    }
}
