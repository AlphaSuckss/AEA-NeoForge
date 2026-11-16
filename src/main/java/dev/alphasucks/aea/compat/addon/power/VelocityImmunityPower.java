package dev.alphasucks.aea.compat.addon.power;

import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * {@code aea:velocity_immunity} (AEA 1.5.4): blocks {@code apoli:add_velocity} on the holder. The entity form is blocked
 * when {@code entity} is set, the bi-entity form when {@code bientity} is set and the optional bientity_condition passes
 * for actor and holder. See the AddVelocity mixins.
 */
public final class VelocityImmunityPower extends PowerType<VelocityImmunityPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "velocity_immunity");

    public record Config(boolean entity, boolean bientity, Optional<BiEntityCondition> bientityCondition) {
        public boolean blocksBi(Entity actor, Entity target) {
            return bientity && bientityCondition.map(c -> c.test(BiEntityCtx.of(actor, target, target.level()))).orElse(true);
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("entity", true).forGetter(Config::entity),
            Codec.BOOL.optionalFieldOf("bientity", true).forGetter(Config::bientity),
            LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(Config::bientityCondition)
        ).apply(i, Config::new));
    }
}
