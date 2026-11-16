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

Six sections: `[copper]` and `[iron]` for the vanilla veins, `[extra_1]` to `[extra_3]` for
[new veins](#new-veins), and `[census]` for the [census command](#counting-what-generated). More new
veins than three go in [their own files](#as-many-veins-as-you-want). Every option is also explained
inside the file.

| Option | Default | What it does |
|---|---|---|
| `enabled` | `true` (`false` for new veins) | `false` = this vein never generates; plain stone or deepslate is left in its place. |
| `size` | `1.0` | Vein volume. `2.0` = about twice (thicker and more frequent), `0.5` = about half, `0` = none. `0` to `4.5`. |
| `ore_amount` | `1.0` | Ore vs filler. `2.0` = twice the ore, `0` = filler only, `5.0` = only ore, no filler. `0` to `5`. |
| `raw_block_amount` | `1.0` | Raw ore blocks among the ore. `5.0` = five times as many, `0` = none, `50` = nearly every ore is a raw block. `0` to `50`. |
| `ore` | vanilla ore | Block used as ore. Any block id, from any mod, or a [palette](#palettes). |
| `raw_block` | vanilla raw block | Block used as raw ore block (a palette works too). |
| `filler` | granite / tuff | Block the ore sits in (a palette works too). |
| `min_y`, `max_y` | per slot | New veins only: their height range. |

**Order of effects.** `size` decides where veins are. Inside a vein, `ore_amount` decides ore vs
filler, then `raw_block_amount` decides which ore becomes a raw block. Finally the blocks are swapped
for `ore`, `raw_block` and `filler`.

A block id that does not exist is reported in the log and the vanilla block (stone, for a new vein)
is kept: a typo never breaks generation.

## New veins

`[extra_1]`, `[extra_2]` and `[extra_3]` each add a new kind of large vein, off by default. They take
every option above, plus `min_y` and `max_y`:

- A vein is at most **110 blocks tall** (`max_y - min_y`): vanilla's vein noises only exist over
  that height. Veins thin out over the 20 blocks at each end, like vanilla ones.
- They are built by vanilla's own vein algorithm, with their own ribbons: they do not follow the
  copper or iron veins. `size = 1` gives about as much vein as vanilla copper.
- Where a new vein crosses a vanilla one, the vanilla vein wins.
- They generate wherever large veins do - the Overworld, unless a datapack says otherwise.

The slots come filled with examples, so turning one on is a single line:

| Slot | Ore | Filler | Height |
|---|---|---|---|
| `extra_1` | deepslate gold ore | smooth basalt | Y -60 to -10 |
| `extra_2` | coal ore | andesite | Y 0 to 60 |
| `extra_3` | deepslate redstone ore | calcite | Y -60 to -20 |

### As many veins as you want

The config file has three slots; for more, put **one TOML file per vein** in
`config/oreveintweaker/veins/`. A file takes the same options as an `[extra_N]` section (`min_y`,
`max_y`, `ore` and `filler` are required, the rest has the defaults above) - the first start writes
an `_example.toml` to copy:

```toml
# config/oreveintweaker/veins/zinc.toml
size = 1.5
ore = "create:zinc_ore*3, minecraft:copper_ore"
filler = "minecraft:calcite"
min_y = -20
max_y = 60
```

- **`/oreveintweaker reload`** (operators) re-reads the folder and the config without a restart and
  lists the veins that are on, and any file it had to skip, with the reason. A wrong file is skipped,
  never half-applied; files starting with `_` and `enabled = false` are ignored.
- A vein keeps its shape and its rolls whatever the other files are: adding or removing a file never
  moves the others. Where two veins meet, the config's slots win, then files in alphabetical order.
- Each vein costs generation time: measured on 1.20.1, about **1-2% per vein** at 50 to 60 blocks of
  height. Past 16 veins the log says so. A taller vein than 110 blocks is two files, one above the other.

### Palettes

`ore`, `raw_block` and `filler` can list several blocks, with a weight after `*` (1 when left out):
`"create:zinc_ore*3, minecraft:copper_ore"` is three zinc for one copper. It works in the config file
and in vein files, and a single block behaves exactly as before.

## Recipes

Copy the part you need into the config file; leave the other lines as they are.

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

**Gold veins, on top of iron**
```toml
[extra_1]
    enabled = true
```

**Zinc veins next to copper** (Create)
```toml
[extra_2]
    enabled = true
    ore = "create:zinc_ore"
    raw_block = "create:raw_zinc_block"
    filler = "minecraft:calcite"
```

## Complete examples

Four complete setups, one per kind of modpack. Put each line in the matching section of
`config/oreveintweaker-common.toml`, in place of the same key; anything you do not paste stays as it is.

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
# Copper and iron stay vanilla. Zinc veins run in calcite, through the copper heights.
[extra_1]
    enabled = true
    size = 0.8                          # a bit rarer than copper
    ore = "create:zinc_ore"
    raw_block = "create:raw_zinc_block"
    filler = "minecraft:calcite"
    min_y = 0
    max_y = 70
```

**💰 Gold rush - the deep is gold and redstone**
```toml
# The iron veins become gold veins, a little poorer since gold is precious...
[iron]
    ore = "minecraft:deepslate_gold_ore"
    raw_block = "minecraft:raw_gold_block"
    ore_amount = 0.6
# ...and redstone veins in calcite join them (the slot's own defaults, Y -60 to -20).
[extra_3]
    enabled = true
```

**🏛️ Builder pack - huge stone bands, no free ore**
```toml
# The veins stay as wide decorative ribbons of granite, tuff and dripstone.
[copper]
    size = 2.5
    ore_amount = 0.0
[iron]
    size = 2.5
    ore_amount = 0.0
[extra_2]
    enabled = true
    size = 2.0
    ore_amount = 0.0
    filler = "minecraft:dripstone_block"
```

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

The multipliers were calibrated against measurements of vanilla generation, then checked in game:

| Setting | Measured |
|---|---|
| `size = 2` / `0.5` | x2.00 / x0.49 the vein volume |
| `size = 3` / `4.5` | x2.97 / x4.49 (blocks placed over 576 fresh chunks, one world) |
| `ore_amount = 2` | x2.1 the ore |
| `raw_block_amount = 5` | x5.2 the raw blocks |
| new vein, `size = 1` | x1.00 vanilla copper's volume |

Bigger veins are also slightly richer at their core, as vanilla ones are: that is how the generator
works.

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
