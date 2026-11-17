# Changelog

Every change to AEA for NeoForge 1.21.1, newest first.

## 1.3.0 (in progress)

### Added
- `aea:hitbox_part`: a power that gives an entity an extra hit box. The box follows the entity's body, and a hit on it counts as a hit on the entity. With hit boxes shown (F3 + B) it is outlined in green.
- Data pack textures: PNG files under `data/<namespace>/textures/` in a data pack are sent to players and registered under `<namespace>:textures/...`, so powers can use them without a resource pack. No resource reload is needed.
- Data pack assets: the `assets/` folder of a data pack in the world's `datapacks` folder is sent to players and loaded on their game as a resource pack. This covers block and item textures, models, sounds, fonts, language files, animated textures, and the Blockbench models and animations of `apoli:custom_model_render`. The player's resources reload only when the files are new or have changed: the pack stays loaded after leaving and is loaded with the rest of the game at the next start.
- `aea:see_invisible`: a power that lets the holder see invisible entities at a chosen opacity. It can be limited to invisibility from the effect, from certain powers, or from powers with a tag (Apoli's own power `tags`), and by a bi-entity condition. Sources can also be written as objects, as in AEA for Fabric. Only what the holder sees changes; `apoli:invisible` still passes.
- `aea:milk_immunity`: a power that stops milk from removing some or all status effects, with `effect`, `effects`, `effect_type` and `inverted`.
- Origin grid: with Origins installed, the choose-origin screen shows every origin of the layer as icons in a grid (35 per page), with the origin's window beside it. Ported from Alternate Origin GUI 1.1.5 by UltrusBot (MIT). Players can switch it off with `origin_screen.grid` in `aea-client.toml`; on screens too small for it, Origins' arrows stay.
- `aea:prevent_spawn_point`: a power that stops the player's spawn point from being set or moved by beds, respawn anchors, `/spawnpoint` or anything else, chosen with `source`, with an optional `message`. Sleeping and clearing a spawn point still work.
- `effect_type` (`beneficial`, `harmful` or `neutral`) for `apoli:effect_immunity` and `apoli:status_effect`.
- Data pack gamerules: `data/<namespace>/gamerules/<name>.json` adds the gamerule `<namespace>:<name>`, set with `/gamerule` and saved with the world. Packs read it with the `aea:gamerule` condition (which also reads vanilla gamerules, and works in every condition context including static) or the `gamerule(...)` expression function.
- Settings `data_pack_textures.enabled` and `data_pack_assets.enabled` in `config/aea-common.toml`, both on by default.
- This changelog.

### Changed
- The documentation moved to its own repository, [AEA-Docs](https://github.com/AlphaSuckss/AEA-Docs).
- Origins is now an optional dependency. AEA never used Origins classes, so it runs on Apoli alone; with Origins installed nothing changes.
- Brought in line with Overgrown's addon guide:
  - Optional nested actions and conditions in AEA's types are read with `LoggedOptionalField`: a malformed action logs a warning naming the power and field, and a malformed condition fails the power instead of being dropped silently.
  - The power `condition` of `aea:hitbox_part` and `aea:mob_ai` is checked in `isActive`, on the context Apoli already builds, instead of building a new one every tick.
  - `aea:see_invisible` resolves its `condition` on the server and the client reads the result (`resolvesForClient`).
  - Hooks that run for every entity (`canAttack`, sleeping near monsters, powder snow, sprint particles, `apoli:status_effect`, skin and upside-down rendering) use `@ModifyReturnValue` instead of a cancellable `@Inject`, and server hooks skip all work when no entity holds the power type.
  - Conditions no longer allocate or look things up per test: `aea:gamerule` parses its rule once, `effect_type` decodes once, and the legacy `apoli:category`, `apoli:material`, `apoli:fluid` and `apoli:nbt` conditions resolve their tags, fluid and component types at load.
  - The legacy `damage_over_time` keeps its timer in Apoli's aux data and only writes it when the entity enters or leaves the condition, instead of rewriting persistent NBT every tick.
  - `onRemoved` hooks wait until the last source of a power is gone.
  - Client-only mixins are marked `@OnlyIn(Dist.CLIENT)`.
  - The list of mobs provoked by `sync:mobs_ignore` holders is cleared when the holder leaves.

### Removed
- AEA's own power tag files (`data/<namespace>/tags/power/`). Apoli already gives powers a `tags` field, so `#tag` sources now read that instead.

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
