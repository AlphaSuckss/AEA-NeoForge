package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.addon.power.WalkOnPowderSnowPower;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
    private static boolean aea$walkOnPowderSnow(boolean original, @Local(argsOnly = true) Entity entity) {
        if (original) return true;
        if (!entity.level().isClientSide() && !WalkOnPowderSnowPower.HELD.isHeld()) return false;
        return PowerLookup.hasActive(entity, WalkOnPowderSnowPower.ID);
    }
}
