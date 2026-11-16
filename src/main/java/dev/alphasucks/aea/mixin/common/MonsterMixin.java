package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.sync.power.MobsIgnorePower;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Monster.class)
public abstract class MonsterMixin {
    @ModifyReturnValue(method = "isPreventingPlayerRest", at = @At("RETURN"))
    private boolean aea$mobsIgnoreRest(boolean original, @Local(argsOnly = true) Player player) {
        return original && !MobsIgnorePower.shouldIgnore((Monster) (Object) this, player);
    }
}
