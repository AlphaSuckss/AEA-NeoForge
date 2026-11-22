package dev.alphasucks.aea.compat.tacz;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.BlockAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * Powers that run actions on TaCZ gun events, all on the server:
 * <ul>
 *   <li>{@code aea:action_on_gun_fire}: the holder fires a round.</li>
 *   <li>{@code aea:action_on_gun_hit}: one of the holder's bullets hurts an entity (after the damage).</li>
 *   <li>{@code aea:action_on_gun_kill}: one of the holder's bullets kills an entity.</li>
 *   <li>{@code aea:action_on_bullet_hit_block}: one of the holder's bullets hits a block.</li>
 *   <li>{@code aea:action_on_gun_reload}: the holder starts or finishes a reload.</li>
 *   <li>{@code aea:action_on_gun_melee}: the holder makes a melee attack with a gun.</li>
 * </ul>
 * Each takes the {@link GunFilter} fields to limit it to some guns.
 */
public final class GunActionPowers {
    private GunActionPowers() {}

    public static final ResourceLocation FIRE = id("action_on_gun_fire"), HIT = id("action_on_gun_hit"),
        KILL = id("action_on_gun_kill"), BLOCK = id("action_on_bullet_hit_block"), RELOAD = id("action_on_gun_reload"),
        MELEE = id("action_on_gun_melee");

    private static final PowerTypeUsage.Handle FIRE_HELD = PowerTypeUsage.handle(FIRE), HIT_HELD = PowerTypeUsage.handle(HIT),
        KILL_HELD = PowerTypeUsage.handle(KILL), BLOCK_HELD = PowerTypeUsage.handle(BLOCK),
        RELOAD_HELD = PowerTypeUsage.handle(RELOAD), MELEE_HELD = PowerTypeUsage.handle(MELEE);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("aea", path);
    }

    // ---- configs ----

    /** Fire, reload and melee: an entity action on the holder. */
    public record SelfCfg(GunFilter filter, Optional<EntityAction> entityAction, Reload when) {}

    public enum Reload { START, FINISH }

    private static final Codec<Reload> RELOAD_CODEC = Codec.STRING.xmap(s -> s.equals("finish") ? Reload.FINISH : Reload.START,
        r -> r == Reload.FINISH ? "finish" : "start");

    private static MapCodec<SelfCfg> selfCodec(boolean reload) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            GunFilter.FIELDS.forGetter(SelfCfg::filter),
            LoggedOptionalField.strict("entity_action", EntityAction.CODEC).forGetter(SelfCfg::entityAction),
            (reload ? RELOAD_CODEC.optionalFieldOf("when", Reload.START) : MapCodec.unit(Reload.START)).forGetter(SelfCfg::when)
        ).apply(i, SelfCfg::new));
    }

    /** Hit and kill: a bi-entity action with the holder as actor and the entity hit as target. */
    public record HitCfg(GunFilter filter, Optional<BiEntityAction> bientityAction, Optional<BiEntityCondition> bientityCondition,
                         Optional<Boolean> headshot) {
        boolean accepts(ResourceLocation gun, boolean head, LivingEntity attacker, Entity target) {
            if (!filter.any() && !filter.test(gun)) return false;
            if (headshot.isPresent() && headshot.get() != head) return false;
            return bientityCondition.isEmpty() || bientityCondition.get().test(BiEntityCtx.of(attacker, target, attacker.level()));
        }
    }

    private static final MapCodec<HitCfg> HIT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        GunFilter.FIELDS.forGetter(HitCfg::filter),
        LoggedOptionalField.strict("bientity_action", BiEntityAction.CODEC).forGetter(HitCfg::bientityAction),
        LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(HitCfg::bientityCondition),
        Codec.BOOL.optionalFieldOf("headshot").forGetter(HitCfg::headshot)
    ).apply(i, HitCfg::new));

    /** Block hits: an entity action on the holder and a block action on the block hit. */
    public record BlockCfg(GunFilter filter, Optional<EntityAction> entityAction, Optional<BlockAction> blockAction,
                           Optional<BlockCondition> blockCondition) {}

    private static final MapCodec<BlockCfg> BLOCK_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        GunFilter.FIELDS.forGetter(BlockCfg::filter),
        LoggedOptionalField.strict("entity_action", EntityAction.CODEC).forGetter(BlockCfg::entityAction),
        LoggedOptionalField.strict("block_action", BlockAction.CODEC).forGetter(BlockCfg::blockAction),
        LoggedOptionalField.strict("block_condition", BlockCondition.CODEC).forGetter(BlockCfg::blockCondition)
    ).apply(i, BlockCfg::new));

    private static final class Type<C> extends PowerType<C> {
        private final MapCodec<C> codec;

        Type(MapCodec<C> codec) {
            this.codec = codec;
        }

        @Override
        public MapCodec<C> configCodec() {
            return codec;
        }
    }

    public static void register() {
        Reg.power(FIRE.toString(), new Type<>(selfCodec(false)));
        Reg.power(RELOAD.toString(), new Type<>(selfCodec(true)));
        Reg.power(MELEE.toString(), new Type<>(selfCodec(false)));
        Reg.power(HIT.toString(), new Type<>(HIT_CODEC));
        Reg.power(KILL.toString(), new Type<>(HIT_CODEC));
        Reg.power(BLOCK.toString(), new Type<>(BLOCK_CODEC));

        // LOWEST and not receiving cancelled events, so a use that aea:prevent_gun_use (or anything else) stopped runs nothing.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, GunFireEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && FIRE_HELD.isHeld()) self(FIRE, e.getShooter(), e.getGunItemStack(), null);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, GunReloadEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && RELOAD_HELD.isHeld()) self(RELOAD, e.getEntity(), e.getGunItemStack(), Reload.START);
        });
        NeoForge.EVENT_BUS.addListener(GunFinishReloadEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && RELOAD_HELD.isHeld()) self(RELOAD, e.getEntity(), e.getGunItemStack(), Reload.FINISH);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, GunMeleeEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && MELEE_HELD.isHeld()) self(MELEE, e.getShooter(), e.getGunItemStack(), null);
        });
        NeoForge.EVENT_BUS.addListener(EntityHurtByGunEvent.Post.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && HIT_HELD.isHeld()) hit(HIT, e.getAttacker(), e.getHurtEntity(), e.getGunId(), e.isHeadShot());
        });
        NeoForge.EVENT_BUS.addListener(EntityKillByGunEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.SERVER && KILL_HELD.isHeld()) hit(KILL, e.getAttacker(), e.getKilledEntity(), e.getGunId(), e.isHeadShot());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, AmmoHitBlockEvent.class, GunActionPowers::blockHit);
    }

    private static void self(ResourceLocation type, LivingEntity holder, ItemStack gun, Reload when) {
        if (holder == null || holder.level().isClientSide()) return;
        List<SelfCfg> active = PowerLookup.active(holder, type, SelfCfg.class);
        for (int i = 0; i < active.size(); i++) {
            SelfCfg cfg = active.get(i);
            if (when != null && cfg.when() != when) continue;
            if (!cfg.filter().any() && !cfg.filter().test(gun)) continue;
            cfg.entityAction().ifPresent(a -> a.run(EntityCtx.of(holder, holder.level())));
        }
    }

    private static void hit(ResourceLocation type, LivingEntity attacker, Entity target, ResourceLocation gun, boolean head) {
        if (attacker == null || target == null || attacker.level().isClientSide()) return;
        List<HitCfg> active = PowerLookup.active(attacker, type, HitCfg.class);
        for (int i = 0; i < active.size(); i++) {
            HitCfg cfg = active.get(i);
            if (cfg.accepts(gun, head, attacker, target)) {
                cfg.bientityAction().ifPresent(a -> a.run(BiEntityCtx.of(attacker, target, attacker.level())));
            }
        }
    }

    private static void blockHit(AmmoHitBlockEvent event) {
        if (!BLOCK_HELD.isHeld()) return;
        EntityKineticBullet bullet = event.getAmmo();
        if (!(bullet.getOwner() instanceof LivingEntity shooter) || shooter.level().isClientSide()) return;
        List<BlockCfg> active = PowerLookup.active(shooter, BLOCK, BlockCfg.class);
        if (active.isEmpty()) return;
        BlockHitResult hit = event.getHitResult();
        BlockCtx ctx = new BlockCtx(hit.getBlockPos(), event.getState(), event.getLevel(), shooter, hit.getLocation());
        for (int i = 0; i < active.size(); i++) {
            BlockCfg cfg = active.get(i);
            if (!cfg.filter().any() && !cfg.filter().test(bullet.getGunId())) continue;
            if (cfg.blockCondition().isPresent() && !cfg.blockCondition().get().test(ctx)) continue;
            cfg.blockAction().ifPresent(a -> a.run(ctx));
            cfg.entityAction().ifPresent(a -> a.run(EntityCtx.of(shooter, shooter.level())));
        }
    }
}
