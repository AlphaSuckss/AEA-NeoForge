package dev.alphasucks.aea.compat.spell_engine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * {@code aea:modify_spell}: changes the cast time (in seconds), cooldown (in seconds) and range (in blocks) of the
 * holder's Spell Engine spells, with Apoli modifiers. Applied on the values Spell Engine works out, after spell haste
 * and its own modifier spells ({@code SpellParametersMixin}). Resolved on the server so the client's cast bar and
 * cooldowns match.
 */
public final class ModifySpellPower extends PowerType<ModifySpellPower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "modify_spell");
    public static final PowerTypeUsage.Handle HELD = PowerTypeUsage.handle(ID);

    public record Cfg(SpellFilter filter, List<AttributeModifier> castTime, List<AttributeModifier> cooldown, List<AttributeModifier> range) {
        /** Sorted once at load, so a single matching power applies its list as is. */
        public Cfg {
            castTime = AttributeModifierHelper.ensureSorted(castTime);
            cooldown = AttributeModifierHelper.ensureSorted(cooldown);
            range = AttributeModifierHelper.ensureSorted(range);
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        SpellFilter.FIELDS.forGetter(Cfg::filter),
        AttributeModifier.LIST_OR_SINGLE.optionalFieldOf("cast_time", List.of()).forGetter(Cfg::castTime),
        AttributeModifier.LIST_OR_SINGLE.optionalFieldOf("cooldown", List.of()).forGetter(Cfg::cooldown),
        AttributeModifier.LIST_OR_SINGLE.optionalFieldOf("range", List.of()).forGetter(Cfg::range)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    @Override
    public boolean resolvesForClient() {
        return true;
    }

    // These run whenever Spell Engine asks for a spell's numbers, which on the client can be every frame: they bail
    // out before any lookup unless the caster holds the type, and don't allocate unless several powers match.

    public static float castTime(LivingEntity caster, Spell spell, float value) {
        PowerContainer container = holder(caster);
        if (container == null || spell == null) return value;
        return apply(caster, container, SpellRegistry.from(caster.level()).wrapAsHolder(spell), value, CAST_TIME);
    }

    public static float cooldown(LivingEntity caster, Holder<Spell> spell, float value) {
        PowerContainer container = holder(caster);
        return container == null ? value : apply(caster, container, spell, value, COOLDOWN);
    }

    public static float range(LivingEntity caster, Holder<Spell> spell, float value) {
        PowerContainer container = holder(caster);
        return container == null ? value : apply(caster, container, spell, value, RANGE);
    }

    private static final Function<Cfg, List<AttributeModifier>> CAST_TIME = Cfg::castTime, COOLDOWN = Cfg::cooldown, RANGE = Cfg::range;

    private static PowerContainer holder(LivingEntity caster) {
        if (caster == null || (!caster.level().isClientSide() && !HELD.isHeld())) return null;
        return ActiveConfigs.holder(caster, ID);
    }

    private static float apply(LivingEntity caster, PowerContainer container, Holder<Spell> spell, float value, Function<Cfg, List<AttributeModifier>> field) {
        List<ResourceLocation> powers = container.powersOfType(ID);
        List<AttributeModifier> mods = null;
        boolean merged = false;
        for (int i = 0; i < powers.size(); i++) {
            Cfg cfg = ActiveConfigs.config(caster, container, powers.get(i), Cfg.class);
            if (cfg == null) continue;
            List<AttributeModifier> m = field.apply(cfg);
            if (m.isEmpty() || (!cfg.filter().any() && !cfg.filter().test(spell))) continue;
            if (mods == null) {
                mods = m;
            } else {
                if (!merged) mods = new ArrayList<>(mods);
                merged = true;
                mods.addAll(m);
            }
        }
        if (mods == null) return value;
        if (merged) mods = AttributeModifierHelper.ensureSorted(mods);
        return (float) Math.max(0, AttributeModifierHelper.apply((double) value, mods, caster));
    }
}
