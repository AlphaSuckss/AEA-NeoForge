package dev.alphasucks.aea.mindcontrol;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a mind control session takes over. Each is a bit in a mask sent to the clients. */
public enum Control implements StringRepresentable {
    MOVE("move"), LOOK("look"), JUMP("jump"), SNEAK("sneak"), SPRINT("sprint"), ATTACK("attack"), USE("use"),
    HOTBAR("hotbar"), DROP("drop"), SWAP_HANDS("swap_hands"), CHAT("chat"), COMMANDS("commands");

    public static final Codec<Control> CODEC = StringRepresentable.fromEnum(Control::values);
    public static final int ALL = (1 << values().length) - 1;

    private final String name;

    Control(String name) {
        this.name = name;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
