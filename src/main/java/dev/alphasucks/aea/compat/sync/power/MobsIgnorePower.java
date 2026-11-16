package dev.alphasucks.aea.compat.sync.power;

import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerTypeUsage;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code sync:mobs_ignore}: matching mobs won't target the holder until the holder hits them (when provokable). */
public final class MobsIgnorePower extends PowerType<MobsIgnorePower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "mobs_ignore");

    private static final Map<UUID, Set<UUID>> PROVOKED = new ConcurrentHashMap<>();

    public record Config(Optional<EntityCondition> mobCondition, Optional<BiEntityCondition> bientityCondition, boolean provokable) {}

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.strict("mob_condition", EntityCondition.CODEC).forGetter(Config::mobCondition),
            LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(Config::bientityCondition),
            Codec.BOOL.optionalFieldOf("provokable", true).forGetter(Config::provokable)
        ).apply(i, Config::new));
    }

    public static boolean matches(Config c, Entity mob, Entity holder) {
        if (c.provokable) {
            Set<UUID> p = PROVOKED.get(holder.getUUID());
            if (p != null && p.contains(mob.getUUID())) return false;
        }
        if (c.mobCondition.isPresent() && !c.mobCondition.get().test(EntityCtx.of(mob, mob.level()))) return false;
        return c.bientityCondition.isEmpty() || c.bientityCondition.get().test(BiEntityCtx.of(mob, holder, mob.level()));
    }

    public static final PowerTypeUsage.Handle HELD = PowerTypeUsage.handle(ID);

    /** Runs from {@code canAttack} for every targeting check, so: server gate, container ladder, no lambdas. */
    public static boolean shouldIgnore(Entity mob, Entity target) {
        if (target == null || mob == null || mob == target) return false;
        if (!target.level().isClientSide() && !HELD.isHeld()) return false;
        PowerContainer container = PowerContainer.of(target);
        if (container == null || container.isEmpty()) return false;
        List<ResourceLocation> powers = container.powersOfType(ID);
        if (powers.isEmpty()) return false;
        EntityCtx ctx = null;
        for (int i = 0; i < powers.size(); i++) {
            ResourceLocation id = powers.get(i);
            if (container.isSuppressed(id)) continue;
            Power power = ApoliPowers.get(id);
            if (power == null || !(power.config() instanceof Config c)) continue;
            if (power.condition().isPresent()) {
                if (ctx == null) ctx = EntityCtx.of(target, target.level());
                if (!power.condition().get().test(ctx)) continue;
            }
            if (matches(c, mob, target)) return true;
        }
        return false;
    }

    /** Provoked sets are per holder; drop a holder's set when it leaves the level so the map doesn't grow. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, e -> {
            if (!PROVOKED.isEmpty()) PROVOKED.remove(e.getEntity().getUUID());
        });
    }

    public static void provoke(Entity holder, Entity mob) {
        List<Config> cfgs = PowerLookup.active(holder, ID, Config.class);
        for (Config c : cfgs) {
            if (c.provokable && matches(c, mob, holder)) {
                PROVOKED.computeIfAbsent(holder.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(mob.getUUID());
                return;
            }
        }
    }
}
