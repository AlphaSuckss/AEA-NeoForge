package dev.alphasucks.aea.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.addon.EffectTypes;
import dev.overgrown.apoli.power.builtin.EffectImmunityPower;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adds {@code effect_type} to {@code apoli:effect_immunity}. The types are turned into the matching effects when the
 * power loads, so the power itself works as before.
 */
@Mixin(value = EffectImmunityPower.class, remap = false)
public abstract class EffectImmunityPowerMixin {
    @ModifyReturnValue(method = "configCodec", at = @At("RETURN"))
    private MapCodec<EffectImmunityPower.Config> aea$effectType(MapCodec<EffectImmunityPower.Config> original) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            original.forGetter(Function.identity()),
            EffectTypes.CODEC.optionalFieldOf("effect_type").forGetter(cfg -> Optional.empty())
        ).apply(i, (cfg, types) -> {
            if (types.isEmpty()) return cfg;
            List<ResourceLocation> effects = new ArrayList<>(cfg.effects().orElse(List.of()));
            for (ResourceLocation id : EffectTypes.effectsOf(types.get())) {
                if (!effects.contains(id)) effects.add(id);
            }
            return new EffectImmunityPower.Config(cfg.effect(), Optional.of(List.copyOf(effects)), cfg.inverted());
        }));
    }
}
