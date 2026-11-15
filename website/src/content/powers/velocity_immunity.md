---
title: "Velocity Immunity"
description: "Stops add_velocity actions from moving the entity that has the power."
type_id: "aea:velocity_immunity"
order: 4
---

While this power is active, `apoli:add_velocity` actions aimed at the entity do nothing. The power covers both forms of the action: the entity action, and the bi-entity action when the entity is the target.

Only that action is blocked. Knockback from attacks, explosions, pistons and other movement still apply.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `entity` | Boolean | `true` | Blocks the entity action form of `add_velocity` on the entity. |
| `bientity` | Boolean | `true` | Blocks the bi-entity action form of `add_velocity` when the entity is its target. |
| `bientity_condition` | [Bi-entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/bientity-conditions) | _optional_ | Limits which bi-entity actions are blocked. The actor is the entity that ran the action and the target is the entity with the power. Without it, every bi-entity `add_velocity` is blocked. |

## Examples

```json
{
    "type": "aea:velocity_immunity"
}
```

No `add_velocity` action can move the entity.

```json
{
    "type": "aea:velocity_immunity",
    "entity": false,
    "bientity_condition": {
        "type": "apoli:actor_condition",
        "condition": {
            "type": "apoli:entity_type",
            "entity_type": "minecraft:player"
        }
    }
}
```

Other players cannot launch the entity with a bi-entity `add_velocity`. The entity's own dashes and leaps, which use the entity action form, keep working.
