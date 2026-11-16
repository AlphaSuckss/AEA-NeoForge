package dev.alphasucks.aea.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.client.AEAClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** A flip power inverts the vanilla answer, so Dinnerbone and Grumm come out right side up. */
    @ModifyReturnValue(method = "isEntityUpsideDown", at = @At("RETURN"))
    private static boolean aea$flipModel(boolean original, @Local(argsOnly = true) LivingEntity entity) {
        return AEAClient.flipped(entity) != original;
    }
}
