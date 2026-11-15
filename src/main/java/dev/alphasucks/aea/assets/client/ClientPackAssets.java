package dev.alphasucks.aea.assets.client;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.assets.AssetNetwork;
import dev.alphasucks.aea.assets.PackAssets;
import dev.alphasucks.aea.textures.PackTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client side of data pack assets. Files are kept in a cache folder under their hash, so each one is downloaded once.
 * When every file of the server's list is there, they are offered to the game as a resource pack that is always on,
 * and resources are reloaded. Leaving the server takes the pack away again.
 */
public final class ClientPackAssets {
    private ClientPackAssets() {}

    private static final PackLocationInfo LOCATION = new PackLocationInfo("aea_data_pack_assets",
        Component.literal("Data pack assets"), PackSource.SERVER, Optional.empty());
    private static final Pack.Metadata METADATA = new Pack.Metadata(Component.literal("Sent by the server (AEA)"),
        PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());

    /** A download in progress. */
    private static final class Incoming {
        final AssetNetwork.Listed listed;
        final byte[] data;
        int received;

        Incoming(AssetNetwork.Listed listed) {
            this.listed = listed;
            this.data = new byte[listed.size()];
        }
    }

    private static int generation = -1;
    /** The list being downloaded, and its hash. */
    private static List<AssetNetwork.Listed> pending = List.of();
    private static String pendingHash = "";
    private static final Map<String, Incoming> INCOMING = new HashMap<>();
    /** The files of the pack the game is using now, or null when there is none. */
    private static volatile Map<ResourceLocation, Path> active;
    private static String activeHash = "";

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientPackAssets::addPack);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            generation = -1;
            pending = List.of();
            INCOMING.clear();
            apply(null, "");
        });
    }

    private static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(packs -> {
            Map<ResourceLocation, Path> files = active;
            if (files == null) return;
            packs.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new AssetPackResources(location, files);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return new AssetPackResources(location, files);
                }
            }, METADATA, new PackSelectionConfig(true, Pack.Position.TOP, true)));
        });
    }

    private static Path cacheFile(String hash) {
        return FMLPaths.GAMEDIR.get().resolve("aea-asset-cache").resolve(hash);
    }

    private static boolean hex40(String s) {
        return s.length() == 40 && s.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }

    public static void onManifest(AssetNetwork.ManifestPayload manifest) {
        generation = manifest.generation();
        INCOMING.clear();
        pending = List.of();
        if (manifest.files().isEmpty()) {
            apply(null, "");
            return;
        }
        long total = 0;
        for (AssetNetwork.Listed f : manifest.files()) {
            total += f.size();
            if (!PackAssets.valid(f.path()) || !hex40(f.hash()) || f.size() <= 0 || f.size() > PackAssets.MAX_FILE || total > PackAssets.MAX_TOTAL) {
                AEA.LOGGER.warn("[AEA] The server's data pack asset list is not usable ({}); it was ignored.", f.path());
                return;
            }
        }
        if (!hex40(manifest.packHash()) || manifest.packHash().equals(activeHash)) return;
        pending = manifest.files();
        pendingHash = manifest.packHash();
        List<String> missing = new ArrayList<>();
        for (AssetNetwork.Listed f : pending) {
            Path file = cacheFile(f.hash());
            try {
                if (Files.isRegularFile(file) && Files.size(file) == f.size()) continue;
            } catch (IOException e) {
                // Treated as missing.
            }
            // Two paths can share one file; it is only downloaded once.
            if (INCOMING.values().stream().noneMatch(i -> i.listed.hash().equals(f.hash()))) {
                INCOMING.put(f.path(), new Incoming(f));
                missing.add(f.path());
            }
        }
        if (missing.isEmpty()) finish();
        else PacketDistributor.sendToServer(new AssetNetwork.RequestPayload(generation, missing));
    }

    public static void onChunk(AssetNetwork.ChunkPayload chunk) {
        if (chunk.generation() != generation) return;
        Incoming incoming = INCOMING.get(chunk.path());
        if (incoming == null) return;
        byte[] slice = chunk.data();
        // Chunks arrive in order; anything else means the two sides disagree, so the download is abandoned.
        if (chunk.offset() != incoming.received || slice.length > incoming.data.length - incoming.received) {
            abandon("out of order");
            return;
        }
        System.arraycopy(slice, 0, incoming.data, incoming.received, slice.length);
        incoming.received += slice.length;
        if (incoming.received < incoming.data.length) return;
        INCOMING.remove(chunk.path());
        String hash = incoming.listed.hash();
        if (!PackTextures.sha1(incoming.data).equals(hash)) {
            abandon("damaged file " + chunk.path());
            return;
        }
        try {
            Path file = cacheFile(hash);
            Files.createDirectories(file.getParent());
            Files.write(file, incoming.data);
        } catch (IOException e) {
            abandon(e.toString());
            return;
        }
        if (INCOMING.isEmpty()) finish();
    }

    private static void abandon(String why) {
        AEA.LOGGER.warn("[AEA] Data pack assets were not applied: {}.", why);
        INCOMING.clear();
        pending = List.of();
    }

    /** Every file is in the cache: turn the list into a pack and reload. */
    private static void finish() {
        Map<ResourceLocation, Path> files = new HashMap<>();
        for (AssetNetwork.Listed f : pending) {
            int slash = f.path().indexOf('/');
            ResourceLocation id = ResourceLocation.tryBuild(f.path().substring(0, slash), f.path().substring(slash + 1));
            if (id != null) files.put(id, cacheFile(f.hash()));
        }
        String hash = pendingHash;
        pending = List.of();
        apply(files, hash);
    }

    private static void apply(Map<ResourceLocation, Path> files, String hash) {
        if (hash.equals(activeHash)) return;
        active = files;
        activeHash = hash;
        Minecraft.getInstance().reloadResourcePacks();
    }
}
