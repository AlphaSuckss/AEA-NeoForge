package dev.alphasucks.aea.compat.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Comparison;
import dev.overgrown.apoli.rope.Rope;
import dev.overgrown.apoli.rope.RopeAnchor;
import dev.overgrown.apoli.rope.RopeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Sync 1.11.8 conditions that Overgrown's Apoli does not ship (or ships with different fields). */
public final class SyncConditions {
    private SyncConditions() {}

    // ---------------------------------------------------------------- sync:velocity
    public static final class Velocity implements ConditionType<EntityCtx, Velocity.Cfg> {
        public record Cfg(Optional<Float> x, Optional<Float> y, Optional<Float> z, Optional<List<String>> axes,
                          Comparison comparison, float compareTo) {}

        @Override
        public MapCodec<Cfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("x").forGetter(Cfg::x),
                Codec.FLOAT.optionalFieldOf("y").forGetter(Cfg::y),
                Codec.FLOAT.optionalFieldOf("z").forGetter(Cfg::z),
                Codecs.singleOrList(Codec.STRING).optionalFieldOf("axes").forGetter(Cfg::axes),
                Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
                Codec.FLOAT.optionalFieldOf("compare_to", 0f).forGetter(Cfg::compareTo)
            ).apply(i, Cfg::new));
        }

        @Override
        public boolean test(Cfg c, EntityCtx ctx) {
            Vec3 v = ctx.raw().getDeltaMovement();
            if (c.x.isPresent() || c.y.isPresent() || c.z.isPresent()) {
                if (c.x.isPresent() && !c.comparison.compare((float) v.x, c.x.get())) return false;
                if (c.y.isPresent() && !c.comparison.compare((float) v.y, c.y.get())) return false;
                if (c.z.isPresent() && !c.comparison.compare((float) v.z, c.z.get())) return false;
                return true;
            }
            if (c.axes.isPresent()) {
                double combined = 0;
                for (String a : c.axes.get()) {
                    combined += switch (a.toLowerCase(Locale.ROOT)) {
                        case "x" -> v.x;
                        case "y" -> v.y;
                        case "z" -> v.z;
                        default -> 0;
                    };
                }
                return c.comparison.compare((float) combined, c.compareTo);
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- sync:in_pose
    public static final class InPose implements ConditionType<EntityCtx, Pose> {
        @Override
        public MapCodec<Pose> codec() {
            return Codecs.POSE.fieldOf("pose");
        }

        @Override
        public boolean test(Pose pose, EntityCtx ctx) {
            return ctx.raw().getPose() == pose;
        }
    }

    // ---------------------------------------------------------------- sync:attached_to_rope
    public static final class AttachedToRope implements ConditionType<EntityCtx, AttachedToRope.Cfg> {
        public record Cfg(String anchorType, Comparison comparison, int compareTo) {}

        @Override
        public MapCodec<Cfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("anchor_type", "any").forGetter(Cfg::anchorType),
                Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
                Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
            ).apply(i, Cfg::new));
        }

        @Override
        public boolean test(Cfg c, EntityCtx ctx) {
            List<Rope> ropes = RopeManager.forEntity(ctx.raw().getUUID());
            int count = 0;
            for (Rope r : ropes) {
                boolean fromSelf = ctx.raw().getUUID().equals(r.fromEntity);
                RopeAnchor other = fromSelf ? r.to : r.from;
                boolean block = other instanceof RopeAnchor.Position;
                switch (c.anchorType.toLowerCase(Locale.ROOT)) {
                    case "block" -> { if (block) count++; }
                    case "entity" -> { if (!block) count++; }
                    default -> count++;
                }
            }
            return c.comparison.compare(count, c.compareTo);
        }
    }

    // ---------------------------------------------------------------- sync:has_aspect (AspectsLib is Fabric-only)
    public static final class HasAspect implements ConditionType<EntityCtx, Object> {
        @Override
        public MapCodec<Object> codec() {
            return MapCodec.unit(Boolean.FALSE);
        }

        @Override
        public boolean test(Object c, EntityCtx ctx) {
            return false;
        }
    }

    // ---------------------------------------------------------------- sync:is_selected_stolen_power
    public static final class IsSelectedStolenPower implements ConditionType<EntityCtx, ResourceLocation> {
        @Override
        public MapCodec<ResourceLocation> codec() {
            return IdCodecs.ID.fieldOf("source");
        }

        @Override
        public boolean test(ResourceLocation source, EntityCtx ctx) {
            return StolenPowers.isSourceSelected(ctx.raw(), source);
        }
    }

    // ---------------------------------------------------------------- sync:colliding (bi-entity)
    public static final class Colliding implements ConditionType<BiEntityCtx, Optional<Vec3>> {
        @Override
        public MapCodec<Optional<Vec3>> codec() {
            return Codecs.VEC3.optionalFieldOf("offset");
        }

        @Override
        public boolean test(Optional<Vec3> offset, BiEntityCtx ctx) {
            if (ctx.actor() == null || ctx.target() == null) return false;
            AABB box = ctx.actor().getBoundingBox();
            if (offset.isPresent()) box = box.move(offset.get());
            return box.intersects(ctx.target().getBoundingBox());
        }
    }

    // ---------------------------------------------------------------- sync:command (bi-entity)
    public static final class BiCommand implements ConditionType<BiEntityCtx, BiCommand.Cfg> {
        public record Cfg(String command, Optional<String> actorSelector, Optional<String> targetSelector,
                          Comparison comparison, int compareTo) {}

        @Override
        public MapCodec<Cfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("command").forGetter(Cfg::command),
                Codec.STRING.optionalFieldOf("actor_selector").forGetter(Cfg::actorSelector),
                Codec.STRING.optionalFieldOf("target_selector").forGetter(Cfg::targetSelector),
                Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
                Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
            ).apply(i, Cfg::new));
        }

        @Override
        public boolean test(Cfg c, BiEntityCtx ctx) {
            Entity actor = ctx.actor();
            Entity target = ctx.target();
            if (actor == null || target == null || actor.getServer() == null) return false;
            String cmd = c.command;
            String actorSel = c.actorSelector.orElse("%actor%");
            String targetSel = c.targetSelector.orElse("%target%");
            cmd = cmd.replace(actorSel, actor.getStringUUID()).replace(targetSel, target.getStringUUID());
            return c.comparison.compare(run(actor, cmd), c.compareTo);
        }
    }

    static int run(Entity as, String command) {
        MinecraftServer server = as.getServer();
        if (server == null) return 0;
        int[] result = {0};
        CommandSourceStack source = as.createCommandSourceStack().withPermission(4).withSuppressedOutput()
            .withCallback((success, value) -> result[0] = value);
        server.getCommands().performPrefixedCommand(source, command);
        return result[0];
    }

    // ---------------------------------------------------------------- sync:on_cooldown (item)
    public static final class OnCooldown implements ConditionType<ItemCtx, Object> {
        @Override
        public MapCodec<Object> codec() {
            return MapCodec.unit(Boolean.TRUE);
        }

        @Override
        public boolean test(Object c, ItemCtx ctx) {
            if (ctx.stack().isEmpty()) return false;
            return ctx.holder() instanceof Player p && p.getCooldowns().isOnCooldown(ctx.stack().getItem());
        }
    }
}
