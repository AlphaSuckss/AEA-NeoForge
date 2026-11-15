# AEA for Overgrown's Origins (NeoForge 1.21.1)

AEA (Alpha's Everything Addon) runs content made for **Origins 1.10.2 / Apoli 2.9.2 / Sync 1.11.8 on Fabric 1.20.1**
on top of **Overgrown's Apoli 1.124.0 + Origins 1.49.0 for NeoForge 1.21.1**.

## Build
1. Copy Overgrown's `apoli-1.124.0.jar` and `origins-1.49.0.jar` into `libs/` (they aren't included in this repo).
2. `gradlew build` (JDK 21). The jar lands in `build/libs/`.

> The Gradle setup (ModDevGradle) hasn't been run yet: releases so far were compiled directly with `javac` against the NeoForge 21.1.265 jars. Report it if it doesn't build.

The port's smoke tests live in `src/selftest/java` and are not in the release jar. `gradlew selftestJar` (or `build.sh`) builds `aea-<version>-selftest.jar` with them; `runServer` uses them too. With `-Daea.selftest=true` that jar adds the destructive `/aea selftest grant|functions|player|gui|items|legacy|audit|probe <power>`.

## Layout
| Package | What it does |
|---|---|
| `legacy/` | Pack layer. `LegacyResources` (+ `mixin/legacy`) serves `functions/`, `tags/items/`, `loot_tables/`… under the 1.21 names and rewrites file contents of packs whose `pack_format` < 48. `CommandUpgrader` (commands), `ApoliJsonUpgrader` (powers/origins/layers), `VanillaJsonUpgrader` (recipes, advancements, loot, predicates, item modifiers), `NbtUpgrader` (Mojang DataFixer for items/entities/particles), `TextFixes`/`TextComponents`, `WorldgenUpgrader`, `LoadConditions` (`fabric:load_conditions`), `ConventionalTags` (Fabric `c:` v1 → v2), `ApoliCommandUpgrader` (`/power`). |
| `compat/` | `AEATypes` registers the sync, legacy and addon types through `SyncTypes` / `LegacyTypes` / `AddonTypes` (`gui/GuiTypes` does the same for the GUI). Each type is one file, laid out like Overgrown's Apoli: `power/XxxPower`, `action/<context>/XxxAction`, `condition/<context>/XxxCondition`. |
| `compat/sync/` | Sync 1.11.8 forward port. `sync:` falls back to `apoli:` (Overgrown's improved versions); types whose fields differ or that Overgrown lacks are implemented here. |
| `compat/legacy/` | Apoli 2.9 / Origins 1.10 types Overgrown dropped (`set_resource`, `side`, `category`, `material`, `fluid`, `launch`, `modify_lava_speed`, `modify_insomnia_ticks`, `modify_fluid_render`) and the legacy item `nbt` condition. |
| `compat/addon/` | Types used by community packs: `aea:velocity_immunity` (AEA 1.5.4 semantics), `moborigins:walk_on_powder_snow`, `apugli:mobs_ignore`, `apoli:damage_immunity`, `apoli:modify_drops`, damage conditions `damage_type` / `attacker_action` / `command`. |
| `gui/` | AEA's data-driven GUI: power `aea:gui`, actions `open_gui` `close_gui` `gui_command` `gui_slot_action` `modify_gui_slot` `gui_craft`, conditions `gui_open` `gui_slot` `gui_value`. Server menu/session/memory + NeoForge payloads; `gui/client` is the screen. |
| `mobai/` | AEA's `aea:mob_ai` (goals `target` `wander` `look_around` `look_at` `melee_attack` `follow` `flee` `custom` `retaliate`, removal, `never_target`, `attack_damage`) and `aea:mob_pathing`; `mobai/mixin/fix` holds AEA's tamed-target fix. |
| `compat/AEALoot` | `origins:origin` and `apoli:power` loot conditions (predicates). |
| `command/` | `/aea validate`, `/aea validate fields`, `/aea upgrade <command>`, Pehkui-compatible `/scale` when Pehkui is absent. |
| `content/` | `origins:temporary_cobweb` (Origins 1.10.2 Arachnid webbing). |
| `client/`, `mixin/client` | `sync:flip_model`, `sync:entity_texture_overlay`. |

## Config
`config/aea-common.toml` → `legacy_datapacks.enabled` (default true), `log_rewrites`.

## License
MIT, see `LICENSE`. Third-party material: see `THIRD_PARTY.md`.
