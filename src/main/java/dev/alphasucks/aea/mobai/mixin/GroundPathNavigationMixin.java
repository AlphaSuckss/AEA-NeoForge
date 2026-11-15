package dev.alphasucks.aea.mobai.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.alphasucks.aea.mobai.MobPathingPower;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** aea:mob_pathing "airborne_pathing": keep following the path while off the ground. */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin extends PathNavigation {
    protected GroundPathNavigationMixin(Mob mob, Level level) {
        super(mob, level);
    }

    @ModifyReturnValue(method = "canUpdatePath", at = @At("RETURN"))
    private boolean aea$airborneIsValid(boolean original) {
        if (original || this.mob.isPassenger()) return original;
        MobPathingPower.Config cfg = MobPathingPower.config(this.mob);
        return cfg != null && cfg.airbornePathing();
    }
}
