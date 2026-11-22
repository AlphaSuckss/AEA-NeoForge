package dev.alphasucks.aea.compat.spellengine.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.spellengine.ModifySpellPower;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code aea:modify_spell}. Each target is the overload every other one calls. */
@Mixin(value = SpellParameters.class, remap = false)
public abstract class SpellParametersMixin {
    @ModifyReturnValue(method = "getCastDuration(Lnet/minecraft/world/entity/LivingEntity;Lnet/spell_engine/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)F", at = @At("RETURN"))
    private static float aea$castTime(float original, @Local(argsOnly = true) LivingEntity caster, @Local(argsOnly = true) Spell spell) {
        return ModifySpellPower.castTime(caster, spell, original);
    }

    @ModifyReturnValue(method = "getCooldownDuration(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemStack;)F", at = @At("RETURN"))
    private static float aea$cooldown(float original, @Local(argsOnly = true) LivingEntity caster, @Local(argsOnly = true) Holder<Spell> spell) {
        return ModifySpellPower.cooldown(caster, spell, original);
    }

    @ModifyReturnValue(method = "getRangeCurved", at = @At("RETURN"))
    private static float aea$range(float original, @Local(argsOnly = true) LivingEntity caster, @Local(argsOnly = true) Holder<Spell> spell) {
        return ModifySpellPower.range(caster, spell, original);
    }
}
