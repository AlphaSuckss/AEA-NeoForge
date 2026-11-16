# Changelog

Every change to AEA for NeoForge 1.21.1, newest first.

## 1.3.0 (in progress)

### Added
- `aea:hitbox_part`: a power that gives an entity an extra hit box. The box follows the entity's body, and a hit on it counts as a hit on the entity. With hit boxes shown (F3 + B) it is outlined in green.
- Data pack textures: PNG files under `data/<namespace>/textures/` in a data pack are sent to players and registered under `<namespace>:textures/...`, so powers can use them without a resource pack. No resource reload is needed.
- Data pack assets: the `assets/` folder of a data pack in the world's `datapacks` folder is sent to players and loaded on their game as a resource pack. This covers block and item textures, models, sounds, fonts, language files, animated textures, and the Blockbench models and animations of `apoli:custom_model_render`. The player's resources reload only when the files are new or have changed: the pack stays loaded after leaving and is loaded with the rest of the game at the next start.
- `aea:see_invisible`: a power that lets the holder see invisible entities at a chosen opacity. It can be limited to invisibility from the effect, from certain powers, or from a power tag, and by a bi-entity condition. Only what the holder sees changes; `apoli:invisible` still passes.
- `aea:milk_immunity`: a power that stops milk from removing some or all status effects, with `effect`, `effects`, `effect_type` and `inverted`.
- `effect_type` (`beneficial`, `harmful` or `neutral`) for `apoli:effect_immunity` and `apoli:status_effect`.
- Power tags: `data/<namespace>/tags/power/<name>.json` groups powers as `#<namespace>:<name>`.
- Data pack gamerules: `data/<namespace>/gamerules/<name>.json` adds the gamerule `<namespace>:<name>`, set with `/gamerule` and saved with the world. Packs read it with the `aea:gamerule` condition (which also reads vanilla gamerules) or the `gamerule(...)` expression function.
- Settings `data_pack_textures.enabled` and `data_pack_assets.enabled` in `config/aea-common.toml`, both on by default.
- This changelog.

### Changed
- The documentation moved to its own repository, [AEA-Docs](https://github.com/AlphaSuckss/AEA-Docs).

## 1.2.2

### Fixed
- Powers from 1.20.1 packs that set no `key` now use Origins' primary ability key. Before, they used a key that is unbound by default, so abilities such as a keyless `active_self` could not be triggered.

## 1.2.1

### Changed
- Source layout cleanup: one file per type, arranged like Overgrown's Apoli. No change in behaviour.

## 1.2.0

The first version in this repository. It includes:

- Conversion of every part of a 1.20.1 data pack, not only powers: functions, recipes, advancements, loot tables, predicates, item modifiers, tags and worldgen, including old item NBT and commands.
- Fabric load conditions and Fabric's old common tags are converted for NeoForge.
- AEA 1.5.4's own types: `aea:gui`, `aea:mob_ai`, `aea:mob_pathing` and `aea:velocity_immunity`, and function macros.
- A Pehkui-compatible `/scale` command when Pehkui is not installed.
