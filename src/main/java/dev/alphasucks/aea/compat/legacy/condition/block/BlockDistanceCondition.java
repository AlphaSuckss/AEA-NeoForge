package dev.alphasucks.aea.compat.legacy.condition.block;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.legacy.LegacyDistance;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BlockCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Apoli 2.9.2's block {@code distance_from_coordinates} / {@code distance_from_spawn}; see {@link LegacyDistance}. */
public final class BlockDistanceCondition implements ConditionType<BlockCtx, LegacyDistance.Cfg> {
    private final MapCodec<LegacyDistance.Cfg> codec;

    public BlockDistanceCondition(String defaultReference) {
        this.codec = LegacyDistance.codec(defaultReference);
    }

    @Override
    public MapCodec<LegacyDistance.Cfg> codec() {
        return codec;
    }

    @Override
    public boolean test(LegacyDistance.Cfg cfg, BlockCtx ctx) {
        BlockPos p = ctx.pos();
        return LegacyDistance.test(cfg, new Vec3(p.getX(), p.getY(), p.getZ()), ctx.level());
    }
}
