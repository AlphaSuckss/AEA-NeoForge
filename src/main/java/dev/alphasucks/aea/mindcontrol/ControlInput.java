package dev.alphasucks.aea.mindcontrol;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One tick of the controller's input. Held keys are flags; presses are counts, so none are lost between ticks.
 * {@code hotbar} is the slot picked with a number key (-1 for none), {@code scroll} the mouse wheel steps.
 */
public record ControlInput(int flags, float yRot, float xRot, int attacks, int uses, int drops, int swaps, int hotbar, int scroll) {
    public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, JUMP = 16, SNEAK = 32, SPRINT = 64,
        ATTACK_DOWN = 128, USE_DOWN = 256, DROP_ALL = 512;
    public static final ControlInput NONE = new ControlInput(0, 0, 0, 0, 0, 0, 0, -1, 0);

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Only what the session's controls cover. */
    public ControlInput masked(int controls) {
        int f = 0;
        if (Control.MOVE.in(controls)) f |= flags & (FORWARD | BACK | LEFT | RIGHT);
        if (Control.JUMP.in(controls)) f |= flags & JUMP;
        if (Control.SNEAK.in(controls)) f |= flags & SNEAK;
        if (Control.SPRINT.in(controls)) f |= flags & SPRINT;
        if (Control.ATTACK.in(controls)) f |= flags & ATTACK_DOWN;
        if (Control.USE.in(controls)) f |= flags & USE_DOWN;
        if (Control.DROP.in(controls)) f |= flags & DROP_ALL;
        boolean hotbarOn = Control.HOTBAR.in(controls);
        return new ControlInput(f, yRot, xRot,
            Control.ATTACK.in(controls) ? attacks : 0, Control.USE.in(controls) ? uses : 0,
            Control.DROP.in(controls) ? drops : 0, Control.SWAP_HANDS.in(controls) ? swaps : 0,
            hotbarOn ? hotbar : -1, hotbarOn ? scroll : 0);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(flags);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        buf.writeByte(Math.min(attacks, 20));
        buf.writeByte(Math.min(uses, 20));
        buf.writeByte(Math.min(drops, 20));
        buf.writeByte(Math.min(swaps, 20));
        buf.writeByte(hotbar);
        buf.writeByte(Math.max(-20, Math.min(scroll, 20)));
    }

    public static ControlInput read(FriendlyByteBuf buf) {
        int flags = buf.readVarInt();
        float y = buf.readFloat(), x = buf.readFloat();
        int a = buf.readByte(), u = buf.readByte(), d = buf.readByte(), s = buf.readByte(), h = buf.readByte(), sc = buf.readByte();
        if (!Float.isFinite(y)) y = 0;
        if (!Float.isFinite(x)) x = 0;
        return new ControlInput(flags, y, Math.max(-90, Math.min(90, x)), Math.max(0, a), Math.max(0, u), Math.max(0, d),
            Math.max(0, s), h >= 0 && h < 9 ? h : -1, sc);
    }
}
