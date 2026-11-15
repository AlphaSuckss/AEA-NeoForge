package dev.alphasucks.aea.gui;

import dev.overgrown.apoli.condition.context.ItemCtx;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A menu slot backed by an {@code aea:slot} / {@code aea:slot_grid} cell. Positioned by the client screen. */
public final class GuiSlot extends Slot {
    public final GuiMenu menu;
    public final GuiModel.SlotDef def;
    /** Render scale applied by the screen (gui_scale_lock). */
    public float drawScale = 1.0F;

    public GuiSlot(GuiMenu menu, GuiModel.SlotDef def, Container container) {
        super(container, def.index(), 0, 0);
        this.menu = menu;
        this.def = def;
    }

    public boolean visible() {
        return menu.isVisible(def.element());
    }

    public boolean accepts(ItemStack stack) {
        if (!def.acceptsInsert()) return false;
        if (def.itemCondition().isEmpty()) return true;
        try {
            return def.itemCondition().get().test(new ItemCtx(stack, menu.player.level(), menu.player));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !menu.isClosed() && visible() && accepts(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !menu.isClosed() && visible() && !def.isDisplay();
    }

    @Override
    public int getMaxStackSize() {
        return def.maxStack().isPresent() ? def.maxStack().get() : super.getMaxStackSize();
    }

    @Override
    public boolean isActive() {
        return visible();
    }

    /** Placeholder for slots of a GUI that could not be built on this side; keeps slot indices in sync. */
    public static final class Dead extends Slot {
        public Dead(Container container, int index) {
            super(container, index, -10000, -10000);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }

        @Override public boolean mayPickup(Player player) { return false; }

        @Override public boolean isActive() { return false; }
    }
}
