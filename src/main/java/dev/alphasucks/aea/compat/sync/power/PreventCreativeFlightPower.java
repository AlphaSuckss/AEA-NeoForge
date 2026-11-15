package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * {@code sync:prevent_creative_flight}: takes flight away from a holder who isn't in creative or spectator, running
 * entity_action if they were flying. See AEAHooks.
 */
public final class PreventCreativeFlightPower extends PowerType<PreventCreativeFlightPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "prevent_creative_flight");

    public record Config(Optional<EntityAction> action) {}

    @Override
    public MapCodec<Config> configCodec() {
        return EntityAction.CODEC.optionalFieldOf("entity_action").xmap(Config::new, Config::action);
    }
}
