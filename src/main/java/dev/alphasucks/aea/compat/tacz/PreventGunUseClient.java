package dev.alphasucks.aea.compat.tacz;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client half of {@link PreventGunUsePower}. TaCZ keeps the local player's aim on the client and re-aims every tick
 * while the aim key is down, so the key is let go before TaCZ reads it, and any aim left over is dropped.
 */
final class PreventGunUseClient {
    private PreventGunUseClient() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> tick());
    }

    private static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof IGun) || PreventGunUsePower.blocking(player, held, PreventGunUsePower.Cfg::aim) == null) return;
        if (AimKey.AIM_KEY.isDown()) AimKey.AIM_KEY.setDown(false);
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator.isAim()) operator.aim(false);
    }
}
