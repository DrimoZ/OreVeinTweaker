# Changelog

## 1.0.0

First release, for Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.

- Per vein type (`[copper]`, `[iron]`) in `config/oreveintweaker-common.toml`:
  - `enabled`: turn the large veins of that type off.
  - `size`: vein volume, calibrated so `2.0` is about twice vanilla's and `0.5` about half.
  - `ore_amount`: ore vs filler inside the veins.
  - `raw_block_amount`: share of raw ore blocks among the ore.
  - `ore`, `raw_block`, `filler`: rebuild the veins from any block, from any mod.
- Defaults are vanilla. Only chunks generated after a change are affected; the file reloads live.
