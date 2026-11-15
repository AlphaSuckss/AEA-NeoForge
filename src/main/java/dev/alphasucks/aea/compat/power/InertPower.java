package dev.alphasucks.aea.compat.power;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.power.PowerType;

/**
 * Accepts any config and does nothing. Registered for types whose effect only exists on Fabric (AspectsLib, Jade) or is
 * purely cosmetic, so packs that use them still load.
 */
public final class InertPower extends PowerType<Object> {
    @Override
    public MapCodec<Object> configCodec() {
        return MapCodec.unit(Boolean.TRUE);
    }
}
