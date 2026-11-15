package dev.alphasucks.aea.textures;

import dev.alphasucks.aea.AEA;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format of data pack textures. The server lists what it has, the client asks for what it lacks, and the server
 * sends those files in chunks. Every message carries the generation of the list it belongs to.
 */
public final class TextureNetwork {
    private TextureNetwork() {}

    /** One texture in the server's list: its id, the SHA-1 of the file and its size in bytes. */
    public record Listed(ResourceLocation id, String hash, int size) {}

    public record ManifestPayload(int generation, List<Listed> textures) implements CustomPacketPayload {
        public static final Type<ManifestPayload> TYPE = new Type<>(AEA.id("textures_manifest"));
        public static final StreamCodec<FriendlyByteBuf, ManifestPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeVarInt(p.textures.size());
            for (Listed t : p.textures) {
                buf.writeResourceLocation(t.id);
                buf.writeUtf(t.hash, 40);
                buf.writeVarInt(t.size);
            }
        }, buf -> {
            int generation = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), PackTextures.MAX_COUNT);
            List<Listed> textures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) textures.add(new Listed(buf.readResourceLocation(), buf.readUtf(40), buf.readVarInt()));
            return new ManifestPayload(generation, textures);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestPayload(int generation, List<ResourceLocation> ids) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(AEA.id("textures_request"));
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeVarInt(p.ids.size());
            for (ResourceLocation id : p.ids) buf.writeResourceLocation(id);
        }, buf -> {
            int generation = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), PackTextures.MAX_COUNT);
            List<ResourceLocation> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
            return new RequestPayload(generation, ids);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code data} is the slice of the file that starts at {@code offset}. */
    public record ChunkPayload(int generation, ResourceLocation id, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<ChunkPayload> TYPE = new Type<>(AEA.id("textures_chunk"));
        public static final StreamCodec<FriendlyByteBuf, ChunkPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeResourceLocation(p.id);
            buf.writeVarInt(p.offset);
            buf.writeByteArray(p.data);
        }, buf -> new ChunkPayload(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt(), buf.readByteArray(PackTextures.CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
