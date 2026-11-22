package dev.alphasucks.aea.compat.spellengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.spellengine.GrantSpellPower;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code aea:grant_spell} with {@code always}: Spell Engine only builds the active spell list from the spell item in
 * the main hand. This adds the granted active spells to that list on both sides, whatever is held; when nothing held
 * carries spells, the granted container becomes the active one.
 */
@Mixin(value = SpellContainerSource.class, remap = false)
public abstract class SpellContainerSourceMixin {
    @WrapOperation(method = "update", at = @At(value = "NEW", target = "Lnet/spell_engine/internals/container/SpellContainerSource$Result;"))
    private static SpellContainerSource.Result aea$grantAlways(SpellContainer active, List<Holder<Spell>> actives, List<Holder<Spell>> passives,
                                                               List<Holder<Spell>> modifiers, List<SpellContainerSource.SourcedContainer> sources,
                                                               Operation<SpellContainerSource.Result> original, @Local(argsOnly = true) Player player) {
        SpellContainer granted = ((SpellContainerSource.Owner) player).serverSideSpellContainers().get(GrantSpellPower.ALWAYS);
        if (granted == null || granted.spell_ids() == null || granted.spell_ids().isEmpty()) {
            return original.call(active, actives, passives, modifiers, sources);
        }
        Registry<Spell> registry = SpellRegistry.from(player.level());
        List<Holder<Spell>> merged = actives == null ? new ArrayList<>() : new ArrayList<>(actives);
        for (String raw : granted.spell_ids()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            Holder<Spell> spell = id == null || registry == null ? null : registry.getHolder(id).orElse(null);
            if (spell == null || spell.value().type != Spell.Type.ACTIVE || merged.contains(spell)) continue;
            merged.add(spell);
        }
        boolean noActive = active == null || active == SpellContainer.EMPTY || !active.isResolver();
        return original.call(noActive ? granted : active, merged, passives, modifiers, sources);
    }
}
