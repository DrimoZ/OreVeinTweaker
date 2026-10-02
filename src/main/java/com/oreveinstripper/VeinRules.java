package com.oreveinstripper;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class VeinRules {
    private static final Logger LOG = LogUtils.getLogger();

    // Block placed by vanilla OreVeinifier -> block to place instead. A null value removes the vein.
    // Read from worldgen threads: the whole map is swapped, never mutated in place.
    private static volatile Map<Block, BlockState> rules = Map.of();

    private VeinRules() {}

    /** @return the block to place, or null to fall back to base stone/deepslate. */
    @Nullable
    public static BlockState apply(BlockState vanilla) {
        // HashMap.getOrDefault returns null when the key is explicitly mapped to null
        return rules.getOrDefault(vanilla.getBlock(), vanilla);
    }

    public static void rebuild() {
        Map<Block, BlockState> next = new HashMap<>();
        add(next, VeinConfig.COPPER, Blocks.COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.GRANITE);
        add(next, VeinConfig.IRON, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.TUFF);
        rules = next;
    }

    private static void add(Map<Block, BlockState> map, VeinConfig.Vein cfg, Block ore, Block raw, Block filler) {
        boolean on = cfg.enabled().get();
        map.put(ore, on ? resolve(cfg.ore().get(), ore) : null);
        map.put(raw, on ? resolve(cfg.rawBlock().get(), raw) : null);
        map.put(filler, on ? resolve(cfg.filler().get(), filler) : null);
    }

    private static BlockState resolve(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                .map(Block::defaultBlockState)
                .orElseGet(() -> {
                    LOG.warn("[{}] Unknown block '{}', keeping {}", OreVeinStripper.MOD_ID, id,
                            BuiltInRegistries.BLOCK.getKey(fallback));
                    return fallback.defaultBlockState();
                });
    }
}
