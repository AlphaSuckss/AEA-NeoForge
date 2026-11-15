package dev.alphasucks.aea.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alphasucks.aea.compat.sync.SyncPowers;
import dev.overgrown.apoli.client.ClientResolvedPowers;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/** Client rendering for sync:flip_model and sync:entity_texture_overlay. */
public final class AEAClient {
    private AEAClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AEAClient::addLayers);
        dev.alphasucks.aea.gui.client.AeaGuiClient.init(modBus);
        NeoForge.EVENT_BUS.addListener(AEAClient::cameraAngles);
        NeoForge.EVENT_BUS.addListener(AEAClient::renderArm);
    }

    private static <C> List<C> active(Entity entity, ResourceLocation type, Class<C> cls) {
        return PowerLookup.active(entity, type, cls);
    }

    public static boolean flipped(Entity entity) {
        return !active(entity, SyncPowers.FLIP_MODEL, SyncPowers.FlipCfg.class).isEmpty();
    }

    public static Optional<SyncPowers.OverlayCfg> overlay(Entity entity) {
        List<SyncPowers.OverlayCfg> l = active(entity, SyncPowers.TEXTURE_OVERLAY, SyncPowers.OverlayCfg.class);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    private static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity e = event.getCamera().getEntity();
        if (e == null) return;
        String name = e.getName().getString();
        if (name.equals("Dinnerbone") || name.equals("Grumm")) return;
        for (SyncPowers.FlipCfg c : active(e, SyncPowers.FLIP_MODEL, SyncPowers.FlipCfg.class)) {
            if (c.flipView()) {
                event.setRoll(event.getRoll() + 180f);
                return;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) renderer.addLayer(new OverlayLayer(renderer));
        }
    }

    private static void renderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        Optional<SyncPowers.OverlayCfg> o = overlay(player);
        if (o.isEmpty() || !o.get().firstPerson()) return;
        SyncPowers.OverlayCfg c = o.get();
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        Optional<ResourceLocation> tex = c.texture(slim);
        if (tex.isEmpty()) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        var part = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        var sleeve = event.getArm() == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(tex.get()));
        int color = FastColor.ARGB32.colorFromFloat(c.alpha(), c.red(), c.green(), c.blue());
        part.render(event.getPoseStack(), vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, color);
        sleeve.render(event.getPoseStack(), vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY, color);
        if (!c.asOverlay()) event.setCanceled(true);
    }

    static final class OverlayLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        OverlayLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Optional<SyncPowers.OverlayCfg> o = overlay(player);
            if (o.isEmpty() || !o.get().asOverlay()) return;
            SyncPowers.OverlayCfg c = o.get();
            Minecraft mc = Minecraft.getInstance();
            boolean firstPerson = mc.player == player && mc.options.getCameraType().isFirstPerson();
            if (firstPerson && !c.firstPerson()) return;
            boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
            Optional<ResourceLocation> tex = c.texture(slim);
            if (tex.isEmpty()) return;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex.get()));
            int color = FastColor.ARGB32.colorFromFloat(c.alpha(), c.red(), c.green(), c.blue());
            this.getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, color);
        }
    }

    static boolean unused(PowerContainer c) {
        return ClientResolvedPowers.isEmpty();
    }
}
