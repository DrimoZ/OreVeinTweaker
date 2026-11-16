package dev.drimoz.oreveintweaker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
import java.nio.charset.StandardCharsets;
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
 * /oreveintweaker census - how much of each ore the already loaded chunks around the player hold.
 * It counts blocks in the world, not what a generator placed, so it also sees every other mod's ore.
 *
 * A pack has 30 to 100 ores, so the chat only ever shows a summary (the top few, the mods, buttons to
 * go further) and the full picture is a self-contained HTML page next to the CSV files.
 *
 * Reads the chunk sections' palettes (one count per distinct block, not per block): a 17x17 chunk scan is
 * a few thousand palette reads, whatever the number of ores. Never loads or generates a chunk: the report
 * says how many it covered.
 */
public final class Census {
    private static final int DEFAULT_RADIUS = 8; // chunks: 128 blocks
    private static final int MAX_RADIUS = 32;
    private static final int TOP = 5;            // ores in the summary: with the header and the buttons, the 10 lines of the chat
    private static final int PAGE = 8;
    private static final int BAR = 8;
    private static final String CMD = "/oreveintweaker census ";
    private static final String LEVELS = "▁▂▃▄▅▆▇█";

    private Census() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("census")
                        .executes(ctx -> summary(ctx.getSource(), DEFAULT_RADIUS, false))
                        .then(Commands.literal("export").executes(ctx -> summary(ctx.getSource(), DEFAULT_RADIUS, true)))
                        .then(named("save", true))
                        .then(named("diff", false))
                        .then(view(Commands.literal("y"), Census::heights))
                        .then(view(Commands.literal("mod"), Census::mods))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource(), 1, DEFAULT_RADIUS))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"), DEFAULT_RADIUS))
                                        .then(radius().executes(ctx -> list(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "page"), IntegerArgumentType.getInteger(ctx, "radius"))))))
                        // `ore <radius> <name>` first: the buttons of the chat carry the radius; `ore <name>` is typed by hand.
                        .then(Commands.literal("ore")
                                .then(radius().then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> ore(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                                                StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> ore(ctx.getSource(), DEFAULT_RADIUS, StringArgumentType.getString(ctx, "name")))))
                        .then(radius()
                                .executes(ctx -> summary(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), false))
                                .then(Commands.literal("export").executes(ctx ->
                                        summary(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), true))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> radius() {
        return Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS));
    }

    private interface View { int show(CommandSourceStack src, int radius); }

    /** `census <word> [radius]`. */
    private static ArgumentBuilder<CommandSourceStack, ?> view(ArgumentBuilder<CommandSourceStack, ?> node, View view) {
        return node.executes(ctx -> view.show(ctx.getSource(), DEFAULT_RADIUS))
                .then(radius().executes(ctx -> view.show(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))));
    }

    /** `census save <name> [radius]` keeps the counts under a name; `census diff <name> [radius]` compares them to now. */
    private static ArgumentBuilder<CommandSourceStack, ?> named(String word, boolean save) {
        return Commands.literal(word).then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> named(ctx, DEFAULT_RADIUS, save))
                .then(radius().executes(ctx -> named(ctx, IntegerArgumentType.getInteger(ctx, "radius"), save))));
    }

    private static int named(CommandContext<CommandSourceStack> ctx, int radius, boolean save) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            send(ctx.getSource(), "A name is letters, digits, - and _ (32 at most).");
            return 0;
        }
        Scan scan = scan(ctx.getSource(), radius, false);
        if (!header(ctx.getSource(), scan)) return 0;
        return save ? save(ctx.getSource(), name, scan) : diff(ctx.getSource(), name, scan);
    }

    // ---------------------------------------------------------------- scan

    /** One chunk's counts, for the export. */
    private record ChunkCounts(int x, int z, Map<String, Long> counts) {}

    /** What the loaded chunks hold. {@code bands}: section Y -> name -> count; {@code exactByY}: export only. */
    private record Scan(ServerLevel level, ChunkPos center, int radius, int loaded, long millis,
                        TreeMap<Integer, Map<String, Long>> bands, Set<String> ores,
                        Map<String, long[]> exactByY, List<ChunkCounts> chunks, List<String> unknown,
                        Map<String, Long> total, List<Map.Entry<String, Long>> rankedOres,
                        List<Map.Entry<String, Long>> rankedOthers, long oreTotal) {
        int side() { return radius * 2 + 1; }
    }

    private static Scan scan(CommandSourceStack src, int radius, boolean exact) {
        long start = System.nanoTime();
        ServerLevel level = src.getLevel();
        ChunkPos center = ChunkPos.containing(BlockPos.containing(src.getPosition()));
        Set<Block> extra = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String id : VeinConfig.CENSUS_BLOCKS.get()) {
            Identifier rl = Identifier.tryParse(id);
            var block = rl == null ? java.util.Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isPresent()) extra.add(block.get()); else unknown.add(id);
        }

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
                    if (exact && any[0]) {
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
                if (exact) chunkRows.add(new ChunkCounts(cx, cz, chunkCounts));
            }
        }

        Map<String, Long> total = new HashMap<>();
        bands.values().forEach(band -> band.forEach((k, v) -> total.merge(k, v, Long::sum)));
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(total.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<Map.Entry<String, Long>> rankedOres = sorted.stream().filter(e -> ores.contains(e.getKey())).toList();
        List<Map.Entry<String, Long>> rankedOthers = sorted.stream().filter(e -> !ores.contains(e.getKey())).toList();
        long oreTotal = rankedOres.stream().mapToLong(Map.Entry::getValue).sum();
        return new Scan(level, center, radius, loaded, (System.nanoTime() - start) / 1_000_000, bands, ores,
                exactByY, chunkRows, unknown, total, rankedOres, rankedOthers, oreTotal);
    }

    /** "" = not counted. Records the ore names: their share is reported against the total of all ores. */
    private static String nameOf(BlockState state, Set<Block> extra, Set<String> ores) {
        boolean ore = state.is(Tags.Blocks.ORES);
        if (!ore && !state.is(Tags.Blocks.STORAGE_BLOCKS) && !extra.contains(state.getBlock())) return "";
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        // The deepslate variant counts with the stone one: it is the same ore at another depth.
        if (ore && path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
        String name = id.getNamespace().equals("minecraft") ? path : id.getNamespace() + ":" + path;
        if (ore) ores.add(name);
        return name;
    }

    // ---------------------------------------------------------------- chat views

    /** The first line. False when no chunk was covered: nothing else to say. */
    private static boolean header(CommandSourceStack src, Scan s) {
        int all = s.side() * s.side();
        String dim = s.level().dimension().identifier().getPath();
        if (!s.unknown().isEmpty()) send(src, "Ignored unknown ids in census.blocks: " + String.join(", ", s.unknown()));
        if (s.loaded() == 0) {
            send(src, "Census " + dim + ": no chunk loaded within " + s.radius() * 16 + " blocks. Walk around, or use a bigger radius.");
            return false;
        }
        MutableComponent line = Component.literal("Census " + dim + ": " + s.loaded() + "/" + all + " chunks, "
                + s.rankedOres().size() + " ores, " + fmt(s.oreTotal()) + " (" + perChunk(s.oreTotal(), s.loaded()) + "/chunk)");
        if (s.loaded() < all) line.append(Component.literal("  only loaded chunks count").withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> line, false);
        return true;
    }

    private static int summary(CommandSourceStack src, int radius, boolean export) {
        Scan s = scan(src, radius, export);
        if (!header(src, s)) return 0;
        s.rankedOres().stream().limit(TOP).forEach(e -> line(src, e, s, radius));
        List<String> mods = modLines(s);
        if (mods.size() > 1) send(src, "  mods: " + String.join(", ", mods.stream().limit(3).toList())
                + (mods.size() > 3 ? ", +" + (mods.size() - 3) : ""));
        MutableComponent buttons = Component.literal("  ");
        buttons.append(button("all " + s.rankedOres().size() + " ores", CMD + "list 1 " + radius, "Every ore, " + PAGE + " per page"));
        buttons.append(Component.literal(" "));
        buttons.append(button("mods", CMD + "mod " + radius, "Ores per mod"));
        buttons.append(Component.literal(" "));
        buttons.append(button("heights", CMD + "y " + radius, "Where they are, by 64 blocks of height"));
        if (!export) {
            buttons.append(Component.literal(" "));
            buttons.append(button("export", CMD + radius + " export", "A web page and two CSV files in config/oreveintweaker/census"));
        }
        src.sendSuccess(() -> buttons, false);
        if (export) export(src, s);
        return s.total().size();
    }

    private static int list(CommandSourceStack src, int page, int radius) {
        Scan s = scan(src, radius, false);
        if (!header(src, s)) return 0;
        List<Map.Entry<String, Long>> all = new ArrayList<>(s.rankedOres());
        all.addAll(s.rankedOthers());
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int p = Math.min(page, pages);
        all.subList((p - 1) * PAGE, Math.min(all.size(), p * PAGE)).forEach(e -> line(src, e, s, radius));
        MutableComponent nav = Component.literal("  ");
        if (p > 1) nav.append(button("< " + (p - 1), CMD + "list " + (p - 1) + " " + radius, "Previous page")).append(Component.literal(" "));
        nav.append(Component.literal("page " + p + "/" + pages).withStyle(ChatFormatting.GRAY));
        if (p < pages) nav.append(Component.literal(" ")).append(button((p + 1) + " >", CMD + "list " + (p + 1) + " " + radius, "Next page"));
        src.sendSuccess(() -> nav, false);
        return all.size();
    }

    /** One ore: a bar of its share, its name (click for its profile), its count. Other blocks have no bar. */
    private static void line(CommandSourceStack src, Map.Entry<String, Long> e, Scan s, int radius) {
        String name = e.getKey();
        boolean ore = s.ores().contains(name);
        MutableComponent line = Component.literal("  ");
        if (ore) {
            double share = 100.0 * e.getValue() / s.oreTotal();
            // The bar is drawn against the most common ore (a full bar), the figure beside it is the real share:
            // against 100% the bars of a list of 41 ores would all be a cell long.
            double top = 100.0 * s.rankedOres().get(0).getValue() / s.oreTotal();
            int full = (int) Math.max(share > 0 ? 1 : 0, Math.round(share / top * BAR));
            line.append(Component.literal("█".repeat(full)).withStyle(markColor(name)));
            // The track says nothing the figure does not: dark, so it does not compete with the bar.
            line.append(Component.literal("░".repeat(BAR - full)).withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal(String.format(Locale.ROOT, " %4.1f%% ", share)).withStyle(ChatFormatting.GRAY));
        } else {
            line.append(Component.literal("  block  ").withStyle(ChatFormatting.GRAY));
        }
        line.append(Component.literal(name).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent.RunCommand(CMD + "ore " + radius + " " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Where it is: height profile")))));
        line.append(Component.literal("  " + fmt(e.getValue())).withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> line, false);
    }

    private static List<String> modLines(Scan s) {
        Map<String, Long> byMod = new HashMap<>();
        s.rankedOres().forEach(e -> byMod.merge(modOf(e.getKey()), e.getValue(), Long::sum));
        return byMod.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> e.getKey() + " " + pct(e.getValue(), s.oreTotal())).toList();
    }

    private static int mods(CommandSourceStack src, int radius) {
        Scan s = scan(src, radius, false);
        if (!header(src, s)) return 0;
        Map<String, long[]> byMod = new HashMap<>(); // {blocks, ore types}
        s.rankedOres().forEach(e -> {
            long[] m = byMod.computeIfAbsent(modOf(e.getKey()), k -> new long[2]);
            m[0] += e.getValue();
            m[1]++;
        });
        List<Map.Entry<String, long[]>> sorted = byMod.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).toList();
        double top = 100.0 * sorted.get(0).getValue()[0] / s.oreTotal();
        sorted.stream().limit(8).forEach(e -> {
            double share = 100.0 * e.getValue()[0] / s.oreTotal();
            int full = (int) Math.max(1, Math.round(share / top * BAR));
            src.sendSuccess(() -> Component.literal("  ")
                    .append(Component.literal("█".repeat(full)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("░".repeat(BAR - full)).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(Locale.ROOT, " %4.1f%% ", share)).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(e.getKey()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + e.getValue()[1] + (e.getValue()[1] == 1 ? " ore" : " ores")).withStyle(ChatFormatting.GRAY)), false);
        });
        if (sorted.size() > 8) send(src, "  +" + (sorted.size() - 8) + " more mods: see the export");
        return sorted.size();
    }

    /** Slabs of 64 blocks: six lines for the whole overworld, which is what the chat can hold. */
    private static int heights(CommandSourceStack src, int radius) {
        Scan s = scan(src, radius, false);
        if (!header(src, s)) return 0;
        int minSection = s.level().getMinSectionY(), maxSection = s.level().getMaxSectionY();
        int lines = 0;
        for (int top = maxSection; top > minSection; top -= 4) {
            int bottom = Math.max(minSection, top - 4);
            Map<String, Long> slab = new HashMap<>();
            for (int sy = bottom; sy < top; sy++) {
                Map<String, Long> band = s.bands().get(sy);
                if (band != null) band.forEach((k, v) -> { if (s.ores().contains(k)) slab.merge(k, v, Long::sum); });
            }
            long sum = slab.values().stream().mapToLong(Long::longValue).sum();
            if (sum == 0) continue;
            List<String> best = slab.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(3)
                    .map(e -> shortName(e.getKey()) + " " + pct(e.getValue(), sum)).toList();
            send(src, "  Y " + bottom * 16 + " to " + (top * 16 - 1) + ": " + fmt(sum) + "  " + String.join(", ", best));
            lines++;
        }
        return lines;
    }

    private static int ore(CommandSourceStack src, int radius, String query) {
        Scan s = scan(src, radius, false);
        if (!header(src, s)) return 0;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> found = match(s.total().keySet(), q);
        if (found.isEmpty()) {
            send(src, "No \"" + query + "\" among the " + s.total().size() + " blocks counted here.");
            return 0;
        }
        if (found.size() > 1) {
            MutableComponent some = Component.literal("  Which one? ");
            found.stream().limit(6).forEach(n -> some.append(Component.literal(n + " ").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand(CMD + "ore " + radius + " " + n)))));
            if (found.size() > 6) some.append(Component.literal("+" + (found.size() - 6) + " more: type more of the name").withStyle(ChatFormatting.GRAY));
            src.sendSuccess(() -> some, false);
            return found.size();
        }
        String name = found.get(0);
        long count = s.total().get(name);
        boolean ore = s.ores().contains(name);
        send(src, "  " + name + ": " + fmt(count) + " blocks, " + perChunk(count, s.loaded()) + "/chunk"
                + (ore ? ", " + pct(count, s.oreTotal()) + " of ores" : ""));
        StringBuilder spark = new StringBuilder();
        long max = 0;
        int minSection = s.level().getMinSectionY(), maxSection = s.level().getMaxSectionY();
        long[] per = new long[maxSection - minSection];
        for (int sy = minSection; sy < maxSection; sy++) {
            Map<String, Long> band = s.bands().get(sy);
            per[sy - minSection] = band == null ? 0 : band.getOrDefault(name, 0L);
            max = Math.max(max, per[sy - minSection]);
        }
        for (long v : per) spark.append(v == 0 ? '·' : LEVELS.charAt((int) Math.min(7, Math.round(7.0 * v / max))));
        send(src, "  Y " + minSection * 16 + " " + spark + " " + (maxSection * 16 - 1) + "  (a mark = 16 blocks)");
        long run = 0;
        int lo = -1, hi = -1;
        for (int i = 0; i < per.length; i++) {
            run += per[i];
            if (lo < 0 && run * 4 >= count) lo = i;
            if (hi < 0 && run * 4 >= count * 3) hi = i;
        }
        send(src, "  Half of it between Y " + (minSection + lo) * 16 + " and " + ((minSection + hi) * 16 + 15));
        return 1;
    }

    /** Exact id, then the part after the namespace, then anything containing the text: the first tier with a match. */
    private static List<String> match(Set<String> names, String q) {
        List<String> exact = names.stream().filter(n -> n.equalsIgnoreCase(q) || n.substring(n.indexOf(':') + 1).equalsIgnoreCase(q)).sorted().toList();
        if (!exact.isEmpty()) return exact;
        return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q)).sorted().toList();
    }

    // ---------------------------------------------------------------- save, diff

    private static Path saveFile(String name) {
        return VeinFiles.dir().resolveSibling("census").resolve("saved-" + name + ".csv");
    }

    /** First line: chunks counted; then block,total. Compared per chunk, so the area may differ a little. */
    private static int save(CommandSourceStack src, String name, Scan s) {
        StringBuilder out = new StringBuilder("loaded,").append(s.loaded()).append('\n');
        s.total().forEach((k, v) -> out.append(k).append(',').append(v).append('\n'));
        try {
            Path file = saveFile(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            send(src, "Save failed: " + e.getMessage());
            return 0;
        }
        send(src, "Saved \"" + name + "\". Later: /oreveintweaker census diff " + name);
        return s.total().size();
    }

    /** Only what changed, the biggest change first: with 100 ores, a list of what did not move hides what did. */
    private static int diff(CommandSourceStack src, String name, Scan s) {
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
        s.total().forEach((k, v) -> now.put(k, v / (double) s.loaded()));
        Set<String> names = new HashSet<>(old.keySet());
        names.addAll(now.keySet());
        // Under 0.05 a chunk, a block is noise: "0.0 -> 0.0 (-100%)" says nothing.
        names.removeIf(n -> old.getOrDefault(n, 0.0) < 0.05 && now.getOrDefault(n, 0.0) < 0.05);
        List<String> changed = names.stream().filter(n -> !same(old.getOrDefault(n, 0.0), now.getOrDefault(n, 0.0)))
                .sorted((a, b) -> Double.compare(Math.abs(now.getOrDefault(b, 0.0) - old.getOrDefault(b, 0.0)),
                        Math.abs(now.getOrDefault(a, 0.0) - old.getOrDefault(a, 0.0)))).toList();
        send(src, "Compared with \"" + name + "\" (" + (int) before + " chunks then), per chunk:");
        if (changed.isEmpty()) send(src, "  Nothing changed (" + names.size() + " unchanged).");
        changed.stream().limit(7).forEach(n -> {
            double was = old.getOrDefault(n, 0.0), is = now.getOrDefault(n, 0.0);
            String change = was == 0 ? "new" : String.format(Locale.ROOT, "%+.0f%%", 100 * (is - was) / was);
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s  %.1f -> %.1f  %s", n, was, is, change))
                    .withStyle(is > was ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        });
        if (changed.size() > 7) send(src, "  +" + (changed.size() - 7) + " more changed");
        if (!changed.isEmpty() && names.size() > changed.size()) send(src, "  " + (names.size() - changed.size()) + " unchanged");
        return changed.size();
    }

    private static boolean same(double was, double is) {
        return Math.abs(is - was) <= 0.005 * Math.max(was, is);
    }

    // ---------------------------------------------------------------- export

    /**
     * In config/oreveintweaker/census/: a web page (the whole picture, sortable, one ore at a time), the count of
     * every block at every Y (one column per block, ready for a chart) and in every chunk (ready for a pivot table).
     */
    private static void export(CommandSourceStack src, Scan s) {
        int minY = s.level().getMinY(), height = s.level().getHeight();
        StringBuilder byY = new StringBuilder("y");
        s.exactByY().keySet().forEach(name -> byY.append(',').append(name));
        byY.append('\n');
        for (int i = height - 1; i >= 0; i--) {
            StringBuilder row = new StringBuilder().append(minY + i);
            boolean any = false;
            for (long[] counts : s.exactByY().values()) {
                row.append(',').append(counts[i]);
                any |= counts[i] != 0;
            }
            if (any) byY.append(row).append('\n');
        }
        StringBuilder byChunk = new StringBuilder("chunk_x,chunk_z,block,count\n");
        for (ChunkCounts c : s.chunks()) {
            c.counts().forEach((name, n) -> byChunk.append(c.x()).append(',').append(c.z()).append(',')
                    .append(name).append(',').append(n).append('\n'));
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String prefix = "census-" + s.level().dimension().identifier().toString().replace(':', '_') + "-" + stamp;
        Path dir = VeinFiles.dir().resolveSibling("census");
        Path page = dir.resolve(prefix + ".html");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(prefix + "-by-height.csv"), byY.toString());
            Files.writeString(dir.resolve(prefix + "-by-chunk.csv"), byChunk.toString());
            // What the files are about, for when they are opened weeks later.
            Files.writeString(dir.resolve(prefix + "-info.txt"), "dimension: " + s.level().dimension().identifier()
                    + "\nseed: " + s.level().getSeed() + "\ncenter chunk: " + s.center().x() + " " + s.center().z()
                    + "\nradius: " + s.radius() + " chunks\nchunks counted: " + s.loaded() + "\ndate: " + stamp + "\n");
            Files.writeString(page, html(s, stamp), StandardCharsets.UTF_8);
        } catch (IOException e) {
            send(src, "Export failed: " + e.getMessage());
            return;
        }
        String where = page.toAbsolutePath().toString();
        MutableComponent done = Component.literal("  Exported in " + s.millis() + " ms: ");
        done.append(button("copy the page's path", where, "", true));
        // A chat line carries at most about 256 KB of text.
        if (byY.length() <= 200_000) {
            done.append(Component.literal(" ")).append(button("copy the height table", byY.toString(), "", true));
        }
        src.sendSuccess(() -> done, false);
        send(src, "  " + dir.toAbsolutePath());
    }

    /** The page's data: one entry per counted block, its count at each Y (trimmed to where it exists) and in each chunk. */
    private static String html(Scan s, String stamp) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("dimension", s.level().dimension().identifier().toString());
        root.addProperty("seed", String.valueOf(s.level().getSeed()));
        JsonArray center = new JsonArray();
        center.add(s.center().x());
        center.add(s.center().z());
        root.add("center", center);
        root.addProperty("radius", s.radius());
        root.addProperty("loaded", s.loaded());
        root.addProperty("side", s.side());
        root.addProperty("minY", s.level().getMinY());
        root.addProperty("height", s.level().getHeight());
        root.addProperty("date", stamp);

        boolean grids = (long) s.side() * s.side() * s.total().size() <= 400_000; // a big radius would make a heavy page
        Map<String, int[]> gridOf = new HashMap<>();
        if (grids) {
            int x0 = s.center().x() - s.radius(), z0 = s.center().z() - s.radius();
            for (ChunkCounts c : s.chunks()) {
                c.counts().forEach((name, n) -> gridOf.computeIfAbsent(name, k -> new int[s.side() * s.side()])
                        [(c.x() - x0) * s.side() + (c.z() - z0)] = (int) Math.min(Integer.MAX_VALUE, n));
            }
        }
        List<Map.Entry<String, Long>> all = new ArrayList<>(s.rankedOres());
        all.addAll(s.rankedOthers());
        JsonArray ores = new JsonArray();
        for (var e : all) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.getKey());
            o.addProperty("mod", modOf(e.getKey()));
            o.addProperty("ore", s.ores().contains(e.getKey()));
            o.addProperty("total", e.getValue());
            long[] y = s.exactByY().get(e.getKey());
            if (y != null) {
                int first = 0, last = y.length - 1;
                while (first < last && y[first] == 0) first++;
                while (last > first && y[last] == 0) last--;
                JsonArray counts = new JsonArray();
                for (int i = first; i <= last; i++) counts.add(y[i]);
                o.addProperty("y0", s.level().getMinY() + first);
                o.add("y", counts);
            }
            int[] grid = gridOf.get(e.getKey());
            if (grid != null) {
                JsonArray g = new JsonArray();
                for (int v : grid) g.add(v);
                o.add("grid", g);
            }
            ores.add(o);
        }
        root.add("ores", ores);

        String template;
        try (var in = Census.class.getResourceAsStream("/census.html")) {
            if (in == null) throw new IOException("census.html is missing from the mod");
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Between two markers, never by looking for a line end: a Windows checkout turns the template's "\n" into
        // "\r\n" (which made the first version fail, on all three branches, with an index of -1).
        int at = template.indexOf("/*DATA*/");
        int end = template.indexOf("/*END*/", at);
        if (at < 0 || end < 0) throw new IOException("census.html has lost its data markers");
        // "</" would end the script block early.
        return template.substring(0, at) + root.toString().replace("</", "<\\/") + template.substring(end + "/*END*/".length());
    }

    // ---------------------------------------------------------------- small helpers

    /** The loader-API lines of the chat (PORTING.md): a button that runs a command, or copies text. */
    private static MutableComponent button(String label, String value, String hover) {
        return button(label, value, hover, false);
    }

    private static MutableComponent button(String label, String value, String hover, boolean copy) {
        ClickEvent click = copy ? new ClickEvent.CopyToClipboard(value)
                : new ClickEvent.RunCommand(value);
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(copy ? "Copy to the clipboard" : hover))));
    }

    /**
     * The colour of an ore's bar. Text stays white or grey (4.5:1 on the chat background); only this mark is
     * coloured, and only with colours that keep 3:1 on a chat background that can be as light as #404040:
     * DARK_GRAY, BLUE and the dark red, purple and blue fall under 2:1 there (measured).
     */
    private static final ChatFormatting[] MARKS = {ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.GOLD, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.RED, ChatFormatting.YELLOW};

    private static ChatFormatting markColor(String name) {
        String path = name.substring(name.indexOf(':') + 1);
        if (path.contains("coal")) return ChatFormatting.GRAY;
        if (path.contains("iron") || path.contains("quartz")) return ChatFormatting.WHITE;
        if (path.contains("copper")) return ChatFormatting.GOLD;
        if (path.contains("gold")) return ChatFormatting.YELLOW;
        if (path.contains("redstone")) return ChatFormatting.RED;
        if (path.contains("lapis")) return ChatFormatting.DARK_AQUA;
        if (path.contains("diamond")) return ChatFormatting.AQUA;
        if (path.contains("emerald")) return ChatFormatting.GREEN;
        return MARKS[Math.floorMod(name.hashCode(), MARKS.length)];
    }

    private static String modOf(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? "minecraft" : name.substring(0, colon);
    }

    /** For a narrow column: "coal_ore" -> "coal". */
    private static String shortName(String name) {
        String path = name.substring(name.indexOf(':') + 1);
        return path.endsWith("_ore") ? path.substring(0, path.length() - 4) : path;
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String perChunk(long n, int chunks) {
        double v = n / (double) chunks;
        return String.format(Locale.ROOT, v < 10 ? "%.1f" : "%,.0f", v);
    }

    private static String pct(long part, long whole) {
        double v = 100.0 * part / whole;
        return String.format(Locale.ROOT, v < 10 ? "%.1f%%" : "%.0f%%", v);
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }
}
