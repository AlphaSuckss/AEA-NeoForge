package dev.alphasucks.aea.compat.sync.power;

import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code sync:modify_enchantment_damage_dealt} and {@code sync:modify_enchantment_damage_taken}: extra damage scaled by
 * the attacker's level of an enchantment. Applied in AEAHooks.
 */
public final class ModifyEnchantmentDamagePower extends PowerType<ModifyEnchantmentDamagePower.Config> {
    public static final ResourceLocation DEALT = ResourceLocation.fromNamespaceAndPath("sync", "modify_enchantment_damage_dealt");
    public static final ResourceLocation TAKEN = ResourceLocation.fromNamespaceAndPath("sync", "modify_enchantment_damage_taken");

    public record Config(ResourceKey<Enchantment> enchantment, float baseValue, Optional<DamageCondition> damageCondition,
                         Optional<EntityCondition> targetCondition, Optional<BiEntityCondition> bientityCondition,
                         List<AttributeModifier> modifiers, Optional<BiEntityAction> bientityAction) {}

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.ENCHANTMENT).fieldOf("enchantment").forGetter(Config::enchantment),
            Codec.FLOAT.optionalFieldOf("base_value", 0f).forGetter(Config::baseValue),
            LoggedOptionalField.strict("damage_condition", DamageCondition.CODEC).forGetter(Config::damageCondition),
            LoggedOptionalField.strict("target_condition", EntityCondition.CODEC).forGetter(Config::targetCondition),
            LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(Config::bientityCondition),
            AttributeModifier.CODEC.optionalFieldOf("modifier").forGetter(c -> Optional.empty()),
            AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Config::modifiers),
            LoggedOptionalField.of("bientity_action", BiEntityAction.CODEC).forGetter(Config::bientityAction)
        ).apply(i, (e, b, d, t, be, single, list, a) -> {
            List<AttributeModifier> all = new ArrayList<>(list);
            single.ifPresent(all::add);
            return new Config(e, b, d, t, be, AttributeModifierHelper.ensureSorted(all), a);
        }));
    }

    /** Sync semantics: bonus = base_value, then the modifiers are applied once per level above 1. */
    public static float bonus(Config c, LivingEntity wielder) {
        Optional<Holder.Reference<Enchantment>> h = wielder.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(c.enchantment);
        if (h.isEmpty()) return 0;
        int level = EnchantmentHelper.getEnchantmentLevel(h.get(), wielder);
        if (level <= 0) return 0;
        float bonus = c.baseValue;
        for (int i = 0; i < level - 1; i++) bonus = AttributeModifierHelper.apply(bonus, c.modifiers, wielder);
        return bonus;
    }
}
