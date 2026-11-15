package dev.alphasucks.aea.hitbox;

import dev.alphasucks.aea.AEA;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the full list of hit boxes one entity has right now. An empty list removes them. */
public final class HitboxNetwork {
    private HitboxNetwork() {}

    private static final int MAX_PARTS = 64;

    /** {@code id} is the network id the server gave the box, so an attack on it reaches the right one. */
    public record Part(int id, float width, float height, float forward, float up, float right, boolean scaleWithEntity) {}

    public record PartsPayload(int owner, Part[] parts) implements CustomPacketPayload {
        public static final Type<PartsPayload> TYPE = new Type<>(AEA.id("hitbox_parts"));
        public static final StreamCodec<FriendlyByteBuf, PartsPayload> CODEC = StreamCodec.of(PartsPayload::write, PartsPayload::read);

        private static void write(FriendlyByteBuf buf, PartsPayload payload) {
            buf.writeVarInt(payload.owner);
            buf.writeVarInt(payload.parts.length);
            for (Part p : payload.parts) {
                buf.writeVarInt(p.id);
                buf.writeFloat(p.width);
                buf.writeFloat(p.height);
                buf.writeFloat(p.forward);
                buf.writeFloat(p.up);
                buf.writeFloat(p.right);
                buf.writeBoolean(p.scaleWithEntity);
            }
        }

        private static PartsPayload read(FriendlyByteBuf buf) {
            int owner = buf.readVarInt();
            Part[] parts = new Part[Math.min(buf.readVarInt(), MAX_PARTS)];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = new Part(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
            }
            return new PartsPayload(owner, parts);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
