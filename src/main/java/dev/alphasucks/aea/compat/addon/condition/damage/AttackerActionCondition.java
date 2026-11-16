package dev.alphasucks.aea.compat.addon.condition.damage;

import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * {@code apoli:attacker_action}: passes when the damage has an attacker that meets the conditions, and runs
 * {@code entity_action} on that attacker as a side effect.
 */
public final class AttackerActionCondition implements ConditionType<DamageCtx, AttackerActionCondition.Cfg> {
    public record Cfg(Optional<EntityAction> action, Optional<DamageCondition> condition, Optional<EntityCondition> entityCondition) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.of("entity_action", EntityAction.CODEC).forGetter(Cfg::action),
            LoggedOptionalField.strict("condition", DamageCondition.CODEC).forGetter(Cfg::condition),
            LoggedOptionalField.strict("attacker_condition", EntityCondition.CODEC).forGetter(Cfg::entityCondition)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg c, DamageCtx ctx) {
        Entity attacker = ctx.source().getEntity();
        if (attacker == null) return false;
        if (c.condition.isPresent() && !c.condition.get().test(ctx)) return false;
        if (c.entityCondition.isPresent() && !c.entityCondition.get().test(EntityCtx.of(attacker, attacker.level()))) return false;
        c.action.ifPresent(a -> a.run(EntityCtx.of(attacker, attacker.level())));
        return true;
    }
}
