package dev.alphasucks.aea.compat.legacy.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.builtin.entity.PowerActiveCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Optional;

/**
 * {@code aea:legacy_damage_over_time}: Apoli 2.9.2's damage_over_time, kept for packs that use protection_enchantment
 * (Overgrown dropped the onset scaling). Damage starts after onset_delay + (2 * protection)^1.3 * effectiveness seconds
 * in the condition.
 */
public final class DamageOverTimePower extends PowerType<DamageOverTimePower.Config> {
    private static final ResourceKey<DamageType> DAMAGE_OVER_TIME = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("apoli", "damage_over_time"));

    public record Config(int interval, Optional<Integer> onsetDelay, float damage, Optional<Float> damageEasy,
                         ResourceLocation damageType, Optional<ResourceLocation> protectionEnchantment, float protectionEffectiveness) {}

    private static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.INT.optionalFieldOf("interval", 20).forGetter(Config::interval),
        Codec.INT.optionalFieldOf("onset_delay").forGetter(Config::onsetDelay),
        Codec.FLOAT.fieldOf("damage").forGetter(Config::damage),
        Codec.FLOAT.optionalFieldOf("damage_easy").forGetter(Config::damageEasy),
        ResourceLocation.CODEC.optionalFieldOf("damage_type", DAMAGE_OVER_TIME.location()).forGetter(Config::damageType),
        ResourceLocation.CODEC.optionalFieldOf("protection_enchantment").forGetter(Config::protectionEnchantment),
        Codec.FLOAT.optionalFieldOf("protection_effectiveness", 1.0f).forGetter(Config::protectionEffectiveness)
    ).apply(i, Config::new));

    @Override
    public MapCodec<Config> configCodec() {
        return CODEC;
    }

    @Override
    public void tick(ResourceLocation id, Config cfg, PowerContainer container) {
        LivingEntity entity = container.owner();
        if (entity == null || entity.level().isClientSide) return;
        // Ticks spent in and out of the condition, per power, kept in persistent data so they survive a relog.
        CompoundTag all = entity.getPersistentData().getCompound("aea_dot");
        String key = id.toString();
        CompoundTag st = all.getCompound(key);
        int in = st.getInt("in"), out = st.getInt("out");
        boolean active = PowerActiveCondition.isActive(container, id, EntityCtx.of(entity, entity.level()));
        if (active) {
            out = 0;
            int begin = cfg.onsetDelay().orElse(cfg.interval()) + (int) (Math.pow(protection(entity, cfg) * 2, 1.3) * cfg.protectionEffectiveness()) * 20;
            if (in - begin >= 0 && (in - begin) % Math.max(1, cfg.interval()) == 0) {
                float amount = entity.level().getDifficulty() == Difficulty.EASY ? cfg.damageEasy().orElse(cfg.damage()) : cfg.damage();
                ResourceKey<DamageType> dmgKey = ResourceKey.create(Registries.DAMAGE_TYPE, cfg.damageType());
                Optional<Holder.Reference<DamageType>> holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(dmgKey);
                entity.hurt(holder.isPresent() ? new DamageSource(holder.get()) : entity.damageSources().generic(), amount);
            }
            in++;
        } else if (out >= 20) {
            in = 0;
        } else {
            out++;
        }
        st.putInt("in", in);
        st.putInt("out", out);
        all.put(key, st);
        entity.getPersistentData().put("aea_dot", all);
    }

    @Override
    public void onRemoved(ResourceLocation id, Config cfg, PowerContainer container, ResourceLocation source) {
        LivingEntity entity = container.owner();
        if (entity != null) entity.getPersistentData().getCompound("aea_dot").remove(id.toString());
    }

    /** Total levels of the protection enchantment on the armour it applies to, plus one per enchanted piece. */
    private static int protection(LivingEntity entity, Config cfg) {
        if (cfg.protectionEnchantment().isEmpty()) return 0;
        Optional<Holder.Reference<Enchantment>> ench = entity.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
            .getHolder(ResourceKey.create(Registries.ENCHANTMENT, cfg.protectionEnchantment().get()));
        if (ench.isEmpty()) return 0;
        int total = 0, items = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean applies = ench.get().value().definition().slots().stream().anyMatch(g -> g.test(slot));
            if (!applies) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(ench.get(), stack);
            total += lvl;
            if (lvl > 0) items++;
        }
        return total + items;
    }
}
