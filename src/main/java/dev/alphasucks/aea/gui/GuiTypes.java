package dev.alphasucks.aea.gui;

import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.gui.action.entity.CloseGuiAction;
import dev.alphasucks.aea.gui.action.entity.GuiCommandAction;
import dev.alphasucks.aea.gui.action.entity.GuiCraftAction;
import dev.alphasucks.aea.gui.action.entity.GuiSlotAction;
import dev.alphasucks.aea.gui.action.entity.ModifyGuiSlotAction;
import dev.alphasucks.aea.gui.action.entity.OpenGuiAction;
import dev.alphasucks.aea.gui.condition.entity.GuiOpenCondition;
import dev.alphasucks.aea.gui.condition.entity.GuiSlotCondition;
import dev.alphasucks.aea.gui.condition.entity.GuiValueCondition;
import dev.alphasucks.aea.gui.power.GuiPower;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.condition.ConditionTypes;

/** The GUI feature's power, action and condition types (all entity-context, as in AEA 1.5.4). */
public final class GuiTypes {
    private GuiTypes() {}

    /** Called once during common setup. */
    public static void register() {
        Reg.power(GuiPower.ID.toString(), new GuiPower());

        Reg.action(ActionTypes.ENTITY, "aea:open_gui", new OpenGuiAction());
        Reg.action(ActionTypes.ENTITY, "aea:close_gui", new CloseGuiAction());
        Reg.action(ActionTypes.ENTITY, "aea:gui_command", new GuiCommandAction());
        Reg.action(ActionTypes.ENTITY, "aea:gui_slot_action", new GuiSlotAction());
        Reg.action(ActionTypes.ENTITY, "aea:modify_gui_slot", new ModifyGuiSlotAction());
        Reg.action(ActionTypes.ENTITY, "aea:gui_craft", new GuiCraftAction());

        Reg.condition(ConditionTypes.ENTITY, "aea:gui_open", new GuiOpenCondition());
        Reg.condition(ConditionTypes.ENTITY, "aea:gui_slot", new GuiSlotCondition());
        Reg.condition(ConditionTypes.ENTITY, "aea:gui_value", new GuiValueCondition());
    }
}
