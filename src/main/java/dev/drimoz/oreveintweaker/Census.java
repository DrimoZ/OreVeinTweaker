package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * /oreveintweaker census [y] [radius] - how much of each ore the already loaded chunks around the player
 * hold. It counts blocks in the world, not what a generator placed, so it also sees every other mod's ore.
 *
 * Reads the chunk sections' palettes (one count per distinct block, not per block): a 17x17 chunk scan is
 * a few thousand palette reads. Never loads or generates a chunk: the report says how many it covered.
 */
public final class Census {
    private static final int DEFAULT_RADIUS = 8; // chunks: 128 blocks
    private static final int MAX_RADIUS = 32;

    private Census() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("census")
                        .executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, false))
                        .then(radius(Commands.literal("y"), true))
                        .then(radius(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS)), false))));
    }

    /** `census y [radius]` and `census <radius>`: the same command, the first one by height. */
    private static ArgumentBuilder<CommandSourceStack, ?> radius(ArgumentBuilder<CommandSourceStack, ?> node, boolean byY) {
        if (!byY) return node.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), false));
        return node.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, true))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), true)));
    }

    private static int run(CommandSourceStack src, int radius, boolean byY) {
        ServerLevel level = src.getLevel();
        ChunkPos center = new ChunkPos(BlockPos.containing(src.getPosition()));
        Set<Block> extra = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String id : VeinConfig.CENSUS_BLOCKS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            var block = rl == null ? java.util.Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isPresent()) extra.add(block.get()); else unknown.add(id);
        }

        // Section Y -> name -> count. A name is the ore's id, deepslate variant merged into the stone one.
        TreeMap<Integer, Map<String, Long>> bands = new TreeMap<>();
        Set<String> ores = new HashSet<>();
        Map<BlockState, String> names = new HashMap<>();
        int loaded = 0;
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                loaded++;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i].hasOnlyAir()) continue;
                    Map<String, Long> band = bands.computeIfAbsent(chunk.getSectionYFromSectionIndex(i), k -> new HashMap<>());
                    sections[i].getStates().count((state, n) -> {
                        String name = names.computeIfAbsent(state, s -> nameOf(s, extra, ores));
                        if (!name.isEmpty()) band.merge(name, (long) n, Long::sum);
                    });
                }
            }
        }

        int side = radius * 2 + 1;
        send(src, "Census " + level.dimension().location() + ": " + loaded + " of " + side * side
                + " chunks loaded within " + radius * 16 + " blocks"
                + (loaded < side * side ? " (only loaded chunks are counted)" : ""));
        if (!unknown.isEmpty()) send(src, "Ignored unknown ids in census.blocks: " + String.join(", ", unknown));
        if (loaded == 0) return 0;

        if (byY) return byHeight(src, bands, ores);

        Map<String, Long> total = new HashMap<>();
        bands.values().forEach(band -> band.forEach((k, v) -> total.merge(k, v, Long::sum)));
        long oreTotal = total.entrySet().stream().filter(e -> ores.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        int covered = loaded;
        total.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).forEach(e -> {
            String share = ores.contains(e.getKey())
                    ? String.format(Locale.ROOT, ", %.1f%% of ores", 100.0 * e.getValue() / oreTotal) : "";
            send(src, String.format(Locale.ROOT, "  %s: %d (%.1f/chunk%s)", e.getKey(), e.getValue(),
                    e.getValue() / (double) covered, share));
        });
        send(src, String.format(Locale.ROOT, "  total ore: %d (%.1f/chunk)", oreTotal, oreTotal / (double) covered));
        return total.size();
    }

    private static int byHeight(CommandSourceStack src, TreeMap<Integer, Map<String, Long>> bands, Set<String> ores) {
        int lines = 0;
        for (var band : bands.descendingMap().entrySet()) {
            long oreTotal = band.getValue().entrySet().stream().filter(e -> ores.contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
            if (oreTotal == 0) continue;
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  Y %d..%d: %d ore -",
                    band.getKey() * 16, band.getKey() * 16 + 15, oreTotal));
            band.getValue().entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(4)
                    .forEach(e -> line.append(' ').append(e.getKey()).append(' ').append(ores.contains(e.getKey())
                            ? String.format(Locale.ROOT, "%.0f%%", 100.0 * e.getValue() / oreTotal) : e.getValue()));
            send(src, line.toString());
            lines++;
        }
        return lines;
    }

    /** "" = not counted. Records the ore names: their share is reported against the total of all ores. */
    private static String nameOf(BlockState state, Set<Block> extra, Set<String> ores) {
        boolean ore = state.is(Tags.Blocks.ORES);
        if (!ore && !state.is(Tags.Blocks.STORAGE_BLOCKS) && !extra.contains(state.getBlock())) return "";
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        if (ore && path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
        String name = id.getNamespace().equals("minecraft") ? path : id.getNamespace() + ":" + path;
        if (ore) ores.add(name);
        return name;
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }
}
