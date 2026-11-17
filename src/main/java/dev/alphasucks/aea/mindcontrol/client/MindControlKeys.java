package dev.alphasucks.aea.mindcontrol.client;

import dev.alphasucks.aea.mindcontrol.Control;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

/** What the client input mixins ask; all cheap when there is no session. */
public final class MindControlKeys {
    private MindControlKeys() {}

    public static List<KeyMapping> filter(List<KeyMapping> mappings, boolean press) {
        return MindControlClient.victim() ? MindControlClient.filter(mappings, press) : mappings;
    }

    public static boolean controlling() {
        return MindControlClient.controlling();
    }

    public static boolean mayTurn() {
        return !MindControlClient.victimOf(Control.LOOK);
    }

    /** True when the wheel event is taken: by the controller for the victim's hotbar, or away from a victim's own. */
    public static boolean scroll(double y) {
        if (y == 0 || Minecraft.getInstance().screen != null) return false;
        if (MindControlClient.controlling()) {
            MindControlClient.scroll += (int) Math.signum(y);
            return true;
        }
        if (MindControlClient.victimOf(Control.HOTBAR)) {
            MindControlClient.struggle++;
            return true;
        }
        return false;
    }
}
