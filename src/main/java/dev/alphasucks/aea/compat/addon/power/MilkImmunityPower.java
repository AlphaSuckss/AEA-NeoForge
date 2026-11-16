package dev.alphasucks.aea.compat.addon.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.addon.EffectTypes;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.List;
import java.util.Optional;

/**
 * {@code aea:milk_immunity}: milk does not remove the listed effects from the holder. Milk, and anything else that
 * cures effects the way milk does, removes each effect through {@link MobEffectEvent.Remove}, which this cancels.
 */
public final class MilkImmunityPower extends PowerType<MilkImmunityPower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "milk_immunity");

    public record Cfg(Optional<ResourceLocation> effect, Optional<List<ResourceLocation>> effects,
                      Optional<List<EffectTypes.Type>> effectType, boolean inverted) {
        /** Whether milk leaves this effect alone. With no effect fields at all, milk removes nothing. */
        boolean protects(Holder<MobEffect> holder) {
            if (effect.isEmpty() && effects.isEmpty() && effectType.isEmpty()) return !inverted;
            boolean listed = effectType.isPresent() && EffectTypes.matches(effectType.get(), holder);
            if (!listed && (effect.isPresent() || effects.isPresent()) && holder instanceof Holder.Reference<MobEffect> ref) {
                ResourceLocation id = ref.key().location();
                listed = effect.isPresent() && effect.get().equals(id) || effects.isPresent() && effects.get().contains(id);
            }
            return inverted != listed;
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.optionalFieldOf("effect").forGetter(Cfg::effect),
        ResourceLocation.CODEC.listOf().optionalFieldOf("effects").forGetter(Cfg::effects),
        EffectTypes.CODEC.optionalFieldOf("effect_type").forGetter(Cfg::effectType),
        Codec.BOOL.optionalFieldOf("inverted", false).forGetter(Cfg::inverted)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Remove.class, event -> {
            if (event.getCure() != EffectCures.MILK) return;
            if (PowerLookup.anyActive(event.getEntity(), ID, Cfg.class, Cfg::protects, event.getEffect())) event.setCanceled(true);
        });
    }
}
