package dev.alphasucks.aea.compat.sync;

import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Port of Sync's StolenPowerSlotManager + TransferModeManager on top of Overgrown's power containers. */
public final class StolenPowers {
    private StolenPowers() {}

    public static final ResourceLocation DEFAULT_SOURCE = ResourceLocation.fromNamespaceAndPath("sync", "transferred");

    private static final Map<UUID, LinkedHashMap<ResourceLocation, List<ResourceLocation>>> PACKAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SELECTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> STEALING = new ConcurrentHashMap<>();

    public static List<ResourceLocation> topLevel(List<ResourceLocation> powers) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation p : powers) if (!ApoliPowers.isSubPower(p)) out.add(p);
        return out;
    }

    public static List<ResourceLocation> powersFromSource(PowerContainer c, ResourceLocation source) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation p : c.allPowers()) if (c.sourcesOf(p).contains(source)) out.add(p);
        return out;
    }

    public static void registerSteal(UUID actor, ResourceLocation source, List<ResourceLocation> powers) {
        List<ResourceLocation> top = topLevel(powers);
        if (top.isEmpty()) return;
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> pk = PACKAGES.computeIfAbsent(actor, k -> new LinkedHashMap<>());
        synchronized (pk) {
            pk.merge(source, new ArrayList<>(top), (a, b) -> {
                Set<ResourceLocation> s = new LinkedHashSet<>(a);
                s.addAll(b);
                return new ArrayList<>(s);
            });
        }
        SELECTED.putIfAbsent(actor, 0);
    }

    public static void deregister(UUID actor, ResourceLocation source) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> pk = PACKAGES.get(actor);
        if (pk == null) return;
        synchronized (pk) {
            pk.remove(source);
            if (pk.isEmpty()) {
                PACKAGES.remove(actor);
                SELECTED.remove(actor);
            }
        }
    }

    public static List<ResourceLocation> sources(Entity e) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> pk = PACKAGES.get(e.getUUID());
        if (pk == null) return Collections.emptyList();
        synchronized (pk) {
            return new ArrayList<>(pk.keySet());
        }
    }

    public static ResourceLocation selected(Entity e) {
        List<ResourceLocation> s = sources(e);
        if (s.isEmpty()) return null;
        int i = Math.floorMod(SELECTED.getOrDefault(e.getUUID(), 0), s.size());
        return s.get(i);
    }

    public static List<ResourceLocation> powersFor(Entity e, ResourceLocation source) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> pk = PACKAGES.get(e.getUUID());
        if (pk == null) return Collections.emptyList();
        synchronized (pk) {
            List<ResourceLocation> l = pk.get(source);
            return l == null ? Collections.emptyList() : new ArrayList<>(l);
        }
    }

    public static boolean isSourceSelected(Entity e, ResourceLocation source) {
        ResourceLocation s = selected(e);
        return s != null && s.equals(source);
    }

    public static void cycle(Entity e, int delta) {
        List<ResourceLocation> s = sources(e);
        if (s.isEmpty()) {
            SELECTED.remove(e.getUUID());
            if (e instanceof ServerPlayer p) p.displayClientMessage(Component.literal("No stolen powers to cycle."), true);
            return;
        }
        int cur = Math.floorMod(SELECTED.getOrDefault(e.getUUID(), 0), s.size());
        int next = Math.floorMod(cur + delta, s.size());
        SELECTED.put(e.getUUID(), next);
        if (e instanceof ServerPlayer p) p.displayClientMessage(Component.literal("[" + (next + 1) + "/" + s.size() + "] " + s.get(next)), true);
    }

    public static boolean isStealing(Entity e) {
        return STEALING.getOrDefault(e.getUUID(), true);
    }

    public static void toggleMode(Entity e) {
        boolean steal = !isStealing(e);
        STEALING.put(e.getUUID(), steal);
        if (e instanceof ServerPlayer p) {
            p.displayClientMessage(Component.literal("⚡ Mode: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(steal ? "STEAL" : "GIVE").withStyle(Style.EMPTY.withBold(true)
                    .withColor(steal ? ChatFormatting.RED : ChatFormatting.GREEN))), true);
        }
    }

    public static void forget(UUID uuid) {
        PACKAGES.remove(uuid);
        SELECTED.remove(uuid);
        STEALING.remove(uuid);
    }
}
