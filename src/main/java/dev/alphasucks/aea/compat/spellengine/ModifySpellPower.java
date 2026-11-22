package dev.alphasucks.aea.compat.spellengine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
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

    public record Cfg(SpellFilter filter, List<AttributeModifier> castTime, List<AttributeModifier> cooldown, List<AttributeModifier> range) {}

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

    public static float castTime(LivingEntity caster, Spell spell, float value) {
        if (caster == null || spell == null || !relevant(caster)) return value;
        Holder<Spell> holder = SpellRegistry.from(caster.level()).wrapAsHolder(spell);
        return apply(caster, holder, value, Cfg::castTime);
    }

    public static float cooldown(LivingEntity caster, Holder<Spell> spell, float value) {
        return caster == null || !relevant(caster) ? value : apply(caster, spell, value, Cfg::cooldown);
    }

    public static float range(LivingEntity caster, Holder<Spell> spell, float value) {
        return caster == null || !relevant(caster) ? value : apply(caster, spell, value, Cfg::range);
    }

    private static boolean relevant(LivingEntity caster) {
        return caster.level().isClientSide() || HELD.isHeld();
    }

    private static float apply(LivingEntity caster, Holder<Spell> spell, float value, Function<Cfg, List<AttributeModifier>> field) {
        List<Cfg> active = ActiveConfigs.of(caster, ID, Cfg.class);
        if (active.isEmpty()) return value;
        List<AttributeModifier> mods = null;
        for (int i = 0; i < active.size(); i++) {
            Cfg cfg = active.get(i);
            List<AttributeModifier> m = field.apply(cfg);
            if (m.isEmpty() || (!cfg.filter().any() && !cfg.filter().test(spell))) continue;
            if (mods == null) mods = new ArrayList<>(m);
            else mods.addAll(m);
        }
        if (mods == null) return value;
        return (float) Math.max(0, AttributeModifierHelper.apply((double) value, AttributeModifierHelper.ensureSorted(mods), caster));
    }
}
