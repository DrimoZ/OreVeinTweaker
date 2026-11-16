# Changelog

## Unreleased

- **More veins**: one TOML file per vein in `config/oreveintweaker/veins/`, as many as you want (same
  options as `[extra_N]`; an `_example.toml` is written on first start). `/oreveintweaker reload`
  re-reads them without a restart and lists what is on, and any file it skipped with the reason. A vein
  keeps its shape whatever the other files are. Measured cost: about 1-2% of chunk generation time
  per vein.
- **Palettes**: `ore`, `raw_block` and `filler` accept `"block*weight, block"` lists, in the config file
  and in vein files. A single block generates exactly as before.
- **Higher limits**: `size` up to 4.5 (measured x4.49 the vein volume), `ore_amount` up to 5 (only ore,
  no filler), `raw_block_amount` up to 50 (nearly every ore is a raw block). Past those values the
  formulas are already at 100%.
- **New command `/oreveintweaker census [radius]`** (permission level 2): counts every ore, raw block and
  storage block in the loaded chunks around you. Built for packs with dozens of ores: the chat shows a
  short summary (the five most common ores as bars, the mods, buttons), `census list` pages through
  every ore, `census mod` gives the share of each mod, `census y` goes by slabs of 64 blocks of height
  and `census ore <name>` shows one ore's profile by height; clicking an ore's name opens it. The radius
  is in chunks (default 8, max 32). It counts what is in the world, so it covers other mods' ores too,
  and `[census] blocks` adds any other block to the count. With `export`, it writes to
  `config/oreveintweaker/census/` a self-contained **web page** (every ore, sortable and searchable,
  filtered by mod, with its height profile and a map of the chunks), two CSV files (the count of every
  block at every Y, exact, and of every block in every chunk) and an info file (seed, centre, radius,
  date). `census save <name>` keeps the counts and `census diff <name>` lists only what changed since,
  biggest change first. Bars keep to colours readable on the chat background.
- **Precision section re-measured** (README): the table now gives the vein volume the mod logs and the
  blocks actually placed, side by side, over the same 576 chunks. Nothing changed in generation: the
  figures are identical on the code from before the higher limits. Two of the old figures (`ore_amount = 2`
  and `raw_block_amount = 5`) could not be reproduced; a new vein holds about a fifth of copper's blocks.
- **New command `/oreveintweaker strip [radius]`** (permission level 2): clears everything below Y 64
  around you except ores, raw and storage blocks and bedrock, to see the veins on their own. Radius 0
  to 16 chunks. No undo.

## 1.1.0

- New veins: `[extra_1]` to `[extra_3]` each add a new kind of large vein, off by default, with the
  same options as copper and iron plus `min_y` / `max_y` (at most 110 blocks tall). Shaped by
  vanilla's vein algorithm with their own ribbons; `size = 1` is about as much vein as vanilla copper.
- Server-side only: clients no longer need the mod, and the server list no longer flags a server that
  has it.

## 1.0.0

First release, for Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.

- Per vein type (`[copper]`, `[iron]`) in `config/oreveintweaker-common.toml`:
  - `enabled`: turn the large veins of that type off.
  - `size`: vein volume, calibrated so `2.0` is about twice vanilla's and `0.5` about half.
  - `ore_amount`: ore vs filler inside the veins.
  - `raw_block_amount`: share of raw ore blocks among the ore.
  - `ore`, `raw_block`, `filler`: rebuild the veins from any block, from any mod.
- Defaults are vanilla. Only chunks generated after a change are affected; the file reloads live.
