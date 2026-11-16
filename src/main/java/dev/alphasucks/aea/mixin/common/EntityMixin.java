package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.alphasucks.aea.compat.sync.power.PreventSprintingParticlesPower;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyReturnValue(method = "canSpawnSprintParticle", at = @At("RETURN"))
    private boolean aea$noSprintParticles(boolean original) {
        return original && !PowerLookup.hasActive((Entity) (Object) this, PreventSprintingParticlesPower.ID);
    }
}
