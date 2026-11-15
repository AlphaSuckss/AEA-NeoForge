package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.legacy.LegacyApoli;
import dev.alphasucks.aea.compat.sync.SyncPowers;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void aea$mobsIgnore(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (SyncPowers.MobsIgnore.shouldIgnore((LivingEntity) (Object) this, target)) cir.setReturnValue(false);
    }

    @ModifyArg(method = "travel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V", ordinal = 1), index = 0)
    private float aea$lavaSpeed(float speed) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isInLava()) return speed;
        List<List> found = new ArrayList<>();
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<List> cfgs = (List) PowerLookup.active(self, LegacyApoli.MODIFY_LAVA_SPEED, List.class);
        if (cfgs.isEmpty()) return speed;
        List<AttributeModifier> all = new ArrayList<>();
        for (List l : cfgs) for (Object o : l) if (o instanceof AttributeModifier m) all.add(m);
        return AttributeModifierHelper.apply(speed, AttributeModifierHelper.ensureSorted(all), self);
    }
}
