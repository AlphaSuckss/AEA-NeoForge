package dev.alphasucks.aea.compat.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.rope.Rope;
import dev.overgrown.apoli.rope.RopeAnchor;
import dev.overgrown.apoli.rope.RopeEndpointSource;
import dev.overgrown.apoli.rope.RopeManager;
import dev.overgrown.apoli.rope.RopeParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Shared config and helpers of Sync's rope actions, built on Overgrown's rope system. */
public final class SyncRopes {
    private SyncRopes() {}

    private static final ResourceLocation ROPE_TEXTURE = ResourceLocation.fromNamespaceAndPath("apoli", "textures/rope/rope.png");

    public record Cfg(float maxLength, ResourceLocation texture, String mode) {}

    public static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.optionalFieldOf("max_length", 30f).forGetter(Cfg::maxLength),
        IdCodecs.ID.optionalFieldOf("texture", ROPE_TEXTURE).forGetter(Cfg::texture),
        Codec.STRING.optionalFieldOf("mode", "toggle").forGetter(Cfg::mode)
    ).apply(i, Cfg::new));

    public static ResourceLocation texture(ResourceLocation tex) {
        // Sync's own rope texture isn't shipped on NeoForge; Overgrown's Apoli has the same art under apoli:.
        return tex.getNamespace().equals("sync") ? ResourceLocation.fromNamespaceAndPath("apoli", tex.getPath()) : tex;
    }

    public static RopeParams params(float maxLength) {
        RopeParams d = RopeParams.DEFAULT;
        return new RopeParams(d.minLength(), maxLength, d.stiffness(), d.radialDamping(), d.springScaling(), d.swingBoost(),
            d.maxSwingSpeed(), d.controlAccel(), d.reelStep(), d.slackPullRate(), d.constrainFrom(), d.constrainTo(),
            d.controllable(), d.mode(), d.breakBeyond(), d.startLength());
    }

    private static boolean detachNear(UUID owner, Vec3 anchor, ServerLevel level) {
        boolean any = false;
        for (Rope r : new ArrayList<>(RopeManager.forOwner(owner))) {
            Vec3 a = r.to.position(level);
            Vec3 b = r.from.position(level);
            if ((a != null && a.distanceToSqr(anchor) < 2.25) || (b != null && b.distanceToSqr(anchor) < 2.25)) {
                RopeManager.remove(r.id);
                any = true;
            }
        }
        return any;
    }

    /** Ropes the player to what they are looking at (a block, or an entity when {@code entityTarget}). */
    public static void attach(Cfg c, Entity actor, boolean entityTarget) {
        if (!(actor instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        String mode = c.mode().toLowerCase(Locale.ROOT);
        if (mode.equals("detach_all")) {
            RopeManager.removeMatching(player.getUUID(), null, null);
            return;
        }
        RopeAnchor to = new RopeEndpointSource(RopeEndpointSource.Type.RAYCAST, c.maxLength(), entityTarget, !entityTarget, Optional.empty())
            .resolve(player, null, level);
        if (to == null) return;
        if (entityTarget && !(to instanceof RopeAnchor.OfEntity)) return;
        Vec3 anchor = to.position(level);
        RopeAnchor from = RopeEndpointSource.self().resolve(player, null, level);
        if (from == null || anchor == null) return;
        switch (mode) {
            case "detach" -> detachNear(player.getUUID(), anchor, level);
            case "attach" -> RopeManager.create(level, from, to, null, player.getUUID(), params(c.maxLength()), texture(c.texture()));
            default -> {
                if (!detachNear(player.getUUID(), anchor, level)) {
                    RopeManager.create(level, from, to, null, player.getUUID(), params(c.maxLength()), texture(c.texture()));
                }
            }
        }
    }
}
