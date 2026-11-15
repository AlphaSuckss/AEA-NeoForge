package dev.alphasucks.aea.mobai.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.alphasucks.aea.mobai.MobPathingPower;
import dev.alphasucks.aea.mobai.Repathable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

/** aea:mob_pathing "repath_interval" and "airborne_reach". */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin implements Repathable {
    @Shadow @Final protected Mob mob;
    @Shadow @Nullable protected Path path;
    @Shadow protected double speedModifier;
    @Shadow @Nullable private BlockPos targetPos;
    @Shadow private int reachRange;

    @Shadow @Nullable public abstract Path createPath(BlockPos pos, int accuracy);
    @Shadow public abstract boolean moveTo(@Nullable Path path, double speed);
    @Shadow protected abstract boolean canUpdatePath();

    @Override
    public void aea$repath() {
        if (targetPos == null || path == null || path.isDone() || !canUpdatePath()) return;
        double speed = speedModifier;
        BlockPos target = targetPos;
        int reach = reachRange;
        Path old = path;
        path = null;
        Path fresh = createPath(target, reach);
        if (fresh == null) {
            path = old;
            return;
        }
        moveTo(fresh, speed);
    }

    /** Counts the next node as reached vertically while the mob is in the air within airborne_reach above it. */
    @ModifyExpressionValue(method = "followThePath", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(D)D", ordinal = 1))
    private double aea$airborneReach(double dy) {
        if (dy < 1.0D || mob.onGround() || path == null) return dy;
        MobPathingPower.Config cfg = MobPathingPower.config(mob);
        if (cfg == null || !cfg.airbornePathing()) return dy;
        BlockPos node = path.getNextNodePos();
        double rel = mob.getY() - node.getY();
        return rel >= -0.5D && rel <= cfg.airborneReach() ? 0.0D : dy;
    }
}
