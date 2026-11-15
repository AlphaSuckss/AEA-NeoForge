package dev.alphasucks.aea.mobai.mixin;

import dev.alphasucks.aea.mobai.ResolvedPath;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Resolves the mob's aea:mob_pathing config once per pathfinding run. */
@Mixin(NodeEvaluator.class)
public abstract class NodeEvaluatorMixin implements ResolvedPath.Holder {
    @Unique @Nullable
    private ResolvedPath aea$path;

    @Inject(method = "prepare", at = @At("TAIL"))
    private void aea$resolve(PathNavigationRegion region, Mob mob, CallbackInfo ci) {
        aea$path = ResolvedPath.of(mob);
    }

    @Inject(method = "done", at = @At("HEAD"))
    private void aea$drop(CallbackInfo ci) {
        aea$path = null;
    }

    @Override
    @Nullable
    public ResolvedPath aea$resolvedPath() {
        return aea$path;
    }
}
