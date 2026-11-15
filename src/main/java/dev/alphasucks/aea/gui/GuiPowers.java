package dev.alphasucks.aea.gui;

import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Lookups of an entity's {@code aea:gui} powers and of the GUI it currently has open. */
public final class GuiPowers {
    private GuiPowers() {}

    /** A held {@code aea:gui} power. */
    public record Held(ResourceLocation powerId, GuiConfig config) {}

    private static final ThreadLocal<GuiMenu> OPENING = new ThreadLocal<>();

    @Nullable
    public static Held find(@Nullable Entity entity, ResourceLocation powerId) {
        for (Held h : all(entity)) if (h.powerId().equals(powerId)) return h;
        return null;
    }

    public static List<Held> all(@Nullable Entity entity) {
        if (entity == null) return List.of();
        PowerContainer container;
        try {
            container = PowerContainer.of(entity);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (container == null || container.isEmpty()) return List.of();
        List<Held> out = new ArrayList<>(2);
        for (ResourceLocation id : container.powersOfType(AeaGui.POWER_TYPE_ID)) {
            Power p = ApoliPowers.get(id);
            if (p != null && p.config() instanceof GuiConfig cfg) out.add(new Held(id, cfg));
        }
        return out;
    }

    /** Whether the held power is active (not suppressed and its condition passes). */
    public static boolean isActive(Entity entity, ResourceLocation powerId) {
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || !container.hasPower(powerId) || container.isSuppressed(powerId)) return false;
        Power p = ApoliPowers.get(powerId);
        if (p == null) return false;
        return p.condition().map(c -> c.test(EntityCtx.of(entity, entity.level()))).orElse(true);
    }

    /** The GUI config of a power: the entity's own if it holds it, else the loaded definition. */
    @Nullable
    public static GuiConfig config(@Nullable Entity entity, ResourceLocation powerId) {
        Held h = find(entity, powerId);
        if (h != null) return h.config();
        Power p = ApoliPowers.get(powerId);
        return p != null && p.config() instanceof GuiConfig cfg ? cfg : null;
    }

    @Nullable
    public static Held byGuiId(@Nullable Entity entity, ResourceLocation guiId) {
        for (Held h : all(entity)) if (h.config().guiId(h.powerId()).equals(guiId)) return h;
        return null;
    }

    static void beginOpening(GuiMenu menu) {
        OPENING.set(menu);
    }

    static void endOpening() {
        OPENING.remove();
    }

    /** The {@code aea:gui} the entity has open (also while it is being opened). */
    @Nullable
    public static GuiMenu open(@Nullable Entity entity) {
        GuiMenu opening = OPENING.get();
        if (opening != null && opening.player == entity) return opening;
        if (entity instanceof Player player && player.containerMenu instanceof GuiMenu menu && !menu.isClosed()) return menu;
        return null;
    }
}
