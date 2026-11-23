# Changelog

Every change to AEA for NeoForge 1.21.1, newest first.

## Unreleased

### Added
- `aea:redstone_pulse` (block action): sends a short redstone signal into a block (`receive`) or out of a position (`emit`), with `strength`, `duration`, `strong` and `sides`. Nothing is placed: blocks see the power the way they see a lever's, so pistons, wire, doors and modded machines react.
- `/aea redstone pulse <pos> [receive|emit] [strength] [duration] [strong]`, the same pulse from commands and functions.
- AppleSkin compatibility, active only when AppleSkin is installed: its tooltips and hunger bar preview show the values `apoli:modify_food` gives.

## 1.5.0

### Added
- TaCZ compatibility, active only when TaCZ is installed:
  - `aea:modify_gun`: changes gun stats with Apoli modifiers: `damage`, `fire_rate`, `recoil`, `inaccuracy`, `aim_inaccuracy`, `aim_time`, `bullet_speed`, `armor_ignore`, `headshot_multiplier`, `knockback`, `pierce`, `effective_range`, `weight`, plus `ammo_save_chance`. Stacks with attachments, and updates the held gun when the power turns on or off.
  - `aea:prevent_gun_use`: blocks `shoot`, `aim`, `reload` and `melee`, each switchable, with an optional `message`.
  - Gun event powers: `aea:action_on_gun_fire`, `aea:action_on_gun_hit` and `aea:action_on_gun_kill` (with `headshot`), `aea:action_on_bullet_hit_block`, `aea:action_on_gun_reload` (`when`: start or finish) and `aea:action_on_gun_melee`.
  - Conditions: item `aea:gun` (gun, type, fire mode, magazine ammo, attachments) and `aea:gun_ammo`; entity `aea:aiming_gun` and `aea:reloading_gun`; damage `aea:gun` (gun, type, `headshot`).
  - Entity and item action `aea:refill_ammo`.
  - Every gun type takes `gun` and `gun_type` to limit it to some guns.
- Spell Engine compatibility, active only when Spell Engine is installed:
  - `aea:grant_spell`: gives spells as if from equipment, on Spell Engine's own spell hotbar. Active spells work whatever the player holds, unless `always` is false.
  - `aea:modify_spell`: changes `cast_time`, `cooldown` and `range` with Apoli modifiers.
  - `aea:prevent_spell_cast`: blocks casting, with an optional `message`.
  - `aea:action_on_spell_cast`: runs an entity action on the caster and a bi-entity action on each target.
  - Entity and bi-entity action `aea:cast_spell`: casts any spell at once, with or without its cost (`use_cost`).
  - Conditions: entity `aea:casting_spell`, `aea:spell_on_cooldown` and `aea:knows_spell`; item `aea:has_spell`; damage `aea:spell_damage` (by school).
  - Every spell type takes `spell` (ids or `#`tags) and `school` to limit it to some spells.

## 1.4.0

### Added
- `aea:hitbox_part`: a power that gives an entity an extra hit box. The box follows the entity's body, and a hit on it counts as a hit on the entity. With hit boxes shown (F3 + B) it is outlined in green.
- Data pack textures: PNG files under `data/<namespace>/textures/` in a data pack are sent to players and registered under `<namespace>:textures/...`, so powers can use them without a resource pack. No resource reload is needed.
- Data pack assets: the `assets/` folder of a data pack in the world's `datapacks` folder is sent to players and loaded on their game as a resource pack. This covers block and item textures, models, sounds, fonts, language files, animated textures, and the Blockbench models and animations of `apoli:custom_model_render`. The player's resources reload only when the files are new or have changed: the pack stays loaded after leaving and is loaded with the rest of the game at the next start.
- `aea:see_invisible`: a power that lets the holder see invisible entities at a chosen opacity. It can be limited to invisibility from the effect, from certain powers, or from powers with a tag (Apoli's own power `tags`), and by a bi-entity condition. Sources can also be written as objects, as in AEA for Fabric. Only what the holder sees changes; `apoli:invisible` still passes.
- `aea:milk_immunity`: a power that stops milk from removing some or all status effects, with `effect`, `effects`, `effect_type` and `inverted`.
- Mind control: the bi-entity action `aea:mind_control` lets a player take over a mob or another player, at any distance and in any dimension. The player sees through the target's eyes and their keys move it, while their body stays behind and takes their damage. Packs choose what is taken over (`controls`: movement, look, jump, sneak, sprint, attack, use, hotbar, drop, swap hands, chat, commands), and can set a `duration`, a `release_condition`, a `struggle` count to break free, `kill_credit`, `close_screens`, whether the controller's body can be hurt (`body_damage`), how much of the target's damage also hits the controller (`share_damage`), and `on_start`/`on_release` actions. Also adds the entity action `aea:release_mind_control` and the conditions `aea:mind_controlled`, `aea:mind_controlling` and `aea:mind_controls`. A player who disconnects or a server that stops mid-control puts the controller back in their body on their next login.
- Origin grid: with Origins installed, the choose-origin screen shows every origin of the layer as icons in a grid (35 per page), with the origin's window beside it. Ported from Alternate Origin GUI 1.1.5 by UltrusBot (MIT). Players can switch it off with `origin_screen.grid` in `aea-client.toml`; on screens too small for it, Origins' arrows stay.
- `aea:prevent_spawn_point`: a power that stops the player's spawn point from being set or moved by beds, respawn anchors, `/spawnpoint` or anything else, chosen with `source`, with an optional `message`. Sleeping and clearing a spawn point still work.
- `effect_type` (`beneficial`, `harmful` or `neutral`) for `apoli:effect_immunity` and `apoli:status_effect`.
- Data pack gamerules: `data/<namespace>/gamerules/<name>.json` adds the gamerule `<namespace>:<name>`, set with `/gamerule` and saved with the world. Packs read it with the `aea:gamerule` condition (which also reads vanilla gamerules, and works in every condition context including static) or the `gamerule(...)` expression function.
- Settings `data_pack_textures.enabled` and `data_pack_assets.enabled` in `config/aea-common.toml`, both on by default.
- This changelog.

### Changed
- The documentation moved to its own repository, [AEA-Docs](https://github.com/AlphaSuckss/AEA-Docs).
- Origins is now an optional dependency. AEA never used Origins classes, so it runs on Apoli alone; with Origins installed nothing changes.

## 1.2.2

### Fixed
- Powers from 1.20.1 packs that set no `key` now use Origins' primary ability key. Before, they used a key that is unbound by default, so abilities such as a keyless `active_self` could not be triggered.

## 1.2.0

The first version in this repository. It includes:

- Conversion of every part of a 1.20.1 data pack, not only powers: functions, recipes, advancements, loot tables, predicates, item modifiers, tags and worldgen, including old item NBT and commands.
- Fabric load conditions and Fabric's old common tags are converted for NeoForge.
- AEA 1.5.4's own types: `aea:gui`, `aea:mob_ai`, `aea:mob_pathing` and `aea:velocity_immunity`, and function macros.
- A Pehkui-compatible `/scale` command when Pehkui is not installed.
