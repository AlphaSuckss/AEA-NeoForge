package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.addon.power.SeeInvisiblePower;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code aea:see_invisible}: an invisible entity is not invisible to a player whose power reveals it. The priority puts
 * this after Apoli's own invisibility check, so it also reveals entities hidden by {@code apoli:invisibility}.
 */
@Mixin(value = Entity.class, priority = 1500)
public abstract class EntitySeeInvisibleMixin {
    @ModifyReturnValue(method = "isInvisibleTo", at = @At("RETURN"))
    private boolean aea$seeInvisible(boolean original, @Local(argsOnly = true) Player observer) {
        return original && Float.isNaN(SeeInvisiblePower.revealOpacity(observer, (Entity) (Object) this));
    }
}
