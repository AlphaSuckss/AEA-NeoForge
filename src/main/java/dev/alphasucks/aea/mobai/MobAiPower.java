package dev.alphasucks.aea.mobai;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.mobai.mixin.AttributeMapAccessor;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.codec.SingleOrList;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeGate;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * aea:mob_ai (AEA 1.5.4): adds goals / target goals to the holding mob, optionally strips vanilla goals by
 * category (or stops its brain), keeps it from ever targeting matching entities, and can set its attack damage.
 * Everything is undone when the power is lost, inactive or suppressed.
 */
public final class MobAiPower extends PowerType<MobAiPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "mob_ai");
    static final PowerTypeGate GATE = new PowerTypeGate(ID);
    private static final ArrayDeque<Runnable> DEFERRED = new ArrayDeque<>();

    static <A> MapCodec<A> strictOr(String name, Codec<A> codec, A fallback) {
        return LoggedOptionalField.strict(name, codec).xmap(o -> o.orElse(fallback), Optional::of);
    }

    public record Config(List<String> remove, List<GoalSpecs.Spec> goals, List<GoalSpecs.Spec> targets,
                         Optional<BiEntityCondition> neverTarget, Optional<Double> attackDamage, int reapplyInterval) {
        boolean removesBrain() {
            for (int i = 0; i < remove.size(); i++) {
                String s = remove.get(i);
                if (s.equalsIgnoreCase("brain") || s.equalsIgnoreCase("all")) return true;
            }
            return false;
        }

        boolean needsAttackDamage() {
            if (attackDamage.isPresent()) return true;
            for (GoalSpecs.Spec s : goals) if (s.melee()) return true;
            return false;
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            strictOr("remove", SingleOrList.of(Codec.STRING), List.<String>of()).forGetter(Config::remove),
            strictOr("goals", GoalSpecs.GOAL.listOf(), List.<GoalSpecs.Spec>of()).forGetter(Config::goals),
            strictOr("targets", GoalSpecs.TARGET.listOf(), List.<GoalSpecs.Spec>of()).forGetter(Config::targets),
            LoggedOptionalField.strict("never_target", BiEntityCondition.CODEC).forGetter(Config::neverTarget),
            Codec.DOUBLE.optionalFieldOf("attack_damage").forGetter(Config::attackDamage),
            Codec.INT.optionalFieldOf("reapply_interval", 20).forGetter(Config::reapplyInterval)
        ).apply(i, Config::new));
    }

    static MobAiState state(Mob mob) {
        return ((MobAiState.Holder) mob).aea$mobAiState();
    }

    @Override
    public void tick(ResourceLocation powerId, Config cfg, PowerContainer holder) {
        if (!(holder.rawOwner() instanceof Mob mob) || !(mob.level() instanceof ServerLevel) || holder.isSuppressed(powerId)) return;
        MobAiState state = state(mob);
        Applied applied = state.applied.get(powerId);
        if (applied != null && applied.cfg != cfg) {
            applied.undo(mob, state);
            state.applied.remove(powerId);
            applied = null;
        }
        if (applied == null) {
            applied = Applied.apply(mob, state, powerId, cfg);
            state.applied.put(powerId, applied);
            MobAi.track(mob);
        }
        applied.pendingUndo = false;
        applied.activeTick = mob.level().getGameTime();
        applied.tick(mob);
    }

    /**
     * Overgrown ticks every held power; AEA 1.5.4 (on Apoli 2.9) only ticked powers whose "condition" held. The check
     * runs on the context the container already built; {@link #tickStored} undoes a power that stopped ticking.
     */
    @Override
    public boolean isActive(ResourceLocation powerId, Config cfg, EntityCtx ctx) {
        Power loaded = ApoliPowers.get(powerId);
        if (loaded == null || loaded.condition().isEmpty()) return true;
        try {
            return loaded.condition().get().test(ctx);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The power's condition, for types that only check it on an interval (see {@link MobPathingPower}). */
    static boolean conditionHolds(ResourceLocation powerId, Mob mob) {
        try {
            Power loaded = ApoliPowers.get(powerId);
            if (loaded == null || loaded.condition().isEmpty()) return true;
            return loaded.condition().get().test(EntityCtx.of(mob, mob.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * AEA 1.5.4 ran this right after every power tick (the 1.20.1 bridge called it whether or not the power was
     * active). Overgrown's container only ticks active powers, so {@link MobAi} calls it after Apoli's server tick.
     */
    static void tickStored(Mob mob) {
        MobAiState state = state(mob);
        if (state.applied.isEmpty()) return;
        long now = mob.level().getGameTime();
        var it = state.applied.entrySet().iterator();
        while (it.hasNext()) {
            Applied a = it.next().getValue();
            if (a.activeTick != now) {
                a.undo(mob, state);
                it.remove();
            }
        }
    }

    @Override
    public void onRemoved(ResourceLocation powerId, Config cfg, PowerContainer holder, ResourceLocation source) {
        if (holder.hasPower(powerId) || !(holder.rawOwner() instanceof Mob mob) || mob.level().isClientSide()) return;
        MobAiState state = state(mob);
        Applied applied = state.applied.get(powerId);
        if (applied == null) return;
        applied.pendingUndo = true;
        DEFERRED.add(() -> {
            if (applied.pendingUndo && state.applied.get(powerId) == applied) {
                applied.undo(mob, state);
                state.applied.remove(powerId);
            }
        });
    }

    static void runDeferred() {
        Runnable r;
        while ((r = DEFERRED.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static List<Config> active(Mob mob) {
        if (!GATE.inUse() || mob.level().isClientSide()) return List.of();
        MobAiState state = state(mob);
        long stamp = MobPathingPower.stamp(mob);
        if (state.aiStamp != stamp) {
            state.aiStamp = stamp;
            List<Config> list;
            try {
                list = PowerLookup.active((Entity) mob, ID, Config.class);
            } catch (RuntimeException e) {
                list = List.of();
            }
            state.ai = list;
        }
        return state.ai;
    }

    public static boolean neverTargets(Mob mob, LivingEntity target) {
        List<Config> list = active(mob);
        for (int i = 0; i < list.size(); i++) {
            Optional<BiEntityCondition> c = list.get(i).neverTarget();
            if (c.isPresent()) {
                try {
                    if (c.get().test(BiEntityCtx.of(mob, target, mob.level()))) return true;
                } catch (RuntimeException ignored) {
                }
            }
        }
        return false;
    }

    public static boolean suppressesBrain(Mob mob) {
        List<Config> list = active(mob);
        for (int i = 0; i < list.size(); i++) if (list.get(i).removesBrain()) return true;
        return false;
    }

    /** What one aea:mob_ai power did to one mob, so it can be undone exactly. */
    static final class Applied {
        final Config cfg;
        final ResourceLocation modifierId;
        long activeTick;
        boolean pendingUndo;
        final boolean ownSelectors;
        final GoalSelector goals;
        final GoalSelector targets;
        final Set<Goal> added = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Pair<Integer, Goal>> removedGoals = new ArrayList<>();
        final List<Pair<Integer, Goal>> removedTargets = new ArrayList<>();
        boolean damageModifier;
        boolean brainStopped;

        private Applied(Config cfg, ResourceLocation powerId, boolean ownSelectors, GoalSelector goals, GoalSelector targets) {
            this.cfg = cfg;
            // AEA 1.5.4 used UUID.nameUUIDFromBytes("aea:mob_ai/<power>"); 1.21 modifiers are keyed by id.
            this.modifierId = ResourceLocation.fromNamespaceAndPath("aea", "mob_ai/" + powerId.getNamespace() + "/" + powerId.getPath());
            this.ownSelectors = ownSelectors;
            this.goals = goals;
            this.targets = targets;
        }

        static Applied apply(Mob mob, MobAiState state, ResourceLocation powerId, Config cfg) {
            GoalSelector vanillaGoals = mob.goalSelector;
            GoalSelector vanillaTargets = mob.targetSelector;
            if (state.brainMob == null || state.applied.isEmpty()) {
                state.brainMob = onlyAea(vanillaGoals, state) && onlyAea(vanillaTargets, state);
            }
            Applied a;
            if (state.brainMob) {
                // Brain-driven mob (villager, piglin, axolotl...): its vanilla selectors are unused, so AEA runs its own.
                a = new Applied(cfg, powerId, true, new GoalSelector(mob.level().getProfilerSupplier()), new GoalSelector(mob.level().getProfilerSupplier()));
            } else {
                a = new Applied(cfg, powerId, false, vanillaGoals, vanillaTargets);
                a.removeVanilla(state);
            }
            for (GoalSpecs.Spec spec : cfg.goals()) {
                Goal g = spec.create(mob);
                if (g != null) {
                    a.goals.addGoal(spec.priority(), g);
                    a.added.add(g);
                    state.aeaGoals.add(g);
                }
            }
            for (GoalSpecs.Spec spec : cfg.targets()) {
                Goal g = spec.create(mob);
                if (g != null) {
                    a.targets.addGoal(spec.priority(), g);
                    a.added.add(g);
                    state.aeaGoals.add(g);
                }
            }
            a.applyAttributes(mob, state);
            return a;
        }

        private static boolean onlyAea(GoalSelector selector, MobAiState state) {
            for (WrappedGoal w : selector.getAvailableGoals()) {
                if (!state.aeaGoals.contains(w.getGoal())) return false;
            }
            return true;
        }

        void tick(Mob mob) {
            MobAiState state = state(mob);
            if (!ownSelectors && cfg.reapplyInterval() > 0 && !cfg.remove().isEmpty() && mob.tickCount % cfg.reapplyInterval() == 0) {
                removeVanilla(state);
            }
            if (ownSelectors && !mob.isNoAi() && mob.isAlive()) {
                if (mob.tickCount % 2 == 0) {
                    targets.tick();
                    goals.tick();
                } else {
                    targets.tickRunningGoals(false);
                    goals.tickRunningGoals(false);
                }
            }
            if (cfg.removesBrain() && !brainStopped && mob.level() instanceof ServerLevel level) {
                brainStopped = true;
                try {
                    stopBrain(mob, level);
                } catch (RuntimeException ignored) {
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void stopBrain(Mob mob, ServerLevel level) {
            ((Brain) mob.getBrain()).stopAll(level, mob);
        }

        private void removeVanilla(MobAiState state) {
            if (cfg.remove().isEmpty()) return;
            strip(goals, removedGoals, true, state);
            strip(targets, removedTargets, false, state);
        }

        private void strip(GoalSelector selector, List<Pair<Integer, Goal>> removed, boolean goalSide, MobAiState state) {
            List<WrappedGoal> matched = null;
            for (WrappedGoal w : selector.getAvailableGoals()) {
                Goal g = w.getGoal();
                if (state.aeaGoals.contains(g)) continue;
                for (String category : cfg.remove()) {
                    if (goalSide ? GoalCategories.matchesGoal(category, g) : GoalCategories.matchesTarget(category, g)) {
                        if (matched == null) matched = new ArrayList<>();
                        matched.add(w);
                        break;
                    }
                }
            }
            if (matched == null) return;
            for (WrappedGoal w : matched) {
                Goal g = w.getGoal();
                boolean known = false;
                for (Pair<Integer, Goal> p : removed) {
                    if (p.getSecond() == g) {
                        known = true;
                        break;
                    }
                }
                if (!known) removed.add(Pair.of(w.getPriority(), g));
                selector.removeGoal(g);
            }
        }

        private void applyAttributes(Mob mob, MobAiState state) {
            if (!cfg.needsAttackDamage()) return;
            AttributeMap map = mob.getAttributes();
            Map<Holder<Attribute>, AttributeInstance> custom = ((AttributeMapAccessor) map).aea$attributes();
            if (!map.hasAttribute(Attributes.ATTACK_DAMAGE)) {
                AttributeInstance inst = new AttributeInstance(Attributes.ATTACK_DAMAGE, i -> {});
                inst.setBaseValue(2.0D);
                custom.put(Attributes.ATTACK_DAMAGE, inst);
                state.injectedAttackDamage = true;
            }
            if (!map.hasAttribute(Attributes.ATTACK_KNOCKBACK)) {
                AttributeInstance inst = new AttributeInstance(Attributes.ATTACK_KNOCKBACK, i -> {});
                inst.setBaseValue(0.0D);
                custom.put(Attributes.ATTACK_KNOCKBACK, inst);
            }
            if (cfg.attackDamage().isPresent()) {
                AttributeInstance inst = map.getInstance(Attributes.ATTACK_DAMAGE);
                if (inst != null) {
                    inst.removeModifier(modifierId);
                    inst.addTransientModifier(new AttributeModifier(modifierId, cfg.attackDamage().get() - inst.getValue(),
                        AttributeModifier.Operation.ADD_VALUE));
                    damageModifier = true;
                }
            }
        }

        void undo(Mob mob, MobAiState state) {
            for (Goal g : added) {
                goals.removeGoal(g);
                targets.removeGoal(g);
                state.aeaGoals.remove(g);
            }
            added.clear();
            if (!ownSelectors) {
                restore(goals, removedGoals, true);
                restore(targets, removedTargets, false);
            }
            removedGoals.clear();
            removedTargets.clear();
            if (damageModifier) {
                AttributeInstance inst = mob.getAttributes().getInstance(Attributes.ATTACK_DAMAGE);
                if (inst != null) inst.removeModifier(modifierId);
                damageModifier = false;
            }
            if (state.applied.size() <= 1) state.brainMob = null;
        }

        /** Re-adds stripped goals unless an equivalent (same class, or another melee/ranged attack) is already there. */
        private static void restore(GoalSelector selector, List<Pair<Integer, Goal>> removed, boolean goalSide) {
            List<Goal> present = new ArrayList<>();
            for (WrappedGoal w : selector.getAvailableGoals()) present.add(w.getGoal());
            for (Pair<Integer, Goal> p : removed) {
                Goal g = p.getSecond();
                boolean dup = false;
                boolean attack = goalSide && (GoalCategories.matchesGoal("melee", g) || GoalCategories.matchesGoal("ranged", g));
                for (Goal other : present) {
                    if (other == g || other.getClass() == g.getClass()
                        || (attack && (GoalCategories.matchesGoal("melee", other) || GoalCategories.matchesGoal("ranged", other)))) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) selector.addGoal(p.getFirst(), g);
            }
        }
    }
}
