package dev.drimoz.oreveintweaker;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                        Palette ore, Palette raw, Palette filler) {
        BlockState block(Role role, RandomSource random) {
            Palette palette = switch (role) {
                case ORE -> ore;
                case RAW -> raw;
                case FILLER -> filler;
            };
            return palette.pick(random);
        }
    }

    private record Entry(Vein vein, Role role) {}

    /**
     * A vein of our own, run through vanilla's algorithm. It reads vanilla's noises at a shifted
     * position: sideways so its ribbons do not follow copper's, and down so its Y range lands on
     * -60..50, the only heights where those noises are defined.
     */
    private static final class Extra {
        /** Seeds the vein's own random stream: a vein keeps its ribbons and rolls when others come and go. */
        final String key;
        final int slot;
        final Vein vein;
        final int minY, maxY;
        private volatile Seeded seeded;

        private record Seeded(PositionalRandomFactory base, PositionalRandomFactory forked) {}

        Extra(String key, int slot, Vein vein, int minY, int maxY) {
            this.key = key;
            this.slot = slot;
            this.vein = vein;
            this.minY = minY;
            this.maxY = maxY;
        }

        int dx() { return 10007 * (slot + 1); }
        int dz() { return 7001 * (slot + 1); }

        /** The vanilla factory is per world: derive once, and again only if a different world asks. */
        PositionalRandomFactory random(PositionalRandomFactory base) {
            Seeded s = seeded;
            if (s == null || s.base() != base) {
                s = new Seeded(base, base.fromHashOf("oreveintweaker:" + key).forkPositional());
                seeded = s;
            }
            return s.forked();
        }
    }

    private record Rules(Map<Block, Entry> byVanillaBlock, Vein copper, Vein iron, List<Extra> extras) {}

    // Read from worldgen threads: the whole snapshot is swapped, never mutated in place. Null until
    // common setup, where the registries this resolves against are complete.
    private static volatile Rules rules;
    private static volatile VeinFiles.Result files;
    private static final int MANY_VEINS = 16;

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
        if (!entry.vein().enabled()) return null;
        return place(entry.vein(), entry.role(), random);
    }

    /**
     * Vanilla's vein algorithm (OreVeinifier.create, unchanged since 1.18) for the extra veins, where
     * vanilla placed nothing. Copper's half of the toggle (> 0) only, so size 1 matches one vanilla type.
     *
     * @param base vanilla's positional random: every extra vein derives its own stream from it
     * @return the block to place, or null when no extra vein is here
     */
    @Nullable
    public static BlockState applyExtra(DensityFunction.FunctionContext ctx, DensityFunction toggle,
                                        DensityFunction ridged, DensityFunction gap, PositionalRandomFactory base) {
        Rules r = rules;
        if (r == null) return null;
        int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
        for (Extra e : r.extras()) {
            if (y < e.minY || y > e.maxY) continue;
            var at = new DensityFunction.SinglePointContext(x + e.dx(), y - e.minY - 60, z + e.dz());
            double t = toggle.compute(at);
            if (t <= 0) continue;
            t += e.vein.shift();
            int edge = Math.min(e.maxY - y, y - e.minY);
            if (t + Mth.clampedMap(edge, 0, 20, -0.2, 0) < 0.4) continue;
            RandomSource random = e.random(base).at(x, y, z);
            if (random.nextFloat() > 0.7F || ridged.compute(at) >= 0) continue;
            double richness = Mth.clampedMap(t, 0.4, 0.6, 0.1, 0.3);
            Role role = random.nextFloat() < richness && gap.compute(at) > -0.3
                    ? (random.nextFloat() < 0.02F ? Role.RAW : Role.ORE) : Role.FILLER;
            return place(e.vein, role, random);
        }
        return null;
    }

    static List<String> extraNames() {
        Rules r = rules;
        return r == null ? List.of() : r.extras().stream().map(e -> e.key).toList();
    }

    /** Dev check: whether enabled extra vein {@code i} passes its size threshold at a Y -60..50 sample. */
    static boolean extraToggleHit(int i, DensityFunction toggle, int x, int sampleY, int z) {
        Extra e = rules.extras().get(i);
        double t = toggle.compute(new DensityFunction.SinglePointContext(x + e.dx(), sampleY, z + e.dz()));
        return t > 0 && t + e.vein.shift() >= 0.4;
    }

    /** Re-rolls a vein block's role for ore_amount and raw_block_amount, then picks the configured block. */
    private static BlockState place(Vein v, Role role, RandomSource random) {
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
        return v.block(role, random);
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
        List<Extra> extras = new ArrayList<>();
        for (int i = 0; i < VeinConfig.EXTRA.length; i++) {
            VeinConfig.Extra cfg = VeinConfig.EXTRA[i];
            if (!cfg.vein().enabled().get()) continue;
            int minY = cfg.minY().get(), maxY = cfg.maxY().get();
            if (maxY - minY > VeinConfig.MAX_EXTRA_HEIGHT) {
                LOG.warn("[{}] extra_{}: max_y is more than {} above min_y, using {}", OreVeinTweaker.MOD_ID,
                        i + 1, VeinConfig.MAX_EXTRA_HEIGHT, minY + VeinConfig.MAX_EXTRA_HEIGHT);
                maxY = minY + VeinConfig.MAX_EXTRA_HEIGHT;
            }
            extras.add(new Extra("extra_" + (i + 1), i, vein(cfg.vein(), Blocks.STONE, Blocks.STONE, Blocks.STONE), minY, maxY));
        }
        // Veins from files. The slot (which ribbons, which rolls) comes from the file name, so adding or
        // removing another file never moves a vein.
        VeinFiles.Result loaded = VeinFiles.load();
        for (VeinFiles.Spec s : loaded.specs()) {
            if (!s.enabled()) continue;
            Palette ore = Palette.parse(s.ore(), Blocks.STONE);
            extras.add(new Extra("vein:" + s.name(), VeinConfig.EXTRA.length + Math.floorMod(s.name().hashCode(), 50_000),
                    new Vein(true, shiftFor(s.size()), s.oreAmount(), s.rawAmount(), ore,
                            s.raw() == null ? ore : Palette.parse(s.raw(), Blocks.STONE),
                            Palette.parse(s.filler(), Blocks.STONE)), s.minY(), s.maxY()));
        }
        files = loaded;
        if (extras.size() > MANY_VEINS) {
            LOG.warn("[{}] {} extra veins are on: each one adds about 1-2% to chunk generation time (measured)",
                    OreVeinTweaker.MOD_ID, extras.size());
        }
        rules = new Rules(byVanillaBlock, copper, iron, List.copyOf(extras));
    }

    /** What the last rebuild found, for /oreveintweaker reload. */
    static List<String> report() {
        Rules r = rules;
        VeinFiles.Result f = files;
        List<String> lines = new ArrayList<>();
        if (r != null) {
            for (Extra e : r.extras()) {
                lines.add(String.format(java.util.Locale.ROOT, "  %s: Y %d..%d", e.key, e.minY, e.maxY));
            }
        }
        if (f != null) f.errors().forEach(err -> lines.add("  SKIPPED " + err));
        lines.add(0, (r == null ? 0 : r.extras().size()) + " extra veins on, folder: " + VeinFiles.dir());
        return lines;
    }

    private static Vein vein(VeinConfig.Vein cfg, Block ore, Block raw, Block filler) {
        // A disabled vein is also sized to zero, so it is never even computed.
        boolean on = cfg.enabled().get();
        return new Vein(on, shiftFor(on ? cfg.size().get() : 0), cfg.oreAmount().get(), cfg.rawBlockAmount().get(),
                Palette.parse(cfg.ore().get(), ore), Palette.parse(cfg.rawBlock().get(), raw),
                Palette.parse(cfg.filler().get(), filler));
    }
}
