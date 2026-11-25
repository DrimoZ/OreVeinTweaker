package dev.drimoz.oreveintweaker;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mod's texts in the player's language. Done on the server, from the language the client reports: the mod
 * is server-side only (no resource pack, no packet), so a lang file in assets/ would never reach a vanilla
 * client. The files are UTF-8 .properties in /oreveintweaker_lang/, English is the fallback key by key.
 * Keys starting with "page." are the exported web page's: they travel inside it, and the browser picks.
 * No Minecraft class in here, so plain JUnit tests cover it.
 */
final class Lang {
    static final List<String> CODES = List.of("en", "fr", "es", "de", "pt", "ru", "zh");
    private static final String PAGE = "page.";
    private static final Pattern HOLE = Pattern.compile("\\{(\\d+)}");
    private static final Map<String, Properties> LOADED = new ConcurrentHashMap<>();

    private Lang() {}

    /** "fr_fr", "pt_br", "zh_tw" -> the two letters we have a file for; anything else, "en". */
    static String code(String minecraft) {
        if (minecraft == null || minecraft.length() < 2) return "en";
        String c = minecraft.substring(0, 2).toLowerCase(Locale.ROOT);
        return CODES.contains(c) ? c : "en";
    }

    static Properties table(String code) {
        return LOADED.computeIfAbsent(code, c -> {
            Properties p = new Properties();
            try (var in = Lang.class.getResourceAsStream("/oreveintweaker_lang/" + c + ".properties")) {
                if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                // A missing table only means English text: LangTest fails if one is really missing.
            }
            return p;
        });
    }

    /** {@code {0}}, {@code {1}}... are replaced by the arguments; a hole with no argument stays as written. */
    static String tr(String minecraftLanguage, String key, Object... args) {
        String text = table(code(minecraftLanguage)).getProperty(key);
        if (text == null) text = table("en").getProperty(key, key);
        return fill(text, args);
    }

    static String fill(String text, Object... args) {
        Matcher m = HOLE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(i < args.length ? String.valueOf(args[i]) : m.group()));
        }
        return m.appendTail(out).toString();
    }

    /** The page's texts of one language, without the "page." prefix. */
    static Map<String, String> page(String code) {
        Map<String, String> out = new TreeMap<>();
        table(code).forEach((k, v) -> {
            String key = (String) k;
            if (key.startsWith(PAGE)) out.put(key.substring(PAGE.length()), (String) v);
        });
        return out;
    }
}
