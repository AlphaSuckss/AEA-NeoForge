package dev.alphasucks.aea.compat.tacz;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import dev.alphasucks.aea.compat.ActiveConfigs;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeUsage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code aea:prevent_gun_use}: the holder can't shoot, aim, reload or melee with TaCZ guns, each switchable. Shooting,
 * reloading and melee are cancelled through TaCZ's own events on both sides, so nothing is predicted on the client
 * either; aiming has no event, so the client lets go of the aim key and the server drops any aim each tick.
 */
public final class PreventGunUsePower extends PowerType<PreventGunUsePower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "prevent_gun_use");
    public static final PowerTypeUsage.Handle HELD = PowerTypeUsage.handle(ID);

    public record Cfg(GunFilter filter, boolean shoot, boolean aim, boolean reload, boolean melee, Optional<Component> message) {
        boolean matches(ItemStack gun) {
            return filter.any() ? IGun.getIGunOrNull(gun) != null : filter.test(gun);
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        GunFilter.FIELDS.forGetter(Cfg::filter),
        Codec.BOOL.optionalFieldOf("shoot", true).forGetter(Cfg::shoot),
        Codec.BOOL.optionalFieldOf("aim", true).forGetter(Cfg::aim),
        Codec.BOOL.optionalFieldOf("reload", true).forGetter(Cfg::reload),
        Codec.BOOL.optionalFieldOf("melee", true).forGetter(Cfg::melee),
        ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(Cfg::message)
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
        NeoForge.EVENT_BUS.addListener(GunShootEvent.class, e -> block(e, e.getShooter(), e.getGunItemStack(), Cfg::shoot));
        NeoForge.EVENT_BUS.addListener(GunReloadEvent.class, e -> block(e, e.getEntity(), e.getGunItemStack(), Cfg::reload));
        NeoForge.EVENT_BUS.addListener(GunMeleeEvent.class, e -> block(e, e.getShooter(), e.getGunItemStack(), Cfg::melee));
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> stopAiming(e.getEntity()));
        if (FMLEnvironment.dist.isClient()) PreventGunUseClient.register();
    }

    /** The first active power that blocks {@code what} for this gun, or null. Runs per tick for aiming; no allocation. */
    static Cfg blocking(LivingEntity entity, ItemStack gun, Predicate<Cfg> what) {
        if (entity == null || (!entity.level().isClientSide() && !HELD.isHeld())) return null;
        PowerContainer container = ActiveConfigs.holder(entity, ID);
        if (container == null) return null;
        List<ResourceLocation> powers = container.powersOfType(ID);
        for (int i = 0; i < powers.size(); i++) {
            Cfg cfg = ActiveConfigs.config(entity, container, powers.get(i), Cfg.class);
            if (cfg != null && what.test(cfg) && cfg.matches(gun)) return cfg;
        }
        return null;
    }

    private static void block(ICancellableEvent event, LivingEntity entity, ItemStack gun, Predicate<Cfg> what) {
        Cfg cfg = blocking(entity, gun, what);
        if (cfg == null) return;
        event.setCanceled(true);
        // Whichever side blocks first shows it: a shot the client already blocked never reaches the server.
        if (cfg.message().isPresent() && entity instanceof Player player) player.displayClientMessage(cfg.message().get(), true);
    }

    static final Predicate<Cfg> AIM = Cfg::aim;

    private static void stopAiming(Player player) {
        if (player.level().isClientSide() || !HELD.isHeld()) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof IGun) || blocking(player, held, AIM) == null) return;
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator.getSynIsAiming()) operator.aim(false);
    }
}
