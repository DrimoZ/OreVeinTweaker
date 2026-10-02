package dev.drimoz.oreveintweaker;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class VeinRules {
    private static final Logger LOG = LogUtils.getLogger();

    // Vanilla proportions, measured with /veins on fresh worlds: about 1 ore per 4 filler blocks and
    // 2 raw blocks per 100 ore. They turn "ore_amount = 2" into "twice the ore" rather than a guess.
    private static final double ORE_PER_FILLER = 0.25;
    private static final double RAW_PER_ORE = 0.02;

    // How much of the vein heights (Y -60..50) has |vein_toggle| >= threshold, measured with
    // DevTools.measureToggle over 200,000 samples. Vanilla's veins sit above 0.40, i.e. 20.2%.
    // Linear scaling of the toggle got this badly wrong: size 0.5 meant a 0.80 threshold, 30x less vein.
    private static final double[] THRESHOLD = {0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50,
            0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90};
    private static final double[] SHARE = {0.75246, 0.63629, 0.52831, 0.42922, 0.34179, 0.26574, 0.20220,
            0.14950, 0.10698, 0.07434, 0.05049, 0.03259, 0.02050, 0.01203, 0.00683, 0.00383, 0.00205};
    private static final double VANILLA_THRESHOLD = 0.40;
    private static final double VANILLA_SHARE = 0.20220;
    public static final double MAX_SHIFT = VANILLA_THRESHOLD - THRESHOLD[0];

    private enum Role { ORE, RAW, FILLER }

    /** @param shift added to |toggle|: positive grows the veins, negative shrinks them, -inf removes them */
    private record Vein(boolean enabled, double shift, double oreAmount, double rawAmount,
                        BlockState ore, BlockState raw, BlockState filler) {
        BlockState block(Role role) {
            return switch (role) {
                case ORE -> ore;
                case RAW -> raw;
                case FILLER -> filler;
            };
        }
    }

    private record Entry(Vein vein, Role role) {}

    private record Rules(Map<Block, Entry> byVanillaBlock, Vein copper, Vein iron) {}

    // Read from worldgen threads: the whole snapshot is swapped, never mutated in place. Null until
    // common setup, where the registries this resolves against are complete.
    private static volatile Rules rules;

    private VeinRules() {}

    /** Vanilla picks copper where the toggle is positive, iron elsewhere; each type has its own size. */
    public static double resizeToggle(double toggle) {
        Rules r = rules;
        if (r == null) return toggle;
        double shift = toggle > 0 ? r.copper().shift() : r.iron().shift();
        if (shift == 0) return toggle;
        double magnitude = Math.abs(toggle) + shift;
        return magnitude <= 0 ? 0 : Math.copySign(magnitude, toggle);
    }

    /** The |toggle| shift that makes the veins cover {@code size} times vanilla's volume. */
    static double shiftFor(double size) {
        if (size <= 0) return Double.NEGATIVE_INFINITY;
        double target = size * VANILLA_SHARE;
        if (target >= SHARE[0]) return MAX_SHIFT;
        for (int k = 1; k < SHARE.length; k++) {
            if (SHARE[k] <= target) {
                double f = (SHARE[k - 1] - target) / (SHARE[k - 1] - SHARE[k]);
                return VANILLA_THRESHOLD - (THRESHOLD[k - 1] + f * (THRESHOLD[k] - THRESHOLD[k - 1]));
            }
        }
        return VANILLA_THRESHOLD - THRESHOLD[THRESHOLD.length - 1];
    }

    /** @return the block to place, or null to leave the base stone/deepslate. */
    @Nullable
    public static BlockState apply(BlockState vanilla, RandomSource random) {
        Rules r = rules;
        Entry entry = r == null ? null : r.byVanillaBlock().get(vanilla.getBlock());
        if (entry == null) return vanilla;
        Vein v = entry.vein();
        if (!v.enabled()) return null;

        Role role = entry.role();
        if (role == Role.FILLER) {
            if (v.oreAmount() > 1 && random.nextDouble() < (v.oreAmount() - 1) * ORE_PER_FILLER) role = Role.ORE;
        } else if (v.oreAmount() < 1 && random.nextDouble() >= v.oreAmount()) {
            role = Role.FILLER;
        }
        if (role == Role.RAW) {
            if (v.rawAmount() < 1 && random.nextDouble() >= v.rawAmount()) role = Role.ORE;
        } else if (role == Role.ORE && v.rawAmount() > 1
                && random.nextDouble() < (v.rawAmount() - 1) * RAW_PER_ORE) {
            role = Role.RAW;
        }
        return v.block(role);
    }

    public static void rebuild() {
        Vein copper = vein(VeinConfig.COPPER, Blocks.COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.GRANITE);
        Vein iron = vein(VeinConfig.IRON, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.TUFF);
        Map<Block, Entry> byVanillaBlock = new HashMap<>();
        byVanillaBlock.put(Blocks.COPPER_ORE, new Entry(copper, Role.ORE));
        byVanillaBlock.put(Blocks.RAW_COPPER_BLOCK, new Entry(copper, Role.RAW));
        byVanillaBlock.put(Blocks.GRANITE, new Entry(copper, Role.FILLER));
        byVanillaBlock.put(Blocks.DEEPSLATE_IRON_ORE, new Entry(iron, Role.ORE));
        byVanillaBlock.put(Blocks.RAW_IRON_BLOCK, new Entry(iron, Role.RAW));
        byVanillaBlock.put(Blocks.TUFF, new Entry(iron, Role.FILLER));
        rules = new Rules(byVanillaBlock, copper, iron);
    }

    private static Vein vein(VeinConfig.Vein cfg, Block ore, Block raw, Block filler) {
        // A disabled vein is also sized to zero, so it is never even computed.
        boolean on = cfg.enabled().get();
        return new Vein(on, shiftFor(on ? cfg.size().get() : 0), cfg.oreAmount().get(), cfg.rawBlockAmount().get(),
                resolve(cfg.ore().get(), ore), resolve(cfg.rawBlock().get(), raw), resolve(cfg.filler().get(), filler));
    }

    private static BlockState resolve(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(id))
                .map(Block::defaultBlockState)
                .orElseGet(() -> {
                    LOG.warn("[{}] Unknown block '{}', keeping {}", OreVeinTweaker.MOD_ID, id,
                            BuiltInRegistries.BLOCK.getKey(fallback));
                    return fallback.defaultBlockState();
                });
    }
}
