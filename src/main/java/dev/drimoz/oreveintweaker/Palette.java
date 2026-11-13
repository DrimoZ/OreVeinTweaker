package dev.drimoz.oreveintweaker;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A weighted list of blocks, written as one string: {@code "minecraft:copper_ore*3, create:zinc_ore"}
 * (the weight after '*' is optional, 1 by default). A single block is the same string as before, and
 * draws nothing from the random: configs that do not use palettes generate exactly what they did.
 */
public final class Palette {
    private final BlockState[] states;
    private final double[] cumulative; // running weight total, last = total
    private final double total;

    private Palette(List<BlockState> states, List<Double> weights) {
        this.states = states.toArray(new BlockState[0]);
        this.cumulative = new double[weights.size()];
        double sum = 0;
        for (int i = 0; i < cumulative.length; i++) cumulative[i] = sum += weights.get(i);
        this.total = sum;
    }

    public BlockState pick(RandomSource random) {
        if (states.length == 1) return states[0];
        double x = random.nextDouble() * total;
        for (int i = 0; i < cumulative.length; i++) if (x < cumulative[i]) return states[i];
        return states[states.length - 1];
    }

    /** Config validator: every entry is a block id with an optional positive weight. */
    public static boolean valid(Object o) {
        if (!(o instanceof String s) || s.isBlank()) return false;
        for (String entry : s.split(",")) {
            if (entry.isBlank() || parseEntry(entry.trim()) == null) return false;
        }
        return true;
    }

    /** Unknown blocks are skipped with a warning; if none is left, {@code fallback} is the whole palette. */
    public static Palette parse(String spec, Block fallback) {
        List<BlockState> states = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String entry : spec.split(",")) {
            Entry e = parseEntry(entry.trim());
            var block = e == null ? java.util.Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(e.id());
            if (block.isPresent()) {
                states.add(block.get().defaultBlockState());
                weights.add(e.weight());
            } else {
                LogUtils.getLogger().warn("[{}] Unknown block '{}' in '{}'", OreVeinTweaker.MOD_ID, entry.trim(), spec);
            }
        }
        if (states.isEmpty()) {
            LogUtils.getLogger().warn("[{}] No known block in '{}', keeping {}", OreVeinTweaker.MOD_ID, spec,
                    BuiltInRegistries.BLOCK.getKey(fallback));
            return new Palette(List.of(fallback.defaultBlockState()), List.of(1.0));
        }
        return new Palette(states, weights);
    }

    private record Entry(Identifier id, double weight) {}

    private static Entry parseEntry(String entry) {
        int star = entry.indexOf('*');
        double weight = 1;
        if (star >= 0) {
            try {
                weight = Double.parseDouble(entry.substring(star + 1).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
            if (!(weight > 0)) return null;
        }
        Identifier id = Identifier.tryParse(star >= 0 ? entry.substring(0, star).trim() : entry);
        return id == null ? null : new Entry(id, weight);
    }
}
