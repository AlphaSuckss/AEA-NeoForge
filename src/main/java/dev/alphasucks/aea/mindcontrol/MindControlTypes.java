package dev.alphasucks.aea.mindcontrol;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.alphasucks.aea.mindcontrol.client.MindControlClientView;
import net.minecraft.world.entity.Entity;

/** The data pack side of mind control: one action to start it, one to end it, and conditions to check it. */
public final class MindControlTypes {
    private MindControlTypes() {}

    private static final MapCodec<Unit> UNIT = MapCodec.unit(Unit.INSTANCE);

    private enum Unit { INSTANCE }

    public static void register() {
        Reg.action(ActionTypes.BI_ENTITY, "aea:mind_control", new ActionType<BiEntityCtx, MindControl.Cfg>() {
            @Override public MapCodec<MindControl.Cfg> codec() { return MindControl.CODEC; }
            @Override public void run(MindControl.Cfg cfg, BiEntityCtx ctx) {
                if (ctx.actor() != null && !ctx.actor().level().isClientSide) MindControl.start(cfg, ctx.actor(), ctx.target());
            }
        });
        Reg.action(ActionTypes.ENTITY, "aea:release_mind_control", new ActionType<EntityCtx, Unit>() {
            @Override public MapCodec<Unit> codec() { return UNIT; }
            @Override public void run(Unit cfg, EntityCtx ctx) {
                Entity e = ctx.raw();
                if (e != null && !e.level().isClientSide) MindControl.release(e);
            }
        });
        Reg.condition(ConditionTypes.ENTITY, "aea:mind_controlled", new ConditionType<EntityCtx, Unit>() {
            @Override public MapCodec<Unit> codec() { return UNIT; }
            @Override public boolean test(Unit cfg, EntityCtx ctx) {
                Entity e = ctx.raw();
                if (e == null) return false;
                return e.level().isClientSide ? MindControlClientView.isControlled(e) : MindControl.isControlled(e);
            }
        });
        Reg.condition(ConditionTypes.ENTITY, "aea:mind_controlling", new ConditionType<EntityCtx, Unit>() {
            @Override public MapCodec<Unit> codec() { return UNIT; }
            @Override public boolean test(Unit cfg, EntityCtx ctx) {
                Entity e = ctx.raw();
                if (e == null) return false;
                return e.level().isClientSide ? MindControlClientView.isControlling(e) : MindControl.isControlling(e);
            }
        });
        Reg.condition(ConditionTypes.BI_ENTITY, "aea:mind_controls", new ConditionType<BiEntityCtx, Unit>() {
            @Override public MapCodec<Unit> codec() { return UNIT; }
            @Override public boolean test(Unit cfg, BiEntityCtx ctx) {
                Entity a = ctx.actor(), t = ctx.target();
                if (a == null || t == null) return false;
                return a.level().isClientSide ? MindControlClientView.controls(a, t) : MindControl.controls(a, t);
            }
        });
    }
}
