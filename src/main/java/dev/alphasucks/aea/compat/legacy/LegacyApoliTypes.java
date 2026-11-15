package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.builtin.entity.PowerActiveCondition;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/** Apoli 2.9.2 types whose Overgrown counterpart behaves differently; ApoliJsonUpgrader points legacy packs here. */
public final class LegacyApoliTypes {
    private LegacyApoliTypes() {}

    // ------------------------------------------------------------------ comparison (Apoli 2.9 symbols)

    static boolean compare(String op, double a, double b) {
        return switch (op) {
            case "==" -> a == b;
            case "<" -> a < b;
            case ">" -> a > b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            case "!=" -> a != b;
            default -> false;
        };
    }

    static final Codec<String> COMPARISON = Codec.STRING.xmap(s -> s.trim(), s -> s);

    // ------------------------------------------------------------------ bientity: undirected

    /** Apoli 2.9: a bi-entity condition tested both ways round (Overgrown's takes an entity condition instead). */
    public static final class Undirected implements ConditionType<BiEntityCtx, BiEntityCondition> {
        @Override
        public MapCodec<BiEntityCondition> codec() {
            return BiEntityCondition.CODEC.fieldOf("condition");
        }

        @Override
        public boolean test(BiEntityCondition cond, BiEntityCtx ctx) {
            return cond.test(ctx) || cond.test(ctx.swap());
        }
    }

    // ------------------------------------------------------------------ distance_from_coordinates / distance_from_spawn

    public record DistanceCfg(String reference, Vec3 offset, Vec3 coordinates, boolean ignoreX, boolean ignoreY, boolean ignoreZ,
                              String shape, boolean scaleReferenceToDimension, boolean scaleDistanceToDimension,
                              String comparison, double compareTo, Optional<Boolean> resultOnWrongDimension,
                              Optional<Integer> roundToDigit) {}

    static MapCodec<DistanceCfg> distanceCodec(String defaultReference) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("reference", defaultReference).forGetter(DistanceCfg::reference),
            Codecs.VEC3.optionalFieldOf("offset", Vec3.ZERO).forGetter(DistanceCfg::offset),
            Codecs.VEC3.optionalFieldOf("coordinates", Vec3.ZERO).forGetter(DistanceCfg::coordinates),
            Codec.BOOL.optionalFieldOf("ignore_x", false).forGetter(DistanceCfg::ignoreX),
            Codec.BOOL.optionalFieldOf("ignore_y", false).forGetter(DistanceCfg::ignoreY),
            Codec.BOOL.optionalFieldOf("ignore_z", false).forGetter(DistanceCfg::ignoreZ),
            Codec.STRING.optionalFieldOf("shape", "cube").forGetter(DistanceCfg::shape),
            Codec.BOOL.optionalFieldOf("scale_reference_to_dimension", true).forGetter(DistanceCfg::scaleReferenceToDimension),
            Codec.BOOL.optionalFieldOf("scale_distance_to_dimension", false).forGetter(DistanceCfg::scaleDistanceToDimension),
            COMPARISON.fieldOf("comparison").forGetter(DistanceCfg::comparison),
            Codec.DOUBLE.fieldOf("compare_to").forGetter(DistanceCfg::compareTo),
            Codec.BOOL.optionalFieldOf("result_on_wrong_dimension").forGetter(DistanceCfg::resultOnWrongDimension),
            Codec.INT.optionalFieldOf("round_to_digit").forGetter(DistanceCfg::roundToDigit)
        ).apply(i, DistanceCfg::new));
    }

    private static boolean outOfBounds(String comparison) {
        return comparison.equals("!=") || comparison.equals(">") || comparison.equals(">=");
    }

    static double shapeDistance(String shape, double x, double y, double z) {
        return switch (shape.toLowerCase(Locale.ROOT)) {
            case "star", "manhattan" -> x + y + z;
            case "sphere", "euclidean" -> Math.sqrt(x * x + y * y + z * z);
            default -> Math.max(Math.max(x, y), z);
        };
    }

    /** Port of Apoli 2.9.2's DistanceFromCoordinatesConditionRegistry.testCondition. */
    static boolean testDistance(DistanceCfg c, Vec3 pos, Level level) {
        if (level == null) return outOfBounds(c.comparison());
        double scale = level.dimensionType().coordinateScale();
        double x = 0, y = 0, z = 0;
        switch (c.reference()) {
            case "world_spawn", "player_spawn", "player_natural_spawn" -> {
                if (c.resultOnWrongDimension().isPresent() && level.dimension() != Level.OVERWORLD) return c.resultOnWrongDimension().get();
                BlockPos spawn = level.getSharedSpawnPos();
                x = spawn.getX(); y = spawn.getY(); z = spawn.getZ();
            }
            default -> {
            }
        }
        x += c.coordinates().x + c.offset().x;
        y += c.coordinates().y + c.offset().y;
        z += c.coordinates().z + c.offset().z;
        if (c.scaleReferenceToDimension() && (x != 0 || z != 0)) {
            if (scale == 0) return outOfBounds(c.comparison());
            x /= scale;
            z /= scale;
        }
        double dx = c.ignoreX() ? 0 : Math.abs(pos.x - x);
        double dy = c.ignoreY() ? 0 : Math.abs(pos.y - y);
        double dz = c.ignoreZ() ? 0 : Math.abs(pos.z - z);
        if (c.scaleDistanceToDimension()) {
            dx *= scale;
            dz *= scale;
        }
        double distance = shapeDistance(c.shape(), dx, dy, dz);
        if (c.roundToDigit().isPresent()) {
            distance = new BigDecimal(distance).setScale(c.roundToDigit().get(), RoundingMode.HALF_UP).doubleValue();
        }
        return compare(c.comparison(), distance, c.compareTo());
    }

    public static final class EntityDistance implements ConditionType<EntityCtx, DistanceCfg> {
        private final MapCodec<DistanceCfg> codec;

        public EntityDistance(String defaultReference) {
            this.codec = distanceCodec(defaultReference);
        }

        @Override
        public MapCodec<DistanceCfg> codec() {
            return codec;
        }

        @Override
        public boolean test(DistanceCfg c, EntityCtx ctx) {
            Entity e = ctx.entity();
            if (e == null) return false;
            return testDistance(c, e.position(), e.level());
        }
    }

    public static final class BlockDistance implements ConditionType<BlockCtx, DistanceCfg> {
        private final MapCodec<DistanceCfg> codec;

        public BlockDistance(String defaultReference) {
            this.codec = distanceCodec(defaultReference);
        }

        @Override
        public MapCodec<DistanceCfg> codec() {
            return codec;
        }

        @Override
        public boolean test(DistanceCfg c, BlockCtx ctx) {
            BlockPos p = ctx.pos();
            return testDistance(c, new Vec3(p.getX(), p.getY(), p.getZ()), ctx.level());
        }
    }

    // ------------------------------------------------------------------ damage_over_time with protection_enchantment

    public static final ResourceKey<DamageType> DAMAGE_OVER_TIME = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("apoli", "damage_over_time"));

    public record DotCfg(int interval, Optional<Integer> onsetDelay, float damage, Optional<Float> damageEasy,
                         ResourceLocation damageType, Optional<ResourceLocation> protectionEnchantment, float protectionEffectiveness) {}

    /**
     * Apoli 2.9.2's DamageOverTimePower, kept for packs that use protection_enchantment (Overgrown dropped the onset
     * scaling): damage starts after onset_delay + (2 * protection)^1.3 * effectiveness seconds in the condition.
     */
    public static final class DamageOverTime extends PowerType<DotCfg> {
        private static final MapCodec<DotCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("interval", 20).forGetter(DotCfg::interval),
            Codec.INT.optionalFieldOf("onset_delay").forGetter(DotCfg::onsetDelay),
            Codec.FLOAT.fieldOf("damage").forGetter(DotCfg::damage),
            Codec.FLOAT.optionalFieldOf("damage_easy").forGetter(DotCfg::damageEasy),
            ResourceLocation.CODEC.optionalFieldOf("damage_type", DAMAGE_OVER_TIME.location()).forGetter(DotCfg::damageType),
            ResourceLocation.CODEC.optionalFieldOf("protection_enchantment").forGetter(DotCfg::protectionEnchantment),
            Codec.FLOAT.optionalFieldOf("protection_effectiveness", 1.0f).forGetter(DotCfg::protectionEffectiveness)
        ).apply(i, DotCfg::new));

        @Override
        public MapCodec<DotCfg> configCodec() {
            return CODEC;
        }

        @Override
        public void tick(ResourceLocation id, DotCfg cfg, PowerContainer container) {
            LivingEntity entity = container.owner();
            if (entity == null || entity.level().isClientSide) return;
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
                    var holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(dmgKey);
                    entity.hurt(holder.isPresent() ? new net.minecraft.world.damagesource.DamageSource(holder.get()) : entity.damageSources().generic(), amount);
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
        public void onRemoved(ResourceLocation id, DotCfg cfg, PowerContainer container, ResourceLocation source) {
            LivingEntity entity = container.owner();
            if (entity != null) entity.getPersistentData().getCompound("aea_dot").remove(id.toString());
        }

        private static int protection(LivingEntity entity, DotCfg cfg) {
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
}
