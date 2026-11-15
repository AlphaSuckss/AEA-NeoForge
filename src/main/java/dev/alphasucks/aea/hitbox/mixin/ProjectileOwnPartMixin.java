package dev.alphasucks.aea.hitbox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.hitbox.HitboxPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A projectile never hits a hit box that belongs to whoever fired it. */
@Mixin(Projectile.class)
public abstract class ProjectileOwnPartMixin {
    @Shadow
    public abstract Entity getOwner();

    @ModifyReturnValue(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private boolean aea$skipOwnParts(boolean original, @Local(argsOnly = true) Entity target) {
        return original && !(target instanceof HitboxPart part && part.getParent() == getOwner());
    }
}
