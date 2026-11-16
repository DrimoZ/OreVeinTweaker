# Changelog

## 1.2.1

- **`/oreveintweaker strip` reworked.** It now runs a few chunks per tick, so the server no longer
  freezes: a progress bar (percentage, blocks removed, time left) is on the action bar and
  `strip cancel` stops it.
- **Choose what goes and what stays.** A new `[strip]` config section holds a `keep` list and a `remove`
  list (block ids from any mod, or `#tags`; `remove` wins, so even ores can go), the built-in rules as
  options (`keep_ores`, `keep_storage_blocks`, `keep_bedrock`), the height range (`min_y`, `max_y`) and
  the default radius. `strip keep|remove add|del|list|clear` edit the lists from the game, and the file
  stays editable by hand.
- **`strip only <blocks>`** removes just those blocks, once, ores included, and leaves the rest alone:
  `strip only 8 limestone`. The height can be given for one run: `strip [radius] [min_y max_y]`.
- **Pick blocks without typing ids**: `target` (the block you look at), `hand`, `hotbar`, `inventory`,
  an id (the mod name may be left out when only one block has that name) or a `#tag`, with suggestions.
- **Census page**: an ore's detail now opens inside the table, under its row, and `census export` ends
  with an `[open]` button that opens the page in the browser.
- Documentation: a vein file is now the documented way to add a vein; the three `[extra_N]` slots of the
  config are a footnote.

## 1.2.0

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
  and `raw_block_amount = 5`) could not be reproduced. Counts over one small area vary enormously
  (copper veins from 1 to 53,818 blocks over six areas), so the table compares settings on the same
  terrain; a new vein at `size = 1` has about four fifths of copper's volume and the same as iron's.
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
