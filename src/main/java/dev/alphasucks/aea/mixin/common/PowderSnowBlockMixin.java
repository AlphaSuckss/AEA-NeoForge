package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.addon.AddonTypes;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void aea$walkOnPowderSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PowerLookup.hasActive(entity, AddonTypes.WALK_ON_POWDER_SNOW)) cir.setReturnValue(true);
    }
}
