package dev.alphasucks.aea.compat.tacz;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.AttachmentDataUtils;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.Codecs;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * Everything AEA adds for TaCZ (Timeless and Classics Zero). Only called when TaCZ is installed; nothing else in AEA
 * refers to this package.
 */
public final class TaczTypes {
    private TaczTypes() {}

    public static void register() {
        Reg.power(ModifyGunPower.ID.toString(), new ModifyGunPower());
        ModifyGunPower.register();
        Reg.power(PreventGunUsePower.ID.toString(), new PreventGunUsePower());
        PreventGunUsePower.register();
        GunActionPowers.register();
        NeoForge.EVENT_BUS.addListener(EntityHurtByGunEvent.Pre.class, e -> CurrentHit.set(e.getBullet(), e.getHurtEntity(), e.isHeadShot()));
        NeoForge.EVENT_BUS.addListener(EntityHurtByGunEvent.Post.class, e -> CurrentHit.clear());

        Reg.condition(ConditionTypes.ITEM, "aea:gun", new GunCondition());
        Reg.condition(ConditionTypes.ITEM, "aea:gun_ammo", new AmmoCondition());
        Reg.condition(ConditionTypes.ENTITY, "aea:aiming_gun", new StateCondition(true));
        Reg.condition(ConditionTypes.ENTITY, "aea:reloading_gun", new StateCondition(false));
        Reg.condition(ConditionTypes.DAMAGE, "aea:gun", new GunDamageCondition());
        RefillAmmo refill = new RefillAmmo();
        Reg.action(ActionTypes.ITEM, "aea:refill_ammo", refill.item());
        Reg.action(ActionTypes.ENTITY, "aea:refill_ammo", refill.entity());
        AEA.LOGGER.info("[AEA] TaCZ found: gun powers, conditions and actions are on.");
    }

    // ---- item: aea:gun ----

    /**
     * {@code aea:gun}: the item is a TaCZ gun, optionally of certain guns or types, in a fire mode, with ammo in the
     * magazine compared to a number, or with attachments.
     */
    public record GunCfg(GunFilter filter, List<FireMode> fireMode, Optional<Comparison> comparison, int compareTo,
                         List<ResourceLocation> attachment, List<AttachmentType> attachmentType) {}

    private static final Codec<FireMode> FIRE_MODE = Codec.STRING.comapFlatMap(s -> {
        for (FireMode m : FireMode.values()) {
            if (m != FireMode.UNKNOWN && m.name().equalsIgnoreCase(s)) return com.mojang.serialization.DataResult.success(m);
        }
        return com.mojang.serialization.DataResult.error(() -> "Unknown fire mode \"" + s + "\"; use auto, semi or burst");
    }, m -> m.name().toLowerCase(java.util.Locale.ROOT));

    private static final class GunCondition implements ConditionType<ItemCtx, GunCfg> {
        private static final MapCodec<GunCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            GunFilter.FIELDS.forGetter(GunCfg::filter),
            Codecs.singleOrList(FIRE_MODE).optionalFieldOf("fire_mode", List.of()).forGetter(GunCfg::fireMode),
            Comparison.CODEC.optionalFieldOf("comparison").forGetter(GunCfg::comparison),
            Codec.INT.optionalFieldOf("compare_to", 0).forGetter(GunCfg::compareTo),
            Codecs.singleOrList(ResourceLocation.CODEC).optionalFieldOf("attachment", List.of()).forGetter(GunCfg::attachment),
            Codecs.singleOrList(AttachmentType.CODEC).optionalFieldOf("attachment_type", List.of()).forGetter(GunCfg::attachmentType)
        ).apply(i, GunCfg::new));

        @Override public MapCodec<GunCfg> codec() { return CODEC; }

        @Override
        public boolean test(GunCfg cfg, ItemCtx ctx) {
            ItemStack stack = ctx.stack();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null || (!cfg.filter().any() && !cfg.filter().test(stack))) return false;
            if (!cfg.fireMode().isEmpty() && !cfg.fireMode().contains(gun.getFireMode(stack))) return false;
            if (cfg.comparison().isPresent() && !cfg.comparison().get().compare(gun.getCurrentAmmoCount(stack), cfg.compareTo())) return false;
            if (!cfg.attachmentType().isEmpty()) {
                for (AttachmentType type : cfg.attachmentType()) {
                    if (attachmentId(ctx, gun, stack, type) == null) return false;
                }
            }
            if (!cfg.attachment().isEmpty()) {
                for (ResourceLocation want : cfg.attachment()) {
                    if (!hasAttachment(ctx, gun, stack, want)) return false;
                }
            }
            return true;
        }

        private static ResourceLocation attachmentId(ItemCtx ctx, IGun gun, ItemStack stack, AttachmentType type) {
            ItemStack att = gun.getAttachment(ctx.level().registryAccess(), stack, type);
            if (att.isEmpty()) att = gun.getBuiltinAttachment(stack, type);
            IAttachment a = IAttachment.getIAttachmentOrNull(att);
            return a == null ? null : a.getAttachmentId(att);
        }

        private static boolean hasAttachment(ItemCtx ctx, IGun gun, ItemStack stack, ResourceLocation want) {
            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE) continue;
                if (want.equals(attachmentId(ctx, gun, stack, type))) return true;
            }
            return false;
        }
    }

    // ---- item: aea:gun_ammo ----

    /** {@code aea:gun_ammo}: the item is TaCZ ammo, optionally of certain ammo ids. */
    public record AmmoCfg(List<ResourceLocation> ammo) {}

    private static final class AmmoCondition implements ConditionType<ItemCtx, AmmoCfg> {
        private static final MapCodec<AmmoCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codecs.singleOrList(ResourceLocation.CODEC).optionalFieldOf("ammo", List.of()).forGetter(AmmoCfg::ammo)
        ).apply(i, AmmoCfg::new));

        @Override public MapCodec<AmmoCfg> codec() { return CODEC; }

        @Override
        public boolean test(AmmoCfg cfg, ItemCtx ctx) {
            IAmmo ammo = IAmmo.getIAmmoOrNull(ctx.stack());
            return ammo != null && (cfg.ammo().isEmpty() || cfg.ammo().contains(ammo.getAmmoId(ctx.stack())));
        }
    }

    // ---- entity: aea:aiming_gun / aea:reloading_gun ----

    private enum Unit { INSTANCE }

    private static final MapCodec<Unit> UNIT = MapCodec.unit(Unit.INSTANCE);

    private static final class StateCondition implements ConditionType<EntityCtx, Unit> {
        private final boolean aiming;

        StateCondition(boolean aiming) {
            this.aiming = aiming;
        }

        @Override public MapCodec<Unit> codec() { return UNIT; }

        @Override
        public boolean test(Unit cfg, EntityCtx ctx) {
            if (!(ctx.raw() instanceof LivingEntity living) || !IGun.mainHandHoldGun(living)) return false;
            IGunOperator operator = IGunOperator.fromLivingEntity(living);
            return aiming ? operator.getSynIsAiming() : operator.getSynReloadState().getStateType().isReloading();
        }
    }

    // ---- damage: aea:gun ----

    /** The gun hit being applied right now, set between TaCZ's pre and post hurt events. Server thread. */
    static final class CurrentHit {
        private static Entity bullet, target;
        private static boolean headshot;

        static void set(Entity b, Entity t, boolean head) {
            if (b == null || b.level().isClientSide()) return;
            bullet = b;
            target = t;
            headshot = head;
        }

        static void clear() {
            bullet = null;
            target = null;
        }

        /** Whether the hit from {@code b} on {@code t} is a headshot; null when it isn't the current hit. */
        static Boolean headshot(Entity b, Entity t) {
            return b != null && b == bullet && t == target ? headshot : null;
        }
    }

    /** {@code aea:gun} (damage): the damage came from a TaCZ bullet, optionally from certain guns or types, or a headshot. */
    public record DamageCfg(GunFilter filter, Optional<Boolean> headshot) {}

    private static final class GunDamageCondition implements ConditionType<DamageCtx, DamageCfg> {
        private static final MapCodec<DamageCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            GunFilter.FIELDS.forGetter(DamageCfg::filter),
            Codec.BOOL.optionalFieldOf("headshot").forGetter(DamageCfg::headshot)
        ).apply(i, DamageCfg::new));

        @Override public MapCodec<DamageCfg> codec() { return CODEC; }

        @Override
        public boolean test(DamageCfg cfg, DamageCtx ctx) {
            if (ctx.source() == null || !(ctx.source().getDirectEntity() instanceof EntityKineticBullet bullet)) return false;
            if (!cfg.filter().any() && !cfg.filter().test(bullet.getGunId())) return false;
            if (cfg.headshot().isEmpty()) return true;
            Boolean head = CurrentHit.headshot(bullet, ctx.target());
            return (head != null && head) == cfg.headshot().get();
        }
    }

    // ---- action: aea:refill_ammo ----

    /** Fills the magazine of a gun, or adds {@code amount} rounds without going over its size. */
    public record RefillCfg(Optional<Integer> amount) {}

    private static final class RefillAmmo {
        private static final MapCodec<RefillCfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("amount").forGetter(RefillCfg::amount)
        ).apply(i, RefillCfg::new));

        static void refill(RefillCfg cfg, ItemStack stack) {
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) return;
            int max = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(index -> AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData())).orElse(-1);
            if (max < 0) return;
            int now = gun.getCurrentAmmoCount(stack);
            int target = cfg.amount().map(a -> Math.min(max, now + a)).orElse(max);
            if (target > now) gun.setCurrentAmmoCount(stack, target);
        }

        ActionType<ItemCtx, RefillCfg> item() {
            return new ActionType<>() {
                @Override public MapCodec<RefillCfg> codec() { return CODEC; }
                @Override public void run(RefillCfg cfg, ItemCtx ctx) {
                    if (ctx.level() != null && !ctx.level().isClientSide()) refill(cfg, ctx.stack());
                }
            };
        }

        ActionType<EntityCtx, RefillCfg> entity() {
            return new ActionType<>() {
                @Override public MapCodec<RefillCfg> codec() { return CODEC; }
                @Override public void run(RefillCfg cfg, EntityCtx ctx) {
                    if (ctx.raw() instanceof LivingEntity living && !living.level().isClientSide()) refill(cfg, living.getMainHandItem());
                }
            };
        }
    }
}
