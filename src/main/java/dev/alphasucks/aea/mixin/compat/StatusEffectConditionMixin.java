package dev.alphasucks.aea.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.addon.EffectTypes;
import dev.alphasucks.aea.compat.addon.StatusEffectTypeCodec;
import dev.overgrown.apoli.condition.builtin.entity.StatusEffectCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds {@code effect_type} to {@code apoli:status_effect}: the condition passes when the entity has any effect of
 * those types. The types travel in the {@code effect} field as an {@code aea:effect_type/...} ID. See
 * {@link StatusEffectTypeCodec}.
 */
@Mixin(value = StatusEffectCondition.class, remap = false)
public abstract class StatusEffectConditionMixin {
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private MapCodec<StatusEffectCondition.Cfg> aea$effectType(MapCodec<StatusEffectCondition.Cfg> original) {
        return StatusEffectTypeCodec.CODEC;
    }

    @Inject(method = "test(Ldev/overgrown/apoli/condition/builtin/entity/StatusEffectCondition$Cfg;Ldev/overgrown/apoli/condition/context/EntityCtx;)Z",
        at = @At("HEAD"), cancellable = true)
    private void aea$testEffectType(StatusEffectCondition.Cfg cfg, EntityCtx ctx, CallbackInfoReturnable<Boolean> cir) {
        List<EffectTypes.Type> types = EffectTypes.fromSentinel(cfg.effect());
        if (types == null) return;
        LivingEntity living = ctx.living();
        if (living == null) {
            cir.setReturnValue(false);
            return;
        }
        for (MobEffectInstance instance : living.getActiveEffects()) {
            if (!EffectTypes.matches(types, instance.getEffect())) continue;
            if (cfg.minAmplifier().isPresent() && instance.getAmplifier() < cfg.minAmplifier().get()) continue;
            if (cfg.maxAmplifier().isPresent() && instance.getAmplifier() > cfg.maxAmplifier().get()) continue;
            if (cfg.minDuration().isPresent() && instance.getDuration() < cfg.minDuration().get()) continue;
            if (cfg.maxDuration().isPresent() && instance.getDuration() > cfg.maxDuration().get()) continue;
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }
}
