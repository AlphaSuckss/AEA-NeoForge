package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.legacy.power.ModifierPower;
import dev.alphasucks.aea.compat.sync.power.MobsIgnorePower;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
    private boolean aea$mobsIgnore(boolean original, @Local(argsOnly = true) LivingEntity target) {
        return original && !MobsIgnorePower.shouldIgnore((LivingEntity) (Object) this, target);
    }

    @ModifyArg(method = "travel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V", ordinal = 1), index = 0)
    private float aea$lavaSpeed(float speed) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isInLava()) return speed;
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<List> cfgs = (List) PowerLookup.active(self, ModifierPower.MODIFY_LAVA_SPEED, List.class);
        if (cfgs.isEmpty()) return speed;
        List<AttributeModifier> all = new ArrayList<>();
        for (List l : cfgs) for (Object o : l) if (o instanceof AttributeModifier m) all.add(m);
        return AttributeModifierHelper.apply(speed, AttributeModifierHelper.ensureSorted(all), self);
    }
}
