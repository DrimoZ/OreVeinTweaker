# Porting

One mixin, one target: `OreVeinifier.create(DensityFunction toggle, DensityFunction ridged,
DensityFunction gap, PositionalRandomFactory random)`. It wraps the toggle going in (`size`) and the
`NoiseChunk.BlockStateFiller` coming out (everything else); vanilla's vein algorithm is never copied.
That signature was identical in 1.20.1, 1.21.1 and 26.1 - check it first on any new version:

```
javap -p -cp <minecraft jar> net.minecraft.world.level.levelgen.OreVeinifier | grep create
```

What actually changed between branches is loader and vanilla API, never the logic:

| | 1.20.1 | 1.21.1 | 26.1 |
|---|---|---|---|
| Build | MDG `legacyforge` | MDG | MDG |
| Mappings at runtime | SRG - refmap required | Mojang | none (unobfuscated) |
| Mixin declared in | jar manifest `MixinConfigs` (set by hand in `build.gradle`) | `neoforge.mods.toml` | `neoforge.mods.toml` |
| Config | `ForgeConfigSpec` | `ModConfigSpec` | `ModConfigSpec` |
| Ids | `new ResourceLocation(s)` | `ResourceLocation.parse(s)` | `Identifier.parse(s)` |
| Mod constructor | `FMLJavaModLoadingContext.get()` | `(IEventBus, ModContainer)` | same |
| Dev tools only | `hasPermission(2)`, `getMinBuildHeight()`, `new ChunkPos(pos)`, `FMLLoader.isProduction()` | same | `Commands.hasPermission(LEVEL_GAMEMASTERS)`, `getMinY()`, `ChunkPos.containing(pos)` + `x()`, `FMLLoader.getCurrent().isProduction()`, `ClickEvent.SuggestCommand` |

## 26.3

The `ore_veins_enabled` noise setting is replaced by a `minecraft:ore_vein` material rule in the 26.3
snapshots. Expect `OreVeinifier` to move; re-check the target before porting, and the measured
`vein_toggle` table in `VeinRules` (re-run `DevTools.measureToggle` if the vein noise changed).
