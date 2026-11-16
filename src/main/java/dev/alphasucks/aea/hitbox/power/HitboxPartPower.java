package dev.alphasucks.aea.hitbox.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.hitbox.HitboxParts;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code aea:hitbox_part}: an extra hit box that follows the holder's body rotation. A hit that lands on it counts as
 * a hit on the holder. The box does not collide with blocks or push entities.
 */
public final class HitboxPartPower extends PowerType<HitboxPartPower.Cfg> {
    public static final ResourceLocation ID = AEA.id("hitbox_part");

    public record Cfg(float width, float height, float forward, float up, float right, boolean scaleWithEntity) {}

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.floatRange(0.05F, 16.0F).optionalFieldOf("width", 1.0F).forGetter(Cfg::width),
        Codec.floatRange(0.05F, 16.0F).optionalFieldOf("height", 1.0F).forGetter(Cfg::height),
        Codec.floatRange(-16.0F, 16.0F).optionalFieldOf("forward", 0.0F).forGetter(Cfg::forward),
        Codec.floatRange(-16.0F, 16.0F).optionalFieldOf("up", 0.0F).forGetter(Cfg::up),
        Codec.floatRange(-16.0F, 16.0F).optionalFieldOf("right", 0.0F).forGetter(Cfg::right),
        Codec.BOOL.optionalFieldOf("scale_with_entity", true).forGetter(Cfg::scaleWithEntity)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    @Override
    public void tick(ResourceLocation id, Cfg cfg, PowerContainer holder) {
        LivingEntity owner = holder.owner();
        if (owner == null || owner.level().isClientSide()) return;
        HitboxParts.of(owner).touch(owner, id, cfg);
    }

    @Override
    public void onRemoved(ResourceLocation id, Cfg cfg, PowerContainer holder, ResourceLocation source) {
        if (!holder.hasPower(id)) drop(id, holder);
    }

    @Override
    public void onSuppressed(ResourceLocation id, Cfg cfg, PowerContainer holder) {
        drop(id, holder);
    }

    private static void drop(ResourceLocation id, PowerContainer holder) {
        LivingEntity owner = holder.owner();
        if (owner != null && !owner.level().isClientSide()) HitboxParts.of(owner).drop(id);
    }

    /**
     * Apoli 1.124 ticks every held power whatever its {@code condition}, so the condition is checked here, on the
     * context the container already built. Parts that stop being touched are dropped.
     */
    @Override
    public boolean isActive(ResourceLocation id, Cfg cfg, EntityCtx ctx) {
        Power power = ApoliPowers.get(id);
        if (power == null || power.condition().isEmpty()) return true;
        try {
            return power.condition().get().test(ctx);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
