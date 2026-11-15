package dev.alphasucks.aea.compat.legacy;

import dev.overgrown.apoli.condition.builtin.entity.PowerActiveCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Origins 1.10.2 hard-coded a few behaviours to power ids ("marker" powers of type origins:simple). The ones Overgrown
 * covers with real power types are shipped as such in AEA's data; this is the rest.
 */
public final class LegacyOriginsMarkers {
    private LegacyOriginsMarkers() {}

    public static final ResourceLocation CONDUIT_POWER_ON_LAND = ResourceLocation.fromNamespaceAndPath("origins", "conduit_power_on_land");

    public static boolean conduitPowerOnLand(Entity entity) {
        return active(entity, CONDUIT_POWER_ON_LAND);
    }

    static boolean active(Entity entity, ResourceLocation power) {
        if (entity == null) return false;
        try {
            PowerContainer c = PowerContainer.of(entity);
            return c != null && PowerActiveCondition.isActive(c, power, EntityCtx.of(entity, entity.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
