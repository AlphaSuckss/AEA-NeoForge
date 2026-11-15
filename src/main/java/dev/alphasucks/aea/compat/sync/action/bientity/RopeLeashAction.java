package dev.alphasucks.aea.compat.sync.action.bientity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.SyncRopes;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.rope.Rope;
import dev.overgrown.apoli.rope.RopeAnchor;
import dev.overgrown.apoli.rope.RopeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Locale;

/** {@code sync:rope_leash}: ropes the actor to the target (toggles by default). */
public final class RopeLeashAction implements ActionType<BiEntityCtx, SyncRopes.Cfg> {
    @Override
    public MapCodec<SyncRopes.Cfg> codec() {
        return SyncRopes.CODEC;
    }

    @Override
    public void run(SyncRopes.Cfg cfg, BiEntityCtx ctx) {
        Entity actor = ctx.actor(), target = ctx.target();
        if (actor == null || target == null || !(actor.level() instanceof ServerLevel level)) return;
        String mode = cfg.mode().toLowerCase(Locale.ROOT);
        boolean exists = RopeManager.connected(actor.getUUID(), target.getUUID());
        if (mode.equals("detach_all")) {
            RopeManager.removeMatching(actor.getUUID(), null, null);
            return;
        }
        if (exists && !mode.equals("attach")) {
            for (Rope r : new ArrayList<>(RopeManager.forEntity(actor.getUUID()))) {
                if (r.touches(target.getUUID())) RopeManager.remove(r.id);
            }
            return;
        }
        if (mode.equals("detach")) return;
        RopeManager.create(level, new RopeAnchor.OfEntity(actor.getId(), Vec3.ZERO), new RopeAnchor.OfEntity(target.getId(), Vec3.ZERO),
            null, actor.getUUID(), SyncRopes.params(cfg.maxLength()), SyncRopes.texture(cfg.texture()));
    }
}
