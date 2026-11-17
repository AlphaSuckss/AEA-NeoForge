package dev.alphasucks.aea.mindcontrol.client;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.mindcontrol.Control;
import dev.alphasucks.aea.mindcontrol.ControlInput;
import dev.alphasucks.aea.mindcontrol.MindControl;
import dev.alphasucks.aea.mindcontrol.MindControlNetwork;
import dev.alphasucks.aea.mindcontrol.mixin.client.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of mind control. As the controller it reads the keys you press and the way you look, sends them each
 * tick, keeps them from moving your own (spectating) player, and points the camera with your mouse. As the victim it
 * ignores your keys for the controlled parts and presses the relayed ones instead, so the game moves you normally.
 */
public final class MindControlClient {
    private MindControlClient() {}

    private static int role = MindControlNetwork.ROLE_NONE;
    private static int other = -1, controls, struggleNeeded, struggleCount;
    private static String otherName = "";

    // Controller: mouse wheel steps since the last tick.
    static int scroll;
    // Victim: what the controller pressed, waiting to be applied this tick.
    private static ControlInput relay = ControlInput.NONE;
    private static int attacks, uses, drops, swaps, hotbar = -1, relayScroll;
    private static boolean dropAll;
    static int struggle;

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> e.registerEntityRenderer(MindControl.BODY.get(), BodyRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(AEA.id("mind_control"), MindControlClient::renderHud));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> preTick());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> postTick());
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, MindControlClient::cameraAngles);
    }

    public static boolean controlling() {
        return role == MindControlNetwork.ROLE_CONTROLLER;
    }

    /** True for a controlled part of the local player, whose real keys are ignored. */
    public static boolean victimOf(Control control) {
        return role == MindControlNetwork.ROLE_VICTIM && control.in(controls);
    }

    public static void receive(CustomPacketPayload payload) {
        if (payload instanceof MindControlNetwork.StatePayload p) {
            int was = role;
            role = p.role();
            other = p.other();
            otherName = p.otherName();
            controls = p.controls();
            struggleNeeded = p.struggleNeeded();
            struggleCount = p.struggleCount();
            Minecraft mc = Minecraft.getInstance();
            MindControlClientView.role = role;
            MindControlClientView.localId = mc.player == null ? -1 : mc.player.getId();
            MindControlClientView.otherId = other;
            if (was == MindControlNetwork.ROLE_VICTIM && role != MindControlNetwork.ROLE_VICTIM) {
                // Back to your own keys, as they are physically held right now.
                KeyMapping.setAll();
                relay = ControlInput.NONE;
            }
            if (was == MindControlNetwork.ROLE_CONTROLLER && role != MindControlNetwork.ROLE_CONTROLLER && mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            if (role == MindControlNetwork.ROLE_VICTIM && was != MindControlNetwork.ROLE_VICTIM) {
                attacks = uses = drops = swaps = relayScroll = 0;
                hotbar = -1;
            }
        } else if (payload instanceof MindControlNetwork.RelayPayload p && role == MindControlNetwork.ROLE_VICTIM) {
            ControlInput in = p.input();
            relay = in;
            attacks += in.attacks();
            uses += in.uses();
            drops += in.drops();
            swaps += in.swaps();
            relayScroll += in.scroll();
            if (in.hotbar() >= 0) hotbar = in.hotbar();
            dropAll = in.has(ControlInput.DROP_ALL);
        }
    }

    private static void preTick() {
        if (role == MindControlNetwork.ROLE_NONE) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (role == MindControlNetwork.ROLE_CONTROLLER) captureAndSend(mc, player);
        else applyRelay(mc, player);
    }

    private static void captureAndSend(Minecraft mc, LocalPlayer player) {
        Entity victim = mc.level.getEntity(other);
        if (victim != null && mc.getCameraEntity() != victim) mc.setCameraEntity(victim);
        Options o = mc.options;
        int flags = 0, a = 0, u = 0, d = 0, s = 0, h = -1;
        if (mc.screen == null) {
            if (o.keyUp.isDown()) flags |= ControlInput.FORWARD;
            if (o.keyDown.isDown()) flags |= ControlInput.BACK;
            if (o.keyLeft.isDown()) flags |= ControlInput.LEFT;
            if (o.keyRight.isDown()) flags |= ControlInput.RIGHT;
            if (o.keyJump.isDown()) flags |= ControlInput.JUMP;
            if (o.keyShift.isDown()) flags |= ControlInput.SNEAK;
            if (o.keySprint.isDown()) flags |= ControlInput.SPRINT;
            if (o.keyAttack.isDown()) flags |= ControlInput.ATTACK_DOWN;
            if (o.keyUse.isDown()) flags |= ControlInput.USE_DOWN;
            if (Screen.hasControlDown()) flags |= ControlInput.DROP_ALL;
        }
        // Presses are taken here, before the game handles them, so your spectating self doesn't act on them.
        while (o.keyAttack.consumeClick()) a++;
        while (o.keyUse.consumeClick()) u++;
        while (o.keyDrop.consumeClick()) d++;
        while (o.keySwapOffhand.consumeClick()) s++;
        for (int i = 0; i < 9; i++) {
            while (o.keyHotbarSlots[i].consumeClick()) h = i;
        }
        int sc = scroll;
        scroll = 0;
        ControlInput in = new ControlInput(flags, player.getYRot(), player.getXRot(), a, u, d, s, h, sc);
        PacketDistributor.sendToServer(new MindControlNetwork.InputPayload(in));
    }

    private static void applyRelay(Minecraft mc, LocalPlayer player) {
        Options o = mc.options;
        ControlInput in = relay;
        if (Control.MOVE.in(controls)) {
            down(o.keyUp, in.has(ControlInput.FORWARD));
            down(o.keyDown, in.has(ControlInput.BACK));
            down(o.keyLeft, in.has(ControlInput.LEFT));
            down(o.keyRight, in.has(ControlInput.RIGHT));
        }
        if (Control.JUMP.in(controls)) down(o.keyJump, in.has(ControlInput.JUMP));
        if (Control.SNEAK.in(controls)) down(o.keyShift, in.has(ControlInput.SNEAK));
        if (Control.SPRINT.in(controls)) down(o.keySprint, in.has(ControlInput.SPRINT));
        if (Control.ATTACK.in(controls)) {
            down(o.keyAttack, in.has(ControlInput.ATTACK_DOWN));
            click(o.keyAttack, attacks);
        }
        if (Control.USE.in(controls)) {
            down(o.keyUse, in.has(ControlInput.USE_DOWN));
            click(o.keyUse, uses);
        }
        if (Control.SWAP_HANDS.in(controls)) click(o.keySwapOffhand, swaps);
        if (Control.DROP.in(controls)) {
            for (int i = 0; i < drops && !player.isSpectator(); i++) {
                if (player.drop(dropAll)) player.swing(InteractionHand.MAIN_HAND);
            }
        }
        if (Control.HOTBAR.in(controls)) {
            if (hotbar >= 0) player.getInventory().selected = hotbar;
            for (int i = 0; i < Math.abs(relayScroll); i++) player.getInventory().swapPaint(Math.signum(relayScroll));
        }
        attacks = uses = drops = swaps = relayScroll = 0;
        hotbar = -1;
        if (struggle > 0) {
            PacketDistributor.sendToServer(new MindControlNetwork.StrugglePayload(struggle));
            struggle = 0;
        }
    }

    /** Turns the victim after their tick, so the frames until the next one ease from the old direction to the new. */
    private static void postTick() {
        if (role != MindControlNetwork.ROLE_VICTIM || !Control.LOOK.in(controls)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(relay.yRot());
        player.setXRot(relay.xRot());
    }

    private static void down(KeyMapping key, boolean down) {
        ((KeyMappingAccessor) key).aea$setDown(down);
    }

    private static void click(KeyMapping key, int times) {
        if (times > 0) ((KeyMappingAccessor) key).aea$setClickCount(((KeyMappingAccessor) key).aea$clickCount() + times);
    }

    /** The camera follows your mouse straight away instead of waiting for the victim to turn on the server. */
    private static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (role != MindControlNetwork.ROLE_CONTROLLER || !Control.LOOK.in(controls)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getCamera().getEntity() == null || event.getCamera().getEntity().getId() != other) return;
        float t = (float) event.getPartialTick();
        event.setYaw(mc.player.getViewYRot(t));
        event.setPitch(mc.player.getViewXRot(t));
    }

    private static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        if (role == MindControlNetwork.ROLE_NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Component text = role == MindControlNetwork.ROLE_CONTROLLER
            ? Component.translatable("aea.mind_control.controlling", otherName)
            : Component.translatable("aea.mind_control.controlled", otherName);
        int cx = graphics.guiWidth() / 2;
        int y = 22;
        graphics.drawCenteredString(mc.font, text, cx, y, 0xD9A6FF);
        if (role == MindControlNetwork.ROLE_VICTIM && struggleNeeded > 0) {
            int w = 102, x = cx - w / 2, by = y + 12;
            graphics.fill(x, by, x + w, by + 5, 0xC0000000);
            int filled = Math.min(w - 2, (w - 2) * struggleCount / struggleNeeded);
            graphics.fill(x + 1, by + 1, x + 1 + filled, by + 4, 0xFFB45CFF);
            graphics.drawCenteredString(mc.font, Component.translatable("aea.mind_control.struggle"), cx, by + 8, 0xBBBBBB);
        }
    }

    static List<KeyMapping> filter(List<KeyMapping> mappings, boolean press) {
        Options o = Minecraft.getInstance().options;
        List<KeyMapping> kept = null;
        for (int i = 0; i < mappings.size(); i++) {
            KeyMapping k = mappings.get(i);
            if (k != null && controlled(o, k)) {
                if (kept == null) {
                    kept = new ArrayList<>(mappings.size());
                    for (int j = 0; j < i; j++) kept.add(mappings.get(j));
                }
                if (press) struggle++;
            } else if (kept != null) {
                kept.add(k);
            }
        }
        return kept == null ? mappings : kept;
    }

    private static boolean controlled(Options o, KeyMapping k) {
        if (k == o.keyUp || k == o.keyDown || k == o.keyLeft || k == o.keyRight) return Control.MOVE.in(controls);
        if (k == o.keyJump) return Control.JUMP.in(controls);
        if (k == o.keyShift) return Control.SNEAK.in(controls);
        if (k == o.keySprint) return Control.SPRINT.in(controls);
        if (k == o.keyAttack) return Control.ATTACK.in(controls);
        if (k == o.keyUse) return Control.USE.in(controls);
        if (k == o.keyDrop) return Control.DROP.in(controls);
        if (k == o.keySwapOffhand) return Control.SWAP_HANDS.in(controls);
        if (k == o.keyChat) return Control.CHAT.in(controls);
        if (k == o.keyCommand) return Control.COMMANDS.in(controls) || Control.CHAT.in(controls);
        for (KeyMapping slot : o.keyHotbarSlots) if (k == slot) return Control.HOTBAR.in(controls);
        return false;
    }

    /** Whether the local player's own keys are being filtered at all. */
    static boolean victim() {
        return role == MindControlNetwork.ROLE_VICTIM;
    }
}
