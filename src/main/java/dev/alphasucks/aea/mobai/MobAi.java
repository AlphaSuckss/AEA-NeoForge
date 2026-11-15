package dev.alphasucks.aea.mobai;

import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** AEA 1.5.4 mob AI: aea:mob_ai and aea:mob_pathing. */
public final class MobAi {
    private MobAi() {}

    private static boolean registered;
    private static final Set<Mob> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    /** Registers the power types (from AEATypes.registerAll) and the server tick hook. */
    public static void register() {
        if (registered) return;
        registered = true;
        if (!PowerTypeRegistry.view().containsKey(MobPathingPower.ID)) PowerTypeRegistry.register(MobPathingPower.ID, new MobPathingPower());
        if (!PowerTypeRegistry.view().containsKey(MobAiPower.ID)) PowerTypeRegistry.register(MobAiPower.ID, new MobAiPower());
        // After Apoli's own ServerTickEvent.Post listener, which is where Overgrown ticks powers.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> endServerTick());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> TRACKED.clear());
    }

    static void track(Mob mob) {
        TRACKED.add(mob);
    }

    private static void endServerTick() {
        MobAiPower.runDeferred();
        if (TRACKED.isEmpty()) return;
        for (Mob mob : new ArrayList<>(TRACKED)) {
            if (mob.isRemoved()) {
                TRACKED.remove(mob);
                continue;
            }
            try {
                MobAiPower.tickStored(mob);
            } catch (RuntimeException ignored) {
            }
            if (MobAiPower.state(mob).applied.isEmpty()) TRACKED.remove(mob);
        }
    }
}
