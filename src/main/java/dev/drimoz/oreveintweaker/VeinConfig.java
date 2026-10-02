package dev.drimoz.oreveintweaker;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class VeinConfig {
    public static final double MAX_SIZE = 3.0;

    public static final ForgeConfigSpec SPEC;
    public static final Vein COPPER;
    public static final Vein IRON;

    public record Vein(ForgeConfigSpec.BooleanValue enabled,
                       ForgeConfigSpec.DoubleValue size,
                       ForgeConfigSpec.DoubleValue oreAmount,
                       ForgeConfigSpec.DoubleValue rawBlockAmount,
                       ForgeConfigSpec.ConfigValue<String> ore,
                       ForgeConfigSpec.ConfigValue<String> rawBlock,
                       ForgeConfigSpec.ConfigValue<String> filler) {}

    static {
        var b = new ForgeConfigSpec.Builder();
        COPPER = vein(b, "copper", "Large copper veins: granite with copper ore, between Y 0 and 50.",
                "minecraft:copper_ore", "minecraft:raw_copper_block", "minecraft:granite");
        IRON = vein(b, "iron", "Large iron veins: tuff with deepslate iron ore, between Y -60 and -8.",
                "minecraft:deepslate_iron_ore", "minecraft:raw_iron_block", "minecraft:tuff");
        SPEC = b.build();
    }

    private VeinConfig() {}

    private static Vein vein(ForgeConfigSpec.Builder b, String name, String title,
                             String ore, String raw, String filler) {
        b.comment(title,
                "Every change only applies to chunks generated afterwards: on an existing world,",
                "the border with older chunks will be visible.").push(name);
        var vein = new Vein(
                b.comment("false = this vein never generates (plain stone/deepslate instead).",
                                "true  = it generates, shaped by the settings below.")
                        .define("enabled", true),
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
        b.pop();
        return vein;
    }

    private static ForgeConfigSpec.ConfigValue<String> blockId(ForgeConfigSpec.Builder b, String key, String def) {
        return b.define(key, def, o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
    }
}
