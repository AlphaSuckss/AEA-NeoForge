package dev.alphasucks.aea.compat;

import dev.overgrown.apoli.client.ClientResolvedPowers;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Active configs of one power type, on either side. On the server this is {@link PowerLookup#active}. On the client
 * only the local player's powers are known for sure: for power types that resolve on the server
 * ({@code resolvesForClient}), the server's answer from {@link ClientResolvedPowers} is used instead of testing the
 * condition again. Other entities on the client get an empty list.
 */
public final class ActiveConfigs {
    private ActiveConfigs() {}

    public static <C> List<C> of(Entity entity, ResourceLocation type, Class<C> cfg) {
        if (entity == null) return List.of();
        if (!entity.level().isClientSide()) return PowerLookup.active(entity, type, cfg);
        if (!ClientSide.isLocal(entity)) return List.of();
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || container.isEmpty()) return List.of();
        List<ResourceLocation> powers = container.powersOfType(type);
        if (powers.isEmpty()) return List.of();
        List<C> out = null;
        for (int i = 0; i < powers.size(); i++) {
            ResourceLocation id = powers.get(i);
            if (container.isSuppressed(id) || !ClientResolvedPowers.isActive(id)) continue;
            Power power = ApoliPowers.get(id);
            if (power == null || !cfg.isInstance(power.config())) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(cfg.cast(power.config()));
        }
        return out == null ? List.of() : out;
    }

    /** Ids of the active powers of a type, in container order; used to notice when the active set changes. */
    public static int signature(Entity entity, ResourceLocation type, Class<?> cfg) {
        List<?> active = of(entity, type, cfg);
        int h = 1;
        for (int i = 0; i < active.size(); i++) h = 31 * h + System.identityHashCode(active.get(i));
        return active.isEmpty() ? 0 : h;
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
