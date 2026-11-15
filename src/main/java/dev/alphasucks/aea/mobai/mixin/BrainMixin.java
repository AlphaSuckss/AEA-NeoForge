package dev.alphasucks.aea.mobai.mixin;

import dev.alphasucks.aea.mobai.MobAiPower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** aea:mob_ai with "remove": "brain" (or "all") stops the mob's brain from ticking. */
@Mixin(Brain.class)
public abstract class BrainMixin {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void aea$suppress(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof Mob mob && MobAiPower.suppressesBrain(mob)) ci.cancel();
    }
}
