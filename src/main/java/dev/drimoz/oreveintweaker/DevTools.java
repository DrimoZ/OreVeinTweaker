package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import com.mojang.logging.LogUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Dev-only tools, never active in a release jar:
 * /veins [reset]   - what the vanilla vein generator wanted to place vs what this mod placed instead.
 */
public final class DevTools {
    public static final boolean ENABLED = !FMLLoader.isProduction();
    private static final int STRIP_MAX_Y = 64;

    // Vanilla vein block -> stats. Written from worldgen threads.
    private static final Map<Block, Stat> STATS = new ConcurrentHashMap<>();
    // What actually went into the world, so ore_amount and friends can be checked against the counts.
    private static final Map<String, LongAdder> PLACED = new ConcurrentHashMap<>();

    private static final class Stat {
        final LongAdder count = new LongAdder();
        volatile String placed = "";
        volatile BlockPos lastPos = BlockPos.ZERO;
    }

    private DevTools() {}

    public static void record(BlockState vanilla, @Nullable BlockState placed, int x, int y, int z) {
        Stat stat = STATS.computeIfAbsent(vanilla.getBlock(), b -> new Stat());
        stat.count.increment();
        stat.placed = placed == null ? "REMOVED" : BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString();
        stat.lastPos = new BlockPos(x, y, z);
        PLACED.computeIfAbsent(stat.placed, k -> new LongAdder()).increment();
    }

    /** Extra veins have no vanilla block to compare with: they only count towards the totals. */
    public static void recordExtra(BlockState placed) {
        PLACED.computeIfAbsent("extra " + BuiltInRegistries.BLOCK.getKey(placed.getBlock()), k -> new LongAdder())
                .increment();
    }

    /**
     * Logs how often |vein_toggle| reaches each threshold over the vein heights. Vanilla's veins sit
     * above 0.4, so this table is what turns "size = 2" into "about twice the vein volume".
     */
    static void measureToggle(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        DensityFunction toggle = level.getChunkSource().randomState().router().veinToggle();
        RandomSource random = RandomSource.create(42);
        // Fine steps from 0 (sizes above 3 live below threshold 0.10), 0.05 steps above.
        double[] thresholds = new double[22];
        long[] above = new long[thresholds.length];
        for (int k = 0; k < thresholds.length; k++) thresholds[k] = k < 6 ? k * 0.025 : 0.15 + (k - 6) * 0.05;
        int samples = 200_000;
        // Vein volume per type, vanilla vs after this mod's resize: the ratio should read as `size`.
        long copperVanilla = 0, copperResized = 0, ironVanilla = 0, ironResized = 0;
        // Extra veins are sized on copper's half of the toggle: vs vanilla copper, they should read as `size`.
        var extras = VeinRules.extraNames();
        long[] extraHits = new long[extras.size()];
        for (int i = 0; i < samples; i++) {
            int x = random.nextInt(40_000) - 20_000;
            int z = random.nextInt(40_000) - 20_000;
            int y = -60 + random.nextInt(111);
            for (int e = 0; e < extraHits.length; e++) if (VeinRules.extraToggleHit(e, toggle, x, y, z)) extraHits[e]++;
            double raw = toggle.compute(new DensityFunction.SinglePointContext(x, y, z));
            double t = Math.abs(raw);
            for (int k = 0; k < thresholds.length; k++) if (t >= thresholds[k]) above[k]++;
            boolean resized = Math.abs(VeinRules.resizeToggle(raw)) >= 0.4;
            if (raw > 0) {
                if (t >= 0.4) copperVanilla++;
                if (resized) copperResized++;
            } else {
                if (t >= 0.4) ironVanilla++;
                if (resized) ironResized++;
            }
        }
        LogUtils.getLogger().info(String.format(java.util.Locale.ROOT,
                "[%s] vein volume vs vanilla with the current config: copper x%.2f, iron x%.2f",
                OreVeinTweaker.MOD_ID, copperResized / (double) copperVanilla, ironResized / (double) ironVanilla));
        for (int e = 0; e < extraHits.length; e++) {
            LogUtils.getLogger().info(String.format(java.util.Locale.ROOT, "[%s] %s volume vs vanilla copper: x%.2f",
                    OreVeinTweaker.MOD_ID, extras.get(e), extraHits[e] / (double) copperVanilla));
        }
        StringBuilder table = new StringBuilder("[" + OreVeinTweaker.MOD_ID + "] |vein_toggle| >= a, over "
                + samples + " samples, Y -60..50:");
        for (int k = 0; k < thresholds.length; k++) {
            table.append(String.format(java.util.Locale.ROOT, "%n  a=%.3f  %.5f", thresholds[k],
                    above[k] / (double) samples));
        }
        LogUtils.getLogger().info(table.toString());
    }

    static void register(RegisterCommandsEvent event) {
        // Generates a size x size square of chunks and times it: what extra veins cost is measured in
        // chunk generation time, so the same area is run with different configs on identical fresh worlds.
        event.getDispatcher().register(Commands.literal("benchgen")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> benchgen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), 1000, 1000))
                        .then(Commands.argument("cx", IntegerArgumentType.integer())
                                .then(Commands.argument("cz", IntegerArgumentType.integer())
                                        .executes(ctx -> benchgen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "cx"), IntegerArgumentType.getInteger(ctx, "cz")))))));

        event.getDispatcher().register(Commands.literal("veins")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> veins(ctx.getSource()))
                .then(Commands.literal("reset").executes(ctx -> {
                    STATS.clear();
                    PLACED.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("Vein stats reset"), false);
                    return 1;
                })));
    }

    private static int benchgen(CommandSourceStack src, int size, int cx, int cz) {
        ServerLevel level = src.getLevel();
        int generated = 0;
        long start = System.nanoTime();
        for (int x = cx; x < cx + size; x++) {
            for (int z = cz; z < cz + size; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) generated++;
                level.getChunk(x, z, ChunkStatus.FULL, true);
            }
        }
        double ms = (System.nanoTime() - start) / 1e6;
        String msg = String.format(java.util.Locale.ROOT, "benchgen: %d chunks in %.0f ms = %.2f ms/chunk (%d were not loaded)",
                size * size, ms, ms / (size * size), generated);
        LogUtils.getLogger().info("[{}] {}", OreVeinTweaker.MOD_ID, msg);
        src.sendSuccess(() -> Component.literal(msg), false);
        return size * size;
    }

    private static int veins(CommandSourceStack src) {
        if (PLACED.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No vein block generated since launch/reset. Explore new chunks."), false);
            return 0;
        }
        STATS.forEach((block, stat) -> {
            BlockPos p = stat.lastPos;
            String tp = "/tp @s " + p.getX() + " " + (p.getY() + 1) + " " + p.getZ();
            Component line = Component.literal(BuiltInRegistries.BLOCK.getKey(block) + " x" + stat.count.sum()
                            + " -> " + stat.placed + "  last at ")
                    .append(Component.literal("[" + p.toShortString() + "]")
                            .withStyle(Style.EMPTY.withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))));
            src.sendSuccess(() -> line, false);
        });
        StringBuilder placed = new StringBuilder("Placed:");
        PLACED.forEach((id, n) -> placed.append(' ').append(id).append(" x").append(n.sum()));
        src.sendSuccess(() -> Component.literal(placed.toString()), false);
        return STATS.size();
    }

}
