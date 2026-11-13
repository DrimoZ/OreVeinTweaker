# Changelog

## Unreleased

- New command `/oreveintweaker census [radius]` (permission level 2): counts every ore, raw block and
  storage block in the loaded chunks around you, with the count per chunk and each ore's share of the
  ores. `census y [radius]` breaks it down by height. The radius is in chunks (default 8, max 32).
  It counts what is in the world, so it also covers other mods' ores. `[census] blocks` adds any other
  block to the count.

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
