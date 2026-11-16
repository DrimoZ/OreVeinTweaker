# Ore Vein Tweaker

Control Minecraft's large ore veins - the long ribbons of copper in granite and iron in tuff added in
1.18 - from one config file: turn them off, resize them, make them richer or poorer, rebuild them from
any block, or add new kinds of veins. Server-side only.

This page is the full guide. The short version is the
[CurseForge page](https://www.curseforge.com/minecraft/mc-mods/oreveintweaker).

- [Getting started](#getting-started)
- [The config file](#the-config-file)
- [New veins](#new-veins)
- [Recipes](#recipes)
- [Complete examples](#complete-examples)
- [Commands](#commands)
- [Counting what generated](#counting-what-generated)
- [How precise is it?](#how-precise-is-it)
- [Compatibility](#compatibility)
- [Versions and building](#versions-and-building)
- [Permissions](#permissions)

## Getting started

1. Install the mod. On a server, **only on the server**: players join with an unmodded game. In
   singleplayer, put it in your mods folder (the game runs its own server).
2. Start once: the mod writes `config/oreveintweaker-common.toml`.
3. Edit it, save, and **explore new chunks**. The file reloads while the game runs: no restart.

**Only chunks generated after a change are affected.** Explored areas keep their veins, and the
border with new chunks can be visible. Best used on a new world, or as a modpack's default config.

All defaults are vanilla: installing the mod changes nothing until you edit the file.

## The config file

`config/oreveintweaker-common.toml` tunes the two vanilla veins, `[copper]` and `[iron]`, and holds
`[census]` for the [census command](#counting-what-generated). **New veins are not in this file: each
one has its own small file** in `config/oreveintweaker/veins/`, as many as you want (see
[New veins](#new-veins)). For older configs the file also keeps three slots, `[extra_1]` to `[extra_3]`,
that do the same thing; they still work, but a vein file is the way to go. Every option is also explained
inside the files.

The options below are the same everywhere, in the main file and in a vein file:

| Option | Default | What it does |
|---|---|---|
| `enabled` | `true` (`false` for a slot) | `false` = this vein never generates; plain stone or deepslate is left in its place. |
| `size` | `1.0` | Vein volume. `2.0` = about twice (thicker and more frequent), `0.5` = about half, `0` = none. `0` to `4.5`. |
| `ore_amount` | `1.0` | Ore vs filler. `2.0` = twice the ore, `0` = filler only, `5.0` = only ore, no filler. `0` to `5`. |
| `raw_block_amount` | `1.0` | Raw ore blocks among the ore. `5.0` = five times as many, `0` = none, `50` = nearly every ore is a raw block. `0` to `50`. |
| `ore` | vanilla ore | Block used as ore. Any block id, from any mod, or a [palette](#palettes). |
| `raw_block` | vanilla raw block | Block used as raw ore block (a palette works too). |
| `filler` | granite / tuff | Block the ore sits in (a palette works too). |
| `min_y`, `max_y` | none | New veins only: their height range (required in a vein file). |

**Order of effects.** `size` decides where veins are. Inside a vein, `ore_amount` decides ore vs
filler, then `raw_block_amount` decides which ore becomes a raw block. Finally the blocks are swapped
for `ore`, `raw_block` and `filler`.

A block id that does not exist is reported in the log and the vanilla block (stone, for a new vein)
is kept: a typo in a name never breaks generation. An entry that is not written properly (a weight
that is not a positive number, for example) is different: in a vein file, that whole file is skipped
and `/oreveintweaker reload` tells you why.

## New veins

A new vein is **one small TOML file** in `config/oreveintweaker/veins/`: as many files as you want,
one kind of vein each. The first start writes an `_example.toml` there to copy (files starting with `_`
are ignored). A file takes the options above, and some are required:

| Option | In a vein file |
|---|---|
| `ore` | **required** |
| `filler` | **required** |
| `min_y`, `max_y` | **required**, at most 110 apart |
| `raw_block` | optional; when left out, the raw blocks are made of the `ore` block |
| `size`, `ore_amount`, `raw_block_amount` | optional, `1.0` when left out |
| `enabled` | optional, `true` when left out |

```toml
# config/oreveintweaker/veins/zinc.toml
size = 1.5
ore = "create:zinc_ore*3, minecraft:copper_ore"
raw_block = "create:raw_zinc_block"
filler = "minecraft:calcite"
min_y = -20
max_y = 60
```

- **`/oreveintweaker reload`** (operators) re-reads the folder and the config without a restart and
  lists the veins that are on, and any file it had to skip, with the reason. A wrong file is skipped,
  never half-applied; files starting with `_` and `enabled = false` are ignored.
- A vein is at most **110 blocks tall** (`max_y - min_y`): vanilla's vein noises only exist over
  that height. For a taller vein, use two files, one above the other. Veins thin out over the 20
  blocks at each end, like vanilla ones.
- They are built by vanilla's own vein algorithm, with their own ribbons: they do not follow the
  copper or iron veins. `size = 1` gives about as much vein as one vanilla type: they read the vein
  noises at the heights of iron's veins, where measured over a large area they come out at about four
  fifths of copper's volume and the same as iron's (see [How precise is it?](#how-precise-is-it)).
- **The file name decides the ribbons.** A vein keeps its shape and its rolls whatever the other files
  are, so adding or removing a file never moves the others. Renaming a file gives that vein new
  ribbons, for the chunks generated afterwards.
- Where a new vein crosses a vanilla one, the vanilla vein wins. Where two new veins meet, the
  config's slots win, then the files in alphabetical order.
- Each vein costs generation time: measured on 1.20.1, about **1-2% per vein** at 50 to 60 blocks of
  height. Past 16 veins the log says so.
- They generate wherever large veins do - the Overworld, unless a datapack says otherwise.

### The three slots in the config file

`[extra_1]`, `[extra_2]` and `[extra_3]` in `config/oreveintweaker-common.toml` are the same thing
inside the main file: the options above plus `min_y` and `max_y`, off by default, no more than three.
They are kept so that existing configs keep working. For anything new, prefer a vein file: no limit,
and one file per vein is easier to read and to share. The slots come filled with examples, so turning
one on is a single line:

| Slot | Ore | Filler | Height |
|---|---|---|---|
| `extra_1` | deepslate gold ore | smooth basalt | Y -60 to -10 |
| `extra_2` | coal ore | andesite | Y 0 to 60 |
| `extra_3` | deepslate redstone ore | calcite | Y -60 to -20 |

### Palettes

`ore`, `raw_block` and `filler` can list several blocks, with a weight after `*` (1 when left out):
`"create:zinc_ore*3, minecraft:copper_ore"` is three zinc for one copper. It works in the config file
and in vein files, and a single block behaves exactly as before.

## Recipes

Copy the part you need into `config/oreveintweaker-common.toml`, leave the other lines as they are. The
recipes that add a vein are a file of their own, with its path in the first line.

**No large veins at all**
```toml
[copper]
    enabled = false
[iron]
    enabled = false
```

**Rarer but richer iron** - fewer veins, each one worth finding.
```toml
[iron]
    size = 0.5
    ore_amount = 2.0
```

**Huge copper veins** for a building or tech pack.
```toml
[copper]
    size = 3.0
```

**Decorative only** - the granite and tuff ribbons stay, the ore goes.
```toml
[copper]
    ore_amount = 0.0
[iron]
    ore_amount = 0.0
```

**Copper veins become zinc veins** (Create)
```toml
[copper]
    ore = "create:zinc_ore"
    raw_block = "create:raw_zinc_block"
```

**Gold veins, on top of iron** - a new file, at iron's heights.
```toml
# config/oreveintweaker/veins/gold.toml
ore = "minecraft:deepslate_gold_ore"
raw_block = "minecraft:raw_gold_block"
filler = "minecraft:smooth_basalt"
min_y = -60
max_y = -10
```

**Zinc veins next to copper** (Create) - a new file, at copper's heights.
```toml
# config/oreveintweaker/veins/zinc.toml
ore = "create:zinc_ore"
raw_block = "create:raw_zinc_block"
filler = "minecraft:calcite"
min_y = 0
max_y = 60
```

## Complete examples

Four complete setups, one per kind of modpack. The parts for `[copper]` and `[iron]` go in the matching
section of `config/oreveintweaker-common.toml`, in place of the same key; anything you do not paste
stays as it is. A new vein is a file of its own, named in the first line.

**⚔️ Expert pack - rare veins, each one a jackpot**
```toml
[copper]
    size = 0.5              # half as many copper veins...
    ore_amount = 2.0        # ...with twice the ore inside
    raw_block_amount = 3.0  # and three times the raw blocks
[iron]
    size = 0.4
    ore_amount = 2.5
    raw_block_amount = 3.0
```

**⚙️ Create pack - zinc gets its own veins**
```toml
# config/oreveintweaker/veins/zinc.toml
# Copper and iron stay vanilla. Zinc veins run in calcite, through the copper heights.
size = 0.8                          # a bit rarer than copper
ore = "create:zinc_ore"
raw_block = "create:raw_zinc_block"
filler = "minecraft:calcite"
min_y = 0
max_y = 70
```

**💰 Gold rush - the deep is gold and redstone**
```toml
# config/oreveintweaker-common.toml
# The iron veins become gold veins, a little poorer since gold is precious...
[iron]
    ore = "minecraft:deepslate_gold_ore"
    raw_block = "minecraft:raw_gold_block"
    ore_amount = 0.6
```
```toml
# config/oreveintweaker/veins/deep_redstone.toml
# ...and redstone veins in calcite join them, deep down.
ore = "minecraft:deepslate_redstone_ore"
raw_block = "minecraft:redstone_block"
filler = "minecraft:calcite"
min_y = -60
max_y = -20
```

**🏛️ Builder pack - huge stone bands, no free ore**
```toml
# config/oreveintweaker-common.toml
# The veins stay as wide decorative ribbons of granite and tuff...
[copper]
    size = 2.5
    ore_amount = 0.0
[iron]
    size = 2.5
    ore_amount = 0.0
```
```toml
# config/oreveintweaker/veins/dripstone.toml
# ...and a third kind of ribbon, in dripstone. The ore is required but never appears: ore_amount is 0.
size = 2.0
ore_amount = 0.0
ore = "minecraft:coal_ore"
filler = "minecraft:dripstone_block"
min_y = 0
max_y = 60
```

## Commands

All of them are for operators (permission level 2), and none of them is needed to play: the mod works
from its config files alone.

| Command | What it does |
|---|---|
| `/oreveintweaker reload` | re-reads the config and the [vein files](#as-many-veins-as-you-want), lists the veins that are on and any file it skipped, with the reason |
| `/oreveintweaker census ...` | counts the ores around you: [a short summary, one ore, a web page](#counting-what-generated) |
| `/oreveintweaker strip [radius]` | clears everything but the ores around you, [to see the veins](#seeing-only-the-ores) |

## Counting what generated

`/oreveintweaker census [radius]` (operators, permission level 2) tells you how much ore the world
actually holds around you, to check a config or compare two of them. A modded pack has dozens of
ores, so the chat only gives a **summary**, and everything else is one click or one command away:

```
> /oreveintweaker census 4
Census overworld: 81/81 chunks, 41 ores, 56,373 (696/chunk)
  ████████ 28.1% copper_ore  15,822
  █████░░░ 19.1% coal_ore  10,788
  █████░░░ 18.9% iron_ore  10,667
  █░░░░░░░  5.0% lapis_ore  2,801
  █░░░░░░░  4.7% redstone_ore  2,659
  [all 41 ores] [mods] [heights] [export]
```

(A test world where 34 extra blocks are tagged as ores, to stand in for a pack. In game each ore's bar
has its own colour, the name is white and the figures grey, and the bracketed words are buttons.) The
bar is drawn against the most common ore; the figure beside it is the real share of all ores. With
several mods, a line `mods: minecraft 64%, create 13%, ...` sits above the buttons.

| Command | What it shows |
|---|---|
| `census [radius]` | the summary above: the five most common ores, the mods, buttons |
| `census list [page] [radius]` | every ore, 8 per page, then the other counted blocks (raw, storage) |
| `census mod [radius]` | the share of each mod's ores |
| `census y [radius]` | by slabs of 64 blocks of height, each with its three main ores |
| `census ore [radius] <name>` | one ore: its count, a profile by height, where half of it lies |
| `census [radius] export` | a web page and CSV files, below |
| `census save <name>` / `census diff <name>` | compare two configs, below |

Clicking an ore's name in the chat runs `census ore` for it. `<name>` is the id with or without the
mod (`zinc_ore`, `create:zinc_ore`) or part of it (`zinc`); if several match, they are listed to click.

```
> /oreveintweaker census y 4
  Y 64 to 127: 5,867  coal 77%, copper 20%, iron 3.1%
  Y 0 to 63: 29,383  copper 47%, coal 21%, iron 16%
  Y -64 to -1: 21,123  iron 27%, redstone 11%, gold 7.8%

> /oreveintweaker census ore 4 copper_ore
  copper_ore: 15,822 blocks, 195/chunk, 28% of ores
  Y -64 ···▂▃█▇█▃▂··· 319  (a mark = 16 blocks)
  Half of it between Y 16 and 47
```

- The **radius** is in chunks (default 8 = 128 blocks, up to 32), around your position, in your
  current dimension: run it in the Nether and it counts the Nether (quartz, gold, ancient debris), in
  the End it tells you there is no ore. A saved count remembers its dimension, and `diff` refuses to
  compare it with another one.
- It counts the blocks in the world, not what a generator placed: ores of every mod are included (it
  finds them by the `forge:ores` tag), deepslate variants are merged with the stone ones, and it cannot
  tell a vein's ore from a normal one.
- It only reads **loaded** chunks and never generates any: the first line says how many it covered.
  Fly around, or stand in the middle of a pregenerated area, for a bigger sample.
- `[census] blocks` in the config adds any other block to the count, for example
  `["minecraft:andesite", "minecraft:tuff"]` to see how much of your stone is that.
- Percentages are shares of the ores only (extra blocks and raw blocks show counts).

**Export.** `/oreveintweaker census [radius] export` prints the summary and writes to
`config/oreveintweaker/census/` (on the server, for a dedicated one):

- `census-...html`: **the whole picture in one page**, which opens in any browser and needs nothing
  else. A table of every ore (sortable, searchable, filtered by mod) with its count, per-chunk figure,
  share, peak height and a height profile in each row; click a row for the exact count at each Y and a
  map of the chunks. A link ends with `#zinc_ore` to open on one ore. This is the tool for a pack with
  dozens of ores.
- `...-by-height.csv`: one row per block height Y, one column per block, **exact to the block**. Ready
  for a chart.
- `...-by-chunk.csv`: `chunk_x,chunk_z,block,count`, one row per block per chunk: the generation works
  chunk by chunk, so this is the table for spreading, averages and outliers in a spreadsheet.
- `...-info.txt`: what the files are about (dimension, seed, centre chunk, radius, chunks counted, date),
  so they still make sense weeks later.

The chat gives buttons to copy the page's path and the by-height table to the clipboard of whoever
clicks them, which works on a dedicated server too.

**Comparing two configs.** `/oreveintweaker census save <name> [radius]` keeps the current per-chunk
counts under a name (in `config/oreveintweaker/census/`); after changing the config and exploring
new chunks, `/oreveintweaker census diff <name> [radius]` compares what is around you now with it.
Only what changed is listed, the biggest change first (at most 7 lines; the rest is counted in a
line, as is what did not move):

```
> /oreveintweaker census diff vanilla 7
Compared with "vanilla" (225 chunks then), per chunk:
  coal_ore  158.0 -> 108.4  -31%
  copper_ore  115.9 -> 90.9  -22%
  iron_ore  71.1 -> 78.1  +10%
  amethyst_block  9.0 -> 14.7  +64%
```

It compares per chunk, so the two areas need not be the same size, but they should be far apart
enough to be new chunks and big enough to average out the terrain: a different spot of the same world
easily differs by 10-30% on its own (the example above is two spots 640 blocks apart). Use a radius of
10 or more, and read changes under about 15% as noise. Green is more, red is fewer.

### Seeing only the ores

`/oreveintweaker strip [radius]` (operators) clears everything below Y 64 around you except ores, raw
and storage blocks, and bedrock: the veins are left floating, easy to see and to screenshot. It is
meant for a throwaway test world:

- **It cannot be undone**, and it takes anything built below Y 64 with it.
- The radius is in chunks, 0 to 16 (default 2). It sets blocks one by one, so the server freezes
  while it works: a few seconds for a small radius, minutes for 16.

## How precise is it?

The multipliers were calibrated against measurements of vanilla generation, then checked in game, in
two ways:

- the **volume** of the vein region, which `size` is calibrated on: the mod logs it at every world
  load (`vein volume vs vanilla with the current config: copper x1.95`);
- the **blocks placed**, counted with the development build's `/veins` over 576 fresh chunks (24 x 24)
  of one world, the same terrain for every row (copper and iron veins together).

| Setting | Volume (log) | Blocks placed |
|---|---|---|
| `size = 0.5` | x0.51 | x0.46 |
| `size = 2` | x1.95 | x2.00 |
| `size = 3` | x2.92 | x2.97 |
| `size = 4.5` | x4.33 | x4.49 |
| `ore_amount = 2` | | x1.86 the ore |
| `raw_block_amount = 5` | | x4.55 the raw blocks |
| new vein, `size = 1` | x1.00 vanilla copper (noise sample, all heights) | see below |

Measured on 1.20.1. On 26.1 the volume figures are the same, and the blocks placed come out at x1.88
for `size = 2` and x4.26 for `size = 4.5`: its terrain has fewer veins to begin with, so the blocks
counted differ even though the mod's calibration is identical. Read the rows as "about": another seed
moves them by a few percent (the raw blocks are rare, a few hundred over the whole sample). Bigger
veins are also slightly richer at their core, as vanilla ones are: that is how the generator works.

**Counts in one area say little on their own.** Veins are long ribbons, so a 16 x 16 chunk area holds
only a few of them: over six such areas of one world, vanilla copper veins went from 1 to 53,818 blocks,
and a new vein's blocks, against copper's, from x0.22 to x25 (x0.32 over the six together). That is why
every row above compares a setting with the default on the **same** terrain. It is also why a new vein
counted over a single small area can look several times thinner than copper without being so: measured
on the noise itself, over 300,000 points, a new vein at `size = 1` comes out at x0.80 of copper's
volume and x1.01 of iron's, because it reads the noise at iron's heights, where it is a little weaker
than at copper's.

## Compatibility

- **Terrain mods** (Terralith, Tectonic and others): the mod changes no worldgen file, so there is
  nothing to conflict with. It applies to whatever veins the world's generator produces. If a mod or
  datapack turns large veins off entirely, there is nothing left to tweak.
- **Other dimensions**: vanilla only generates large veins in the Overworld. If a datapack enables
  them elsewhere, the same settings, new veins included, apply there.
- **Mods that replace the vein generator**, such as Dynamic Ore Veins, change the same thing in a
  different way: use one or the other.
- **Normal ore** is untouched: only the large veins are affected.

## Versions and building

| Branch | Minecraft | Loader | Java |
|---|---|---|---|
| `1.20.1` | 1.20.1 | Forge 47.2+ | 17 |
| `1.21.1` | 1.21.1 | NeoForge 21.1.252+ | 21 |
| `26.1` / `main` | 26.1.2 | NeoForge 26.1.2.112+ | 25 |

All versions have the same features. Build: `gradlew build`. The logo is generated:
`java tools/GenerateLogo.java`.

## Permissions

**Modpacks: yes.** No permission needed, no message required, public or private, monetised or not,
on any platform or launcher. If you are reading this to find out whether you may include
Ore Vein Tweaker, the answer is yes and you can stop reading.

**Credit** is appreciated and never required.

**Forks and addons: yes**, under the MIT terms. Please do not publish a fork under the name
*Ore Vein Tweaker* — the name is not covered by the licence, and two projects sharing one name only
confuses the people trying to work out which one broke.

**Assets** — artwork, icons, logo — are the one exception: redistribute them with the project
freely, but do not lift them into another project. See [LICENSE-ASSETS](LICENSE-ASSETS).

**Contributions** are accepted under the same terms as the rest of the repository.
