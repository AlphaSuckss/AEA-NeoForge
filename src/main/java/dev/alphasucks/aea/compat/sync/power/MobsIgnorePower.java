package dev.alphasucks.aea.compat.sync.power;

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
            EntityCondition.CODEC.optionalFieldOf("mob_condition").forGetter(Config::mobCondition),
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(Config::bientityCondition),
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

    public static boolean shouldIgnore(Entity mob, Entity target) {
        if (target == null || mob == null || mob == target) return false;
        return PowerLookup.anyActive(target, ID, Config.class, (c, m) -> matches(c, m, target), mob);
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
