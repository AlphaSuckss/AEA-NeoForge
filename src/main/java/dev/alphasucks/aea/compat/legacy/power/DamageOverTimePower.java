package dev.alphasucks.aea.compat.legacy.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.builtin.entity.PowerActiveCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerContainerImpl;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Two aux stamps per power: when the current spell in the condition began, and when the entity last left it
     * ({@code -1} while inside). They only change on entering or leaving, so the container isn't dirtied every tick.
     * 20 ticks outside resets the spell, as in Apoli 2.9.
     */
    @Override
    public void tick(ResourceLocation id, Config cfg, PowerContainer container) {
        LivingEntity entity = container.owner();
        if (entity == null || entity.level().isClientSide || !(container instanceof PowerContainerImpl impl)) return;
        int now = (int) entity.level().getGameTime();
        int[] st = impl.getAuxInts(id);
        int start = st != null && st.length == 2 ? st[0] : now, left = st != null && st.length == 2 ? st[1] : now - 20;
        boolean active = PowerActiveCondition.isActive(container, id, EntityCtx.of(entity, entity.level()));
        if (!active) {
            if (left == -1) impl.setAuxInts(id, new int[]{start, now});
            return;
        }
        if (left != -1) {
            // Back in the condition: resume the spell after a short gap, start a new one after a long one.
            start = now - left >= 20 ? now : start + (now - left);
            impl.setAuxInts(id, new int[]{start, -1});
        }
        int in = now - start;
        int onset = cfg.onsetDelay().orElse(cfg.interval());
        if (in < onset) return;
        int begin = onset + (int) (Math.pow(protection(entity, cfg) * 2, 1.3) * cfg.protectionEffectiveness()) * 20;
        if (in - begin >= 0 && (in - begin) % Math.max(1, cfg.interval()) == 0) {
            float amount = entity.level().getDifficulty() == Difficulty.EASY ? cfg.damageEasy().orElse(cfg.damage()) : cfg.damage();
            Optional<Holder.Reference<DamageType>> holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, cfg.damageType()));
            entity.hurt(holder.isPresent() ? new DamageSource(holder.get()) : entity.damageSources().generic(), amount);
        }
    }

    @Override
    public void onRemoved(ResourceLocation id, Config cfg, PowerContainer container, ResourceLocation source) {
        if (!container.hasPower(id) && container instanceof PowerContainerImpl impl) impl.removeAux(id);
    }

    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static RegistryAccess cachedAccess;
    private static final Map<ResourceLocation, Optional<Holder.Reference<Enchantment>>> ENCHANTMENTS = new HashMap<>();

    /** The enchantment's holder, looked up once per registry access (once per world). */
    private static Optional<Holder.Reference<Enchantment>> enchantment(RegistryAccess access, ResourceLocation id) {
        if (access != cachedAccess) {
            ENCHANTMENTS.clear();
            cachedAccess = access;
        }
        Optional<Holder.Reference<Enchantment>> ench = ENCHANTMENTS.get(id);
        if (ench == null) {
            ench = access.registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceKey.create(Registries.ENCHANTMENT, id));
            ENCHANTMENTS.put(id, ench);
        }
        return ench;
    }

    /** Total levels of the protection enchantment on the armour it applies to, plus one per enchanted piece. */
    private static int protection(LivingEntity entity, Config cfg) {
        if (cfg.protectionEnchantment().isEmpty()) return 0;
        Optional<Holder.Reference<Enchantment>> ench = enchantment(entity.level().registryAccess(), cfg.protectionEnchantment().get());
        if (ench.isEmpty()) return 0;
        List<EquipmentSlotGroup> groups = ench.get().value().definition().slots();
        int total = 0, items = 0;
        for (EquipmentSlot slot : SLOTS) {
            boolean applies = false;
            for (int i = 0; i < groups.size() && !applies; i++) applies = groups.get(i).test(slot);
            if (!applies) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(ench.get(), stack);
            total += lvl;
            if (lvl > 0) items++;
        }
        return total + items;
    }
}
