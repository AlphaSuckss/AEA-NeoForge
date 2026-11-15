package dev.alphasucks.aea.gui.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the GUI screen place slots after layout (Slot.x / Slot.y are final). */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void aea$setX(int x);

    @Mutable
    @Accessor("y")
    void aea$setY(int y);
}
