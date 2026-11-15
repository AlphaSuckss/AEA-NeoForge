package dev.alphasucks.aea.mixin.client;

import dev.alphasucks.aea.client.AEAClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "isEntityUpsideDown", at = @At("HEAD"), cancellable = true)
    private static void aea$flipModel(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!AEAClient.flipped(entity)) return;
        String name = entity.getName().getString();
        // Dinnerbone/Grumm are already upside down: a flip power turns them right side up.
        cir.setReturnValue(!(name.equals("Dinnerbone") || name.equals("Grumm")));
    }
}
