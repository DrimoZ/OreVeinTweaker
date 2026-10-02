# Ore Vein Tweaker

Control Minecraft's large iron and copper ore veins - the long ribbons of ore in tuff and granite
added in 1.18 - per vein type, from one config file:

- turn a type off,
- make its veins bigger or smaller (`size`),
- richer or poorer (`ore_amount`, `raw_block_amount`),
- or rebuild them from any block, from any mod;
- and add up to three new kinds of large vein (`[extra_1..3]`).

Server-side only: players do not need it to join. Defaults are vanilla; nothing changes until `config/oreveintweaker-common.toml` is edited, and only
newly generated chunks are affected. The full player guide, with every option and ready-made recipes,
is the store page: [STORE.md](STORE.md).

| Branch | Minecraft | Loader | Java |
|---|---|---|---|
| `1.20.1` | 1.20.1 | Forge 47 | 17 |
| `1.21.1` | 1.21.1 | NeoForge 21.1 | 21 |
| `26.1` / `main` | 26.1.2 | NeoForge 26.1.2 | 25 |

Build: `gradlew build`. The logo is generated: `java tools/GenerateLogo.java`.

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
