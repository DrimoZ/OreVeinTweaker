package dev.drimoz.oreveintweaker;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class VeinConfig {
    // Past ~3 the gain flattens: most of the underground is already vein. 4.5 is where the measured
    // table (VeinRules.SHARE) still resolves the toggle; ore_amount 5 and raw 50 are where the formulas
    // in VeinRules.place reach 100% ore / 98% raw, so more would change nothing.
    public static final double MAX_SIZE = 4.5;
    public static final double MAX_ORE_AMOUNT = 5.0;
    public static final double MAX_RAW_AMOUNT = 50.0;
    // Vanilla's vein noises are only defined over Y -60..50: an extra vein is that slice, moved.
    public static final int MAX_EXTRA_HEIGHT = 110;

    public static final ModConfigSpec SPEC;
    public static final Vein COPPER;
    public static final Vein IRON;
    public static final Extra[] EXTRA;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CENSUS_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIP_KEEP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRIP_REMOVE;
    public static final ModConfigSpec.BooleanValue STRIP_KEEP_ORES;
    public static final ModConfigSpec.BooleanValue STRIP_KEEP_STORAGE;
    public static final ModConfigSpec.BooleanValue STRIP_KEEP_BEDROCK;
    public static final ModConfigSpec.IntValue STRIP_MIN_Y;
    public static final ModConfigSpec.IntValue STRIP_MAX_Y;
    public static final ModConfigSpec.IntValue STRIP_RADIUS;

    public record Vein(ModConfigSpec.BooleanValue enabled,
                       ModConfigSpec.DoubleValue size,
                       ModConfigSpec.DoubleValue oreAmount,
                       ModConfigSpec.DoubleValue rawBlockAmount,
                       ModConfigSpec.ConfigValue<String> ore,
                       ModConfigSpec.ConfigValue<String> rawBlock,
                       ModConfigSpec.ConfigValue<String> filler) {}

    public record Extra(Vein vein, ModConfigSpec.IntValue minY, ModConfigSpec.IntValue maxY) {}

    static {
        var b = new ModConfigSpec.Builder();
        COPPER = vein(b, "copper", "Large copper veins: granite with copper ore, between Y 0 and 50.",
                true, "minecraft:copper_ore", "minecraft:raw_copper_block", "minecraft:granite");
        b.pop();
        IRON = vein(b, "iron", "Large iron veins: tuff with deepslate iron ore, between Y -60 and -8.",
                true, "minecraft:deepslate_iron_ore", "minecraft:raw_iron_block", "minecraft:tuff");
        b.pop();
        // ponytail: three fixed slots, not a list - the config has no usable list of tables, and
        // each extra vein costs one more noise sample per underground block.
        EXTRA = new Extra[]{
                extra(b, 1, "minecraft:deepslate_gold_ore", "minecraft:raw_gold_block", "minecraft:smooth_basalt", -60, -10),
                extra(b, 2, "minecraft:coal_ore", "minecraft:coal_block", "minecraft:andesite", 0, 60),
                extra(b, 3, "minecraft:deepslate_redstone_ore", "minecraft:redstone_block", "minecraft:calcite", -60, -20)};
        CENSUS_BLOCKS = b.comment("Extra blocks counted by /oreveintweaker census, next to every ore and raw/storage block.",
                        "Any block id, from any mod, e.g. [\"minecraft:andesite\", \"minecraft:tuff\"].")
                .push("census").defineListAllowEmpty("blocks", List.of(), o -> o instanceof String);
        b.pop();
        b.comment("/oreveintweaker strip: clears everything but the ores around you, to look at the veins.",
                "Lists take block ids from any mod, or a tag with a #: [\"minecraft:tuff\", \"#forge:ores/gold\"].",
                "The commands `strip keep add` and `strip remove add` edit them for you.").push("strip");
        STRIP_KEEP = b.comment("Blocks strip leaves in place, on top of the rules below.")
                .defineListAllowEmpty("keep", List.of(), o -> o instanceof String);
        STRIP_REMOVE = b.comment("Blocks strip always removes, even ores and storage blocks: this list wins over `keep`.")
                .defineListAllowEmpty("remove", List.of(), o -> o instanceof String);
        STRIP_KEEP_ORES = b.comment("Keep every block of the ores tag (any mod's ores).")
                .define("keep_ores", true);
        STRIP_KEEP_STORAGE = b.comment("Keep every storage block, which includes the raw ore blocks.")
                .define("keep_storage_blocks", true);
        STRIP_KEEP_BEDROCK = b.comment("Keep bedrock.").define("keep_bedrock", true);
        STRIP_MIN_Y = b.comment("Lowest Y strip clears (the bottom of the world if lower).")
                .defineInRange("min_y", -2032, -2032, 2031);
        STRIP_MAX_Y = b.comment("Highest Y strip clears. `strip <radius> <min_y> <max_y>` overrides both for one run.")
                .defineInRange("max_y", 64, -2032, 2031);
        STRIP_RADIUS = b.comment("Radius in chunks when the command gives none.")
                .defineInRange("default_radius", 2, 0, 16);
        b.pop();
        SPEC = b.build();
    }

    private VeinConfig() {}

    private static Extra extra(ModConfigSpec.Builder b, int n, String ore, String raw, String filler,
                               int minY, int maxY) {
        var vein = vein(b, "extra_" + n, "A new kind of large vein, off by default: shaped like the vanilla ones,"
                + " made of the blocks below, between min_y and max_y.", false, ore, raw, filler);
        var extra = new Extra(vein,
                b.comment("Lowest Y of the vein.").defineInRange("min_y", minY, -2032, 2031),
                b.comment("Highest Y of the vein, at most " + MAX_EXTRA_HEIGHT + " above min_y.",
                        "Veins thin out over the 20 blocks at each end, as vanilla ones do.")
                        .defineInRange("max_y", maxY, -2032, 2031));
        b.pop();
        return extra;
    }

    /** Leaves the builder inside the vein's section, so the caller can add keys before popping it. */
    private static Vein vein(ModConfigSpec.Builder b, String name, String title, boolean enabled,
                             String ore, String raw, String filler) {
        b.comment(title,
                "Every change only applies to chunks generated afterwards: on an existing world,",
                "the border with older chunks will be visible.").push(name);
        return new Vein(
                b.comment("false = this vein never generates (plain stone/deepslate instead).",
                                "true  = it generates, shaped by the settings below.")
                        .define("enabled", enabled),
                b.comment("How much of the underground is vein. 1.0 = vanilla.",
                                "2.0 = about twice the vein volume (thicker and more common), 0.5 = about half,",
                                "0 = none at all. Bigger veins are also slightly richer at their core.")
                        .defineInRange("size", 1.0, 0.0, MAX_SIZE),
                b.comment("How much of the vein is ore. 1.0 = vanilla (about 1 ore for 4 filler blocks).",
                                "0 = filler only, 2.0 = twice as much ore, 5.0 = no filler at all, only ore.")
                        .defineInRange("ore_amount", 1.0, 0.0, MAX_ORE_AMOUNT),
                b.comment("How many raw ore blocks the vein holds. 1.0 = vanilla (about 2 per 100 ore).",
                                "0 = none, 5.0 = five times as many, 50.0 = nearly every ore is a raw block.")
                        .defineInRange("raw_block_amount", 1.0, 0.0, MAX_RAW_AMOUNT),
                blockId(b.comment("Block used as the vein's ore. Any block id, from any mod.",
                        "Several blocks make a palette: \"minecraft:copper_ore*3, create:zinc_ore\" is 3 copper for 1 zinc."), "ore", ore),
                blockId(b.comment("Block used as the vein's raw ore block (a palette works here too)."), "raw_block", raw),
                blockId(b.comment("Block the ore sits in (a palette works here too)."), "filler", filler));
    }

    private static ModConfigSpec.ConfigValue<String> blockId(ModConfigSpec.Builder b, String key, String def) {
        return b.define(key, def, Palette::valid);
    }
}
