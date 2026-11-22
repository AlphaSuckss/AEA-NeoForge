package dev.alphasucks.aea.compat.spellengine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.alphasucks.aea.compat.Codecs;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

import java.util.List;
import java.util.Optional;

/**
 * Everything AEA adds for Spell Engine. Only called when Spell Engine is installed; nothing else in AEA refers to
 * this package (the mixin config has its own plugin check).
 */
public final class SpellEngineTypes {
    private SpellEngineTypes() {}

    public static final ResourceLocation PREVENT = ResourceLocation.fromNamespaceAndPath("aea", "prevent_spell_cast");
    public static final ResourceLocation ON_CAST = ResourceLocation.fromNamespaceAndPath("aea", "action_on_spell_cast");
    private static final PowerTypeUsage.Handle PREVENT_HELD = PowerTypeUsage.handle(PREVENT), ON_CAST_HELD = PowerTypeUsage.handle(ON_CAST);

    public static void register() {
        Reg.power(GrantSpellPower.ID.toString(), new GrantSpellPower());
        GrantSpellPower.register();
        Reg.power(ModifySpellPower.ID.toString(), new ModifySpellPower());
        Reg.power(PREVENT.toString(), new PreventSpellCast());
        Reg.power(ON_CAST.toString(), new OnSpellCast());

        SpellEvents.CASTING_ATTEMPT.PRE.register(args -> PreventSpellCast.attempt(args.caster(), args.spell()));
        SpellEvents.SPELL_CAST.register(args -> OnSpellCast.cast(args.caster(), args.spell(), args.targets()));

        Reg.action(ActionTypes.ENTITY, "aea:cast_spell", new CastSpell.Self());
        Reg.action(ActionTypes.BI_ENTITY, "aea:cast_spell", new CastSpell.AtTarget());

        Reg.condition(ConditionTypes.ENTITY, "aea:casting_spell", new EntitySpellCondition(Check.CASTING));
        Reg.condition(ConditionTypes.ENTITY, "aea:spell_on_cooldown", new EntitySpellCondition(Check.COOLDOWN));
        Reg.condition(ConditionTypes.ENTITY, "aea:knows_spell", new EntitySpellCondition(Check.KNOWS));
        Reg.condition(ConditionTypes.ITEM, "aea:has_spell", new ItemSpellCondition());
        Reg.condition(ConditionTypes.DAMAGE, "aea:spell_damage", new SpellDamageCondition());
        AEA.LOGGER.info("[AEA] Spell Engine found: spell powers, conditions and actions are on.");
    }

    static Holder<Spell> spell(Level level, ResourceLocation id) {
        Registry<Spell> registry = SpellRegistry.from(level);
        return registry == null ? null : registry.getHolder(ResourceKey.create(SpellRegistry.KEY, id)).orElse(null);
    }

    // ---- power: aea:prevent_spell_cast ----

    /** {@code aea:prevent_spell_cast}: the holder can't start casting matching spells. Passive spells still trigger. */
    public record PreventCfg(SpellFilter filter, Optional<Component> message) {}

    private static final class PreventSpellCast extends PowerType<PreventCfg> {
        private static final MapCodec<PreventCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpellFilter.FIELDS.forGetter(PreventCfg::filter),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(PreventCfg::message)
        ).apply(i, PreventCfg::new));

        @Override public MapCodec<PreventCfg> configCodec() { return CODEC; }
        @Override public boolean resolvesForClient() { return true; }

        /** Null lets Spell Engine decide; a failed attempt stops the cast on that side. */
        static SpellCast.Attempt attempt(Player caster, Holder<Spell> spell) {
            if (caster == null || (!caster.level().isClientSide() && !PREVENT_HELD.isHeld())) return null;
            List<PreventCfg> active = ActiveConfigs.of(caster, PREVENT, PreventCfg.class);
            for (int i = 0; i < active.size(); i++) {
                PreventCfg cfg = active.get(i);
                if (!cfg.filter().any() && !cfg.filter().test(spell)) continue;
                cfg.message().ifPresent(m -> caster.displayClientMessage(m, true));
                return SpellCast.Attempt.none();
            }
            return null;
        }
    }

    // ---- power: aea:action_on_spell_cast ----

    /**
     * {@code aea:action_on_spell_cast}: when the holder casts a matching spell, runs {@code entity_action} on the
     * holder and {@code bientity_action} on each target it hit (holder as actor), filtered by {@code bientity_condition}.
     */
    public record OnCastCfg(SpellFilter filter, Optional<EntityAction> entityAction, Optional<BiEntityAction> bientityAction,
                            Optional<BiEntityCondition> bientityCondition) {}

    private static final class OnSpellCast extends PowerType<OnCastCfg> {
        private static final MapCodec<OnCastCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpellFilter.FIELDS.forGetter(OnCastCfg::filter),
            LoggedOptionalField.strict("entity_action", EntityAction.CODEC).forGetter(OnCastCfg::entityAction),
            LoggedOptionalField.strict("bientity_action", BiEntityAction.CODEC).forGetter(OnCastCfg::bientityAction),
            LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(OnCastCfg::bientityCondition)
        ).apply(i, OnCastCfg::new));

        @Override public MapCodec<OnCastCfg> configCodec() { return CODEC; }

        static void cast(Player caster, Holder<Spell> spell, List<Entity> targets) {
            if (caster == null || caster.level().isClientSide() || !ON_CAST_HELD.isHeld()) return;
            List<OnCastCfg> active = PowerLookup.active(caster, ON_CAST, OnCastCfg.class);
            for (int i = 0; i < active.size(); i++) {
                OnCastCfg cfg = active.get(i);
                if (!cfg.filter().any() && !cfg.filter().test(spell)) continue;
                cfg.entityAction().ifPresent(a -> a.run(EntityCtx.of(caster, caster.level())));
                if (cfg.bientityAction().isEmpty() || targets == null) continue;
                for (Entity target : List.copyOf(targets)) {
                    BiEntityCtx ctx = BiEntityCtx.of(caster, target, caster.level());
                    if (cfg.bientityCondition().isEmpty() || cfg.bientityCondition().get().test(ctx)) cfg.bientityAction().get().run(ctx);
                }
            }
        }
    }

    // ---- action: aea:cast_spell ----

    /**
     * {@code aea:cast_spell}: the entity (a player) casts a spell at once, with no cast time. As an entity action it
     * aims the way Spell Engine would (where the caster looks); as a bi-entity action it targets the target. With
     * {@code use_cost} false (the default) it costs nothing and starts no cooldown.
     */
    public record CastCfg(ResourceLocation spell, boolean useCost) {}

    private static final MapCodec<CastCfg> CAST_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.fieldOf("spell").forGetter(CastCfg::spell),
        Codec.BOOL.optionalFieldOf("use_cost", false).forGetter(CastCfg::useCost)
    ).apply(i, CastCfg::new));

    private static final class CastSpell {
        static void cast(CastCfg cfg, Entity caster, Entity target) {
            if (!(caster instanceof Player player) || player.level().isClientSide()) return;
            Holder<Spell> spell = spell(player.level(), cfg.spell());
            if (spell == null) {
                AEA.LOGGER.warn("[AEA] aea:cast_spell: no spell {}", cfg.spell());
                return;
            }
            SpellTarget.SearchResult targets = target != null ? SpellTarget.SearchResult.of(target)
                : SpellTarget.findTargets(player, spell, SpellTarget.SearchResult.empty(), true);
            // Spell Engine only performs spells the player has from some source; lend one for the cast if needed.
            SpellContainerSource.Owner owner = (SpellContainerSource.Owner) player;
            SpellContainerSource.Result before = owner.getSpellContainers();
            boolean lent = SpellContainerSource.getFirstSourceOfSpell(cfg.spell(), player) == null;
            if (lent) {
                SpellContainerSource.Result r = before == null ? SpellContainerSource.Result.EMPTY : before;
                List<SpellContainerSource.SourcedContainer> sources = new java.util.ArrayList<>(r.sources() == null ? List.of() : r.sources());
                sources.add(new SpellContainerSource.SourcedContainer("aea:cast_spell", null,
                    new SpellContainer(SpellContainer.ContentType.ANY, "", "", 1, List.of(cfg.spell().toString()))));
                owner.setSpellContainers(new SpellContainerSource.Result(r.activeContainer(), r.actives(), r.passives(), r.modifiers(), sources));
            }
            try {
                SpellExecution.performSpell(player.level(), player, spell, targets,
                    cfg.useCost() ? SpellCast.Action.RELEASE : SpellCast.Action.TRIGGER, 1F);
            } finally {
                if (lent) owner.setSpellContainers(before);
            }
        }

        static final class Self implements ActionType<EntityCtx, CastCfg> {
            @Override public MapCodec<CastCfg> codec() { return CAST_CODEC; }
            @Override public void run(CastCfg cfg, EntityCtx ctx) { cast(cfg, ctx.raw(), null); }
        }

        static final class AtTarget implements ActionType<BiEntityCtx, CastCfg> {
            @Override public MapCodec<CastCfg> codec() { return CAST_CODEC; }
            @Override public void run(CastCfg cfg, BiEntityCtx ctx) {
                if (ctx.target() != null) cast(cfg, ctx.actor(), ctx.target());
            }
        }
    }

    // ---- conditions ----

    private enum Check { CASTING, COOLDOWN, KNOWS }

    /**
     * {@code aea:casting_spell}: the player is casting a matching spell. {@code aea:spell_on_cooldown}: a matching spell
     * is on cooldown. {@code aea:knows_spell}: the player can use a matching spell from any source (equipment, books,
     * {@code aea:grant_spell}). With no {@code spell} or {@code school}, any spell counts.
     */
    private static final class EntitySpellCondition implements ConditionType<EntityCtx, SpellFilter> {
        private final Check check;

        EntitySpellCondition(Check check) {
            this.check = check;
        }

        @Override public MapCodec<SpellFilter> codec() { return SpellFilter.FIELDS; }

        @Override
        public boolean test(SpellFilter filter, EntityCtx ctx) {
            if (!(ctx.raw() instanceof Player player) || !(player instanceof SpellCasterEntity caster)) return false;
            switch (check) {
                case CASTING -> {
                    SpellCast.Process process = caster.getSpellCastProcess();
                    return process != null && filter.test(process.spell());
                }
                case COOLDOWN -> {
                    for (ResourceLocation id : caster.getCooldownManager().spellsOnCooldown()) {
                        Holder<Spell> spell = spell(player.level(), id);
                        if (spell != null && caster.getCooldownManager().isCoolingDown(spell) && filter.test(spell)) return true;
                    }
                    return false;
                }
                default -> {
                    SpellContainerSource.Result spells = SpellContainerSource.getSpellsOf(player);
                    if (spells == null) return false;
                    return anyMatch(spells.actives(), filter) || anyMatch(spells.passives(), filter);
                }
            }
        }

        private static boolean anyMatch(List<Holder<Spell>> spells, SpellFilter filter) {
            if (spells == null) return false;
            for (int i = 0; i < spells.size(); i++) {
                if (filter.test(spells.get(i))) return true;
            }
            return false;
        }
    }

    /** {@code aea:has_spell}: the item carries a matching spell (a spell book, scroll or spell weapon). */
    private static final class ItemSpellCondition implements ConditionType<ItemCtx, SpellFilter> {
        @Override public MapCodec<SpellFilter> codec() { return SpellFilter.FIELDS; }

        @Override
        public boolean test(SpellFilter filter, ItemCtx ctx) {
            if (ctx.stack().isEmpty() || ctx.level() == null) return false;
            SpellContainer container = SpellContainerHelper.containerFromItemStack(ctx.stack());
            if (container == null || container.spell_ids() == null) return false;
            for (String raw : container.spell_ids()) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) continue;
                if (filter.any()) return true;
                Holder<Spell> spell = spell(ctx.level(), id);
                if (spell != null && filter.test(spell)) return true;
            }
            return false;
        }
    }

    /** {@code aea:spell_damage}: the damage is spell damage (a Spell Power school's damage type), optionally of certain schools. */
    public record SpellDamageCfg(List<ResourceLocation> schools) {}

    private static final class SpellDamageCondition implements ConditionType<DamageCtx, SpellDamageCfg> {
        private static final MapCodec<SpellDamageCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codecs.singleOrList(ResourceLocation.CODEC).optionalFieldOf("school", List.of()).forGetter(SpellDamageCfg::schools)
        ).apply(i, SpellDamageCfg::new));

        @Override public MapCodec<SpellDamageCfg> codec() { return CODEC; }

        @Override
        public boolean test(SpellDamageCfg cfg, DamageCtx ctx) {
            if (ctx.source() == null) return false;
            for (SpellSchool school : SpellSchools.all()) {
                if (school.damageType == null || (!cfg.schools().isEmpty() && !cfg.schools().contains(school.id))) continue;
                if (ctx.source().is(school.damageType)) return true;
            }
            return false;
        }
    }
}
