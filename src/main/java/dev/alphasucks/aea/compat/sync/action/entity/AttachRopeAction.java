package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.SyncRopes;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;

/** {@code sync:attach_rope} (to a block) and {@code sync:attach_rope_entity} (to an entity). */
public final class AttachRopeAction implements ActionType<EntityCtx, SyncRopes.Cfg> {
    private final boolean entity;

    public AttachRopeAction(boolean entity) {
        this.entity = entity;
    }

    @Override
    public MapCodec<SyncRopes.Cfg> codec() {
        return SyncRopes.CODEC;
    }

    @Override
    public void run(SyncRopes.Cfg cfg, EntityCtx ctx) {
        SyncRopes.attach(cfg, ctx.raw(), entity);
    }
}
