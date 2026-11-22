package dev.alphasucks.aea.compat;

import dev.overgrown.apoli.client.ClientResolvedPowers;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Active configs of one power type, on either side, without allocating. On the server a power is active when it isn't
 * suppressed and its condition passes. On the client only the local player's powers are known for sure: for power
 * types that resolve on the server ({@code resolvesForClient}), the server's answer from {@link ClientResolvedPowers}
 * is used instead of testing the condition again. Other entities on the client have none.
 *
 * <p>Typical use, on a hot path:
 * <pre>{@code
 * PowerContainer c = ActiveConfigs.holder(entity, TYPE);   // null: nothing to do
 * List<ResourceLocation> powers = c.powersOfType(TYPE);     // Apoli's index, no copy
 * for (int i = 0; i < powers.size(); i++) {
 *     Cfg cfg = ActiveConfigs.config(entity, c, powers.get(i), Cfg.class); // null when inactive
 * }
 * }</pre>
 */
public final class ActiveConfigs {
    private ActiveConfigs() {}

    /** The entity's container when it holds at least one power of {@code type} that can be active on this side. */
    public static PowerContainer holder(Entity entity, ResourceLocation type) {
        if (entity == null || (entity.level().isClientSide() && !ClientSide.isLocal(entity))) return null;
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || container.isEmpty() || container.powersOfType(type).isEmpty()) return null;
        return container;
    }

    /** The config of power {@code powerId} if it is active on {@code entity}, or null. */
    public static <C> C config(Entity entity, PowerContainer container, ResourceLocation powerId, Class<C> cfg) {
        if (container.isSuppressed(powerId)) return null;
        Power power = ApoliPowers.get(powerId);
        if (power == null || !cfg.isInstance(power.config())) return null;
        if (entity.level().isClientSide()) {
            if (!ClientResolvedPowers.isActive(powerId)) return null;
        } else if (power.condition().isPresent()) {
            try {
                if (!power.condition().get().test(EntityCtx.of(entity, entity.level()))) return null;
            } catch (RuntimeException e) {
                return null;
            }
        }
        return cfg.cast(power.config());
    }

    /** All active configs, as a new list. For per-event paths; hot paths loop with {@link #config} instead. */
    public static <C> List<C> of(Entity entity, ResourceLocation type, Class<C> cfg) {
        if (entity == null) return List.of();
        if (!entity.level().isClientSide()) return PowerLookup.active(entity, type, cfg);
        PowerContainer container = holder(entity, type);
        if (container == null) return List.of();
        List<ResourceLocation> powers = container.powersOfType(type);
        List<C> out = null;
        for (int i = 0; i < powers.size(); i++) {
            C c = config(entity, container, powers.get(i), cfg);
            if (c == null) continue;
            if (out == null) out = new java.util.ArrayList<>(2);
            out.add(c);
        }
        return out == null ? List.of() : out;
    }

    /** A hash of which powers of {@code type} are active, 0 when none; used to notice when the active set changes. */
    public static int signature(Entity entity, PowerContainer container, ResourceLocation type, Class<?> cfg) {
        if (container == null) return 0;
        List<ResourceLocation> powers = container.powersOfType(type);
        int h = 0;
        for (int i = 0; i < powers.size(); i++) {
            if (config(entity, container, powers.get(i), cfg) != null) h = 31 * h + powers.get(i).hashCode() + 1;
        }
        return h;
    }

    /** Kept apart so the server never loads client classes. */
    private static final class ClientSide {
        static boolean isLocal(Entity entity) {
            return net.neoforged.fml.loading.FMLEnvironment.dist.isClient() && Local.is(entity);
        }
    }

    private static final class Local {
        static boolean is(Entity entity) {
            return entity == net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
