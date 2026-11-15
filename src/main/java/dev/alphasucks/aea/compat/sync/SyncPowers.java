package dev.alphasucks.aea.compat.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sync 1.11.8 power types that Overgrown's Apoli does not ship. Hooks live in {@link SyncHooks} and the mixins. */
public final class SyncPowers {
    private SyncPowers() {}

    public static final ResourceLocation MOBS_IGNORE = ResourceLocation.fromNamespaceAndPath("sync", "mobs_ignore");
    public static final ResourceLocation FLIP_MODEL = ResourceLocation.fromNamespaceAndPath("sync", "flip_model");
    public static final ResourceLocation TEXTURE_OVERLAY = ResourceLocation.fromNamespaceAndPath("sync", "entity_texture_overlay");
    public static final ResourceLocation PREVENT_CREATIVE_FLIGHT = ResourceLocation.fromNamespaceAndPath("sync", "prevent_creative_flight");
    public static final ResourceLocation PREVENT_SPRINT_PARTICLES = ResourceLocation.fromNamespaceAndPath("sync", "prevent_sprinting_particles");
    public static final ResourceLocation ENCH_DEALT = ResourceLocation.fromNamespaceAndPath("sync", "modify_enchantment_damage_dealt");
    public static final ResourceLocation ENCH_TAKEN = ResourceLocation.fromNamespaceAndPath("sync", "modify_enchantment_damage_taken");
    public static final ResourceLocation BODY_PART = ResourceLocation.fromNamespaceAndPath("sync", "body_part_damage_modifier");
    public static final ResourceLocation SENDING_MESSAGE = ResourceLocation.fromNamespaceAndPath("sync", "action_on_sending_message");

    // ---------------------------------------------------------------- sync:mobs_ignore
    public record MobsIgnoreCfg(Optional<EntityCondition> mobCondition, Optional<BiEntityCondition> bientityCondition, boolean provokable) {}

    public static final class MobsIgnore extends PowerType<MobsIgnoreCfg> {
        static final Map<UUID, Set<UUID>> PROVOKED = new ConcurrentHashMap<>();

        @Override
        public MapCodec<MobsIgnoreCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                EntityCondition.CODEC.optionalFieldOf("mob_condition").forGetter(MobsIgnoreCfg::mobCondition),
                BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(MobsIgnoreCfg::bientityCondition),
                Codec.BOOL.optionalFieldOf("provokable", true).forGetter(MobsIgnoreCfg::provokable)
            ).apply(i, MobsIgnoreCfg::new));
        }

        public static boolean matches(MobsIgnoreCfg c, Entity mob, Entity holder) {
            if (c.provokable) {
                Set<UUID> p = PROVOKED.get(holder.getUUID());
                if (p != null && p.contains(mob.getUUID())) return false;
            }
            if (c.mobCondition.isPresent() && !c.mobCondition.get().test(EntityCtx.of(mob, mob.level()))) return false;
            return c.bientityCondition.isEmpty() || c.bientityCondition.get().test(BiEntityCtx.of(mob, holder, mob.level()));
        }

        public static boolean shouldIgnore(Entity mob, Entity target) {
            if (target == null || mob == null || mob == target) return false;
            return PowerLookup.anyActive(target, MOBS_IGNORE, MobsIgnoreCfg.class, (c, m) -> matches(c, m, target), mob);
        }

        public static void provoke(Entity holder, Entity mob) {
            List<MobsIgnoreCfg> cfgs = PowerLookup.active(holder, MOBS_IGNORE, MobsIgnoreCfg.class);
            for (MobsIgnoreCfg c : cfgs) {
                if (c.provokable && matches(c, mob, holder)) {
                    PROVOKED.computeIfAbsent(holder.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(mob.getUUID());
                    return;
                }
            }
        }

        @Override
        public void onRemoved(ResourceLocation powerId, MobsIgnoreCfg cfg, PowerContainer holder, ResourceLocation source) {
        }
    }

    // ---------------------------------------------------------------- sync:flip_model
    public record FlipCfg(boolean flipView) {}

    public static final class FlipModel extends PowerType<FlipCfg> {
        @Override
        public MapCodec<FlipCfg> configCodec() {
            return Codec.BOOL.optionalFieldOf("flip_view", false).xmap(FlipCfg::new, FlipCfg::flipView);
        }

        @Override
        public boolean resolvesForClient() {
            return true;
        }
    }

    // ---------------------------------------------------------------- sync:entity_texture_overlay
    public record OverlayCfg(Optional<ResourceLocation> wide, Optional<ResourceLocation> slim, boolean firstPerson, boolean asOverlay,
                             float red, float green, float blue, float alpha, boolean hideCape) {
        public Optional<ResourceLocation> texture(boolean slimModel) {
            return slimModel ? slim.or(() -> wide) : wide.or(() -> slim);
        }
    }

    public static final class TextureOverlay extends PowerType<OverlayCfg> {
        @Override
        public MapCodec<OverlayCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.optionalFieldOf("wide_texture_location").forGetter(OverlayCfg::wide),
                IdCodecs.ID.optionalFieldOf("slim_texture_location").forGetter(OverlayCfg::slim),
                Codec.BOOL.optionalFieldOf("show_first_person", false).forGetter(OverlayCfg::firstPerson),
                Codec.BOOL.optionalFieldOf("render_as_overlay", false).forGetter(OverlayCfg::asOverlay),
                Codec.FLOAT.optionalFieldOf("red", 1f).forGetter(OverlayCfg::red),
                Codec.FLOAT.optionalFieldOf("green", 1f).forGetter(OverlayCfg::green),
                Codec.FLOAT.optionalFieldOf("blue", 1f).forGetter(OverlayCfg::blue),
                Codec.FLOAT.optionalFieldOf("alpha", 1f).forGetter(OverlayCfg::alpha),
                Codec.BOOL.optionalFieldOf("hide_cape", false).forGetter(OverlayCfg::hideCape)
            ).apply(i, OverlayCfg::new));
        }

        @Override
        public boolean resolvesForClient() {
            return true;
        }
    }

    // ---------------------------------------------------------------- sync:prevent_creative_flight
    public record FlightCfg(Optional<EntityAction> action) {}

    public static final class PreventCreativeFlight extends PowerType<FlightCfg> {
        @Override
        public MapCodec<FlightCfg> configCodec() {
            return EntityAction.CODEC.optionalFieldOf("entity_action").xmap(FlightCfg::new, FlightCfg::action);
        }
    }

    // ---------------------------------------------------------------- sync:prevent_sprinting_particles
    public record Marker() {}

    public static final class PreventSprintParticles extends PowerType<Marker> {
        @Override
        public MapCodec<Marker> configCodec() {
            return MapCodec.unit(new Marker());
        }
    }

    // ---------------------------------------------------------------- sync:modify_enchantment_damage_dealt / taken
    public record EnchDamageCfg(ResourceKey<Enchantment> enchantment, float baseValue, Optional<DamageCondition> damageCondition,
                                Optional<EntityCondition> targetCondition, Optional<BiEntityCondition> bientityCondition,
                                List<AttributeModifier> modifiers, Optional<BiEntityAction> bientityAction) {}

    public static final class EnchDamage extends PowerType<EnchDamageCfg> {
        @Override
        public MapCodec<EnchDamageCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.ENCHANTMENT).fieldOf("enchantment").forGetter(EnchDamageCfg::enchantment),
                Codec.FLOAT.optionalFieldOf("base_value", 0f).forGetter(EnchDamageCfg::baseValue),
                DamageCondition.CODEC.optionalFieldOf("damage_condition").forGetter(EnchDamageCfg::damageCondition),
                EntityCondition.CODEC.optionalFieldOf("target_condition").forGetter(EnchDamageCfg::targetCondition),
                BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(EnchDamageCfg::bientityCondition),
                AttributeModifier.CODEC.optionalFieldOf("modifier").forGetter(c -> Optional.empty()),
                AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(EnchDamageCfg::modifiers),
                BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(EnchDamageCfg::bientityAction)
            ).apply(i, (e, b, d, t, be, single, list, a) -> {
                List<AttributeModifier> all = new ArrayList<>(list);
                single.ifPresent(all::add);
                return new EnchDamageCfg(e, b, d, t, be, AttributeModifierHelper.ensureSorted(all), a);
            }));
        }

        /** Sync semantics: bonus = base_value, then the modifiers are applied once per level above 1. */
        public static float bonus(EnchDamageCfg c, LivingEntity wielder) {
            Optional<Holder.Reference<Enchantment>> h = wielder.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(c.enchantment);
            if (h.isEmpty()) return 0;
            int level = EnchantmentHelper.getEnchantmentLevel(h.get(), wielder);
            if (level <= 0) return 0;
            float bonus = c.baseValue;
            for (int i = 0; i < level - 1; i++) bonus = AttributeModifierHelper.apply(bonus, c.modifiers, wielder);
            return bonus;
        }
    }

    // ---------------------------------------------------------------- stubs for Fabric-only integrations (AspectsLib, Jade)
    public static final class Inert extends PowerType<Object> {
        @Override
        public MapCodec<Object> configCodec() {
            return MapCodec.unit(Boolean.TRUE);
        }
    }

    static boolean isPlayer(Entity e) {
        return e instanceof Player;
    }

    static boolean server(Entity e) {
        return e instanceof ServerPlayer;
    }
}
