package dev.alphasucks.aea.assets;

import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.AEAConfig;
import dev.alphasucks.aea.assets.client.ClientPackAssets;
import dev.alphasucks.aea.textures.PackTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.storage.LevelResource;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Data pack assets: the {@code assets/} folder of every data pack in the world's {@code datapacks} folder is sent to
 * players and loaded on their game as a resource pack. Unlike data pack textures this reloads the player's resources,
 * so it also covers models, sounds, fonts, language files and animated textures.
 */
public final class PackAssets {
    private PackAssets() {}

    public static final int MAX_FILE = 16 * 1024 * 1024;
    public static final int MAX_TOTAL = 64 * 1024 * 1024;
    public static final int MAX_COUNT = 4096;
    public static final int CHUNK = 30 * 1024;
    private static final int CHUNKS_PER_TICK = 8;

    private record Stored(AssetNetwork.Listed listed, byte[] data) {}

    /** One file on its way to one player. */
    private static final class Transfer {
        final String path;
        final byte[] data;
        int offset;

        Transfer(String path, byte[] data) {
            this.path = path;
            this.data = data;
        }
    }

    private static volatile Map<String, Stored> stored = Map.of();
    private static volatile String packHash = "";
    private static volatile int generation;
    /** Set when data packs reload. The files are read again the next time a list is sent. */
    private static volatile boolean stale = true;
    private static final Map<UUID, ArrayDeque<Transfer>> QUEUES = new HashMap<>();

    public static void register(IEventBus modBus) {
        modBus.addListener(PackAssets::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener((ResourceManagerReloadListener) manager -> stale = true));
        NeoForge.EVENT_BUS.addListener(PackAssets::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> sendChunks(e.getServer().getPlayerList().getPlayers()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> QUEUES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            QUEUES.clear();
            stored = Map.of();
            stale = true;
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(AssetNetwork.ManifestPayload.TYPE, AssetNetwork.ManifestPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ClientPackAssets.onManifest(payload);
        });
        registrar.playToClient(AssetNetwork.ChunkPayload.TYPE, AssetNetwork.ChunkPayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ClientPackAssets.onChunk(payload);
        });
        registrar.playToServer(AssetNetwork.RequestPayload.TYPE, AssetNetwork.RequestPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) onRequest(player, payload);
        });
    }

    /** Reads the {@code assets/} folder of every enabled data pack in the world's {@code datapacks} folder. */
    private static void load(MinecraftServer server) {
        Map<String, Stored> found = new TreeMap<>();
        if (AEAConfig.packAssets()) {
            Path packs = server.getWorldPath(LevelResource.DATAPACK_DIR);
            // Later packs win, as they do for data.
            for (Pack pack : server.getPackRepository().getSelectedPacks()) {
                if (!pack.getId().startsWith("file/")) continue;
                Path root = packs.resolve(pack.getId().substring("file/".length()));
                try {
                    if (Files.isDirectory(root)) {
                        collect(root.resolve("assets"), found);
                    } else if (Files.isRegularFile(root)) {
                        try (FileSystem zip = FileSystems.newFileSystem(root)) {
                            collect(zip.getPath("assets"), found);
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    AEA.LOGGER.warn("[AEA] Could not read the assets of data pack {}: {}", pack.getId(), e.toString());
                }
            }
        }
        long total = 0;
        StringBuilder all = new StringBuilder();
        for (Stored s : found.values()) {
            total += s.data().length;
            all.append(s.listed().path()).append('\0').append(s.listed().hash()).append('\n');
        }
        stored = found;
        packHash = found.isEmpty() ? "" : PackTextures.sha1(all.toString().getBytes(StandardCharsets.UTF_8));
        generation++;
        if (!found.isEmpty()) AEA.LOGGER.info("[AEA] Loaded {} data pack asset file(s), {} KB.", found.size(), total / 1024);
    }

    private static void collect(Path assets, Map<String, Stored> out) throws IOException {
        if (!Files.isDirectory(assets)) return;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(assets)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        long total = 0;
        for (Stored s : out.values()) total += s.data().length;
        for (Path file : files) {
            String path = assets.relativize(file).toString().replace('\\', '/');
            if (!valid(path)) {
                AEA.LOGGER.warn("[AEA] Data pack asset {} has a name the game cannot use and is not sent.", path);
                continue;
            }
            long size = Files.size(file);
            if (size == 0) continue;
            if (size > MAX_FILE) {
                AEA.LOGGER.warn("[AEA] Data pack asset {} is larger than {} MB and is not sent.", path, MAX_FILE / (1024 * 1024));
                continue;
            }
            Stored previous = out.get(path);
            long without = total - (previous == null ? 0 : previous.data().length);
            if (previous == null && out.size() >= MAX_COUNT) {
                AEA.LOGGER.warn("[AEA] More than {} data pack asset files; {} and the rest are not sent.", MAX_COUNT, path);
                return;
            }
            if (without + size > MAX_TOTAL) {
                AEA.LOGGER.warn("[AEA] Data pack assets add up to more than {} MB; {} and the rest are not sent.", MAX_TOTAL / (1024 * 1024), path);
                return;
            }
            byte[] data = Files.readAllBytes(file);
            total = without + data.length;
            out.put(path, new Stored(new AssetNetwork.Listed(path, PackTextures.sha1(data), data.length), data));
        }
    }

    /** {@code path} is {@code <namespace>/<path inside the namespace>}, both made of characters an ID allows. */
    public static boolean valid(String path) {
        int slash = path.indexOf('/');
        return slash > 0 && slash < path.length() - 1 && path.length() <= AssetNetwork.MAX_PATH
            && ResourceLocation.isValidNamespace(path.substring(0, slash)) && ResourceLocation.isValidPath(path.substring(slash + 1));
    }

    /** Fires when a player joins and after {@code /reload}. An empty list tells the client to drop what it had. */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (stale) {
            stale = false;
            load(event.getPlayerList().getServer());
        }
        List<AssetNetwork.Listed> list = new ArrayList<>(stored.size());
        for (Stored s : stored.values()) list.add(s.listed());
        AssetNetwork.ManifestPayload manifest = new AssetNetwork.ManifestPayload(generation, packHash, list);
        event.getRelevantPlayers().forEach(player -> {
            if (player instanceof FakePlayer || player.connection == null || !player.connection.hasChannel(AssetNetwork.ManifestPayload.TYPE)) return;
            QUEUES.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, manifest);
        });
    }

    private static void onRequest(ServerPlayer player, AssetNetwork.RequestPayload request) {
        if (request.generation() != generation) return;
        Map<String, Stored> current = stored;
        ArrayDeque<Transfer> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        queue.clear();
        for (String path : request.paths()) {
            Stored s = current.get(path);
            if (s != null) queue.add(new Transfer(path, s.data()));
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
                PacketDistributor.sendToPlayer(player, new AssetNetwork.ChunkPayload(current, transfer.path, transfer.offset, slice));
                transfer.offset += length;
                if (transfer.offset >= transfer.data.length) queue.poll();
            }
            if (queue.isEmpty()) QUEUES.remove(player.getUUID());
        }
    }
}
