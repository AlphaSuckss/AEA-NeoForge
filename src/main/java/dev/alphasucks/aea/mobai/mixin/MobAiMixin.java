package dev.alphasucks.aea.mobai.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.mobai.MobAiState;
import dev.alphasucks.aea.mobai.MobPathingPower;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Holds the per-mob AEA mob AI state; applies aea:mob_pathing "penalties". */
@Mixin(Mob.class)
public abstract class MobAiMixin implements MobAiState.Holder {
    @Unique
    private MobAiState aea$state;

    @Override
    public MobAiState aea$mobAiState() {
        MobAiState s = aea$state;
        if (s == null) {
            s = new MobAiState();
            aea$state = s;
        }
        return s;
    }

    @ModifyReturnValue(method = "getPathfindingMalus", at = @At("RETURN"))
    private float aea$nodePenalty(float original, @Local(argsOnly = true) PathType type) {
        MobPathingPower.Config cfg = MobPathingPower.config((Mob) (Object) this);
        if (cfg != null) {
            Float p = cfg.penaltyFor(type);
            if (p != null) return p;
        }
        return original;
    }
}
