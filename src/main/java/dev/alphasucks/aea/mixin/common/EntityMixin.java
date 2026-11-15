package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.sync.SyncPowers;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void aea$noSprintParticles(CallbackInfoReturnable<Boolean> cir) {
        if (PowerLookup.hasActive((Entity) (Object) this, SyncPowers.PREVENT_SPRINT_PARTICLES)) cir.setReturnValue(false);
    }
}
