package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * /oreveintweaker strip [radius] - removes everything below Y 64 except ores, raw/storage blocks and
 * bedrock around you, so only the ores are left to look at (and to /oreveintweaker census).
 * No undo, and builds below Y 64 go too: it is a test world tool, and operators only.
 */
final class Strip {
    private static final int MAX_Y = 64;
    private static final int MAX_RADIUS = 16;
    private static final int DEFAULT_RADIUS = 2;

    private Strip() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("strip")
                        .executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                                .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    // ponytail: plain setBlock per block, so the server freezes while it runs (a radius of 16 is minutes).
    static int run(CommandSourceStack src, int radius) {
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int removed = 0;
        Set<Block> kept = new HashSet<>();
        for (String id : VeinConfig.STRIP_KEEP.get()) {
            Identifier rl = Identifier.tryParse(id);
            if (rl != null) BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(kept::add);
        }
        // The action bar goes out per chunk: the packet is flushed by the network thread, so it
        // reaches the player even while this tick is blocked.
        ServerPlayer player = src.getEntity() instanceof ServerPlayer p ? p : null;
        int side = 2 * radius + 1;
        int done = 0;

        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                if (player != null) player.sendSystemMessage(bar(done++, side * side), true);
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        for (int y = level.getMinY(); y < MAX_Y; y++) {
                            BlockState state = level.getBlockState(pos.set(x, y, z));
                            if (!state.isAir() && !keep(state, kept)) {
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

    private static boolean keep(BlockState state, Set<Block> kept) {
        return state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.STORAGE_BLOCKS) || state.is(Blocks.BEDROCK)
                || kept.contains(state.getBlock());
    }

    private static Component bar(int done, int total) {
        int filled = done * 20 / total;
        return Component.literal("Strip " + "█".repeat(filled) + "░".repeat(20 - filled) + " " + done * 100 / total + "%");
    }
}
