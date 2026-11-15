package dev.alphasucks.aea.textures;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.AEAConfig;
import dev.alphasucks.aea.textures.client.ClientPackTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Data pack textures: PNG files under {@code data/<namespace>/textures/} are sent to every player and registered on
 * their game under {@code <namespace>:textures/...}, so a power can use them without a resource pack.
 */
public final class PackTextures {
    private PackTextures() {}

    public static final int MAX_FILE = 2 * 1024 * 1024;
    public static final int MAX_TOTAL = 32 * 1024 * 1024;
    public static final int MAX_COUNT = 1024;
    public static final int CHUNK = 30 * 1024;
    /** Chunks sent to one player per tick: about 4.7 MB a second. */
    private static final int CHUNKS_PER_TICK = 8;

    private record Stored(TextureNetwork.Listed listed, byte[] data) {}

    /** One file on its way to one player. */
    private static final class Transfer {
        final ResourceLocation id;
        final byte[] data;
        int offset;

        Transfer(ResourceLocation id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }

    private static volatile Map<ResourceLocation, Stored> stored = Map.of();
    private static volatile int generation;
    private static final Map<UUID, ArrayDeque<Transfer>> QUEUES = new HashMap<>();

    public static void register(IEventBus modBus) {
        modBus.addListener(PackTextures::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener((ResourceManagerReloadListener) PackTextures::load));
        NeoForge.EVENT_BUS.addListener(PackTextures::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> sendChunks(e.getServer().getPlayerList().getPlayers()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> QUEUES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> QUEUES.clear());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(TextureNetwork.ManifestPayload.TYPE, TextureNetwork.ManifestPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ClientPackTextures.onManifest(payload);
        });
        registrar.playToClient(TextureNetwork.ChunkPayload.TYPE, TextureNetwork.ChunkPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ClientPackTextures.onChunk(payload);
        });
        registrar.playToServer(TextureNetwork.RequestPayload.TYPE, TextureNetwork.RequestPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) onRequest(player, payload);
        });
    }

    /** Reads every data pack texture into memory. Runs on each data pack load. */
    private static void load(ResourceManager manager) {
        Map<ResourceLocation, Stored> found = new TreeMap<>();
        long total = 0;
        if (AEAConfig.packTextures()) {
            Map<ResourceLocation, Resource> resources = new TreeMap<>(manager.listResources("textures", id -> id.getPath().endsWith(".png")));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                ResourceLocation id = entry.getKey();
                if (found.size() >= MAX_COUNT) {
                    AEA.LOGGER.warn("[AEA] More than {} data pack textures; the rest are not sent.", MAX_COUNT);
                    break;
                }
                byte[] data;
                try (InputStream in = entry.getValue().open()) {
                    data = in.readNBytes(MAX_FILE + 1);
                } catch (IOException e) {
                    AEA.LOGGER.warn("[AEA] Could not read data pack texture {}: {}", id, e.toString());
                    continue;
                }
                if (data.length > MAX_FILE) {
                    AEA.LOGGER.warn("[AEA] Data pack texture {} is larger than {} KB and is not sent.", id, MAX_FILE / 1024);
                    continue;
                }
                if (total + data.length > MAX_TOTAL) {
                    AEA.LOGGER.warn("[AEA] Data pack textures add up to more than {} MB; {} and the rest are not sent.", MAX_TOTAL / (1024 * 1024), id);
                    break;
                }
                total += data.length;
                found.put(id, new Stored(new TextureNetwork.Listed(id, sha1(data), data.length), data));
            }
        }
        stored = found;
        generation++;
        if (!found.isEmpty()) AEA.LOGGER.info("[AEA] Loaded {} data pack texture(s), {} KB.", found.size(), total / 1024);
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fires when a player joins and after {@code /reload}. An empty list tells the client to drop what it had. */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        List<TextureNetwork.Listed> list = new ArrayList<>(stored.size());
        for (Stored s : stored.values()) list.add(s.listed());
        TextureNetwork.ManifestPayload manifest = new TextureNetwork.ManifestPayload(generation, list);
        event.getRelevantPlayers().forEach(player -> {
            if (player instanceof FakePlayer || player.connection == null || !player.connection.hasChannel(TextureNetwork.ManifestPayload.TYPE)) return;
            QUEUES.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, manifest);
        });
    }

    private static void onRequest(ServerPlayer player, TextureNetwork.RequestPayload request) {
        if (request.generation() != generation) return;
        Map<ResourceLocation, Stored> current = stored;
        ArrayDeque<Transfer> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        queue.clear();
        for (ResourceLocation id : request.ids()) {
            Stored s = current.get(id);
            if (s != null) queue.add(new Transfer(id, s.data()));
        }
        if (queue.isEmpty()) QUEUES.remove(player.getUUID());
    }

    private static void sendChunks(List<ServerPlayer> players) {
        if (QUEUES.isEmpty()) return;
        int current = generation;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            ArrayDeque<Transfer> queue = QUEUES.get(player.getUUID());
            if (queue == null) continue;
            for (int sent = 0; sent < CHUNKS_PER_TICK && !queue.isEmpty(); sent++) {
                Transfer transfer = queue.peek();
                int length = Math.min(CHUNK, transfer.data.length - transfer.offset);
                byte[] slice = Arrays.copyOfRange(transfer.data, transfer.offset, transfer.offset + length);
                PacketDistributor.sendToPlayer(player, new TextureNetwork.ChunkPayload(current, transfer.id, transfer.offset, slice));
                transfer.offset += length;
                if (transfer.offset >= transfer.data.length) queue.poll();
            }
            if (queue.isEmpty()) QUEUES.remove(player.getUUID());
        }
        // Drop queues of players who are gone.
        if (QUEUES.size() > players.size()) {
            Iterator<UUID> it = QUEUES.keySet().iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                boolean online = false;
                for (int i = 0; i < players.size(); i++) online |= players.get(i).getUUID().equals(id);
                if (!online) it.remove();
            }
        }
    }
}
