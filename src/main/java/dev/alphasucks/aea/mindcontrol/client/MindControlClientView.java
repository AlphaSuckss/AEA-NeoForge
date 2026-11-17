package dev.alphasucks.aea.mindcontrol.client;

import dev.alphasucks.aea.mindcontrol.MindControlNetwork;
import net.minecraft.world.entity.Entity;

/**
 * What this client knows about the local player's session, for conditions checked on the client. Holds plain values
 * only, so the server can load it too.
 */
public final class MindControlClientView {
    private MindControlClientView() {}

    static volatile int role = MindControlNetwork.ROLE_NONE;
    static volatile int localId = -1, otherId = -1;

    public static boolean isControlled(Entity e) {
        return role == MindControlNetwork.ROLE_VICTIM && e.getId() == localId
            || role == MindControlNetwork.ROLE_CONTROLLER && e.getId() == otherId;
    }

    public static boolean isControlling(Entity e) {
        return role == MindControlNetwork.ROLE_CONTROLLER && e.getId() == localId
            || role == MindControlNetwork.ROLE_VICTIM && e.getId() == otherId;
    }

    public static boolean controls(Entity actor, Entity target) {
        return isControlling(actor) && isControlled(target);
    }
}
