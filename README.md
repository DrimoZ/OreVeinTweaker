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

Five sections: `[copper]` and `[iron]` for the vanilla veins, `[extra_1]` to `[extra_3]` for
[new veins](#new-veins). Every option is also explained inside the file.

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
actually holds around you, to check a config or compare two of them:

```
> /oreveintweaker census 6
Census minecraft:overworld: 169 of 169 chunks loaded within 96 blocks
  copper_ore: 18219 (107.8/chunk, 26.4% of ores)
  coal_ore: 17195 (101.7/chunk, 24.9% of ores)
  iron_ore: 14381 (85.1/chunk, 20.9% of ores)
  redstone_ore: 6247 (37.0/chunk, 9.1% of ores)
  gold_ore: 4381 (25.9/chunk, 6.4% of ores)
  lapis_ore: 4302 (25.5/chunk, 6.2% of ores)
  diamond_ore: 4216 (24.9/chunk, 6.1% of ores)
  raw_copper_block: 23 (0.1/chunk)
  raw_iron_block: 18 (0.1/chunk)
  total ore: 68941 (407.9/chunk)

> /oreveintweaker census y 6
  Y 48..63: 10525 ore - coal_ore 55% copper_ore 37% iron_ore 5% lapis_ore 3%
  Y 16..31: 11883 ore - copper_ore 39% iron_ore 30% coal_ore 26% lapis_ore 4%
  Y -32..-17: 4241 ore - iron_ore 26% gold_ore 24% diamond_ore 21% redstone_ore 16%
  Y -64..-49: 5361 ore - redstone_ore 49% diamond_ore 24% gold_ore 13% iron_ore 8%
```

(A vanilla-config world on 1.21.1, 169 chunks around one point; the `y` output is cut to four of its bands.)

- The **radius** is in chunks (default 8 = 128 blocks, up to 32), around your position, in your
  current dimension.
- `census y [radius]` gives the same by 16-block height band, with each band's top ores: the quick
  way to see *where* an ore is.
- It counts the blocks in the world, not what a generator placed: ores of every mod are included,
  deepslate variants are merged with the stone ones, and it cannot tell a vein's ore from a normal
  one.
- It only reads **loaded** chunks and never generates any: the first line says how many it covered.
  Fly around, or stand in the middle of a pregenerated area, for a bigger sample.
- `[census] blocks` in the config adds any other block to the count, for example
  `["minecraft:andesite", "minecraft:tuff"]` to see how much of your stone is that.
- Percentages are shares of the ores only (extra blocks and raw blocks show counts).

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
