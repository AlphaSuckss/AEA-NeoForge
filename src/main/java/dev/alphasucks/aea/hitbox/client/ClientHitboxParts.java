package dev.alphasucks.aea.hitbox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alphasucks.aea.hitbox.HitboxNetwork;
import dev.alphasucks.aea.hitbox.HitboxPart;
import dev.alphasucks.aea.hitbox.mixin.ClientLevelPartsAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Client copies of {@code aea:hitbox_part} boxes. The server says which boxes an entity has; the client builds them
 * with the same network ids, moves them every tick, and lets the crosshair hit them.
 */
public final class ClientHitboxParts {
    private ClientHitboxParts() {}

    private static final HitboxPart[] NONE = new HitboxPart[0];
    private static final Int2ObjectOpenHashMap<HitboxNetwork.Part[]> SPECS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<HitboxPart[]> LIVE = new Int2ObjectOpenHashMap<>();
    private static ClientLevel level;

    public static void init() {
        HitboxPart.viewer = () -> Minecraft.getInstance().getCameraEntity();
        NeoForge.EVENT_BUS.addListener(ClientHitboxParts::tick);
        NeoForge.EVENT_BUS.addListener(ClientHitboxParts::renderOutlines);
    }

    public static void apply(HitboxNetwork.PartsPayload payload) {
        syncLevel();
        discard(payload.owner());
        if (payload.parts().length == 0) SPECS.remove(payload.owner());
        else SPECS.put(payload.owner(), payload.parts());
    }

    /** Everything belongs to one client level; a new level starts empty and the server sends the boxes again. */
    private static void syncLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == level) return;
        level = current;
        SPECS.clear();
        LIVE.clear();
    }

    private static void discard(int owner) {
        HitboxPart[] parts = LIVE.remove(owner);
        if (parts == null || level == null) return;
        Int2ObjectMap<PartEntity<?>> map = ((ClientLevelPartsAccessor) level).aea$parts();
        for (HitboxPart part : parts) map.remove(part.getId(), part);
    }

    private static void tick(ClientTickEvent.Post event) {
        syncLevel();
        if (level == null || SPECS.isEmpty()) return;
        for (Int2ObjectMap.Entry<HitboxNetwork.Part[]> entry : SPECS.int2ObjectEntrySet()) {
            int owner = entry.getIntKey();
            HitboxPart[] parts = LIVE.getOrDefault(owner, NONE);
            if (!(level.getEntity(owner) instanceof LivingEntity living) || living.isRemoved()) {
                if (parts.length > 0) discard(owner);
                continue;
            }
            if (parts.length == 0 || parts[0].getParent() != living) {
                if (parts.length > 0) discard(owner);
                parts = build(living, entry.getValue());
                LIVE.put(owner, parts);
            }
            for (HitboxPart part : parts) part.reposition();
        }
    }

    private static HitboxPart[] build(LivingEntity owner, HitboxNetwork.Part[] specs) {
        Int2ObjectMap<PartEntity<?>> map = ((ClientLevelPartsAccessor) level).aea$parts();
        HitboxPart[] parts = new HitboxPart[specs.length];
        for (int i = 0; i < specs.length; i++) {
            HitboxNetwork.Part spec = specs[i];
            HitboxPart part = new HitboxPart(owner, spec.width(), spec.height(), spec.forward(), spec.up(), spec.right(), spec.scaleWithEntity());
            part.setId(spec.id());
            map.put(spec.id(), part);
            parts[i] = part;
        }
        return parts;
    }

    /** With hit boxes shown (F3 + B), the extra boxes are outlined in green. */
    private static void renderOutlines(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (HitboxPart[] parts : LIVE.values()) {
            for (HitboxPart part : parts) {
                LevelRenderer.renderLineBox(pose, lines, part.getBoundingBox().move(-camera.x, -camera.y, -camera.z), 0.25F, 1.0F, 0.0F, 1.0F);
            }
        }
        buffers.endBatch(RenderType.lines());
    }
}
