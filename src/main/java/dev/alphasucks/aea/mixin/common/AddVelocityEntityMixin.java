package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.addon.AddonTypes;
import dev.overgrown.apoli.action.builtin.entity.AddVelocityAction;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AddVelocityAction.class, remap = false)
public abstract class AddVelocityEntityMixin {
    @Inject(method = "run(Ldev/overgrown/apoli/action/builtin/entity/AddVelocityAction$Cfg;Ldev/overgrown/apoli/condition/context/EntityCtx;)V", at = @At("HEAD"), cancellable = true)
    private void aea$velocityImmunity(AddVelocityAction.Cfg cfg, EntityCtx ctx, CallbackInfo ci) {
        if (ctx.raw() instanceof net.minecraft.world.entity.LivingEntity && PowerLookup.anyActive(ctx.raw(), AddonTypes.VELOCITY_IMMUNITY, AddonTypes.VelocityImmunityCfg.class, AddonTypes.VelocityImmunityCfg::entity)) ci.cancel();
    }
}
