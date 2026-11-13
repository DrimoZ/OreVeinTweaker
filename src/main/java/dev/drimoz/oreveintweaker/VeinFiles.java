package dev.drimoz.oreveintweaker;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Veins of your own, one TOML file each in config/oreveintweaker/veins/: as many as you want, where the
 * config file itself only has three slots. The keys are the ones of an [extra_N] section.
 * A file that is wrong is skipped and reported, never half-applied; files starting with '_' are ignored.
 */
final class VeinFiles {
    record Spec(String name, boolean enabled, double size, double oreAmount, double rawAmount,
                String ore, String raw, String filler, int minY, int maxY) {}

    record Result(List<Spec> specs, List<String> errors) {}

    private static final String EXAMPLE = """
            # Copy this file, remove the leading '_' from the name, and edit it: one file = one kind of vein.
            # Run /oreveintweaker reload (or restart) to apply. Only chunks generated afterwards change.
            # Files starting with '_' are ignored, so this one never does anything.

            # false = this vein is ignored.
            enabled = true

            # Vein volume, 1.0 = as much as vanilla copper. Up to 3.0.
            size = 1.0
            # 1.0 = vanilla (about 1 ore for 4 filler blocks). 0 = filler only, 4 = mostly ore.
            ore_amount = 1.0
            # 1.0 = vanilla (about 2 raw blocks per 100 ore). 0 = none, up to 20.
            raw_block_amount = 1.0

            # Blocks. Any block id from any mod. Several blocks make a palette: "block*weight, block*weight",
            # the weight being 1 when left out. "minecraft:copper_ore*3, minecraft:iron_ore" is 3 copper for 1 iron.
            ore = "minecraft:emerald_ore*3, minecraft:diamond_ore"
            raw_block = "minecraft:raw_gold_block"
            filler = "minecraft:calcite"

            # Heights, at most 110 blocks apart. For a taller vein, use two files.
            min_y = -60
            max_y = 40
            """;

    private VeinFiles() {}

    static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(OreVeinTweaker.MOD_ID).resolve("veins");
    }

    static Result load() {
        List<Spec> specs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Path dir = dir();
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("_example.toml"), EXAMPLE);
            }
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".toml")
                        && !p.getFileName().toString().startsWith("_")).sorted().toList();
            }
            for (Path p : files) {
                String name = p.getFileName().toString().replaceFirst("\\.toml$", "");
                try (FileConfig c = FileConfig.of(p, TomlFormat.instance())) {
                    c.load();
                    specs.add(parse(name, c));
                } catch (Exception e) {
                    errors.add(name + ".toml: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("cannot read " + dir + ": " + e.getMessage());
        }
        errors.forEach(e -> LogUtils.getLogger().warn("[{}] vein file skipped, {}", OreVeinTweaker.MOD_ID, e));
        return new Result(specs, errors);
    }

    private static Spec parse(String name, Config c) {
        Object enabled = c.get("enabled");
        if (enabled != null && !(enabled instanceof Boolean)) throw new IllegalArgumentException("enabled must be true or false");
        int minY = integer(c, "min_y"), maxY = integer(c, "max_y");
        if (maxY < minY) throw new IllegalArgumentException("max_y is below min_y");
        if (maxY - minY > VeinConfig.MAX_EXTRA_HEIGHT) {
            LogUtils.getLogger().warn("[{}] {}.toml: max_y is more than {} above min_y, using {}", OreVeinTweaker.MOD_ID,
                    name, VeinConfig.MAX_EXTRA_HEIGHT, minY + VeinConfig.MAX_EXTRA_HEIGHT);
            maxY = minY + VeinConfig.MAX_EXTRA_HEIGHT;
        }
        return new Spec(name, enabled == null || (Boolean) enabled,
                num(c, "size", 1, 0, VeinConfig.MAX_SIZE), num(c, "ore_amount", 1, 0, 4),
                num(c, "raw_block_amount", 1, 0, 20),
                palette(c, "ore", true), palette(c, "raw_block", false), palette(c, "filler", true), minY, maxY);
    }

    private static double num(Config c, String key, double def, double min, double max) {
        Object v = c.get(key);
        if (v == null) return def;
        if (!(v instanceof Number n)) throw new IllegalArgumentException(key + " must be a number");
        if (n.doubleValue() < min || n.doubleValue() > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return n.doubleValue();
    }

    private static int integer(Config c, String key) {
        Object v = c.get(key);
        if (!(v instanceof Number n)) throw new IllegalArgumentException(key + " is required (a whole number)");
        if (n.intValue() < -2032 || n.intValue() > 2031) throw new IllegalArgumentException(key + " must be between -2032 and 2031");
        return n.intValue();
    }

    /** A string, or a list of strings, written as one palette; null when optional and absent. */
    private static String palette(Config c, String key, boolean required) {
        Object v = c.get(key);
        if (v == null) {
            if (required) throw new IllegalArgumentException(key + " is required");
            return null;
        }
        String s = v instanceof List<?> l ? String.join(",", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
        if (!Palette.valid(s)) throw new IllegalArgumentException(key + " is not a valid block (or \"block*weight, block\" list): " + s);
        return s;
    }
}
