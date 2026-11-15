---
title: "Mob Pathing"
description: "Changes the routes a mob plans: how high it steps, how far it drops, and which blocks it avoids."
type_id: "aea:mob_pathing"
order: 3
---

Changes how the mob that has the power finds its way. It can plan routes up taller steps and down longer drops, keep following a path while in the air, and treat chosen blocks as preferred, costly or impassable.

The power only works on mobs. If a mob has several active `aea:mob_pathing` powers, the first one is used.

> [!NOTE]
> This power changes the routes a mob plans. It does not change what the mob can physically do. A mob with `"jump_height": 3` will plan a route up a 3 block ledge, but it still needs a way to get up there, such as a jump boost.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `jump_height` | Float or [Expression](https://0vergrown.github.io/Handbook/docs/datapack/data-types/expression) | _optional_ | How many blocks up the mob may plan to step at once. Rounded down, at most `8`. It can only raise the mob's normal limit. |
| `drop_height` | Float or [Expression](https://0vergrown.github.io/Handbook/docs/datapack/data-types/expression) | _optional_ | How many blocks down the mob is willing to drop. Rounded down, at most `256`. It replaces the mob's normal limit. |
| `airborne_pathing` | Boolean | `false` | Lets the mob keep following and updating its path while it is off the ground. Mobs normally wait until they land. |
| `airborne_reach` | Float | `4.0` | Used with `airborne_pathing`. While the mob is in the air, the next point of its path counts as reached when the mob is up to this many blocks above it. |
| `walkable` | [Block Condition](https://0vergrown.github.io/Handbook/docs/datapack/block-conditions) | _optional_ | The mob only walks where the block at the position, or the block under it, passes this condition. Points in open air, water and lava are not checked. |
| `blocked` | [Block Condition](https://0vergrown.github.io/Handbook/docs/datapack/block-conditions) | _optional_ | Positions whose block passes this condition are impassable. |
| `avoid` | Array of [avoid rules](#avoid-rules) | `[]` | Blocks the mob prefers to stay away from. |
| `penalties` | Object | `{}` | Replaces the mob's cost for [node types](#node-types). Each key is a node type and each value is a number. |
| `repath_interval` | Integer | `0` | Recalculates the mob's current path every this many ticks. `0` turns it off. |

### Avoid rules

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `block_condition` | [Block Condition](https://0vergrown.github.io/Handbook/docs/datapack/block-conditions) | | The blocks this rule applies to. It is tested on the block at the position and the block under it. |
| `penalty` | Float | `8.0` | Extra cost for walking there. Higher values make the mob take longer detours. A negative value makes the position impassable. |

When several rules match one position, the highest penalty is used. Only the first 30 rules are read.

### Node types

The game sorts every position into a node type while it plans a route. In `penalties`, `0` means no extra cost, higher numbers make the mob avoid that type, and a negative number means the mob never walks there.

`blocked`, `open`, `walkable`, `walkable_door`, `trapdoor`, `powder_snow`, `danger_powder_snow`, `fence`, `lava`, `water`, `water_border`, `rail`, `unpassable_rail`, `danger_fire`, `damage_fire`, `danger_other`, `damage_other`, `door_open`, `door_wood_closed`, `door_iron_closed`, `breach`, `leaves`, `sticky_honey`, `cocoa`, `damage_cautious`

## Examples

```json
{
    "type": "aea:mob_pathing",
    "jump_height": 3,
    "drop_height": 12,
    "airborne_pathing": true,
    "repath_interval": 20
}
```

The mob plans routes up 3 block ledges and down 12 block drops, keeps steering while in the air, and rechecks its route once a second.

```json
{
    "type": "aea:mob_pathing",
    "penalties": {
        "water": -1,
        "danger_fire": 0
    },
    "avoid": [
        {
            "block_condition": {
                "type": "apoli:block",
                "block": "minecraft:soul_sand"
            },
            "penalty": 16
        }
    ]
}
```

The mob never enters water, walks next to fire without hesitating, and goes around soul sand unless the detour is long.
