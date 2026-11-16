package dev.alphasucks.aea.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.addon.EffectTypes;
import dev.alphasucks.aea.compat.addon.StatusEffectTypeCodec;
import dev.overgrown.apoli.condition.builtin.entity.StatusEffectCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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

    /** Apoli's own test answers false for the sentinel ID (no such effect), so only that case is recomputed. */
    @ModifyReturnValue(method = "test(Ldev/overgrown/apoli/condition/builtin/entity/StatusEffectCondition$Cfg;Ldev/overgrown/apoli/condition/context/EntityCtx;)Z",
        at = @At("RETURN"))
    private boolean aea$testEffectType(boolean original, @Local(argsOnly = true) StatusEffectCondition.Cfg cfg, @Local(argsOnly = true) EntityCtx ctx) {
        if (original) return true;
        List<EffectTypes.Type> types = EffectTypes.fromSentinel(cfg.effect());
        if (types == null) return false;
        LivingEntity living = ctx.living();
        if (living == null) return false;
        for (MobEffectInstance instance : living.getActiveEffects()) {
            if (!EffectTypes.matches(types, instance.getEffect())) continue;
            if (cfg.minAmplifier().isPresent() && instance.getAmplifier() < cfg.minAmplifier().get()) continue;
            if (cfg.maxAmplifier().isPresent() && instance.getAmplifier() > cfg.maxAmplifier().get()) continue;
            if (cfg.minDuration().isPresent() && instance.getDuration() < cfg.minDuration().get()) continue;
            if (cfg.maxDuration().isPresent() && instance.getDuration() > cfg.maxDuration().get()) continue;
            return true;
        }
        return false;
    }
}
