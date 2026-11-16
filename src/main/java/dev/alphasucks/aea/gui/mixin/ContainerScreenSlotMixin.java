package dev.alphasucks.aea.gui.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.alphasucks.aea.gui.GuiSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Hover hitbox of GUI slots drawn at a gui_scale_lock scale. */
@OnlyIn(Dist.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenSlotMixin {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @WrapMethod(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z")
    private boolean aea$scaledSlotHitbox(Slot slot, double mouseX, double mouseY, Operation<Boolean> original) {
        float scale = slot instanceof GuiSlot gs ? gs.drawScale : 1.0F;
        if (scale == 1.0F) return original.call(slot, mouseX, mouseY);
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        return x >= slot.x - scale && x < slot.x + 17.0F * scale && y >= slot.y - scale && y < slot.y + 17.0F * scale;
    }
}
