package dev.alphasucks.aea.mobai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.mobai.goal.AeaCustomGoal;
import dev.alphasucks.aea.mobai.goal.AeaFleeGoal;
import dev.alphasucks.aea.mobai.goal.AeaFollowGoal;
import dev.alphasucks.aea.mobai.goal.AeaLookAtGoal;
import dev.alphasucks.aea.mobai.goal.AeaRetaliateGoal;
import dev.alphasucks.aea.mobai.goal.AeaTargetGoal;
import dev.alphasucks.aea.mobai.goal.AeaWanderGoal;
import dev.alphasucks.aea.mobai.goal.GatedGoal;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The goal specs aea:mob_ai accepts under "goals" and "targets" (AEA 1.5.4). Types are aea:<name>; the aea: prefix is optional. */
public final class GoalSpecs {
    private GoalSpecs() {}

    private static final Map<String, Goal.Flag> CONTROLS = Map.of(
        "move", Goal.Flag.MOVE, "look", Goal.Flag.LOOK, "jump", Goal.Flag.JUMP, "target", Goal.Flag.TARGET);

    private static final Codec<Goal.Flag> CONTROL = Codec.STRING.comapFlatMap(s -> {
        Goal.Flag f = CONTROLS.get(s.toLowerCase(Locale.ROOT));
        return f != null ? DataResult.success(f) : DataResult.error(() -> "Unknown goal control '" + s + "' (use move, look, jump or target)");
    }, f -> CONTROLS.entrySet().stream().filter(e -> e.getValue() == f).map(Map.Entry::getKey).findFirst().orElse("move"));

    public interface Spec {
        String type();
        int priority();
        Optional<EntityCondition> condition();
        Goal build(Mob mob);

        default boolean melee() {
            return false;
        }

        default Goal create(Mob mob) {
            Goal g = build(mob);
            if (g == null) return null;
            return condition().isPresent() ? new GatedGoal(g, condition().get(), mob) : g;
        }
    }

    private static <A> MapCodec<Optional<A>> opt(String name, Codec<A> codec) {
        return LoggedOptionalField.strict(name, codec);
    }

    private static String key(String type) {
        String s = type.toLowerCase(Locale.ROOT);
        return s.startsWith("aea:") ? s.substring(4) : s;
    }

    public record Wander(int priority, double speed, int interval, int horizontal, int vertical, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<Wander> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 6).forGetter(Wander::priority),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0D).forGetter(Wander::speed),
            Codec.INT.optionalFieldOf("interval", 120).forGetter(Wander::interval),
            Codec.INT.optionalFieldOf("horizontal_range", 10).forGetter(Wander::horizontal),
            Codec.INT.optionalFieldOf("vertical_range", 7).forGetter(Wander::vertical),
            opt("condition", EntityCondition.CODEC).forGetter(Wander::condition)
        ).apply(i, Wander::new));

        @Override public String type() { return "aea:wander"; }

        @Override
        public Goal build(Mob mob) {
            return mob instanceof PathfinderMob p ? new AeaWanderGoal(p, speed, interval, horizontal, vertical) : null;
        }
    }

    public record LookAround(int priority, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<LookAround> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 8).forGetter(LookAround::priority),
            opt("condition", EntityCondition.CODEC).forGetter(LookAround::condition)
        ).apply(i, LookAround::new));

        @Override public String type() { return "aea:look_around"; }

        @Override
        public Goal build(Mob mob) {
            return new RandomLookAroundGoal(mob);
        }
    }

    public record LookAt(int priority, double range, float chance, Optional<BiEntityCondition> bientityCondition, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<LookAt> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 7).forGetter(LookAt::priority),
            Codec.DOUBLE.optionalFieldOf("range", 8.0D).forGetter(LookAt::range),
            Codec.FLOAT.optionalFieldOf("chance", 0.02F).forGetter(LookAt::chance),
            opt("bientity_condition", BiEntityCondition.CODEC).forGetter(LookAt::bientityCondition),
            opt("condition", EntityCondition.CODEC).forGetter(LookAt::condition)
        ).apply(i, LookAt::new));

        @Override public String type() { return "aea:look_at"; }

        @Override
        public Goal build(Mob mob) {
            return new AeaLookAtGoal(mob, range, chance, bientityCondition);
        }
    }

    public record MeleeAttack(int priority, double speed, boolean pauseWhenIdle, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<MeleeAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 2).forGetter(MeleeAttack::priority),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0D).forGetter(MeleeAttack::speed),
            Codec.BOOL.optionalFieldOf("pause_when_idle", false).forGetter(MeleeAttack::pauseWhenIdle),
            opt("condition", EntityCondition.CODEC).forGetter(MeleeAttack::condition)
        ).apply(i, MeleeAttack::new));

        @Override public String type() { return "aea:melee_attack"; }

        @Override public boolean melee() { return true; }

        @Override
        public Goal build(Mob mob) {
            return mob instanceof PathfinderMob p ? new MeleeAttackGoal(p, speed, pauseWhenIdle) : null;
        }
    }

    public record Follow(int priority, String target, Optional<BiEntityCondition> bientityCondition, double speed, double startDistance,
                         double stopDistance, double range, double teleportDistance, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<Follow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 4).forGetter(Follow::priority),
            Codec.STRING.optionalFieldOf("target", "owner").forGetter(Follow::target),
            opt("bientity_condition", BiEntityCondition.CODEC).forGetter(Follow::bientityCondition),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0D).forGetter(Follow::speed),
            Codec.DOUBLE.optionalFieldOf("start_distance", 6.0D).forGetter(Follow::startDistance),
            Codec.DOUBLE.optionalFieldOf("stop_distance", 2.5D).forGetter(Follow::stopDistance),
            Codec.DOUBLE.optionalFieldOf("range", 16.0D).forGetter(Follow::range),
            Codec.DOUBLE.optionalFieldOf("teleport_distance", 0.0D).forGetter(Follow::teleportDistance),
            opt("condition", EntityCondition.CODEC).forGetter(Follow::condition)
        ).apply(i, Follow::new));

        @Override public String type() { return "aea:follow"; }

        @Override
        public Goal build(Mob mob) {
            boolean owner = !"nearest".equalsIgnoreCase(target);
            return new AeaFollowGoal(mob, owner, bientityCondition, speed, startDistance, stopDistance, range, teleportDistance);
        }
    }

    public record Flee(int priority, double range, double speed, double fastSpeed, Optional<BiEntityCondition> bientityCondition, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<Flee> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 1).forGetter(Flee::priority),
            Codec.DOUBLE.optionalFieldOf("range", 8.0D).forGetter(Flee::range),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0D).forGetter(Flee::speed),
            Codec.DOUBLE.optionalFieldOf("fast_speed", 1.25D).forGetter(Flee::fastSpeed),
            opt("bientity_condition", BiEntityCondition.CODEC).forGetter(Flee::bientityCondition),
            opt("condition", EntityCondition.CODEC).forGetter(Flee::condition)
        ).apply(i, Flee::new));

        @Override public String type() { return "aea:flee"; }

        @Override
        public Goal build(Mob mob) {
            return mob instanceof PathfinderMob p ? new AeaFleeGoal(p, range, speed, fastSpeed, bientityCondition) : null;
        }
    }

    public record Custom(int priority, List<Goal.Flag> controls, Optional<EntityCondition> startCondition, Optional<EntityCondition> continueCondition,
                         Optional<EntityAction> startAction, Optional<EntityAction> tickAction, Optional<EntityAction> stopAction,
                         int interval, int duration, int cooldown) implements Spec {
        static final MapCodec<Custom> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 3).forGetter(Custom::priority),
            CONTROL.listOf().optionalFieldOf("controls", List.of()).forGetter(Custom::controls),
            opt("start_condition", EntityCondition.CODEC).forGetter(Custom::startCondition),
            opt("continue_condition", EntityCondition.CODEC).forGetter(Custom::continueCondition),
            opt("start_action", EntityAction.CODEC).forGetter(Custom::startAction),
            opt("tick_action", EntityAction.CODEC).forGetter(Custom::tickAction),
            opt("stop_action", EntityAction.CODEC).forGetter(Custom::stopAction),
            Codec.INT.optionalFieldOf("interval", 1).forGetter(Custom::interval),
            Codec.INT.optionalFieldOf("duration", 0).forGetter(Custom::duration),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(Custom::cooldown)
        ).apply(i, Custom::new));

        @Override public String type() { return "aea:custom"; }

        /** Custom goals gate themselves through start_condition; the shared "condition" field does not apply. */
        @Override public Optional<EntityCondition> condition() { return Optional.empty(); }

        @Override
        public Goal build(Mob mob) {
            EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);
            flags.addAll(controls);
            return new AeaCustomGoal(mob, flags, startCondition, continueCondition, startAction, tickAction, stopAction, interval, duration, cooldown);
        }
    }

    public record Target(int priority, Optional<Double> range, boolean mustSee, int interval, Optional<BiEntityCondition> bientityCondition, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<Target> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 2).forGetter(Target::priority),
            Codec.DOUBLE.optionalFieldOf("range").forGetter(Target::range),
            Codec.BOOL.optionalFieldOf("must_see", true).forGetter(Target::mustSee),
            Codec.INT.optionalFieldOf("interval", 10).forGetter(Target::interval),
            opt("bientity_condition", BiEntityCondition.CODEC).forGetter(Target::bientityCondition),
            opt("condition", EntityCondition.CODEC).forGetter(Target::condition)
        ).apply(i, Target::new));

        @Override public String type() { return "aea:target"; }

        @Override
        public Goal build(Mob mob) {
            return new AeaTargetGoal(mob, range, mustSee, interval, bientityCondition);
        }
    }

    public record Retaliate(int priority, Optional<BiEntityCondition> bientityCondition, boolean alertOthers, double alertRange, Optional<EntityCondition> condition) implements Spec {
        static final MapCodec<Retaliate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("priority", 1).forGetter(Retaliate::priority),
            opt("bientity_condition", BiEntityCondition.CODEC).forGetter(Retaliate::bientityCondition),
            Codec.BOOL.optionalFieldOf("alert_others", false).forGetter(Retaliate::alertOthers),
            Codec.DOUBLE.optionalFieldOf("alert_range", 10.0D).forGetter(Retaliate::alertRange),
            opt("condition", EntityCondition.CODEC).forGetter(Retaliate::condition)
        ).apply(i, Retaliate::new));

        @Override public String type() { return "aea:retaliate"; }

        @Override
        public Goal build(Mob mob) {
            return new AeaRetaliateGoal(mob, bientityCondition, alertOthers, alertRange);
        }
    }

    private static final Map<String, MapCodec<? extends Spec>> GOALS = Map.of(
        "wander", Wander.CODEC, "look_around", LookAround.CODEC, "look_at", LookAt.CODEC, "melee_attack", MeleeAttack.CODEC,
        "follow", Follow.CODEC, "flee", Flee.CODEC, "custom", Custom.CODEC);

    private static final Map<String, MapCodec<? extends Spec>> TARGETS = Map.of(
        "target", Target.CODEC, "retaliate", Retaliate.CODEC);

    private static Codec<Spec> dispatch(Map<String, MapCodec<? extends Spec>> types, String label) {
        Codec<String> typeCodec = Codec.STRING.comapFlatMap(
            s -> types.containsKey(key(s)) ? DataResult.success("aea:" + key(s))
                : DataResult.error(() -> "Unknown mob_ai " + label + " type '" + s + "'. Known: aea:" + String.join(", aea:", types.keySet())),
            s -> s);
        return typeCodec.dispatch("type", Spec::type, t -> types.get(key(t)));
    }

    public static final Codec<Spec> GOAL = dispatch(GOALS, "goal");
    public static final Codec<Spec> TARGET = dispatch(TARGETS, "target");
}
