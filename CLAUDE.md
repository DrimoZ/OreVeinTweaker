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
| `java tools/Banners.java` | regenerates the store page art in `docs/store-art/` (after one build: it reads the font from the game jar) |

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
- **extra veins**: where vanilla places nothing, `VeinRules.applyExtra` runs vanilla's algorithm
  (copied from `OreVeinifier.create`) for each enabled `[extra_N]` and each vein file of
  `config/oreveintweaker/veins/` (`VeinFiles`; a file's slot comes from its name, so a vein never moves
  when others come and go; blocks are weighted `Palette`s), reading vanilla's own toggle,
  ridged and gap noises at a shifted position: sideways per slot so the ribbons are new, and down so
  `min_y..max_y` lands on Y -60..50, the only range where the overworld router defines those noises
  (hence the 110-block cap). NoiseChunk's caches compute directly for a foreign context, so sampling
  elsewhere is safe. Copper's half of the toggle only, so `size` keeps its meaning.

Server-side only: no registries, no packets, `displayTest = "IGNORE_ALL_VERSION"`.

**Player commands** (all permission level 2, none needed to play): `/oreveintweaker reload`
(`VeinCommand`), `strip` (`Strip`) and `census` (`Census`):
- `Census` counts blocks in the loaded chunks around the player, in the dimension they are in, by reading
  the sections' palettes (cost per distinct block, not per block). Ores are the `forge:ores` / `c:ores`
  tag, so other mods' ores need no config. It never loads a chunk.
- A pack has 30 to 100 ores, so the chat is a **summary** (header, five bars, mods, buttons) and every other
  view is a sub-command or a click: `list`, `mod`, `y` (slabs of 64), `ore <name>`, `save`/`diff`. Chat
  text is white or grey and only the bar is coloured, with colours kept to 3:1 on the chat background
  (measured: DARK_GRAY, BLUE and the dark red/purple are under 2:1).
- `export` writes a self-contained web page (`src/main/resources/census.html`, the data injected between
  `/*DATA*/` and `/*END*/`) next to two CSV files. The markers, not a line end: a Windows checkout turns
  the template's `\n` into `\r\n`, which once made the export fail on every branch.
- The chat buttons and the section-range calls are loader-API lines: see `PORTING.md`.

`VeinRules` is an immutable snapshot swapped on common setup and on every config reload: worldgen
threads read it without locks.

## Dev tools

Registered only outside production (`DevTools.ENABLED`):
- `/benchgen <size> [cx cz]` - generates a square of fresh chunks and times it.
- `/veins [reset]` - per vanilla vein block: how often vanilla placed it, what the mod placed instead,
  last position (click to tp); plus the totals actually placed.
- On world load, the log gets the |vein_toggle| distribution and the vein volume ratio for the current
  config (`copper xN, iron xN`) - the check that `size` still means what the docs say.

## Documentation map

- `README.md` - the full player guide (the project's wiki: every option, recipes, measured
  precision, compatibility), plus branches and the permissions block
- `STORE.md` - the short CurseForge/Modrinth page, linking to the README, and the release checklist
- **Any number changed in code must be changed in both.**
- `PORTING.md` - what differs between branches
- `CHANGELOG.md`

## Licensing

Code MIT, assets (the logo) all rights reserved with redistribution granted, from `licensing-kit`.
`mod_license` states both halves; both files are packed into the jar.

## Conventions

Comments explain *why* - usually the failure that motivated the code. UTF-8. Package
`dev.drimoz.oreveintweaker`, author DrimoZ, version `x.y.z+<minecraft>`.
