package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.addon.power.VelocityImmunityPower;
import dev.overgrown.apoli.action.builtin.bientity.AddVelocityAction;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AddVelocityAction.class, remap = false)
public abstract class AddVelocityBiEntityMixin {
    @Inject(method = "run(Ldev/overgrown/apoli/action/builtin/bientity/AddVelocityAction$Cfg;Ldev/overgrown/apoli/condition/context/BiEntityCtx;)V", at = @At("HEAD"), cancellable = true)
    private void aea$velocityImmunity(AddVelocityAction.Cfg cfg, BiEntityCtx ctx, CallbackInfo ci) {
        Entity target = ctx.target();
        if (target instanceof LivingEntity && PowerLookup.anyActive(target, VelocityImmunityPower.ID, VelocityImmunityPower.Config.class, c -> c.blocksBi(ctx.actor(), target))) ci.cancel();
    }
}
