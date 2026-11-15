---
title: "GUI"
description: "A custom screen built from images, text, buttons, sliders, entity previews and item slots."
type_id: "aea:gui"
order: 1
---

Defines a screen the player can open. The screen is a list of elements placed on a panel. Buttons and sliders run actions, and slots hold real items that other actions and conditions can read, change and craft with.

The power does nothing until something opens it with the `aea:open_gui` action. See [Opening the screen](#opening-the-screen).

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `id` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | The ID other types use to refer to this screen. Defaults to the power's own ID. |
| `title` | [Text Component](https://0vergrown.github.io/Handbook/docs/datapack/data-types/text-component) | _optional_ | Title drawn in the top left corner of the panel. |
| `elements` | Array of [elements](#elements) | `[]` | What is on the screen. Later elements are drawn over earlier ones. |
| `show_vanilla_background` | Boolean | `true` | Draws the standard grey panel and darkens the world behind it. Turn it off to draw your own background with an image. |
| `width` | Integer | _optional_ | Width of the panel in pixels, from `1` to `4096`. |
| `height` | Integer | _optional_ | Height of the panel in pixels, from `1` to `4096`. |
| `show_inventory` | Boolean | `false` | Shows the player's inventory and hotbar so items can be moved in and out. |
| `inventory_x` | Integer | `0` | Moves the inventory sideways, in pixels. |
| `inventory_y` | Integer | `0` | Moves the inventory up or down, in pixels. |
| `memory` | Boolean | `false` | Keeps slot contents and slider values between openings. See [Memory](#memory). |
| `pause_game` | Boolean | `false` | Pauses a singleplayer game while the screen is open. Only works on a screen with no slots, buttons, sliders or inventory. |

## Opening the screen

The `aea:open_gui` entity action opens the screen of one of the player's `aea:gui` powers. The player needs both powers: the screen and whatever opens it.

```json
{
    "type": "apoli:action_on_key_press",
    "key": "key.origins.primary_active",
    "entity_action": {
        "type": "aea:open_gui",
        "power": "mypack:satchel"
    }
}
```

Pressing the primary ability key opens the screen defined by the power `mypack:satchel`.

## Layout

With `show_vanilla_background` on, the panel is 176 by 166 pixels unless `width` and `height` say otherwise. That is the size of a chest screen. With it off and no size set, the panel shrinks to fit its elements.

Each element is placed with an `anchor` and an offset. The anchor names a point of the panel, and the same point of the element is put there. `x` and `y` then move the element right and down.

- `"anchor": "center"` with no offset centres the element on the panel.
- `"anchor": "top_left", "x": 8, "y": 20` puts the element's top left corner 8 pixels from the left edge and 20 from the top.
- `"anchor": "bottom_right", "x": -8, "y": -8` puts its bottom right corner 8 pixels in from that corner.

With `show_inventory` on, the inventory sits at the bottom of a fixed-size panel, or under the elements when the panel has no fixed size.

## Elements

Every element has a `type`. The `aea:` prefix is optional. These fields are shared by all of them:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `x` | Integer | `0` | Offset to the right of the anchor, in pixels. |
| `y` | Integer | `0` | Offset below the anchor, in pixels. |
| `anchor` | String | `"center"` | One of `center`, `top_left`, `top`, `top_right`, `left`, `right`, `bottom_left`, `bottom`, `bottom_right`. |
| `width` | Integer | _varies_ | Width of the element in pixels. |
| `height` | Integer | _varies_ | Height of the element in pixels. |
| `condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The element is only shown, and can only be used, while the player passes this condition. |
| `gui_scale_lock` | Integer | _optional_ | Draws the element as if the game's GUI scale were this value, from `1` to `6`, whatever the player has set. |

### `aea:image`

Draws a texture, or part of one. With `progress` it works as a progress bar. The default size is the texture size, or 16 by 16 if that is not given.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | | The texture file, for example `mypack:textures/gui/panel.png`. |
| `texture_width` | Integer | _optional_ | Width of the whole texture file in pixels. Defaults to the element's width. |
| `texture_height` | Integer | _optional_ | Height of the whole texture file in pixels. Defaults to the element's height. |
| `u` | Integer | `0` | Left edge of the part to draw, in pixels of the texture. |
| `v` | Integer | `0` | Top edge of the part to draw, in pixels of the texture. |
| `progress` | Float or [Expression](https://0vergrown.github.io/Handbook/docs/datapack/data-types/expression) | _optional_ | How much of the image is drawn, from `0` to `1`. An expression is worked out again every tick. |
| `progress_direction` | String | `"horizontal"` | `horizontal` or `vertical`. |
| `progress_invert` | Boolean | `false` | Fills from the opposite side. |

> [!NOTE]
> Textures are loaded by the player's game. A texture that is not part of Minecraft has to come from a resource pack or a mod.

### `aea:text`

Draws a line of text. Text can contain [placeholders](#placeholders).

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `text` | [Text Component](https://0vergrown.github.io/Handbook/docs/datapack/data-types/text-component) | | The text to draw. |
| `color` | Color | white | Text color, as a hex string such as `"#FFAA00"`. |
| `shadow` | Boolean | `true` | Draws the text with a shadow. |
| `centered` | Boolean | `false` | Centres the text on its position. |

### `aea:button`

A button that runs actions on the player. It looks like a vanilla button unless textures are given. The default size is 80 by 20.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `text` | [Text Component](https://0vergrown.github.io/Handbook/docs/datapack/data-types/text-component) | _optional_ | Label on the button. |
| `text_color` | Color | white | Label color. |
| `texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Texture for the button. |
| `hover_texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Texture while the mouse is over the button. |
| `pressed_texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Texture while the button is held down. |
| `action_on_press` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs when the button is pressed. |
| `action_while_pressed` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs every tick while the button is held down. |
| `action_on_release` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs when the button is let go. |
| `press_condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The button can only be pressed while the player passes this condition. |
| `cooldown` | Integer | `0` | Ticks before the button can be pressed again. |

### `aea:slider`

A slider the player drags to pick a number. The default size is 120 by 20.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `id` | String | | Name of the slider. It has to be unique on the screen. |
| `min` | Float | `0` | Lowest value. |
| `max` | Float | `100` | Highest value. It has to be greater than `min`. |
| `start_value` | Float | _optional_ | Value when the screen opens. Defaults to `min`. |
| `step` | Float | `1` | The slider snaps to multiples of this. `0` allows any value. |
| `text` | [Text Component](https://0vergrown.github.io/Handbook/docs/datapack/data-types/text-component) | _optional_ | Label on the slider. Use the `{value}` placeholder to show the number. |
| `text_color` | Color | white | Label color. |
| `background_texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Texture for the track. |
| `slider_texture_location` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Texture for the handle. |
| `slider_width` | Integer | `8` | Width of the handle in pixels. |
| `action_on_slide` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs when the value changes. |
| `action_on_release` | [Entity Action](https://0vergrown.github.io/Handbook/docs/datapack/entity-actions) | _optional_ | Runs when the player lets go of the handle. |
| `slide_condition` | [Entity Condition](https://0vergrown.github.io/Handbook/docs/datapack/entity-conditions) | _optional_ | The slider can only be moved while the player passes this condition. |

### `aea:entity`

Draws an entity, the way the inventory screen draws the player. The default size is 50 by 70. Give exactly one of `entity_type`, `selector` or `set`.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `entity_type` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Draws a stand-in of this entity type, for example `minecraft:zombie`. |
| `selector` | String | _optional_ | Draws the entity found by a selector. `"@s"` is the player who has the screen open. |
| `set` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | _optional_ | Draws a member of this entity set. |
| `equipment` | Array of Objects | `[]` | Items the stand-in wears or holds. Each entry has a `slot` (`mainhand`, `offhand`, `head`, `chest`, `legs` or `feet`) and an `item` in the [item format](#items). |
| `nbt` | [NBT](https://0vergrown.github.io/Handbook/docs/datapack/data-types/nbt) | _optional_ | Entity data for the stand-in. |
| `follow_mouse` | Boolean | `false` | The entity looks at the mouse pointer. |
| `scale` | Float | `1.0` | Size multiplier, from `0.01` to `100`. |
| `show_scale` | Boolean | `false` | Draws the entity larger or smaller to match its current scale in the world. |
| `fit_scale` | Float | `1.0` | Used with `show_scale`. The scale at which the entity exactly fills the element. |

### `aea:slot`

One item slot.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `id` | String | _optional_ | Name of the slot, used by the slot actions and conditions. Give every slot you refer to an `id`. |
| `slot_type` | String | `"storage"` | `storage` and `input` let the player put items in and take them out. `output` only lets items be taken out. `display` shows `item` and cannot be changed by the player. |
| `item_condition` | [Item Condition](https://0vergrown.github.io/Handbook/docs/datapack/item-conditions) | _optional_ | Only items that pass this condition can be put in. |
| `max_stack` | Integer | _optional_ | Most items the slot holds, from `1` to `64`. |
| `item` | [Item](#items) | _optional_ | The item a `display` slot shows. |
| `background` | Boolean | `true` | Draws the slot's square background. |

### `aea:slot_grid`

A grid of item slots. It takes the same fields as `aea:slot`, which apply to every cell, and these:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `rows` | Integer | | Number of rows, from `1` to `32`. |
| `columns` | Integer | | Number of columns, from `1` to `32`. |
| `visible` | String or Array of Strings | `[]` | Which cells exist. Each entry is `"row,column"`, counted from 1, and either part can be a range such as `"1-3,2"`. Empty means every cell. |
| `trim` | Boolean | `true` | When only some cells are visible, cuts away the unused rows and columns around them, keeping a margin of one cell. |
| `cell_size` | Integer | `18` | Size of one cell in pixels, from `16` to `256`. |

A cell is named after its grid: the cell in row 2, column 3 of a grid with the ID `bag` is `bag[2,3]`. The ID `bag` on its own means every cell of the grid.

## Items

Wherever an element takes an item, it can be an item ID or an object:

```json
{
    "item": "minecraft:diamond_sword",
    "count": 1,
    "components": {
        "minecraft:custom_name": "\"Old Blade\""
    }
}
```

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `item` | [Identifier](https://0vergrown.github.io/Handbook/docs/datapack/data-types/identifier) | | The item. `id` is accepted as another name for this field. |
| `count` | Integer | `1` | Stack size. `amount` is accepted as another name. |
| `components` | Object | _optional_ | Item components, in the 1.21.1 format. |
| `nbt` | [NBT](https://0vergrown.github.io/Handbook/docs/datapack/data-types/nbt) | _optional_ | Item NBT in the 1.20.1 format. AEA converts it to components. `tag` is accepted as another name. |

## Placeholders

Text on the screen, and the command of an `aea:gui_command` action, can contain these:

| Placeholder | Replaced with |
| --- | --- |
| `{value}` | The value of the slider the text or action belongs to. |
| `{slider:<id>}` | The value of the slider with that ID. |
| `{player}` | The player's name. |

## Memory

Without `memory`, items left in the slots go back to the player when the screen closes.

With `"memory": true`, slot contents and slider values are saved for each player and restored the next time the screen opens. They are kept through death. `display` slots are never saved. If a pack update removes a slot that still held items, those items are handed back to the player.

Saved data is filed under the screen's `id`, so changing the ID starts the screen empty.

## Related actions and conditions

These types work with an open screen. In all of them, `slot` takes a slot ID, a grid ID or a single cell such as `bag[2,3]`.

| Type | Kind | What it does |
| --- | --- | --- |
| `aea:open_gui` | Entity action | Opens the screen of the power named in `power`. |
| `aea:close_gui` | Entity action | Closes the open screen. With `gui`, only if it is that screen. |
| `aea:gui_command` | Entity action | Runs `command` with the placeholders filled in. The command runs as the player with operator permission, and its output is hidden. |
| `aea:modify_gui_slot` | Entity action | Changes the items in `slot`. `operation` is `set`, `add`, `consume` or `clear`, with an optional `item` and `count`. |
| `aea:gui_slot_action` | Entity action | Runs `item_action` on the item in each matching `slot`. |
| `aea:gui_craft` | Entity action | Crafts one result from the `input` slots into the `output` slot. `recipe_type` is `crafting`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting` or `smithing`. |
| `aea:gui_open` | Entity condition | Whether the player has a screen open. With `gui`, whether it is that screen. |
| `aea:gui_slot` | Entity condition | Whether `slot` holds an item. With `comparison` and `compare_to`, compares the number of items instead. |
| `aea:gui_value` | Entity condition | Compares the value of `slider` using `comparison` and `compare_to`. |

## Examples

```json
{
    "type": "aea:gui",
    "title": "Satchel",
    "show_inventory": true,
    "memory": true,
    "elements": [
        {
            "type": "aea:slot_grid",
            "id": "bag",
            "rows": 2,
            "columns": 5,
            "anchor": "top",
            "y": 22
        }
    ]
}
```

A ten slot bag that keeps its contents. The player's inventory is shown under it so items can be moved across.

```json
{
    "type": "aea:gui",
    "title": "Wind",
    "height": 70,
    "elements": [
        {
            "type": "aea:slider",
            "id": "strength",
            "min": 1,
            "max": 5,
            "text": "Strength: {value}",
            "anchor": "top",
            "y": 20
        },
        {
            "type": "aea:button",
            "text": "Launch",
            "anchor": "bottom",
            "y": -8,
            "cooldown": 20,
            "action_on_press": {
                "type": "aea:gui_command",
                "command": "effect give @s minecraft:levitation {slider:strength} 1 true"
            }
        }
    ]
}
```

A slider from 1 to 5 and a button. Pressing the button gives the player levitation for as many seconds as the slider shows.
