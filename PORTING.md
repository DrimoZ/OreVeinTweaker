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
| Census (`Census.java`) | `new ClickEvent(Action.RUN_COMMAND, s)`, `new HoverEvent(Action.SHOW_TEXT, c)`, `level.getMinSection()` / `getMaxSection()`, `Tags` from Forge | same, `Tags` from NeoForge | `new ClickEvent.RunCommand(s)`, `new ClickEvent.CopyToClipboard(s)`, `new HoverEvent.ShowText(c)`, `getMinSectionY()` / `getMaxSectionY()`, plus the `Identifier`, `hasPermission`, `getMinY()`, `ChunkPos.containing` and `x()`/`z()` changes below |
| Strip (`Strip.java`) | `TickEvent.ServerTickEvent` (phase END), `ServerStoppingEvent` from Forge, `new ResourceLocation(s)`, `BuiltInRegistries.BLOCK.getTag(key)` / `getTagNames()`, `player.pick(d, 0, false)`, `chunk.getMinSection()`, `sendSystemMessage(c, true)` for the action bar, `ConfigValue.set` + `save()`, `Tags` from Forge | `ServerTickEvent.Post`, `ServerStoppingEvent` from NeoForge, `ResourceLocation.parse(s)`, `Tags` from NeoForge | same plus the `Identifier`, `getMinSectionY()`, `ClickEvent` and `hasPermission` changes of Census, `registry.get(tagKey)` for `getTag`, `listTagIds()` for `getTagNames()`, `inventory.getItem(i)` (the `items` field is private), `getMaxY() + 1` for `getMaxBuildHeight()`, `net.minecraft.util.Util` |
| Dev tools only | `hasPermission(2)`, `getMinBuildHeight()`, `new ChunkPos(pos)`, `FMLLoader.isProduction()` | same | `Commands.hasPermission(LEVEL_GAMEMASTERS)`, `getMinY()`, `ChunkPos.containing(pos)` + `x()`, `FMLLoader.getCurrent().isProduction()`, `ClickEvent.SuggestCommand` |
| Vein files, palettes | `net.minecraftforge.fml.loading.FMLPaths`, `ResourceLocation.tryParse`, `RegisterCommandsEvent` in `net.minecraftforge.event` | `net.neoforged.fml.loading.FMLPaths`, `net.neoforged.neoforge.event.RegisterCommandsEvent` | same, `Identifier.tryParse` |
| `/benchgen` (dev) | `net.minecraft.world.level.chunk.ChunkStatus` | `...chunk.status.ChunkStatus` | same as 1.21.1 |
| Census and strip commands | `ResourceLocation.tryParse`, `getMinBuildHeight()`, `hasPermission(2)`, `new ChunkPos(pos)`, `center.x`, `dimension().location()` | same | `Identifier.tryParse`, `getMinY()`, `Commands.hasPermission(LEVEL_GAMEMASTERS)`, `ChunkPos.containing(pos)`, `center.x()`, `dimension().identifier()` |
| Census export | `new ClickEvent(Action.COPY_TO_CLIPBOARD, text)` | same | `new ClickEvent.CopyToClipboard(text)` |

## 26.3

The `ore_veins_enabled` noise setting is replaced by a `minecraft:ore_vein` material rule in the 26.3
snapshots. Expect `OreVeinifier` to move; re-check the target before porting, and the measured
`vein_toggle` table in `VeinRules` (re-run `DevTools.measureToggle` if the vein noise changed).
