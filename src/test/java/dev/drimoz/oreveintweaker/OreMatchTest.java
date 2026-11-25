package dev.drimoz.oreveintweaker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The case that started it: one ore, one block per stone it comes in. */
class OreMatchTest {
    // Names as the census records them: namespace dropped for minecraft, deepslate merged into the stone ore.
    private static final Map<String, Long> TOTAL = Map.of(
            "immersivegeology:poor_ore_block_bituminous_stone", 4546L,
            "immersivegeology:normal_ore_block_bituminous_stone", 2044L,
            "immersivegeology:rich_ore_block_bituminous_stone", 1377L,
            "immersivegeology:poor_ore_block_bituminous_chert", 895L,
            "immersivegeology:raw_bituminous", 30L,   // a raw block: counted in the world, not an ore
            "coal_ore", 9000L,
            "iron_ore", 500L);
    private static final Set<String> ORES = Set.of(
            "immersivegeology:poor_ore_block_bituminous_stone", "immersivegeology:normal_ore_block_bituminous_stone",
            "immersivegeology:rich_ore_block_bituminous_stone", "immersivegeology:poor_ore_block_bituminous_chert",
            "coal_ore", "iron_ore");

    @Test
    void aWordFindsEveryStoneTheOreComesIn() {
        List<String> found = OreMatch.match(TOTAL.keySet(), "bituminous");
        assertEquals(5, found.size()); // four ores and the raw block
        assertTrue(found.stream().allMatch(n -> n.contains("bituminous")));
    }

    @Test
    void theGroupAddsUpTheOresOnly() {
        OreMatch.Group g = OreMatch.group(OreMatch.match(TOTAL.keySet(), "bituminous"), ORES, TOTAL);
        assertEquals(4, g.ores());
        assertEquals(4546 + 2044 + 1377 + 895, g.blocks()); // the 30 raw blocks are not in it
    }

    @Test
    void matchingIsCaseAndSpaceInsensitive() {
        assertEquals(OreMatch.match(TOTAL.keySet(), "bituminous"), OreMatch.match(TOTAL.keySet(), "  BITUMINOUS "));
    }

    @Test
    void anExactNameWinsOverAPartOfAnotherName() {
        Set<String> names = Set.of("iron_ore", "create:iron_ore_deep", "deepslate_iron_ore_x");
        assertEquals(List.of("iron_ore"), OreMatch.match(names, "iron_ore"));
    }

    @Test
    void theIdWithoutItsNamespaceIsExactToo() {
        Set<String> names = Set.of("create:zinc_ore", "create:zinc_ore_big");
        assertEquals(List.of("create:zinc_ore"), OreMatch.match(names, "zinc_ore"));
    }

    @Test
    void nothingMatchesNothing() {
        assertTrue(OreMatch.match(TOTAL.keySet(), "unobtainium").isEmpty());
        OreMatch.Group g = OreMatch.group(List.of(), ORES, TOTAL);
        assertEquals(0, g.ores());
        assertEquals(0, g.blocks());
    }

    @Test
    void aSingleOreIsOneOreNotAGroup() {
        assertEquals(1, OreMatch.group(OreMatch.match(TOTAL.keySet(), "coal"), ORES, TOTAL).ores());
    }

    @Test
    void theTagPutsAnOreInItsFamily() {
        // The real tags of "dense_deepslate_coal_ore": the pack's own #c:ores/coal wins over look-alikes.
        assertEquals("coal", OreMatch.family(List.of("c:ores", "c:ores/coal", "c:ores_in_ground/deepslate", "minecraft:mineable/pickaxe")));
        assertEquals("coal", OreMatch.family(List.of("forge:ores/coal"))); // Forge says forge:
        assertEquals("a", OreMatch.family(List.of("c:ores/b", "c:ores/a"))); // never changes between two exports
    }

    @Test
    void noTagNoFamily() {
        assertEquals(null, OreMatch.family(List.of("c:ores", "c:ores_in_ground/stone", "minecraft:mineable/pickaxe")));
        assertEquals(null, OreMatch.family(List.of("c:ores/")));          // an empty name is no family
        assertEquals(null, OreMatch.family(List.of("c:ores/a/b")));       // only ores/<x>, not a sub-tag of one
        assertEquals(null, OreMatch.family(List.of()));
    }

    @Test
    void aNameWithNoCountCountsZero() {
        assertEquals(0, OreMatch.group(List.of("ghost_ore"), Set.of("ghost_ore"), Map.of()).blocks());
    }
}
