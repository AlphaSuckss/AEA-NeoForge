package dev.alphasucks.aea.compat;

import dev.alphasucks.aea.compat.sync.power.ActionOnSendingMessagePower;
import dev.alphasucks.aea.compat.sync.power.BodyPartDamageModifierPower;
import dev.alphasucks.aea.compat.sync.power.MobsIgnorePower;
import dev.alphasucks.aea.compat.sync.power.ModifyEnchantmentDamagePower;
import dev.alphasucks.aea.compat.sync.power.PreventCreativeFlightPower;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Server-side event hooks for the ported Sync powers. */
public final class AEAHooks {

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && MobsIgnorePower.shouldIgnore(event.getEntity(), target)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (attacker != null && attacker != victim) MobsIgnorePower.provoke(attacker, victim);

        float amount = event.getAmount();
        float bonus = 0;
        if (attacker instanceof LivingEntity la) {
            for (ModifyEnchantmentDamagePower.Config c : PowerLookup.active(la, ModifyEnchantmentDamagePower.DEALT, ModifyEnchantmentDamagePower.Config.class)) {
                if (!enchApplies(c, source, amount, la, victim)) continue;
                if (c.targetCondition().isPresent() && !c.targetCondition().get().test(EntityCtx.of(victim, victim.level()))) continue;
                float b = ModifyEnchantmentDamagePower.bonus(c, la);
                if (b != 0) { bonus += b; c.bientityAction().ifPresent(a -> a.run(BiEntityCtx.of(la, victim, victim.level()))); }
            }
            for (ModifyEnchantmentDamagePower.Config c : PowerLookup.active(victim, ModifyEnchantmentDamagePower.TAKEN, ModifyEnchantmentDamagePower.Config.class)) {
                if (!enchApplies(c, source, amount, la, victim)) continue;
                float b = ModifyEnchantmentDamagePower.bonus(c, la);
                if (b != 0) { bonus += b; c.bientityAction().ifPresent(a -> a.run(BiEntityCtx.of(la, victim, victim.level()))); }
            }
        }
        amount += bonus;

        List<BodyPartDamageModifierPower.Config> body = PowerLookup.active(victim, BodyPartDamageModifierPower.ID, BodyPartDamageModifierPower.Config.class);
        if (!body.isEmpty()) {
            for (BodyPartDamageModifierPower.Config c : body) {
                if (c.damageCondition().isPresent() && !c.damageCondition().get().test(new DamageCtx(source, victim, victim.level(), amount))) continue;
                Vec3 hit = hitLocation(victim, source, c.requireHitData());
                if (hit == null) continue;
                double before = amount;
                for (BodyPartDamageModifierPower.Entry e : c.entries()) {
                    if (!e.region().contains(hit.x, hit.y, hit.z)) continue;
                    amount = AttributeModifierHelper.apply(amount, e.modifiers(), victim);
                    if (attacker != null) e.action().ifPresent(a -> a.run(BiEntityCtx.of(attacker, victim, victim.level())));
                }
                if (c.showHitLocation()) {
                    String msg = String.format("[BodyPart] x=%.2f y=%.2f z=%.2f | dmg %.1f -> %.1f", hit.x, hit.y, hit.z, before, (double) amount);
                    if (victim instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg));
                    if (attacker instanceof ServerPlayer ap && ap != victim) ap.sendSystemMessage(Component.literal(msg));
                }
            }
        }
        if (amount != event.getAmount()) event.setAmount(Math.max(0, amount));
    }

    private static boolean enchApplies(ModifyEnchantmentDamagePower.Config c, DamageSource source, float amount, LivingEntity attacker, LivingEntity victim) {
        if (c.damageCondition().isPresent() && !c.damageCondition().get().test(new DamageCtx(source, victim, victim.level(), amount))) return false;
        return c.bientityCondition().isEmpty() || c.bientityCondition().get().test(BiEntityCtx.of(attacker, victim, victim.level()));
    }

    /** Normalised hit position on the victim: x ∈ [-1,1] (left +), y ∈ [0,1], z ∈ [-1,1] (behind +). */
    private static Vec3 hitLocation(LivingEntity victim, DamageSource source, boolean require) {
        Vec3 world = null;
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (direct != null && direct != attacker) {
            world = direct.position().add(0, direct.getBbHeight() / 2, 0);
        } else if (attacker != null) {
            Vec3 eye = attacker.getEyePosition();
            Vec3 end = eye.add(attacker.getViewVector(1f).scale(8));
            Optional<Vec3> clip = victim.getBoundingBox().inflate(0.1).clip(eye, end);
            world = clip.orElse(null);
        }
        if (world == null) {
            if (require) return null;
            if (source.is(DamageTypes.FALL) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.STALAGMITE)) {
                return victim.getRandom().nextBoolean() ? new Vec3(0, 0.09, 0) : new Vec3(0, 0.34, 0);
            }
            if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.STARVE)) return new Vec3(0, 0.69, 0);
            if (source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.DAMAGES_HELMET)) return new Vec3(0, 0.94, 0);
            return switch (victim.getRandom().nextInt(8)) {
                case 0 -> new Vec3(0, 0.94, 0);
                case 1 -> new Vec3(0, 0.69, 0);
                case 2 -> new Vec3(0.9, 0.74, 0);
                case 3 -> new Vec3(-0.9, 0.74, 0);
                case 4, 5 -> new Vec3(0, 0.34, 0);
                default -> new Vec3(0, 0.09, 0);
            };
        }
        AABB box = victim.getBoundingBox();
        double y = (world.y - box.minY) / Math.max(0.01, box.getYsize());
        double yaw = Math.toRadians(victim.yBodyRot);
        double dx = world.x - victim.getX(), dz = world.z - victim.getZ();
        // Rotate into body space: forward = -sin(yaw), cos(yaw).
        double fwd = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double right = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        double half = Math.max(0.01, victim.getBbWidth() / 2);
        return new Vec3(clamp(-right / half), clamp01(y), clamp(-fwd / half));
    }

    private static double clamp(double v) { return Math.max(-1, Math.min(1, v)); }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;
        List<PreventCreativeFlightPower.Config> list = PowerLookup.active(player, PreventCreativeFlightPower.ID, PreventCreativeFlightPower.Config.class);
        if (list.isEmpty()) return;
        boolean flying = player.getAbilities().flying;
        if (flying || player.getAbilities().mayfly) {
            if (flying) for (PreventCreativeFlightPower.Config a : list) a.action().ifPresent(x -> x.run(EntityCtx.of(player, player.level())));
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        List<ActionOnSendingMessagePower.Config> cfgs = new ArrayList<>(PowerLookup.active(player, ActionOnSendingMessagePower.ID, ActionOnSendingMessagePower.Config.class));
        if (cfgs.isEmpty()) return;
        cfgs.sort(Comparator.comparingInt(ActionOnSendingMessagePower.Config::priority).reversed());
        String original = event.getRawText();
        String current = original;
        for (ActionOnSendingMessagePower.Config c : cfgs) {
            if (c.messageType().isPresent() && !c.messageType().get().toString().equals("minecraft:chat")) continue;
            boolean prevented = false;
            for (ActionOnSendingMessagePower.Filter con : c.filters()) {
                if (!con.matches(current)) continue;
                con.before().ifPresent(a -> a.run(EntityCtx.of(player, player.level())));
                current = con.apply(current);
                if (con.prevent()) prevented = true;
                con.after().ifPresent(a -> a.run(EntityCtx.of(player, player.level())));
                if (prevented) break;
            }
            if (prevented) {
                event.setCanceled(true);
                return;
            }
        }
        if (!current.equals(original)) event.setMessage(Component.literal(current));
    }

}
