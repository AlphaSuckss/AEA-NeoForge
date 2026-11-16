package dev.alphasucks.aea.compat.addon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.builtin.entity.StatusEffectCondition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The codec of {@code apoli:status_effect} with {@code effect_type} added (see StatusEffectConditionMixin). It mirrors
 * Apoli 1.124's fields with {@code effect} made optional; the types travel in {@code effect} as a sentinel ID.
 */
public final class StatusEffectTypeCodec {
    private StatusEffectTypeCodec() {}

    public static final MapCodec<StatusEffectCondition.Cfg> CODEC = RecordCodecBuilder.<Fields>mapCodec(i -> i.group(
        ResourceLocation.CODEC.optionalFieldOf("effect").forGetter(Fields::effect),
        EffectTypes.CODEC.optionalFieldOf("effect_type").forGetter(Fields::types),
        Codec.INT.optionalFieldOf("min_amplifier").forGetter(Fields::minAmplifier),
        Codec.INT.optionalFieldOf("max_amplifier").forGetter(Fields::maxAmplifier),
        Codec.INT.optionalFieldOf("min_duration").forGetter(Fields::minDuration),
        Codec.INT.optionalFieldOf("max_duration").forGetter(Fields::maxDuration)
    ).apply(i, Fields::new)).flatXmap(Fields::toCfg, Fields::fromCfg);

    private record Fields(Optional<ResourceLocation> effect, Optional<List<EffectTypes.Type>> types,
                          Optional<Integer> minAmplifier, Optional<Integer> maxAmplifier,
                          Optional<Integer> minDuration, Optional<Integer> maxDuration) {
        DataResult<StatusEffectCondition.Cfg> toCfg() {
            if (effect.isPresent() == types.isPresent()) {
                return DataResult.error(() -> "apoli:status_effect needs either \"effect\" or \"effect_type\", not both");
            }
            ResourceLocation id = effect.orElseGet(() -> EffectTypes.sentinel(types.get()));
            return DataResult.success(new StatusEffectCondition.Cfg(id, minAmplifier, maxAmplifier, minDuration, maxDuration));
        }

        static DataResult<Fields> fromCfg(StatusEffectCondition.Cfg cfg) {
            List<EffectTypes.Type> types = EffectTypes.fromSentinel(cfg.effect());
            return DataResult.success(new Fields(types == null ? Optional.of(cfg.effect()) : Optional.empty(), Optional.ofNullable(types),
                cfg.minAmplifier(), cfg.maxAmplifier(), cfg.minDuration(), cfg.maxDuration()));
        }
    }
}
