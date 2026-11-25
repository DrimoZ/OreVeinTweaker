package dev.drimoz.oreveintweaker;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * `census ore <text>`: which counted blocks a text names, and what a group of them adds up to ("bituminous":
 * the same ore in every stone it comes in, each a block of its own). No Minecraft class: plain JUnit tests it.
 */
final class OreMatch {
    private OreMatch() {}

    /** What the ores of a group add up to. */
    record Group(int ores, long blocks) {}

    /** Exact id, then the part after the namespace, then anything containing the text: the first tier with a match. */
    static List<String> match(Set<String> names, String q) {
        String text = q.trim().toLowerCase(Locale.ROOT);
        List<String> exact = names.stream()
                .filter(n -> n.equalsIgnoreCase(text) || n.substring(n.indexOf(':') + 1).equalsIgnoreCase(text)).sorted().toList();
        if (!exact.isEmpty()) return exact;
        return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(text)).sorted().toList();
    }

    /**
     * The family a block's tags put an ore in: "coal" for #c:ores/coal (#forge:ores/coal on Forge). The tag is the
     * pack author's own statement that two blocks are the same ore, so it needs no list in this mod. Tags that only
     * look alike (ores_in_ground/stone, ores/netherite_scrap are fine: they are ores) are not: only ores/&lt;x&gt;.
     * With several, the alphabetically first, so a block never changes group between two exports.
     */
    static String family(Collection<String> tagIds) {
        return tagIds.stream()
                .map(t -> t.substring(t.indexOf(':') + 1))
                .filter(p -> p.startsWith("ores/") && p.length() > 5 && p.indexOf('/', 5) < 0)
                .map(p -> p.substring(5))
                .sorted().findFirst().orElse(null);
    }

    /** Only the ores: raw and storage blocks are not part of "share of ores", so they would inflate the group. */
    static Group group(Collection<String> names, Set<String> ores, Map<String, Long> total) {
        int n = 0;
        long blocks = 0;
        for (String name : names) {
            if (!ores.contains(name)) continue;
            n++;
            blocks += total.getOrDefault(name, 0L);
        }
        return new Group(n, blocks);
    }
}
