package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("census")
                        .executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, false, false))
                        .then(Commands.literal("export").executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, false, true)))
                        .then(named("save", true))
                        .then(named("diff", false))
                        .then(radius(Commands.literal("y"), true))
                        .then(radius(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS)), false))));
    }

    /** `census y [radius]` and `census <radius> [export]`: the same command, the first one by height. */
    private static ArgumentBuilder<CommandSourceStack, ?> radius(ArgumentBuilder<CommandSourceStack, ?> node, boolean byY) {
        if (!byY) {
            return node.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), false, false))
                    .then(Commands.literal("export").executes(ctx ->
                            run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), false, true)));
        }
        return node.executes(ctx -> run(ctx.getSource(), DEFAULT_RADIUS, true, false))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), true, false)));
    }

    /** `census save <name> [radius]` keeps the counts under a name; `census diff <name> [radius]` compares them to now. */
    private static ArgumentBuilder<CommandSourceStack, ?> named(String word, boolean save) {
        return Commands.literal(word).then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> named(ctx, DEFAULT_RADIUS, save))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(ctx -> named(ctx, IntegerArgumentType.getInteger(ctx, "radius"), save))));
    }

    private static int named(CommandContext<CommandSourceStack> ctx, int radius, boolean save) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            send(ctx.getSource(), "A name is letters, digits, - and _ (32 at most).");
            return 0;
        }
        return save ? run(ctx.getSource(), radius, false, false, name, null)
                : run(ctx.getSource(), radius, false, false, null, name);
    }

    /** One chunk's counts, for the export. */
    private record ChunkCounts(int x, int z, Map<String, Long> counts) {}

    private static int run(CommandSourceStack src, int radius, boolean byY, boolean export) {
        return run(src, radius, byY, export, null, null);
    }

    private static int run(CommandSourceStack src, int radius, boolean byY, boolean export, String save, String diff) {
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        Set<Block> extra = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String id : VeinConfig.CENSUS_BLOCKS.get()) {
            Identifier rl = Identifier.tryParse(id);
            var block = rl == null ? java.util.Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isPresent()) extra.add(block.get()); else unknown.add(id);
        }

        // Section Y -> name -> count. A name is the ore's id, deepslate variant merged into the stone one.
        TreeMap<Integer, Map<String, Long>> bands = new TreeMap<>();
        Set<String> ores = new HashSet<>();
        Map<BlockState, String> names = new HashMap<>();
        int loaded = 0;
        // Export only: exact count per block and Y (the palette gives sections, so those holding a
        // counted block are read block by block), and the counts of each chunk.
        int minY = level.getMinY(), height = level.getHeight();
        Map<String, long[]> exactByY = new TreeMap<>();
        List<ChunkCounts> chunkRows = new ArrayList<>();
        for (int cx = center.x() - radius; cx <= center.x() + radius; cx++) {
            for (int cz = center.z() - radius; cz <= center.z() + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                loaded++;
                Map<String, Long> chunkCounts = new TreeMap<>();
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i].hasOnlyAir()) continue;
                    int sectionY = chunk.getSectionYFromSectionIndex(i);
                    Map<String, Long> band = bands.computeIfAbsent(sectionY, k -> new HashMap<>());
                    boolean[] any = {false};
                    sections[i].getStates().count((state, n) -> {
                        String name = names.computeIfAbsent(state, s -> nameOf(s, extra, ores));
                        if (name.isEmpty()) return;
                        band.merge(name, (long) n, Long::sum);
                        chunkCounts.merge(name, (long) n, Long::sum);
                        any[0] = true;
                    });
                    if (export && any[0]) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                for (int lx = 0; lx < 16; lx++) {
                                    String name = names.get(sections[i].getBlockState(lx, ly, lz));
                                    if (name != null && !name.isEmpty()) {
                                        exactByY.computeIfAbsent(name, k -> new long[height])[sectionY * 16 + ly - minY]++;
                                    }
                                }
                            }
                        }
                    }
                }
                if (export) chunkRows.add(new ChunkCounts(cx, cz, chunkCounts));
            }
        }

        int side = radius * 2 + 1;
        send(src, "Census " + level.dimension().identifier() + ": " + loaded + " of " + side * side
                + " chunks loaded within " + radius * 16 + " blocks"
                + (loaded < side * side ? " (only loaded chunks are counted)" : ""));
        if (!unknown.isEmpty()) send(src, "Ignored unknown ids in census.blocks: " + String.join(", ", unknown));
        if (loaded == 0) return 0;

        if (byY) return byHeight(src, bands, ores);

        Map<String, Long> total = new HashMap<>();
        bands.values().forEach(band -> band.forEach((k, v) -> total.merge(k, v, Long::sum)));
        if (save != null) return save(src, save, total, loaded);
        if (diff != null) return diff(src, diff, total, loaded);
        long oreTotal = total.entrySet().stream().filter(e -> ores.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        int covered = loaded;
        total.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).forEach(e -> {
            String name = e.getKey();
            MutableComponent line;
            if (ores.contains(name)) {
                double share = 100.0 * e.getValue() / oreTotal;
                line = Component.literal("  " + bar(share) + String.format(Locale.ROOT, " %5.1f%%  %s", share, name))
                        .withStyle(colorOf(name));
            } else {
                line = Component.literal("  " + " ".repeat(BAR) + "         " + name).withStyle(ChatFormatting.DARK_GRAY);
            }
            line.append(Component.literal(String.format(Locale.ROOT, "  %,d  (%.1f/chunk)", e.getValue(),
                    e.getValue() / (double) covered)).withStyle(ChatFormatting.GRAY));
            src.sendSuccess(() -> line, false);
        });
        send(src, String.format(Locale.ROOT, "  total ore: %,d (%.1f/chunk)", oreTotal, oreTotal / (double) covered));
        if (export) export(src, level, minY, exactByY, chunkRows, center, radius, loaded);
        return total.size();
    }

    private static final int BAR = 10;

    private static final Map<String, ChatFormatting> COLORS = Map.of(
            "coal", ChatFormatting.DARK_GRAY, "copper", ChatFormatting.GOLD, "iron", ChatFormatting.GRAY,
            "gold", ChatFormatting.YELLOW, "redstone", ChatFormatting.RED, "lapis", ChatFormatting.BLUE,
            "diamond", ChatFormatting.AQUA, "emerald", ChatFormatting.GREEN);

    private static String bar(double percent) {
        int full = (int) Math.round(percent / 100 * BAR);
        return "█".repeat(full) + "░".repeat(BAR - full);
    }

    private static ChatFormatting colorOf(String name) {
        for (var e : COLORS.entrySet()) if (name.contains(e.getKey())) return e.getValue();
        return ChatFormatting.WHITE;
    }

    private static Path saveFile(String name) {
        return VeinFiles.dir().resolveSibling("census").resolve("saved-" + name + ".csv");
    }

    /** First line: chunks counted; then block,total. Compared per chunk, so the area may differ a little. */
    private static int save(CommandSourceStack src, String name, Map<String, Long> total, int loaded) {
        StringBuilder out = new StringBuilder("loaded,").append(loaded).append('\n');
        total.forEach((k, v) -> out.append(k).append(',').append(v).append('\n'));
        try {
            Path file = saveFile(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            send(src, "Save failed: " + e.getMessage());
            return 0;
        }
        send(src, "Saved \"" + name + "\". Later: /oreveintweaker census diff " + name);
        return total.size();
    }

    private static int diff(CommandSourceStack src, String name, Map<String, Long> total, int loaded) {
        List<String> lines;
        try {
            lines = Files.readAllLines(saveFile(name));
        } catch (IOException e) {
            send(src, "No saved census called \"" + name + "\": run /oreveintweaker census save " + name + " first.");
            return 0;
        }
        double before = Double.parseDouble(lines.get(0).substring("loaded,".length()));
        Map<String, Double> old = new HashMap<>();
        for (String l : lines.subList(1, lines.size())) {
            int comma = l.lastIndexOf(',');
            old.put(l.substring(0, comma), Long.parseLong(l.substring(comma + 1)) / before);
        }
        Map<String, Double> now = new HashMap<>();
        total.forEach((k, v) -> now.put(k, v / (double) loaded));
        Set<String> names = new HashSet<>(old.keySet());
        names.addAll(now.keySet());
        // Under 0.05 a chunk, a block is noise: "0.0 -> 0.0 (-100%)" says nothing.
        names.removeIf(n -> old.getOrDefault(n, 0.0) < 0.05 && now.getOrDefault(n, 0.0) < 0.05);
        send(src, "Compared with \"" + name + "\" (" + (int) before + " chunks then), per chunk:");
        names.stream().sorted((a, b) -> Double.compare(Math.abs(now.getOrDefault(b, 0.0) - old.getOrDefault(b, 0.0)),
                Math.abs(now.getOrDefault(a, 0.0) - old.getOrDefault(a, 0.0)))).forEach(n -> {
            double was = old.getOrDefault(n, 0.0), is = now.getOrDefault(n, 0.0);
            String change = was == 0 ? "new" : String.format(Locale.ROOT, "%+.0f%%", 100 * (is - was) / was);
            ChatFormatting color = Math.abs(is - was) <= 0.005 * Math.max(was, is) ? ChatFormatting.GRAY
                    : is > was ? ChatFormatting.GREEN : ChatFormatting.RED;
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s: %.1f -> %.1f  (%s)", n, was, is, change))
                    .withStyle(color), false);
        });
        return names.size();
    }

    /**
     * Two CSV files in config/oreveintweaker/census/: the count of every block at every Y (one column per
     * block, ready for a chart), and the count of every block in every chunk (ready for a pivot table).
     * The by-height table also goes into a chat line that copies it to the clipboard of whoever clicks.
     */
    private static void export(CommandSourceStack src, ServerLevel level, int minY,
                               Map<String, long[]> exactByY, List<ChunkCounts> chunks,
                               ChunkPos center, int radius, int loaded) {
        StringBuilder byY = new StringBuilder("y");
        exactByY.keySet().forEach(name -> byY.append(',').append(name));
        byY.append('\n');
        int height = level.getHeight();
        for (int i = height - 1; i >= 0; i--) {
            StringBuilder row = new StringBuilder().append(minY + i);
            boolean any = false;
            for (long[] counts : exactByY.values()) {
                row.append(',').append(counts[i]);
                any |= counts[i] != 0;
            }
            if (any) byY.append(row).append('\n');
        }
        StringBuilder byChunk = new StringBuilder("chunk_x,chunk_z,block,count\n");
        for (ChunkCounts c : chunks) {
            c.counts().forEach((name, n) -> byChunk.append(c.x()).append(',').append(c.z()).append(',')
                    .append(name).append(',').append(n).append('\n'));
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String prefix = "census-" + level.dimension().identifier().toString().replace(':', '_') + "-" + stamp;
        Path dir = VeinFiles.dir().resolveSibling("census");
        try {
            Files.createDirectories(dir);
            Path heightFile = dir.resolve(prefix + "-by-height.csv");
            Path chunkFile = dir.resolve(prefix + "-by-chunk.csv");
            Files.writeString(heightFile, byY.toString());
            Files.writeString(chunkFile, byChunk.toString());
            // What the files are about, for when they are opened weeks later.
            Files.writeString(dir.resolve(prefix + "-info.txt"), "dimension: " + level.dimension().identifier()
                    + "\nseed: " + level.getSeed() + "\ncenter chunk: " + center.x() + " " + center.z()
                    + "\nradius: " + radius + " chunks\nchunks counted: " + loaded + "\ndate: " + stamp + "\n");
            send(src, "Exported " + heightFile.toAbsolutePath());
            send(src, "Exported " + chunkFile.toAbsolutePath());
        } catch (IOException e) {
            send(src, "Export failed: " + e.getMessage());
            return;
        }
        // A chat line carries at most about 256 KB of text.
        if (byY.length() <= 200_000) {
            src.sendSuccess(() -> Component.literal("[Click to copy the by-height table]").withStyle(Style.EMPTY
                    .withUnderlined(true).withClickEvent(new ClickEvent.CopyToClipboard(byY.toString()))), false);
        } else {
            send(src, "The by-height table is too big to copy from the chat: use the file.");
        }
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
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
