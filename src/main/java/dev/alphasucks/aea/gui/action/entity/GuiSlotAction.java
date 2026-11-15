package dev.alphasucks.aea.gui.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** {@code aea:gui_slot_action}: runs an item action on the stack in each matching GUI slot. */
public final class GuiSlotAction implements ActionType<EntityCtx, GuiSlotAction.Cfg> {
    public record Cfg(String slot, ItemAction itemAction) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("slot").forGetter(Cfg::slot),
            ItemAction.CODEC.fieldOf("item_action").forGetter(Cfg::itemAction)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        GuiMenu menu = GuiPowers.serverGui(ctx.entity());
        if (menu == null) return;
        Player player = (Player) ctx.entity();
        for (GuiSlot slot : menu.resolveSlots(cfg.slot())) {
            ItemStack stack = slot.getItem();
            cfg.itemAction().run(new ItemCtx(stack, player.level(), player, slot::set));
            if (slot.getItem() == stack && stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
    }
}
