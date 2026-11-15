package dev.alphasucks.aea.gui;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.gui.action.GuiActions;
import dev.alphasucks.aea.gui.condition.GuiConditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * AEA's data-driven GUI ({@code aea:gui}), ported from AEA 1.5.4 for Fabric 1.20.1.
 * Registers the power type, its actions and conditions, the menu, the memory attachment and the network payloads.
 */
public final class AeaGui {
    private AeaGui() {}

    public static final ResourceLocation POWER_TYPE_ID = AEA.id("gui");

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AEA.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AEA.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<GuiMenu>> MENU =
        MENUS.register("gui", () -> IMenuTypeExtension.create(GuiMenu::createClient));
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> MEMORY =
        ATTACHMENTS.register("gui_memory", () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    private static boolean initialised;

    public static void register(IEventBus modBus) {
        if (initialised) return;
        initialised = true;
        GuiMemory.TYPE = MEMORY;
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(AeaGui::registerPayloads);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(AeaGui::registerTypes));
        NeoForge.EVENT_BUS.addListener(AeaGui::tickServer);
        NeoForge.EVENT_BUS.addListener(AeaGui::onLogout);
    }

    /** Power, action and condition types; called once during common setup. */
    public static void registerTypes() {
        Reg.power(POWER_TYPE_ID.toString(), new GuiPowerType());
        GuiActions.register();
        GuiConditions.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(GuiNetwork.ButtonPayload.TYPE, GuiNetwork.ButtonPayload.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GuiSession session = sessionFor(player, p.syncId());
            if (session == null || p.element() < 0 || p.element() >= session.h().model.elementCount()) return;
            if (p.pressed()) session.pressButton(p.element());
            else session.releaseButton(p.element());
        });
        registrar.playToServer(GuiNetwork.SliderPayload.TYPE, GuiNetwork.SliderPayload.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GuiSession session = sessionFor(player, p.syncId());
            if (session == null || p.element() < 0 || p.element() >= session.h().model.elementCount()) return;
            session.moveSlider(p.element(), p.value(), p.release());
        });
        registrar.playToClient(GuiNetwork.StatePayload.TYPE, GuiNetwork.StatePayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) dev.alphasucks.aea.gui.client.AeaGuiClient.applyState(p);
        });
    }

    @Nullable
    private static GuiSession sessionFor(ServerPlayer player, int syncId) {
        return player.containerMenu instanceof GuiMenu menu && menu.containerId == syncId && !menu.isClosed() ? menu.session() : null;
    }

    private static void tickServer(ServerTickEvent.Post event) {
        for (ServerPlayer player : List.copyOf(event.getServer().getPlayerList().getPlayers())) {
            if (player.containerMenu instanceof GuiMenu menu && menu.session() != null) menu.session().tick();
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof GuiMenu) player.doCloseContainer();
    }

    /** Opens the GUI of {@code powerId} if the player holds that power and it is active. */
    public static boolean open(ServerPlayer player, ResourceLocation powerId) {
        GuiPowers.Held held = GuiPowers.find(player, powerId);
        if (held == null || player.level().isClientSide()) return false;
        boolean active;
        try {
            active = GuiPowers.isActive(player, powerId);
        } catch (RuntimeException e) {
            active = false;
        }
        if (!active || !player.isAlive()) return false;
        if (player.containerMenu instanceof GuiMenu current && current.session() != null && current.session().closing()) {
            // Opened from an action of a GUI that is closing: open once that close has finished.
            MinecraftServer server = player.server;
            server.execute(() -> open(player, powerId));
            return true;
        }
        GuiConfig config = held.config();
        ResourceLocation guiId = config.guiId(powerId);
        GuiMenu[] created = new GuiMenu[1];
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, p) -> created[0] = GuiMenu.createServer(id, inventory, player, powerId, guiId, config),
            config.title().orElse(Component.empty())), buf -> GuiNetwork.writeOpenData(created[0], buf));
        return true;
    }
}
