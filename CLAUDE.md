# CLAUDE.md

Ore Vein Tweaker - adjusts Minecraft's large iron and copper ore veins per vein type from a config
file: off, size, ore richness, raw blocks, and the blocks they are made of.

## Branches - read this first

One branch per Minecraft version: `1.20.1` (Forge), `1.21.1` (NeoForge), `26.1` (NeoForge); `main`
follows the newest. Changes are made on the oldest branch they apply to and **merged forward**
(`1.20.1` -> `1.21.1` -> `26.1` -> `main`). Conflicts are expected only in build files, the mods toml
and the loader-API lines listed in `PORTING.md`; take the target branch's side for those.

Minecraft numbers since December 2025 are `year.drop.hotfix` (26.1.2), and NeoForge has four
components to match (26.1.2.112).

## Build and run

| Task | What it does |
|---|---|
| `gradlew build` | the jar, `build/libs/oreveintweaker-<version>+<mc>.jar` |
| `gradlew runClient` | dev client: `run-1.20.1/` on 1.20.1, `run/` on the NeoForge branches |
| `java tools/GenerateLogo.java` | regenerates `src/main/resources/logo.png` |

`org.gradle.daemon=false`. A dev client killed mid-write has twice left a truncated file behind
(`build/downloadMCMeta/version.json`, Forge's `config/fml.toml`): if Gradle or the game fails on
"Expected BEGIN_OBJECT" or a null config value right after a killed run, delete that file.

## How it works

`OreVeinifierMixin` wraps vanilla, it never rewrites it:
- **in**: the vein toggle is replaced by `VeinSizeToggle`, which shifts |toggle| per vein type
  (copper where toggle > 0, iron elsewhere). The shift comes from a **measured** table of how much of
  the vein heights each |toggle| threshold covers (`VeinRules.THRESHOLD/SHARE`): scaling the toggle
  linearly made `size = 0.5` remove 97% of the veins.
- **out**: each block vanilla places goes through `VeinRules.apply`: ore/raw/filler role re-rolled for
  `ore_amount` and `raw_block_amount` (vanilla ratios, measured: 1 ore per 4 filler, 2 raw per 100
  ore), then swapped for the configured blocks. Rolls use the mod's own seeded positional random, not
  vanilla's, so they do not correlate with vanilla's ore decision.

`VeinRules` is an immutable snapshot swapped on common setup and on every config reload: worldgen
threads read it without locks.

## Dev tools

Registered only outside production (`DevTools.ENABLED`):
- `/strip [radius]` - clears everything below Y 64 except ores, raw blocks and bedrock (World Stripper
  has no 1.20.1 build).
- `/veins [reset]` - per vanilla vein block: how often vanilla placed it, what the mod placed instead,
  last position (click to tp); plus the totals actually placed.
- On world load, the log gets the |vein_toggle| distribution and the vein volume ratio for the current
  config (`copper xN, iron xN`) - the check that `size` still means what the docs say.

## Documentation map

- `README.md` - short version, with the permissions block
- `STORE.md` - CurseForge/Modrinth page and release checklist; **any number changed in code must be
  changed there too**
- `PORTING.md` - what differs between branches
- `CHANGELOG.md`

## Licensing

Code MIT, assets (the logo) all rights reserved with redistribution granted, from `licensing-kit`.
`mod_license` states both halves; both files are packed into the jar.

## Conventions

Comments explain *why* - usually the failure that motivated the code. UTF-8. Package
`dev.drimoz.oreveintweaker`, author DrimoZ, version `x.y.z+<minecraft>`.
