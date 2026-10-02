package com.oreveinstripper;

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
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
