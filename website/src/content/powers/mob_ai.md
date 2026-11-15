---
title: "Mob AI"
description: "Gives a mob new goals and targets, removes vanilla ones, and controls what it may attack."
type_id: "aea:mob_ai"
order: 2
---

Changes the behaviour of the mob that has the power. It can add goals such as following, fleeing or attacking, remove the mob's vanilla goals, stop it from ever targeting certain entities, and set its attack damage.

The power only works on mobs, not on players. Give it to a mob the same way as any other power, for example with `/power grant`. Everything it changed is put back when the power is removed, or while the power's `condition` does not hold.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `remove` | String or Array of Strings | `[]` | Vanilla goals to take away from the mob. See [Remove categories](#remove-categories). |
| `goals` | Array of [goals](#goals) | `[]` | Goals to add. |
| `targets` | Array of [target goals](#target-goals) | `[]` | Target goals to add. These decide what the mob attacks. |
| `never_target` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | The mob can never attack an entity that passes this condition. The actor is the mob and the target is the other entity. |
| `attack_damage` | Float | _optional_ | Sets the mob's attack damage to this value. |
| `reapply_interval` | Integer | `20` | How often, in ticks, the goals listed in `remove` are removed again in case the mob added them back. `0` removes them only once. |

> [!NOTE]
> Mobs that cannot normally attack, such as cows, have no attack damage. When the power sets `attack_damage` or adds an `aea:melee_attack` goal, the mob is given a base attack damage of 2.

## Remove categories

| Value | Removes |
| --- | --- |
| `all` | Every goal and target goal, and stops the mob's brain. |
| `goals` | Every goal, but no target goals. |
| `targets` | Every target goal. |
| `brain` | Stops the mob's brain. See the note below. |
| `wander` | Walking around at random. |
| `look_around` | Looking around at random. |
| `look_at` | Looking at players and other entities. |
| `melee` | Melee attacks. |
| `ranged` | Bow, crossbow and other ranged attacks. |
| `follow_owner` | A pet following its owner. |
| `panic` | Running away after being hurt. |
| `flee` | Avoiding certain entities, such as a creeper avoiding cats. |
| `swim` | Floating up in water. |
| `revenge` | Targeting whatever hurt the mob. |

A value that contains a `.` is read as the Java class name of a goal, for example `net.minecraft.world.entity.ai.goal.TemptGoal`, and removes goals of that class. Class names written for 1.20.1 Fabric are translated for common vanilla goals.

> [!NOTE]
> Villagers, piglins, axolotls and some other mobs are driven by a brain instead of goals. The named categories have no effect on them. Use `"remove": "brain"` to stop their vanilla behaviour. Goals added by this power still run on them.

## Goals

Every goal has a `type`. The `aea:` prefix is optional, so `"wander"` and `"aea:wander"` are the same. These two fields are shared:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `priority` | Integer | _varies_ | Lower numbers win when two goals want to control the mob at once. Each goal lists its default. |
| `condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The goal only runs while the mob passes this condition. Not used by `aea:custom`. |

`aea:wander`, `aea:melee_attack` and `aea:flee` need a mob that can walk a path. They are skipped on mobs that cannot, such as slimes and ghasts.

### `aea:wander`

Walks to random places nearby. Default priority `6`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `speed` | Float | `1.0` | Speed multiplier while walking. |
| `interval` | Integer | `120` | Average ticks between walks. |
| `horizontal_range` | Integer | `10` | How far away, in blocks, a destination may be. |
| `vertical_range` | Integer | `7` | How far up or down a destination may be. |

### `aea:look_around`

Looks in random directions while idle. Default priority `8`. It has no fields of its own.

### `aea:look_at`

Looks at the nearest matching entity for a few seconds. Default priority `7`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `range` | Float | `8.0` | How close the entity has to be. |
| `chance` | Float | `0.02` | Chance each tick that the mob starts looking. |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Which entities the mob looks at. The actor is the mob. |

### `aea:melee_attack`

Chases the mob's current target and hits it. Default priority `2`. The goal does not pick a target itself, so add a [target goal](#target-goals) as well.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `speed` | Float | `1.0` | Speed multiplier while chasing. |
| `pause_when_idle` | Boolean | `false` | When `true`, the mob keeps the attack going after its path to the target runs out, instead of giving up. |

### `aea:follow`

Follows the mob's owner or the nearest matching entity. Default priority `4`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `target` | String | `"owner"` | `"owner"` follows the mob's owner, if it has one. `"nearest"` follows the nearest matching entity. |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Which entities may be followed. The actor is the mob. |
| `speed` | Float | `1.0` | Speed multiplier while following. |
| `start_distance` | Float | `6.0` | The mob starts following once the leader is at least this far away. |
| `stop_distance` | Float | `2.5` | The mob stops once it is this close. |
| `range` | Float | `16.0` | With `"nearest"`, how far the mob looks for someone to follow. It gives up when the leader is more than twice this far away. |
| `teleport_distance` | Float | `0.0` | The mob teleports to the leader when it falls this far behind. `0` never teleports. |

### `aea:flee`

Runs away from the nearest matching entity. Default priority `1`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `range` | Float | `8.0` | How close an entity has to be for the mob to flee. |
| `speed` | Float | `1.0` | Speed multiplier while fleeing. |
| `fast_speed` | Float | `1.25` | Speed multiplier while the entity is within 7 blocks. |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Which entities the mob flees from. The actor is the mob. Without it, the mob flees from every living entity. |

### `aea:custom`

A goal built from conditions and actions. Default priority `3`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `controls` | Array of Strings | `[]` | What the goal takes over while it runs: `move`, `look`, `jump` or `target`. Two goals that use the same control cannot run at the same time. |
| `start_condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The goal starts when the mob passes this condition. |
| `continue_condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The goal keeps running while the mob passes this condition. Defaults to `start_condition`. |
| `start_action` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs on the mob when the goal starts. |
| `tick_action` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs on the mob while the goal is running. |
| `stop_action` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs on the mob when the goal stops. |
| `interval` | Integer | `1` | Ticks between runs of `tick_action`. |
| `duration` | Integer | `0` | The goal stops after this many ticks. `0` means no limit. |
| `cooldown` | Integer | `0` | Ticks the goal waits after stopping before it can start again. |

## Target goals

Target goals go in `targets` and share the `priority` and `condition` fields described under [Goals](#goals).

### `aea:target`

Targets the nearest matching entity. Default priority `2`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `range` | Float | _optional_ | How far the mob looks for a target. Defaults to the mob's follow range. |
| `must_see` | Boolean | `true` | The mob needs a line of sight to pick a target, and drops it after 3 seconds out of sight. |
| `interval` | Integer | `10` | Average ticks between searches for a target. |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Which entities may be targeted. The actor is the mob. |

### `aea:retaliate`

Targets whatever last hurt the mob. Default priority `1`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Which attackers the mob fights back against. The actor is the mob. |
| `alert_others` | Boolean | `false` | Nearby mobs of the same type that have no target join the fight. |
| `alert_range` | Float | `10.0` | How far away those mobs can be. |

## Examples

```json
{
    "type": "aea:mob_ai",
    "remove": ["panic", "wander"],
    "attack_damage": 6,
    "goals": [
        {
            "type": "aea:melee_attack",
            "speed": 1.4
        },
        {
            "type": "aea:wander",
            "speed": 0.8
        }
    ],
    "targets": [
        {
            "type": "aea:retaliate",
            "alert_others": true
        },
        {
            "type": "aea:target",
            "range": 12,
            "bientity_condition": {
                "type": "apoli:target_condition",
                "condition": {
                    "type": "apoli:entity_type",
                    "entity_type": "minecraft:zombie"
                }
            }
        }
    ]
}
```

Turns a passive mob into a guard. It stops panicking, hunts zombies within 12 blocks, fights back when hurt along with others of its kind, and deals 6 damage per hit.

```json
{
    "type": "aea:mob_ai",
    "never_target": {
        "type": "apoli:target_condition",
        "condition": {
            "type": "apoli:entity_type",
            "entity_type": "minecraft:player"
        }
    }
}
```

The mob keeps its vanilla behaviour but never attacks players.
