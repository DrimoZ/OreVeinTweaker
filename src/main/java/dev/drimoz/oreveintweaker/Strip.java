package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /oreveintweaker strip [radius] confirm - removes everything below Y 64 except ores, raw/storage blocks
 * and bedrock around you, so only the ores are left to look at (and to /oreveintweaker census).
 *
 * It cannot be undone and it takes builds below Y 64 with it, so it only runs with the word "confirm",
 * and the radius stops at 4 chunks: it sets blocks one by one, which freezes the server for the duration.
 */
final class Strip {
    private static final int MAX_Y = 64;
    private static final int MAX_RADIUS = 4;
    private static final int DEFAULT_RADIUS = 2;

    private Strip() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("strip")
                        .executes(ctx -> warn(ctx.getSource(), DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                                .executes(ctx -> warn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    private static int warn(CommandSourceStack src, int radius) {
        int side = radius * 2 + 1;
        src.sendSuccess(() -> Component.literal("Strip deletes every block below Y " + MAX_Y
                + " except ores, raw/storage blocks and bedrock, over " + side + "x" + side + " chunks around you."
                + " Builds below Y " + MAX_Y + " are lost too, and there is no undo. To go ahead:"
                + " /oreveintweaker strip " + radius + " confirm"), false);
        return 0;
    }

    // ponytail: plain setBlock per block, so the server freezes while it runs: hence the radius cap.
    static int run(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;

        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        for (int y = level.getMinY(); y < MAX_Y; y++) {
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
        LogUtils.getLogger().info("[{}] {} stripped {} blocks around {}", OreVeinTweaker.MOD_ID,
                src.getTextName(), total, center);
        src.sendSuccess(() -> Component.literal("Stripped " + total + " blocks"), true);
        return total;
    }

    private static boolean keep(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.STORAGE_BLOCKS) || state.is(Blocks.BEDROCK);
    }
}
