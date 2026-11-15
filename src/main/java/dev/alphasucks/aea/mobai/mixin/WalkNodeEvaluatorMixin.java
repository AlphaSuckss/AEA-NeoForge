package dev.alphasucks.aea.mobai.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.mobai.ResolvedPath;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** aea:mob_pathing jump/drop heights and block rules (walkable / blocked / avoid). */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    @Unique
    private ResolvedPath aea$path() {
        return ((ResolvedPath.Holder) this).aea$resolvedPath();
    }

    @ModifyExpressionValue(method = "getNeighbors", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(F)I"))
    private int aea$jumpSteps(int original) {
        ResolvedPath p = aea$path();
        return p != null && p.jump > original ? p.jump : original;
    }

    @ModifyReturnValue(method = "getMobJumpHeight", at = @At("RETURN"))
    private double aea$maxStepUp(double original) {
        ResolvedPath p = aea$path();
        return p != null && p.jump > 0 ? Math.max(original, p.jump + 0.125D) : original;
    }

    @ModifyExpressionValue(method = "tryFindFirstGroundNodeBelow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMaxFallDistance()I"))
    private int aea$dropHeight(int original) {
        ResolvedPath p = aea$path();
        return p != null && p.drop >= 0 ? p.drop : original;
    }

    @ModifyReturnValue(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At("RETURN"))
    private PathType aea$blockedCells(PathType original, @Local(argsOnly = true, ordinal = 0) int x,
                                      @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int z) {
        ResolvedPath p = aea$path();
        if (p == null || p.config.blocked().isEmpty() || original == PathType.BLOCKED) return original;
        return p.isBlocked(x, y, z) ? PathType.BLOCKED : original;
    }

    @ModifyReturnValue(method = "findAcceptedNode", at = @At("RETURN"))
    private Node aea$blockRules(Node node) {
        if (node == null || node.costMalus < 0.0F) return node;
        ResolvedPath p = aea$path();
        if (p == null || !p.hasBlockRules()) return node;
        int x = node.x, y = node.y, z = node.z;
        if (p.isBlocked(x, y, z)) return null;
        PathType type = node.type;
        boolean needsFloor = type != PathType.OPEN && type != PathType.WATER && type != PathType.LAVA;
        if (needsFloor && !p.isWalkableFloor(x, y, z)) return null;
        if (!p.config.avoid().isEmpty()) {
            float penalty = p.avoidPenalty(x, y, z);
            if (penalty < 0.0F) return null;
            if (penalty > node.costMalus) node.costMalus = penalty;
        }
        return node;
    }
}
