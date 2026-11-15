package dev.alphasucks.aea.compat.sync.action.bientity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.entity.SavedLocations;
import net.minecraft.world.entity.Entity;

/** {@code sync:save_location}: saves the target's position under {@code id} in the actor's saved locations. */
public final class SaveLocationAction implements ActionType<BiEntityCtx, SaveLocationAction.Cfg> {
    public record Cfg(String id, boolean overwrite, boolean track) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Cfg::id),
            Codec.BOOL.optionalFieldOf("overwrite", true).forGetter(Cfg::overwrite),
            Codec.BOOL.optionalFieldOf("track_entity", false).forGetter(Cfg::track)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, BiEntityCtx ctx) {
        Entity actor = ctx.actor(), target = ctx.target();
        if (actor == null || target == null || actor.level().isClientSide()) return;
        SavedLocations locs = SavedLocations.of(actor);
        if (locs == null) return;
        locs.put(actor.getUUID(), cfg.id, new SavedLocations.Location(target.getX(), target.getY(), target.getZ(),
            target.level().dimension(), target.getYRot(), target.getXRot()), cfg.overwrite);
    }
}
