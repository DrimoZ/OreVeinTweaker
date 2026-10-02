# Store copy

Paste-ready text for the CurseForge (and Modrinth) project page. Not documentation of the code: this
file explains and sells the mod to players and pack makers. Every claim here is something the mod does
today; check numbers against `VeinConfig.java` / `VeinRules.java` and `CHANGELOG.md` before changing
one.


## Summary

> One line, 256 characters at most, shown under the name in every search result.

Control the large iron and copper ore veins: turn each type off, make veins bigger or smaller, richer or
poorer, or rebuild them from any block - even another mod's ore. One config file, no datapack.

## Categories

Main: **World Gen**. Additional: Ores and Resources, Utility & QoL, Server Utility.

## Licence field

Custom. Point it at `LICENSE` in the repository: MIT for the code, all rights reserved for the
artwork with redistribution granted.

## Description

<!-- Everything below this line is pasted into CurseForge's Markdown editor as-is. -->

### The large ore veins, your way.

Since Minecraft 1.18, deep underground you find **large ore veins**: long ribbons of **copper ore in
granite** between Y 0 and 50, and **deepslate iron ore in tuff** between Y -60 and -8, studded with
**raw ore blocks**. They are some of the best ore in the game - and in many modpacks, too much of it.

Vanilla only lets you turn them all off at once, by overwriting the whole Overworld generation file -
which fights with every terrain mod that overwrites the same file. **Ore Vein Tweaker** instead adjusts
the veins as they are generated, **per vein type**, from one config file:

- **Turn a vein type off** - only iron, only copper, or both.
- **Resize** - bigger and more common, or smaller and rarer.
- **Enrich or impoverish** - more ore and fewer filler blocks, or the other way round.
- **Raw ore blocks** - more, fewer, or none at all.
- **Rebuild them from other blocks** - any block from any mod: zinc veins for Create, gold veins,
  decorative stone bands...

It does not touch any worldgen file, so it works alongside terrain mods instead of replacing their
files.

### Getting started

1. Install the mod (on a server, on the server; in singleplayer, in your game).
2. Start the game once: it writes `config/oreveintweaker-common.toml`.
3. Edit it - every option is explained inside the file - and **explore new chunks**.

> **Only chunks generated after a change are affected.** On an existing world, already-explored areas
> keep their veins, and the border with new chunks can be visible. Best used on a new world, or for a
> modpack's default config.

The file is reloaded while the game runs: change a value, fly to unexplored land, and the new chunks
use it. No restart needed.

### Every option

The file has one section per vein type, `[copper]` and `[iron]`, with the same options. All defaults
are vanilla: **installing the mod changes nothing until you edit the file.**

| Option | Default | What it does |
|---|---|---|
| `enabled` | `true` | `false` = this vein type never generates. Plain stone or deepslate is left in its place. |
| `size` | `1.0` | How much of the underground is vein. `2.0` = about **twice the vein volume** (thicker and more frequent), `0.5` = about half, `0` = none. From `0` to `3`. |
| `ore_amount` | `1.0` | How much of each vein is ore rather than filler. `2.0` = **twice the ore**, `0` = filler only, `4.0` = mostly ore. From `0` to `4`. |
| `raw_block_amount` | `1.0` | Share of raw ore blocks among the ore. `5.0` = **five times as many**, `0` = none. From `0` to `20`. |
| `ore` | vanilla ore | The block used as the vein's ore. Any block id, from any mod. |
| `raw_block` | vanilla raw block | The block used as the vein's raw ore block. |
| `filler` | granite / tuff | The block the ore sits in. |

**How precise are the numbers?** The multipliers were calibrated against measurements of vanilla
generation, then checked in game: `size = 2` and `0.5` give ×2.00 and ×0.49 the vein volume;
`ore_amount = 2` gave ×2.1 the ore; `raw_block_amount = 5` gave ×5.2 the raw blocks. Bigger veins are
also slightly richer at their core, as vanilla veins are - that is how the generator works.

**Order of effects.** `size` decides where veins are. Inside a vein, `ore_amount` decides ore vs
filler, then `raw_block_amount` decides which ore becomes a raw block. Finally the blocks are swapped
for `ore`, `raw_block` and `filler`.

### Recipes

Copy the part you need into `config/oreveintweaker-common.toml`; keep the other lines as they are.

**No large veins at all**
```toml
[copper]
    enabled = false
[iron]
    enabled = false
```

**Keep copper, remove iron** - iron still exists as normal ore, just not in huge ribbons.
```toml
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

**Zinc veins for Create** - the copper veins become zinc veins.
```toml
[copper]
    ore = "create:zinc_ore"
    raw_block = "create:raw_zinc_block"
```

**Gold veins in the deep** - the iron veins become gold.
```toml
[iron]
    ore = "minecraft:deepslate_gold_ore"
    raw_block = "minecraft:raw_gold_block"
```

A block id that does not exist is reported in the log and the vanilla block is kept, so a typo never
breaks generation.

### Compatibility

- **Terrain mods** (Terralith, Tectonic and others): the mod changes no worldgen file, so there is
  nothing to conflict with. It applies to whatever veins the world's generator produces. If a mod or
  datapack turns large veins off entirely, there is nothing left to tweak.
- **Other dimensions**: vanilla only generates large veins in the Overworld. If a datapack enables them
  in another dimension, the same settings apply there.
- **Mods that replace the vein generator itself**, such as Dynamic Ore Veins, change the same thing
  in a different way: use one or the other.

### Requirements

| | 1.20.1 | 1.21.1 | 26.1 |
|---|---|---|---|
| Loader | Forge 47.2+ | NeoForge 21.1.252+ | NeoForge 26.1.2.112+ |
| Java | 17 | 21 | 25 |

All versions have the same features. No other mod required.

### FAQ

**Will it change my existing world?** Only chunks generated from now on. Already-explored land is
never modified.

**Does it remove normal iron and copper ore?** No. Only the large veins are affected; the ordinary ore
blobs generate as usual.

**Can I add new kinds of veins, like a third type?** No: Minecraft has exactly two large vein types,
and this mod adjusts those two. Rebuilding one from other blocks (see Recipes) covers most uses.

**Do I need a datapack?** No. Everything is in the config file.

**Fabric?** No.

**Can I put it in my modpack?** Yes. No need to ask.

### Permissions

**Modpacks: yes.** No permission needed, no message required, public or private, monetised or not,
on any platform or launcher.

**Credit** is appreciated and never required.

**Forks and addons: yes**, under the MIT terms. Please do not publish a fork under the name
*Ore Vein Tweaker*: the name is not covered by the licence.

**Assets** (the logo) are the one exception: redistribute them with the mod freely, but do not lift
them into another project.

### Links

[Source](https://github.com/DrimoZ/OreVeinTweaker) ·
[Report a bug](https://github.com/DrimoZ/OreVeinTweaker/issues)

<!-- End of the pasted description. -->


## Release checklist: 1.0.0

- [ ] Create the GitHub repository `DrimoZ/OreVeinTweaker` and push all branches - the Links section
      above points to it.
- [ ] Rename the local project folder to `OreVeinTweaker` (it still carries the mod's former name).
- [ ] Project avatar: `src/main/resources/logo.png` (512 x 512, from `java tools/GenerateLogo.java`).
- [ ] Gallery: before/after screenshots of the same seed, taken in a dev run with `/strip` (dev-only
      command that clears the stone around you so the veins show).
- [ ] Paste the description.
- [ ] Three files, release type **Release**, each with the `## 1.0.0` section of `CHANGELOG.md`:

| File | Game version | Loader | Java |
|---|---|---|---|
| the jar built on branch `1.20.1` | 1.20.1 | Forge | 17 |
| the jar built on branch `1.21.1` | 1.21.1 | NeoForge | 21 |
| the jar built on branch `26.1` | 26.1.2 | NeoForge | 25 |

- [ ] Environment: **Server** required. Before ticking "client optional", check that a client without
      the mod can join a server that has it, on each loader.
