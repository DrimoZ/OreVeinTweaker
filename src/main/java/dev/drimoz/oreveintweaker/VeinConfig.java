package dev.drimoz.oreveintweaker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VeinConfig {
    public static final double MAX_SIZE = 3.0;
    // Vanilla's vein noises are only defined over Y -60..50: an extra vein is that slice, moved.
    public static final int MAX_EXTRA_HEIGHT = 110;

    public static final ModConfigSpec SPEC;
    public static final Vein COPPER;
    public static final Vein IRON;
    public static final Extra[] EXTRA;

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
                                "0 = filler only, 2.0 = twice as much ore, 4.0 = mostly ore.")
                        .defineInRange("ore_amount", 1.0, 0.0, 4.0),
                b.comment("How many raw ore blocks the vein holds. 1.0 = vanilla (about 2 per 100 ore).",
                                "0 = none, 5.0 = five times as many.")
                        .defineInRange("raw_block_amount", 1.0, 0.0, 20.0),
                blockId(b.comment("Block used as the vein's ore. Any block id, from any mod."), "ore", ore),
                blockId(b.comment("Block used as the vein's raw ore block."), "raw_block", raw),
                blockId(b.comment("Block the ore sits in."), "filler", filler));
    }

    private static ModConfigSpec.ConfigValue<String> blockId(ModConfigSpec.Builder b, String key, String def) {
        return b.define(key, def, o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
    }
}
