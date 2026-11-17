package dev.alphasucks.aea.mindcontrol;

import dev.alphasucks.aea.AEA;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class MindControlNetwork {
    private MindControlNetwork() {}

    public static final int ROLE_NONE = 0, ROLE_CONTROLLER = 1, ROLE_VICTIM = 2;

    /** Server → client: this player's part in a session. {@code other} is the entity id of the other side. */
    public record StatePayload(int role, int other, String otherName, int controls, int struggleNeeded, int struggleCount)
        implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(AEA.id("mind_control_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.role);
                buf.writeVarInt(p.other);
                buf.writeUtf(p.otherName, 64);
                buf.writeVarInt(p.controls);
                buf.writeVarInt(p.struggleNeeded);
                buf.writeVarInt(p.struggleCount);
            },
            buf -> new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        public static final StatePayload NONE = new StatePayload(ROLE_NONE, -1, "", 0, 0, 0);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Controller → server: this tick's input. */
    public record InputPayload(ControlInput input) implements CustomPacketPayload {
        public static final Type<InputPayload> TYPE = new Type<>(AEA.id("mind_control_input"));
        public static final StreamCodec<FriendlyByteBuf, InputPayload> CODEC = StreamCodec.of((buf, p) -> p.input.write(buf), buf -> new InputPayload(ControlInput.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → controlled player: the controller's input, limited to the session's controls. */
    public record RelayPayload(ControlInput input) implements CustomPacketPayload {
        public static final Type<RelayPayload> TYPE = new Type<>(AEA.id("mind_control_relay"));
        public static final StreamCodec<FriendlyByteBuf, RelayPayload> CODEC = StreamCodec.of((buf, p) -> p.input.write(buf), buf -> new RelayPayload(ControlInput.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Controlled player → server: keys they pressed that were ignored, for struggling free. */
    public record StrugglePayload(int presses) implements CustomPacketPayload {
        public static final Type<StrugglePayload> TYPE = new Type<>(AEA.id("mind_control_struggle"));
        public static final StreamCodec<FriendlyByteBuf, StrugglePayload> CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.presses), buf -> new StrugglePayload(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
