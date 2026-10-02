# Ore Vein Tweaker

Control Minecraft's large ore veins - the long ribbons of copper in granite and iron in tuff added in
1.18 - from one config file: turn them off, resize them, make them richer or poorer, rebuild them from
any block, or add new kinds of veins. Server-side only.

This page is the full guide. The short version is the
[CurseForge page](https://www.curseforge.com/minecraft/mc-mods/ore-vein-tweaker).

- [Getting started](#getting-started)
- [The config file](#the-config-file)
- [New veins](#new-veins)
- [Recipes](#recipes)
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
| `size` | `1.0` | Vein volume. `2.0` = about twice (thicker and more frequent), `0.5` = about half, `0` = none. `0` to `3`. |
| `ore_amount` | `1.0` | Ore vs filler. `2.0` = twice the ore, `0` = filler only, `4.0` = mostly ore. `0` to `4`. |
| `raw_block_amount` | `1.0` | Raw ore blocks among the ore. `5.0` = five times as many, `0` = none. `0` to `20`. |
| `ore` | vanilla ore | Block used as ore. Any block id, from any mod. |
| `raw_block` | vanilla raw block | Block used as raw ore block. |
| `filler` | granite / tuff | Block the ore sits in. |
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

## How precise is it?

The multipliers were calibrated against measurements of vanilla generation, then checked in game:

| Setting | Measured |
|---|---|
| `size = 2` / `0.5` | x2.00 / x0.49 the vein volume |
| `size = 3` | x2.78 |
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
