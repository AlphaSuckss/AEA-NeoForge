package dev.alphasucks.aea.mixin.common;

import dev.alphasucks.aea.compat.sync.power.MobsIgnorePower;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class MonsterMixin {
    @Inject(method = "isPreventingPlayerRest", at = @At("HEAD"), cancellable = true)
    private void aea$mobsIgnoreRest(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (MobsIgnorePower.shouldIgnore((Monster) (Object) this, player)) cir.setReturnValue(false);
    }
}
