package dev.alphasucks.aea.textures.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.textures.PackTextures;
import dev.alphasucks.aea.textures.TextureNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of data pack textures. Files the server lists are taken from a disk cache when the same file was
 * downloaded before, and requested otherwise. A texture a resource pack already provides is left to the resource pack.
 */
public final class ClientPackTextures {
    private ClientPackTextures() {}

    private static final int MAX_DIMENSION = 4096;

    /** A download in progress. */
    private static final class Incoming {
        final TextureNetwork.Listed listed;
        final byte[] data;
        int received;

        Incoming(TextureNetwork.Listed listed) {
            this.listed = listed;
            this.data = new byte[listed.size()];
        }
    }

    private static int generation = -1;
    /** Textures this class registered, by id, with the hash of the file each came from. */
    private static final Map<ResourceLocation, String> REGISTERED = new HashMap<>();
    private static final Map<ResourceLocation, String> WANTED = new HashMap<>();
    private static final Map<ResourceLocation, Incoming> INCOMING = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> clear());
    }

    private static Path cacheFile(String hash) {
        return FMLPaths.GAMEDIR.get().resolve("aea-texture-cache").resolve(hash + ".png");
    }

    private static boolean acceptable(TextureNetwork.Listed t) {
        String path = t.id().getPath();
        return path.startsWith("textures/") && path.endsWith(".png") && t.size() > 0 && t.size() <= PackTextures.MAX_FILE
            && t.hash().length() == 40 && t.hash().chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }

    public static void onManifest(TextureNetwork.ManifestPayload manifest) {
        Minecraft mc = Minecraft.getInstance();
        generation = manifest.generation();
        INCOMING.clear();
        WANTED.clear();
        for (TextureNetwork.Listed t : manifest.textures()) {
            if (acceptable(t)) WANTED.put(t.id(), t.hash());
        }
        // Drop what the server no longer lists, or lists with different contents.
        for (ResourceLocation id : new ArrayList<>(REGISTERED.keySet())) {
            if (!REGISTERED.get(id).equals(WANTED.get(id))) release(id);
        }
        List<ResourceLocation> missing = new ArrayList<>();
        for (TextureNetwork.Listed t : manifest.textures()) {
            if (!WANTED.containsKey(t.id()) || REGISTERED.containsKey(t.id())) continue;
            if (mc.getResourceManager().getResource(t.id()).isPresent()) continue;
            byte[] cached = readCache(t);
            if (cached != null) {
                decode(t.id(), t.hash(), cached);
            } else {
                INCOMING.put(t.id(), new Incoming(t));
                missing.add(t.id());
            }
        }
        if (!missing.isEmpty()) PacketDistributor.sendToServer(new TextureNetwork.RequestPayload(generation, missing));
    }

    private static byte[] readCache(TextureNetwork.Listed t) {
        Path file = cacheFile(t.hash());
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != t.size()) return null;
            byte[] data = Files.readAllBytes(file);
            return PackTextures.sha1(data).equals(t.hash()) ? data : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void onChunk(TextureNetwork.ChunkPayload chunk) {
        if (chunk.generation() != generation) return;
        Incoming incoming = INCOMING.get(chunk.id());
        if (incoming == null) return;
        byte[] slice = chunk.data();
        // Chunks arrive in order; anything else means the two sides disagree, so the file is abandoned.
        if (chunk.offset() != incoming.received || slice.length > incoming.data.length - incoming.received) {
            INCOMING.remove(chunk.id());
            return;
        }
        System.arraycopy(slice, 0, incoming.data, incoming.received, slice.length);
        incoming.received += slice.length;
        if (incoming.received < incoming.data.length) return;
        INCOMING.remove(chunk.id());
        String hash = incoming.listed.hash();
        if (!PackTextures.sha1(incoming.data).equals(hash)) {
            AEA.LOGGER.warn("[AEA] Data pack texture {} arrived damaged and was dropped.", chunk.id());
            return;
        }
        byte[] data = incoming.data;
        Util.ioPool().execute(() -> {
            try {
                Path file = cacheFile(hash);
                Files.createDirectories(file.getParent());
                Files.write(file, data);
            } catch (IOException e) {
                AEA.LOGGER.warn("[AEA] Could not cache data pack texture {}: {}", chunk.id(), e.toString());
            }
        });
        decode(chunk.id(), hash, data);
    }

    /** Width and height from the PNG header, or null when the file is not a PNG. */
    private static int[] pngSize(byte[] data) {
        if (data.length < 24 || (data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') return null;
        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        return new int[]{width, height};
    }

    /** Decodes off the main thread, then registers the texture on it. */
    private static void decode(ResourceLocation id, String hash, byte[] data) {
        int[] size = pngSize(data);
        if (size == null || size[0] < 1 || size[1] < 1 || size[0] > MAX_DIMENSION || size[1] > MAX_DIMENSION) {
            AEA.LOGGER.warn("[AEA] Data pack texture {} is not a PNG of at most {}x{} and was skipped.", id, MAX_DIMENSION, MAX_DIMENSION);
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            NativeImage image;
            try {
                image = NativeImage.read(new ByteArrayInputStream(data));
            } catch (IOException | RuntimeException e) {
                AEA.LOGGER.warn("[AEA] Could not decode data pack texture {}: {}", id, e.toString());
                return;
            }
            Minecraft.getInstance().execute(() -> {
                // The server may have reloaded, or the player left, while this was decoding.
                if (!hash.equals(WANTED.get(id))) {
                    image.close();
                    return;
                }
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                REGISTERED.put(id, hash);
            });
        });
    }

    private static void release(ResourceLocation id) {
        REGISTERED.remove(id);
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private static void clear() {
        generation = -1;
        WANTED.clear();
        INCOMING.clear();
        for (ResourceLocation id : new ArrayList<>(REGISTERED.keySet())) release(id);
    }
}
