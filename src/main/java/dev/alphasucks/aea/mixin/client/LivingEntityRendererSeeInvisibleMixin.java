package dev.alphasucks.aea.mixin.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alphasucks.aea.compat.addon.power.SeeInvisiblePower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code aea:see_invisible}: draws a revealed entity at the power's opacity. Vanilla already draws an invisible entity
 * the viewer may see as a see-through body; this sets how see-through, and draws it solid at opacity 0.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSeeInvisibleMixin {
    /** Alpha for the body being drawn right now, or -1 when it is not a revealed entity. Render thread only. */
    @Unique
    private static int aea$revealAlpha = -1;

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean aea$revealBody(LivingEntityRenderer<?, ?> renderer, LivingEntity entity, Operation<Boolean> original) {
        aea$revealAlpha = -1;
        if (original.call(renderer, entity)) return true;
        float opacity = SeeInvisiblePower.revealOpacity(Minecraft.getInstance().player, entity);
        if (Float.isNaN(opacity)) return false;
        if (opacity <= 0.0F) return true;
        aea$revealAlpha = Mth.clamp(Math.round((1.0F - opacity) * 255.0F), 0, 255);
        return false;
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void aea$revealAlpha(EntityModel<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, Operation<Void> original) {
        int alpha = aea$revealAlpha;
        aea$revealAlpha = -1;
        original.call(model, pose, buffer, light, overlay, alpha < 0 ? color : alpha << 24 | (color & 0xFFFFFF));
    }
}
