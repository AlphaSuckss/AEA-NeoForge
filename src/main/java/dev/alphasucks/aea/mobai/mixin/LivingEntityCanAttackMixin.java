package dev.alphasucks.aea.mobai.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.mobai.MobAiPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** aea:mob_ai "never_target". */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCanAttackMixin {
    @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
    private boolean aea$neverTarget(boolean original, @Local(argsOnly = true) LivingEntity target) {
        if (original && target != null && (Object) this instanceof Mob mob && MobAiPower.neverTargets(mob, target)) return false;
        return original;
    }
}
