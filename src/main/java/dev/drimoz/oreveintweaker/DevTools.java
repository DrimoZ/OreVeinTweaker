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
 * /strip [radius]  - World Stripper replacement (no 1.20.1 build exists): removes everything below Y 64
 *                    except ores, raw/storage blocks and bedrock.
 * /veins [reset]   - what the vanilla vein generator wanted to place vs what this mod placed instead.
 */
public final class DevTools {
    public static final boolean ENABLED = !FMLLoader.getCurrent().isProduction();
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

    /**
     * Logs how often |vein_toggle| reaches each threshold over the vein heights. Vanilla's veins sit
     * above 0.4, so this table is what turns "size = 2" into "about twice the vein volume".
     */
    static void measureToggle(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        DensityFunction toggle = level.getChunkSource().randomState().router().veinToggle();
        RandomSource random = RandomSource.create(42);
        double[] thresholds = new double[17];
        long[] above = new long[thresholds.length];
        for (int k = 0; k < thresholds.length; k++) thresholds[k] = 0.1 + k * 0.05;
        int samples = 200_000;
        // Vein volume per type, vanilla vs after this mod's resize: the ratio should read as `size`.
        long copperVanilla = 0, copperResized = 0, ironVanilla = 0, ironResized = 0;
        for (int i = 0; i < samples; i++) {
            int x = random.nextInt(40_000) - 20_000;
            int z = random.nextInt(40_000) - 20_000;
            int y = -60 + random.nextInt(111);
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
        StringBuilder table = new StringBuilder("[" + OreVeinTweaker.MOD_ID + "] |vein_toggle| >= a, over "
                + samples + " samples, Y -60..50:");
        for (int k = 0; k < thresholds.length; k++) {
            table.append(String.format(java.util.Locale.ROOT, "%n  a=%.2f  %.5f", thresholds[k],
                    above[k] / (double) samples));
        }
        LogUtils.getLogger().info(table.toString());
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("strip")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> strip(ctx.getSource(), 2))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                        .executes(ctx -> strip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));

        event.getDispatcher().register(Commands.literal("veins")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> veins(ctx.getSource()))
                .then(Commands.literal("reset").executes(ctx -> {
                    STATS.clear();
                    PLACED.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("Vein stats reset"), false);
                    return 1;
                })));
    }

    private static int veins(CommandSourceStack src) {
        if (STATS.isEmpty()) {
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
                                    .withClickEvent(new ClickEvent.SuggestCommand(tp))));
            src.sendSuccess(() -> line, false);
        });
        StringBuilder placed = new StringBuilder("Placed:");
        PLACED.forEach((id, n) -> placed.append(' ').append(id).append(" x").append(n.sum()));
        src.sendSuccess(() -> Component.literal(placed.toString()), false);
        return STATS.size();
    }

    // ponytail: plain setBlock per block, a radius of 8 freezes the server for a while; fine for a dev tool.
    private static int strip(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;

        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        for (int y = level.getMinY(); y < STRIP_MAX_Y; y++) {
                            BlockState state = level.getBlockState(pos.set(x, y, z));
                            if (!state.isAir() && !keep(state)) {
                                level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                                removed++;
                            }
                        }
                    }
                }
            }
        }

        int total = removed;
        src.sendSuccess(() -> Component.literal("Stripped " + total + " blocks"), true);
        return total;
    }

    private static boolean keep(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.STORAGE_BLOCKS) || state.is(Blocks.BEDROCK);
    }
}
