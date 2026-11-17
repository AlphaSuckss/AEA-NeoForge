package dev.alphasucks.aea.mindcontrol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alphasucks.aea.mindcontrol.ControlBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Draws a mind controller's body as them: their skin (wide or slim), armour and held items. */
public final class BodyRenderer extends EntityRenderer<ControlBody> {
    private final Model wide, slim;

    public BodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wide = new Model(ctx, false);
        this.slim = new Model(ctx, true);
        this.shadowRadius = 0.5F;
    }

    private static PlayerSkin skin(ControlBody body) {
        UUID id = body.owner().orElse(body.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    @Override
    public void render(ControlBody body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        (skin(body).model() == PlayerSkin.Model.SLIM ? slim : wide).render(body, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ControlBody body) {
        return skin(body).texture();
    }

    private static final class Model extends LivingEntityRenderer<ControlBody, PlayerModel<ControlBody>> {
        Model(EntityRendererProvider.Context ctx, boolean slim) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
            addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(ControlBody body) {
            return skin(body).texture();
        }

        @Override
        protected boolean shouldShowName(ControlBody body) {
            return body.hasCustomName();
        }
    }
}
