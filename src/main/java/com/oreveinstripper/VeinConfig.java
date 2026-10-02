package com.oreveinstripper;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VeinConfig {
    public static final ModConfigSpec SPEC;
    public static final Vein COPPER;
    public static final Vein IRON;

    public record Vein(ModConfigSpec.BooleanValue enabled,
                       ModConfigSpec.ConfigValue<String> ore,
                       ModConfigSpec.ConfigValue<String> rawBlock,
                       ModConfigSpec.ConfigValue<String> filler) {}

    static {
        var b = new ModConfigSpec.Builder();
        COPPER = vein(b, "copper", "Large copper veins (Y 0 to 50)",
                "minecraft:copper_ore", "minecraft:raw_copper_block", "minecraft:granite");
        IRON = vein(b, "iron", "Large iron veins (Y -60 to -8)",
                "minecraft:deepslate_iron_ore", "minecraft:raw_iron_block", "minecraft:tuff");
        SPEC = b.build();
    }

    private VeinConfig() {}

    private static Vein vein(ModConfigSpec.Builder b, String name, String title,
                             String ore, String raw, String filler) {
        b.comment(title,
                "Only affects chunks generated after the change: on an existing world,",
                "the border with older chunks will be visible.").push(name);
        var vein = new Vein(
                b.comment("false = this vein no longer generates (base stone/deepslate instead)",
                        "true  = the vein generates using the blocks below").define("enabled", false),
                blockId(b.comment("Ore block of the vein"), "ore", ore),
                blockId(b.comment("Raw ore block scattered in the vein"), "raw_block", raw),
                blockId(b.comment("Filler block around the ore"), "filler", filler));
        b.pop();
        return vein;
    }

    private static ModConfigSpec.ConfigValue<String> blockId(ModConfigSpec.Builder b, String key, String def) {
        return b.define(key, def, o -> o instanceof String s && Identifier.tryParse(s) != null);
    }
}
