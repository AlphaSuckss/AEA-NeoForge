package dev.alphasucks.aea.compat.tacz;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.ParameterizedCache;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerContainerImpl;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code aea:modify_gun}: changes the stats of the TaCZ guns the holder uses. Each stat takes Apoli modifiers. They are
 * applied where TaCZ works out a gun's stats from its attachments ({@link AttachmentPropertyEvent}), so they stack
 * with attachments and show in TaCZ's own numbers. That event runs when the gun is drawn or its attachments change;
 * {@link #refresh} runs it again when the holder's active {@code modify_gun} powers change.
 *
 * <p>Recoil is the exception: TaCZ stores it as a multiplier on the gun's recoil curve, so the modifiers apply to that
 * multiplier (1 = normal recoil).
 */
public final class ModifyGunPower extends PowerType<ModifyGunPower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "modify_gun");
    public static final PowerTypeUsage.Handle HELD = PowerTypeUsage.handle(ID);

    private static final Codec<List<AttributeModifier>> MODS = AttributeModifier.LIST_OR_SINGLE;

    public record Cfg(GunFilter filter, List<AttributeModifier> damage, List<AttributeModifier> fireRate,
                      List<AttributeModifier> recoil, List<AttributeModifier> inaccuracy, List<AttributeModifier> aimInaccuracy,
                      List<AttributeModifier> aimTime, List<AttributeModifier> bulletSpeed, List<AttributeModifier> armorIgnore,
                      List<AttributeModifier> headshotMultiplier, List<AttributeModifier> knockback, List<AttributeModifier> pierce,
                      List<AttributeModifier> effectiveRange, List<AttributeModifier> weight, float ammoSaveChance) {}

    private static MapCodec<List<AttributeModifier>> mods(String name) {
        return MODS.optionalFieldOf(name, List.of());
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        GunFilter.FIELDS.forGetter(Cfg::filter),
        mods("damage").forGetter(Cfg::damage),
        mods("fire_rate").forGetter(Cfg::fireRate),
        mods("recoil").forGetter(Cfg::recoil),
        mods("inaccuracy").forGetter(Cfg::inaccuracy),
        mods("aim_inaccuracy").forGetter(Cfg::aimInaccuracy),
        mods("aim_time").forGetter(Cfg::aimTime),
        mods("bullet_speed").forGetter(Cfg::bulletSpeed),
        mods("armor_ignore").forGetter(Cfg::armorIgnore),
        mods("headshot_multiplier").forGetter(Cfg::headshotMultiplier),
        mods("knockback").forGetter(Cfg::knockback),
        mods("pierce").forGetter(Cfg::pierce),
        mods("effective_range").forGetter(Cfg::effectiveRange),
        mods("weight").forGetter(Cfg::weight),
        Codec.floatRange(0, 1).optionalFieldOf("ammo_save_chance", 0F).forGetter(Cfg::ammoSaveChance)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    @Override
    public boolean resolvesForClient() {
        return true;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AttachmentPropertyEvent.class, ModifyGunPower::onProperties);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> refresh(e.getEntity()));
        // Aux data is saved with the player, but TaCZ works the stats out afresh for a new or moved player.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> forget(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(GunShootEvent.class, ModifyGunPower::onShoot);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> refundAmmo());
    }

    // ---- stats ----

    private static void onProperties(AttachmentPropertyEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter == null || (!shooter.level().isClientSide() && !HELD.isHeld())) return;
        List<Cfg> active = matching(shooter, event.getGunItem());
        if (active.isEmpty()) return;
        AttachmentCacheProperty cache = event.getCacheProperty();

        scaleFloat(cache, GunProperties.ADS_TIME, collect(active, Cfg::aimTime), shooter);
        scaleFloat(cache, GunProperties.AMMO_SPEED, collect(active, Cfg::bulletSpeed), shooter);
        scaleFloat(cache, GunProperties.ARMOR_IGNORE, collect(active, Cfg::armorIgnore), shooter);
        scaleFloat(cache, GunProperties.EFFECTIVE_RANGE, collect(active, Cfg::effectiveRange), shooter);
        scaleFloat(cache, GunProperties.HEADSHOT_MULTIPLIER, collect(active, Cfg::headshotMultiplier), shooter);
        scaleFloat(cache, GunProperties.KNOCKBACK, collect(active, Cfg::knockback), shooter);
        scaleFloat(cache, GunProperties.WEIGHT, collect(active, Cfg::weight), shooter);
        scaleInt(cache, GunProperties.ROUNDS_PER_MINUTE, collect(active, Cfg::fireRate), shooter, 1);
        scaleInt(cache, GunProperties.PIERCE, collect(active, Cfg::pierce), shooter, 1);
        scaleInaccuracy(cache, GunProperties.INACCURACY, collect(active, Cfg::inaccuracy), shooter);
        scaleInaccuracy(cache, GunProperties.AIM_INACCURACY, collect(active, Cfg::aimInaccuracy), shooter);
        scaleDamage(cache, collect(active, Cfg::damage), shooter);
        scaleRecoil(cache, collect(active, Cfg::recoil), shooter);
    }

    private static List<Cfg> matching(LivingEntity shooter, ItemStack gun) {
        List<Cfg> active = ActiveConfigs.of(shooter, ID, Cfg.class);
        if (active.isEmpty()) return active;
        List<Cfg> out = new ArrayList<>(active.size());
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).filter().any() || active.get(i).filter().test(gun)) out.add(active.get(i));
        }
        return out;
    }

    private static List<AttributeModifier> collect(List<Cfg> active, Function<Cfg, List<AttributeModifier>> field) {
        List<AttributeModifier> all = null;
        for (int i = 0; i < active.size(); i++) {
            List<AttributeModifier> mods = field.apply(active.get(i));
            if (mods.isEmpty()) continue;
            if (all == null) all = new ArrayList<>(mods);
            else all.addAll(mods);
        }
        return all == null ? List.of() : AttributeModifierHelper.ensureSorted(all);
    }

    private static void scaleFloat(AttachmentCacheProperty cache, GunProperty<Float> property, List<AttributeModifier> mods, LivingEntity shooter) {
        if (mods.isEmpty()) return;
        Float value = cache.getCache(property);
        if (value == null) return;
        cache.setCache(property, (float) Math.max(0, AttributeModifierHelper.apply((double) value, mods, shooter)));
    }

    private static void scaleInt(AttachmentCacheProperty cache, GunProperty<Integer> property, List<AttributeModifier> mods, LivingEntity shooter, int min) {
        if (mods.isEmpty()) return;
        Integer value = cache.getCache(property);
        if (value == null) return;
        cache.setCache(property, (int) Math.max(min, Math.round(AttributeModifierHelper.apply((double) value, mods, shooter))));
    }

    private static void scaleInaccuracy(AttachmentCacheProperty cache, GunProperty<Map<InaccuracyType, Float>> property, List<AttributeModifier> mods, LivingEntity shooter) {
        if (mods.isEmpty()) return;
        Map<InaccuracyType, Float> value = cache.getCache(property);
        if (value == null) return;
        Map<InaccuracyType, Float> scaled = new EnumMap<>(InaccuracyType.class);
        for (Map.Entry<InaccuracyType, Float> e : value.entrySet()) {
            scaled.put(e.getKey(), (float) Math.max(0, AttributeModifierHelper.apply((double) e.getValue(), mods, shooter)));
        }
        cache.setCache(property, scaled);
    }

    private static void scaleDamage(AttachmentCacheProperty cache, List<AttributeModifier> mods, LivingEntity shooter) {
        if (mods.isEmpty()) return;
        LinkedList<ExtraDamage.DistanceDamagePair> value = cache.getCache(GunProperties.DAMAGE);
        if (value == null) return;
        LinkedList<ExtraDamage.DistanceDamagePair> scaled = new LinkedList<>();
        for (ExtraDamage.DistanceDamagePair pair : value) {
            float damage = (float) Math.max(0, AttributeModifierHelper.apply((double) pair.getDamage(), mods, shooter));
            scaled.add(new ExtraDamage.DistanceDamagePair(pair.getDistance(), damage));
        }
        cache.setCache(GunProperties.DAMAGE, scaled);
    }

    // ParameterizedCache keeps its multiplier private and final; there is no other way to scale recoil from outside.
    private static Field multiplier;
    private static boolean recoilBroken;

    private static void scaleRecoil(AttachmentCacheProperty cache, List<AttributeModifier> mods, LivingEntity shooter) {
        if (mods.isEmpty() || recoilBroken) return;
        ParameterizedCachePair<Float, Float> value = cache.getCache(GunProperties.RECOIL);
        if (value == null) return;
        try {
            if (multiplier == null) {
                multiplier = ParameterizedCache.class.getDeclaredField("multiplier");
                multiplier.setAccessible(true);
            }
            // The cache was built fresh for this event, so changing it in place is safe.
            for (ParameterizedCache<?> side : List.of(value.left(), value.right())) {
                double m = multiplier.getDouble(side);
                multiplier.setDouble(side, Math.max(0, m * AttributeModifierHelper.apply(1.0, mods, shooter)));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            recoilBroken = true;
            AEA.LOGGER.warn("[AEA] aea:modify_gun can't change recoil on this TaCZ version: {}", e.toString());
        }
    }

    /**
     * Aux keys holding a signature of the active configs last applied to the held gun, kept on the holder. Each side
     * computes its own gun stats, so each keeps its own key.
     */
    private static final ResourceLocation APPLIED = ResourceLocation.fromNamespaceAndPath("aea", "modify_gun/applied"),
        APPLIED_CLIENT = ResourceLocation.fromNamespaceAndPath("aea", "modify_gun/applied_client");

    /** Recomputes the held gun's stats when the player's active {@code modify_gun} powers change. Per player per tick. */
    private static void refresh(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof IGun)) return;
        boolean client = player.level().isClientSide();
        if (client && player != ClientPlayer.get()) return;
        if (!(PowerContainer.of(player) instanceof PowerContainerImpl container)) return;
        ResourceLocation key = client ? APPLIED_CLIENT : APPLIED;
        int before = container.getAuxIntOr(key, 0);
        // Nobody holds the type and nothing was applied here: nothing to undo either.
        if (before == 0 && (client ? container.powersOfType(ID).isEmpty() : !HELD.isHeld())) return;
        int now = ActiveConfigs.signature(player, container, ID, Cfg.class);
        if (now == before) return;
        if (now == 0) container.removeAux(key);
        else container.setAuxInt(key, now);
        AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
    }

    private static void forget(Player player) {
        if (PowerContainer.of(player) instanceof PowerContainerImpl container) container.removeAux(APPLIED);
    }

    /** Kept apart so the server never loads client classes. */
    private static final class ClientPlayer {
        static Player get() {
            return net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? Local.get() : null;
        }
    }

    private static final class Local {
        static Player get() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }

    // ---- ammo_save_chance ----

    /** Rounds to give back per gun stack this tick, and the most it held before any of those shots. */
    private static final class Refund {
        final LivingEntity shooter;
        int rounds, before, age;

        Refund(LivingEntity shooter, int before) {
            this.shooter = shooter;
            this.before = before;
        }
    }

    private static final Map<ItemStack, Refund> REFUNDS = new java.util.IdentityHashMap<>();

    private static void onShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !HELD.isHeld()) return;
        LivingEntity shooter = event.getShooter();
        if (!(shooter.level() instanceof ServerLevel)) return;
        ItemStack gunStack = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        List<Cfg> active = matching(shooter, gunStack);
        double keep = 1;
        for (int i = 0; i < active.size(); i++) keep *= 1 - active.get(i).ammoSaveChance();
        if (keep >= 1 || shooter.getRandom().nextDouble() < keep) return;
        int before = gun.getCurrentAmmoCount(gunStack);
        Refund r = REFUNDS.computeIfAbsent(gunStack, s -> new Refund(shooter, before));
        r.rounds++;
        r.before = Math.max(r.before, before);
    }

    /**
     * TaCZ takes the round after the shoot event, sometimes a tick later; give it back at the end of the tick, or the
     * next one if the magazine hasn't gone down yet.
     */
    private static void refundAmmo() {
        if (REFUNDS.isEmpty()) return;
        var it = REFUNDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemStack, Refund> e = it.next();
            ItemStack stack = e.getKey();
            Refund r = e.getValue();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null || r.shooter.isRemoved()) {
                it.remove();
                continue;
            }
            int now = gun.getCurrentAmmoCount(stack);
            if (now >= r.before && r.age++ < 2) continue;
            int target = Math.min(r.before, now + r.rounds);
            if (target > now) gun.setCurrentAmmoCount(stack, target);
            it.remove();
        }
    }
}
