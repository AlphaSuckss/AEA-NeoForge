package dev.alphasucks.aea.assets;

import dev.alphasucks.aea.AEA;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format of data pack assets. It has the same shape as data pack textures: the server lists its files, the
 * client asks for the ones it lacks, and the server sends them in chunks.
 */
public final class AssetNetwork {
    private AssetNetwork() {}

    static final int MAX_PATH = 256;

    /** One file: its path under {@code assets/}, the SHA-1 of its contents and its size in bytes. */
    public record Listed(String path, String hash, int size) {}

    /** {@code packHash} identifies the whole set, so a client that already applied it does nothing. */
    public record ManifestPayload(int generation, String packHash, List<Listed> files) implements CustomPacketPayload {
        public static final Type<ManifestPayload> TYPE = new Type<>(AEA.id("assets_manifest"));
        public static final StreamCodec<FriendlyByteBuf, ManifestPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeUtf(p.packHash, 40);
            buf.writeVarInt(p.files.size());
            for (Listed f : p.files) {
                buf.writeUtf(f.path, MAX_PATH);
                buf.writeUtf(f.hash, 40);
                buf.writeVarInt(f.size);
            }
        }, buf -> {
            int generation = buf.readVarInt();
            String packHash = buf.readUtf(40);
            int count = Math.min(buf.readVarInt(), PackAssets.MAX_COUNT);
            List<Listed> files = new ArrayList<>(count);
            for (int i = 0; i < count; i++) files.add(new Listed(buf.readUtf(MAX_PATH), buf.readUtf(40), buf.readVarInt()));
            return new ManifestPayload(generation, packHash, files);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestPayload(int generation, List<String> paths) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(AEA.id("assets_request"));
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeVarInt(p.paths.size());
            for (String path : p.paths) buf.writeUtf(path, MAX_PATH);
        }, buf -> {
            int generation = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), PackAssets.MAX_COUNT);
            List<String> paths = new ArrayList<>(count);
            for (int i = 0; i < count; i++) paths.add(buf.readUtf(MAX_PATH));
            return new RequestPayload(generation, paths);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code data} is the slice of the file that starts at {@code offset}. */
    public record ChunkPayload(int generation, String path, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<ChunkPayload> TYPE = new Type<>(AEA.id("assets_chunk"));
        public static final StreamCodec<FriendlyByteBuf, ChunkPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.generation);
            buf.writeUtf(p.path, MAX_PATH);
            buf.writeVarInt(p.offset);
            buf.writeByteArray(p.data);
        }, buf -> new ChunkPayload(buf.readVarInt(), buf.readUtf(MAX_PATH), buf.readVarInt(), buf.readByteArray(PackAssets.CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
