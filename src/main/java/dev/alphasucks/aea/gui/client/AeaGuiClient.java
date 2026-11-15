package dev.alphasucks.aea.gui.client;

import dev.alphasucks.aea.gui.AeaGui;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of {@code aea:gui}: the screen and incoming state updates. */
public final class AeaGuiClient {
    private AeaGuiClient() {}

    private static boolean initialised;

    public static void init(IEventBus modBus) {
        if (initialised) return;
        initialised = true;
        modBus.addListener(AeaGuiClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AeaGui.MENU.get(), GuiScreen::new);
    }

    public static void applyState(GuiNetwork.StatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof GuiMenu menu && menu.containerId == payload.syncId()
            && !menu.broken && menu.isClient()) {
            payload.state().applyTo(menu);
        }
    }
}
