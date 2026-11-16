package dev.drimoz.oreveintweaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * /oreveintweaker strip - removes everything but the ores around you, so only the veins are left to look
 * at (and to /oreveintweaker census). What goes and what stays comes from the [strip] config: the
 * built-in rules (ores, storage blocks, bedrock), a `keep` list and a `remove` list (which wins), all
 * editable with the `keep` / `remove` sub-commands. `only` removes just the blocks it is given, once.
 * It works a few chunk columns per tick, so the server keeps running and a bar shows the progress.
 * No undo, and builds in the range go too: it is a test world tool, and operators only.
 */
final class Strip {
    private static final int MAX_RADIUS = 16;
    private static final int HOTBAR = 9;
    private static final double REACH = 20;
    /** What one tick may spend; at least one chunk column runs each tick, however slow. */
    private static final long TICK_BUDGET_NS = 25_000_000L;
    private static final int BAR = 20;
    private static final int GRACE_TICKS = 100;
    private static final String CMD = "/oreveintweaker strip ";
    private static final String[] WORDS = {"target", "hand", "hotbar", "inventory"};

    private Strip() {}

    /** Blocks and block tags, as the config lists write them: "minecraft:tuff", "#forge:ores/gold". */
    private record BlockSet(Set<Block> blocks, Set<TagKey<Block>> tags) {
        boolean has(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) if (state.is(tag)) return true;
            return false;
        }
    }

    /** One run. Server thread only. */
    private static final class Job {
        final CommandSourceStack src;
        final ServerLevel level;
        final UUID player;
        final int firstX, firstZ, side, total, minY, maxY;
        final Predicate<BlockState> wanted;
        final long start = System.nanoTime();
        int next;
        long removed;

        Job(CommandSourceStack src, int radius, int minY, int maxY, Predicate<BlockState> toRemove) {
            this.src = src;
            this.level = src.getLevel();
            this.player = src.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
            ChunkPos center = new ChunkPos(BlockPos.containing(src.getPosition()));
            this.firstX = center.x - radius;
            this.firstZ = center.z - radius;
            this.side = 2 * radius + 1;
            this.total = side * side;
            this.minY = minY;
            this.maxY = maxY;
            this.wanted = s -> !s.isAir() && toRemove.test(s);
        }
    }

    // Read and written from the command and the tick event, both on the server thread.
    private static Job job;

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oreveintweaker")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("strip")
                        .executes(ctx -> run(ctx.getSource(), defaultRadius(), null, null, null))
                        .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                        .then(Commands.literal("list").executes(ctx -> status(ctx.getSource())))
                        .then(listNode("keep", VeinConfig.STRIP_KEEP, VeinConfig.STRIP_REMOVE))
                        .then(listNode("remove", VeinConfig.STRIP_REMOVE, VeinConfig.STRIP_KEEP))
                        // `only [radius [min_y max_y]] <blocks>`: the blocks come last, being free text.
                        .then(Commands.literal("only")
                                .then(radius().then(heights(true)).then(only(false)))
                                .then(only(false)))
                        .then(radius()
                                .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), null, null, null))
                                .then(heights(false)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> radius() {
        return Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS));
    }

    /** `<min_y> <max_y>`, then either the run itself or, for `only`, the blocks. */
    private static ArgumentBuilder<CommandSourceStack, ?> heights(boolean only) {
        var max = Commands.argument("max_y", IntegerArgumentType.integer(-2032, 2031));
        if (only) max.then(only(true));
        else max.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                IntegerArgumentType.getInteger(ctx, "min_y"), IntegerArgumentType.getInteger(ctx, "max_y"), null));
        return Commands.argument("min_y", IntegerArgumentType.integer(-2032, 2031)).then(max);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> only(boolean withHeights) {
        return selector().executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            BlockSet blocks = parse(select(src, StringArgumentType.getString(ctx, "blocks")), new ArrayList<>());
            // A node the command did not pass through has no such argument: Brigadier throws, the default applies.
            int r = hasArg(ctx, "radius") ? IntegerArgumentType.getInteger(ctx, "radius") : defaultRadius();
            Integer lo = withHeights ? IntegerArgumentType.getInteger(ctx, "min_y") : null;
            Integer hi = withHeights ? IntegerArgumentType.getInteger(ctx, "max_y") : null;
            return run(src, r, lo, hi, blocks);
        });
    }

    private static boolean hasArg(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            IntegerArgumentType.getInteger(ctx, name);
            return true;
        } catch (IllegalArgumentException absent) {
            return false;
        }
    }

    private static int defaultRadius() {
        return VeinConfig.STRIP_RADIUS.get();
    }

    /** `keep|remove add|del <blocks>`, `list`, `clear`; `other` is the opposite list, for the conflict note. */
    private static ArgumentBuilder<CommandSourceStack, ?> listNode(String name,
            ForgeConfigSpec.ConfigValue<List<? extends String>> list, ForgeConfigSpec.ConfigValue<List<? extends String>> other) {
        return Commands.literal(name)
                .then(Commands.literal("add")
                        .executes(ctx -> edit(ctx.getSource(), name, list, other, "target", true))
                        .then(selector().executes(ctx -> edit(ctx.getSource(), name, list, other,
                                StringArgumentType.getString(ctx, "blocks"), true))))
                .then(Commands.literal("del")
                        .executes(ctx -> edit(ctx.getSource(), name, list, other, "target", false))
                        .then(selector().executes(ctx -> edit(ctx.getSource(), name, list, other,
                                StringArgumentType.getString(ctx, "blocks"), false))))
                .then(Commands.literal("list").executes(ctx -> show(ctx.getSource(), name, list)))
                .then(Commands.literal("clear").executes(ctx -> {
                    int n = read(list).size();
                    write(list, new ArrayList<>());
                    ctx.getSource().sendSuccess(() -> Component.literal("Emptied the " + name + " list (" + n + " entries)"), false);
                    return n;
                }));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> selector() {
        return Commands.argument("blocks", StringArgumentType.greedyString()).suggests(Strip::suggest);
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.concat(Stream.of(WORDS), Stream.concat(
                BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::toString),
                BuiltInRegistries.BLOCK.getTagNames().map(t -> "#" + t.location()))), builder);
    }

    // ---------------------------------------------------------------- selecting blocks

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    /**
     * What the player means by `text`, as config entries: `target` (the block looked at), `hand`, `hotbar`,
     * `inventory`, a block id (the namespace may be left out when only one block has that name), or `#tag`.
     */
    private static List<String> select(CommandSourceStack src, String text) throws CommandSyntaxException {
        String word = text.trim();
        Set<String> out = new LinkedHashSet<>();
        switch (word.toLowerCase()) {
            case "target" -> {
                ServerPlayer player = src.getPlayerOrException();
                if (!(player.pick(REACH, 0, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
                    throw error("No block in sight (within " + (int) REACH + " blocks)");
                out.add(id(player.level().getBlockState(hit.getBlockPos()).getBlock()));
            }
            case "hand" -> {
                ServerPlayer player = src.getPlayerOrException();
                add(out, player.getMainHandItem());
                add(out, player.getOffhandItem());
                if (out.isEmpty()) throw error("No block in your hands");
            }
            case "hotbar", "inventory" -> {
                ServerPlayer player = src.getPlayerOrException();
                var items = player.getInventory().items;
                for (ItemStack stack : word.equalsIgnoreCase("hotbar") ? items.subList(0, HOTBAR) : items) add(out, stack);
                add(out, player.getOffhandItem());
                if (out.isEmpty()) throw error("No block in your " + word.toLowerCase());
            }
            default -> out.add(entry(word));
        }
        return new ArrayList<>(out);
    }

    private static void add(Set<String> out, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && !item.getBlock().defaultBlockState().isAir()) out.add(id(item.getBlock()));
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** A typed block id or #tag, checked against the registries. */
    private static String entry(String text) throws CommandSyntaxException {
        if (text.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(text.substring(1));
            if (rl == null || BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).isEmpty())
                throw error("Unknown block tag " + text);
            return text;
        }
        ResourceLocation rl = ResourceLocation.tryParse(text);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) return rl.toString();
        if (!text.contains(":")) {
            List<String> named = BuiltInRegistries.BLOCK.keySet().stream()
                    .filter(k -> k.getPath().equals(text)).map(ResourceLocation::toString).sorted().toList();
            if (named.size() == 1) return named.get(0);
            if (named.size() > 1) throw error(text + " is in several mods: " + String.join(", ", named));
        }
        throw error("Unknown block " + text);
    }

    /** Resolves config entries; what no longer exists (a removed mod) is reported in `unknown`, never fatal. */
    private static BlockSet parse(Collection<? extends String> entries, List<String> unknown) {
        Set<Block> blocks = new HashSet<>();
        Set<TagKey<Block>> tags = new HashSet<>();
        for (String e : entries) {
            boolean tag = e.startsWith("#");
            ResourceLocation rl = ResourceLocation.tryParse(tag ? e.substring(1) : e);
            if (rl == null) {
                unknown.add(e);
            } else if (tag) {
                TagKey<Block> key = TagKey.create(Registries.BLOCK, rl);
                if (BuiltInRegistries.BLOCK.getTag(key).isPresent()) tags.add(key); else unknown.add(e);
            } else {
                BuiltInRegistries.BLOCK.getOptional(rl).ifPresentOrElse(blocks::add, () -> unknown.add(e));
            }
        }
        return new BlockSet(blocks, tags);
    }

    // ---------------------------------------------------------------- the lists

    /**
     * Forge's file watcher reloads the config right after our own save(), and once it read the file
     * mid-write, called it "not correct" and put the list back to empty (the list showed 0 just after an add).
     * So the last list written is kept here for a few seconds: read from, and written again if the config lost it.
     */
    private static final class Written {
        final List<String> list;
        int left = GRACE_TICKS;
        Written(List<String> list) { this.list = list; }
    }

    private static final java.util.Map<ForgeConfigSpec.ConfigValue<List<? extends String>>, Written> written = new java.util.HashMap<>();
    private static int clock;

    private static List<String> read(ForgeConfigSpec.ConfigValue<List<? extends String>> cfg) {
        Written w = written.get(cfg);
        return new ArrayList<>(w != null ? w.list : cfg.get());
    }

    private static void write(ForgeConfigSpec.ConfigValue<List<? extends String>> cfg, List<String> list) {
        written.put(cfg, new Written(list));
        cfg.set(list);
        cfg.save();
    }

    /** Once a second: put back what the watcher dropped, and forget a list once it has been safe for the grace period. */
    private static void recheck() {
        written.entrySet().removeIf(e -> {
            if (!e.getKey().get().equals(e.getValue().list)) {
                e.getKey().set(e.getValue().list);
                e.getKey().save();
            }
            return (e.getValue().left -= 20) <= 0;
        });
    }

    private static int edit(CommandSourceStack src, String name, ForgeConfigSpec.ConfigValue<List<? extends String>> list,
                            ForgeConfigSpec.ConfigValue<List<? extends String>> other, String blocks, boolean add)
            throws CommandSyntaxException {
        List<String> current = read(list);
        List<String> changed = new ArrayList<>(), same = new ArrayList<>(), shadowed = new ArrayList<>();
        for (String e : select(src, blocks)) {
            boolean did = add ? !current.contains(e) && current.add(e) : current.remove(e);
            (did ? changed : same).add(e);
            // `remove` wins over `keep`: say so instead of letting a kept block vanish unexplained.
            if (did && add && name.equals("keep") && read(other).contains(e)) shadowed.add(e);
        }
        if (!changed.isEmpty()) {
            write(list, current);
        }
        MutableComponent msg = Component.literal((add ? "Added to " : "Removed from ") + name + ": ")
                .append(Component.literal(changed.isEmpty() ? "nothing" : names(changed)).withStyle(ChatFormatting.WHITE));
        if (!same.isEmpty()) msg.append(Component.literal(" (" + names(same) + (add ? " already there)" : " not in the list)"))
                .withStyle(ChatFormatting.GRAY));
        if (!shadowed.isEmpty()) msg.append(Component.literal(" - also in the remove list, which wins: " + names(shadowed))
                .withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> msg, false);
        return changed.size();
    }

    private static String names(List<String> entries) {
        return entries.size() <= 4 ? String.join(", ", entries)
                : String.join(", ", entries.subList(0, 4)) + " +" + (entries.size() - 4) + " more";
    }

    private static int show(CommandSourceStack src, String name, ForgeConfigSpec.ConfigValue<List<? extends String>> list) {
        List<String> entries = read(list);
        String title = Character.toUpperCase(name.charAt(0)) + name.substring(1) + " (" + entries.size() + ")";
        src.sendSuccess(() -> Component.literal(title).withStyle(ChatFormatting.WHITE).append(
                Component.literal(" ").append(Census.button("+ target", CMD + name + " add target", "Add the block you look at", false))
                        .append(Component.literal(" ")).append(Census.button("+ hotbar", CMD + name + " add hotbar", "Add your hotbar's blocks", false))), false);
        for (String e : entries) {
            src.sendSuccess(() -> Component.literal("  " + e + " ").withStyle(ChatFormatting.GRAY)
                    .append(Census.button("x", CMD + name + " del " + e, "Take it off the " + name + " list", false)), false);
        }
        return entries.size();
    }

    private static int status(CommandSourceStack src) {
        show(src, "keep", VeinConfig.STRIP_KEEP);
        show(src, "remove", VeinConfig.STRIP_REMOVE);
        src.sendSuccess(() -> Component.literal("Also kept: " + String.join(", ", builtIns())
                + ". Y " + VeinConfig.STRIP_MIN_Y.get() + " to " + VeinConfig.STRIP_MAX_Y.get()
                + ", radius " + defaultRadius() + ".").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static List<String> builtIns() {
        List<String> kept = new ArrayList<>();
        if (VeinConfig.STRIP_KEEP_ORES.get()) kept.add("ores");
        if (VeinConfig.STRIP_KEEP_STORAGE.get()) kept.add("storage blocks");
        if (VeinConfig.STRIP_KEEP_BEDROCK.get()) kept.add("bedrock");
        if (kept.isEmpty()) kept.add("nothing else");
        return kept;
    }

    // ---------------------------------------------------------------- the run

    /** `only` null = the config's rules; otherwise only those blocks go, whatever they are. */
    private static int run(CommandSourceStack src, int radius, Integer minY, Integer maxY, BlockSet only)
            throws CommandSyntaxException {
        if (job != null) throw error("A strip is already running: /oreveintweaker strip cancel stops it");
        ServerLevel level = src.getLevel();
        int lo = Math.max(minY != null ? minY : VeinConfig.STRIP_MIN_Y.get(), level.getMinBuildHeight());
        int hi = Math.min((maxY != null ? maxY : VeinConfig.STRIP_MAX_Y.get()) + 1, level.getMaxBuildHeight());
        if (lo >= hi) throw error("Nothing to clear between Y " + lo + " and " + (hi - 1) + " in this dimension");

        List<String> unknown = new ArrayList<>();
        Predicate<BlockState> toRemove;
        if (only != null) {
            toRemove = only::has;
        } else {
            BlockSet keep = parse(read(VeinConfig.STRIP_KEEP), unknown);
            BlockSet remove = parse(read(VeinConfig.STRIP_REMOVE), unknown);
            boolean ores = VeinConfig.STRIP_KEEP_ORES.get(), storage = VeinConfig.STRIP_KEEP_STORAGE.get(),
                    bedrock = VeinConfig.STRIP_KEEP_BEDROCK.get();
            toRemove = s -> remove.has(s) || !(keep.has(s) || ores && s.is(Tags.Blocks.ORES)
                    || storage && s.is(Tags.Blocks.STORAGE_BLOCKS) || bedrock && s.is(Blocks.BEDROCK));
        }
        Job started = new Job(src, radius, lo, hi, toRemove);
        job = started;

        String what = only != null ? "Removing the chosen blocks" : "Stripping";
        src.sendSuccess(() -> Component.literal(what + " over " + started.total + " chunks, Y " + started.minY + " to "
                + (started.maxY - 1) + ". Progress is in the action bar; /oreveintweaker strip cancel stops it."), false);
        if (!unknown.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Ignored, not in this game: " + names(unknown)).withStyle(ChatFormatting.GRAY), false);
        }
        return started.total;
    }

    private static int cancel(CommandSourceStack src) throws CommandSyntaxException {
        if (job == null) throw error("No strip is running");
        finish(true);
        return 1;
    }

    static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!written.isEmpty() && ++clock % 20 == 0) recheck();
        if (job == null) return;
        Job j = job;
        long deadline = System.nanoTime() + TICK_BUDGET_NS;
        do {
            column(j, j.firstX + j.next / j.side, j.firstZ + j.next % j.side);
            j.next++;
        } while (j.next < j.total && System.nanoTime() < deadline);

        if (j.next >= j.total) {
            finish(false);
        } else {
            ServerPlayer player = j.player == null ? null : j.src.getServer().getPlayerList().getPlayer(j.player);
            if (player != null) player.sendSystemMessage(bar(j), true);
        }
    }

    static void onStop(ServerStoppingEvent event) {
        job = null; // its level is going away; a world opened next must not inherit it
        written.clear();
    }

    // ponytail: getChunk generates a chunk that was never visited, on this thread - the budget cannot cut that short.
    private static void column(Job j, int cx, int cz) {
        LevelChunk chunk = j.level.getChunk(cx, cz);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = chunk.getSectionIndex(j.minY); i <= chunk.getSectionIndex(j.maxY - 1); i++) {
            LevelChunkSection section = chunk.getSection(i);
            // The palette says whether anything in the section is wanted: most sections cost nothing.
            if (section.hasOnlyAir() || !section.maybeHas(j.wanted)) continue;
            int base = (chunk.getMinSection() + i) << 4;
            for (int y = Math.max(j.minY, base); y < Math.min(j.maxY, base + 16); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (j.wanted.test(section.getBlockState(x, y - base, z))) {
                            j.level.setBlock(pos.set((cx << 4) + x, y, (cz << 4) + z), air,
                                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            j.removed++;
                        }
                    }
                }
            }
        }
    }

    private static void finish(boolean cancelled) {
        Job j = job;
        job = null;
        String text = (cancelled ? "Cancelled after " + j.next + " of " + j.total + " chunks: " : "Stripped ")
                + String.format("%,d", j.removed) + " blocks";
        LogUtils.getLogger().info("[{}] {} {} ({} chunks)", OreVeinTweaker.MOD_ID, j.src.getTextName(), text, j.next);
        j.src.sendSuccess(() -> Component.literal(text), true);
    }

    private static Component bar(Job j) {
        int filled = j.next * BAR / j.total;
        long left = (System.nanoTime() - j.start) / 1_000_000_000L * (j.total - j.next) / Math.max(1, j.next);
        return Component.literal("Strip " + "█".repeat(filled) + "░".repeat(BAR - filled) + " " + j.next * 100 / j.total + "%  "
                + String.format("%,d", j.removed) + " blocks  ~" + (left < 90 ? left + " s" : left / 60 + " min"));
    }
}
