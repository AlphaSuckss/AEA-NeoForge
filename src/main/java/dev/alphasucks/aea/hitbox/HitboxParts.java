package dev.alphasucks.aea.hitbox;

import dev.alphasucks.aea.hitbox.client.ClientHitboxParts;
import dev.alphasucks.aea.hitbox.mixin.ServerLevelPartsAccessor;
import dev.alphasucks.aea.hitbox.power.HitboxPartPower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/** The {@code aea:hitbox_part} boxes of one living entity, and the server hooks that keep them in step with its powers. */
public final class HitboxParts {
    /** Implemented by every living entity through a mixin. */
    public interface Holder {
        HitboxParts aea$hitboxParts();
    }

    /** Entities that have at least one box. Entries leave when the entity is removed or its last box goes. */
    private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    private final ArrayList<ResourceLocation> powers = new ArrayList<>(2);
    private final ArrayList<HitboxPart> parts = new ArrayList<>(2);
    private boolean dirty;

    public static HitboxParts of(LivingEntity entity) {
        return ((Holder) entity).aea$hitboxParts();
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(HitboxParts::registerPayloads);
        // After Apoli's own ServerTickEvent.Post listener, which is where powers are ticked.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> endServerTick());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, HitboxParts::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> TRACKED.clear());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(HitboxNetwork.PartsPayload.TYPE, HitboxNetwork.PartsPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ClientHitboxParts.apply(payload);
        });
    }

    /** Called from the power's tick: makes sure the box exists and moves it. */
    public void touch(LivingEntity owner, ResourceLocation power, HitboxPartPower.Cfg cfg) {
        int index = powers.indexOf(power);
        HitboxPart part = index < 0 ? null : parts.get(index);
        if (part != null && (part.level() != owner.level() || !matches(part, cfg))) {
            removeAt(index);
            part = null;
        }
        if (part == null) {
            part = new HitboxPart(owner, cfg.width(), cfg.height(), cfg.forward(), cfg.up(), cfg.right(), cfg.scaleWithEntity());
            powers.add(power);
            parts.add(part);
            if (owner.level() instanceof ServerLevel level) ((ServerLevelPartsAccessor) level).aea$parts().put(part.getId(), part);
            dirty = true;
            TRACKED.add(owner);
        } else if (part.reposition()) {
            dirty = true;
        }
        part.seen = owner.level().getGameTime();
    }

    public void drop(ResourceLocation power) {
        int index = powers.indexOf(power);
        if (index >= 0) removeAt(index);
    }

    private static boolean matches(HitboxPart part, HitboxPartPower.Cfg cfg) {
        return part.width == cfg.width() && part.height == cfg.height() && part.forward == cfg.forward()
            && part.up == cfg.up() && part.right == cfg.right() && part.scaleWithEntity == cfg.scaleWithEntity();
    }

    private void removeAt(int index) {
        HitboxPart part = parts.remove(index);
        powers.remove(index);
        if (part.level() instanceof ServerLevel level) ((ServerLevelPartsAccessor) level).aea$parts().remove(part.getId());
        dirty = true;
    }

    private static void endServerTick() {
        if (TRACKED.isEmpty()) return;
        Iterator<LivingEntity> it = TRACKED.iterator();
        while (it.hasNext()) {
            LivingEntity owner = it.next();
            HitboxParts state = of(owner);
            if (owner.isRemoved()) {
                while (!state.parts.isEmpty()) state.removeAt(state.parts.size() - 1);
                it.remove();
                continue;
            }
            // A box whose power did not tick this tick belongs to a power that is gone or switched off.
            long now = owner.level().getGameTime();
            for (int i = state.parts.size() - 1; i >= 0; i--) {
                if (state.parts.get(i).seen != now) state.removeAt(i);
            }
            if (state.dirty) {
                state.dirty = false;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, state.payload(owner));
            }
            if (state.parts.isEmpty()) it.remove();
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity target) || !(event.getEntity() instanceof ServerPlayer player)) return;
        HitboxParts state = of(target);
        if (!state.parts.isEmpty()) PacketDistributor.sendToPlayer(player, state.payload(target));
    }

    private HitboxNetwork.PartsPayload payload(LivingEntity owner) {
        HitboxNetwork.Part[] out = new HitboxNetwork.Part[parts.size()];
        for (int i = 0; i < out.length; i++) {
            HitboxPart p = parts.get(i);
            out[i] = new HitboxNetwork.Part(p.getId(), p.width, p.height, p.forward, p.up, p.right, p.scaleWithEntity);
        }
        return new HitboxNetwork.PartsPayload(owner.getId(), out);
    }
}
